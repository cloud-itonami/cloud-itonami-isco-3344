(ns medsecretary.governor
  "MedicalSecretaryGovernor — the independent safety/traceability layer
  named in this repository's README/business-model.md, gating every
  scheduling/filing operation an advisor may propose for a provider.
  The governor never dispatches hardware itself and never finalizes
  disclosure of a patient's medical record, provides clinical judgment
  or advice, or authorizes a prescription/refill — this actor
  coordinates SCHEDULING/FILING LOGISTICS ONLY. Modeled on
  cloud-itonami-isco-3313's accountingsupport.governor. Task twist:
  medical-privacy is the strictest domain in this batch, so the scope
  exclusion is enforced TWICE, independently: (1) a closed op allowlist
  plus an explicit denylist checked against the proposal's `:op`
  keyword (the primary, reliable mechanism — keyword equality never has
  false positives), and (2) a defense-in-depth scan of the proposal's
  free-text `:rationale` for scope-exclusion ACTION-phrases (e.g.
  'finalize the disclosure of the patient record', not the bare noun
  'medical record') so that scope creep hidden in advisor prose (e.g. a
  future LLM advisor) is caught even under an otherwise-allowlisted
  :op. The terms are deliberately phrased as the finalization/execution
  action rather than a bare noun, because a bare noun like 'medical
  record' would false-positive on entirely legitimate rationale text
  (this is the exact self-tripping bug pattern observed on the sibling
  ISIC industry-actor track: a governor's own scope-exclusion term list
  matched inside the mock advisor's own default rationale for a
  legitimate proposal). See
  `medsecretary.governor-test/default-mock-advisor-proposals-never-self-trip-scope-exclusion`
  for the dedicated regression test proving the mock advisor's default
  proposals for all four allowlisted ops never trip rule (2).

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. provider provenance    — the provider/practice record must be
                                independently verified/registered
                                before any action.
    2. no-actuation           — proposal :effect must be :propose (the
                                governor never dispatches hardware and
                                never finalizes a disclosure, clinical
                                judgment or prescription authorization;
                                it only gates what the advisor may log
                                or schedule).
    3. closed op-allowlist    — the proposal's :op must be one of
                                `closed-ops`; anything else (including
                                but not limited to `scope-excluded-ops`)
                                is rejected.
    4. scope-excluded op      — :finalize-record-disclosure,
                                :provide-clinical-judgment and
                                :authorize-prescription-refill are a
                                hard, PERMANENT block — never
                                auto-commit-eligible, never
                                approval-eligible. This actor never
                                finalizes disclosure of a patient's
                                medical record to any third party,
                                never provides medical/clinical
                                judgment or advice, and never
                                authorizes a prescription/refill.
    5. scope-excluded language — the proposal's :rationale must not
                                contain a scope-exclusion action-phrase
                                (defense-in-depth; see docstring above).
    6. appointment basis      — an appointment-filing proposal
                                (`:log-appointment-record`) must cite a
                                REGISTERED appointment belonging to this
                                provider (scheduling a NEW appointment
                                via `:schedule-patient-operation` does
                                not require a pre-existing one).
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off per
  business-model.md's Trust Controls — these are :high/
  :safety-critical regardless of confidence):
    7. :op :flag-privacy-concern — surfacing a PHI-disclosure-risk or
                                consent concern always escalates to a
                                human and is never in any auto-commit
                                path.
    8. :op :coordinate-supply-order with :cost above
                                `supply-cost-ceiling` (procurement above
                                the threshold always requires human
                                sign-off, unlike a hard block — it is a
                                legitimate operation, just not one the
                                actor auto-commits at scale).
    9. low confidence (< `confidence-floor`)."
  (:require [clojure.string :as str]
            [medsecretary.store :as store]))

(def confidence-floor 0.6)
(def supply-cost-ceiling 500)

(def closed-ops
  "The complete, closed set of ops this actor may ever propose. Every
  proposal's :op must be a member of this set — see `hard-violations`
  rule :op-not-allowlisted."
  #{:log-appointment-record :schedule-patient-operation
    :flag-privacy-concern :coordinate-supply-order})

(def scope-excluded-ops
  "Ops that directly finalize disclosure of a patient's medical record
  to a third party, provide clinical judgment/advice, or authorize a
  prescription/refill. Never allowed, regardless of confidence or
  human approval — a hard, permanent block. These are not part of
  `closed-ops` (so rule :op-not-allowlisted alone would already reject
  them); they are checked as an explicit, separately-named rule so the
  block reads as an intentional permanent scope exclusion rather than
  an incidental 'unknown op'."
  #{:finalize-record-disclosure :provide-clinical-judgment
    :authorize-prescription-refill})

(def ^:private scope-exclusion-terms
  "Defense-in-depth free-text scan terms, phrased as the
  finalization/execution ACTION (verb + object), never as a bare noun
  like 'medical record' alone — a bare noun would false-positive on
  ordinary, legitimate rationale text (e.g. 'proposed
  log-appointment-record for provider ...' never mentions these
  multi-word action phrases, but could easily mention the bare noun
  'record')."
  ["finalize the disclosure of the patient record"
   "finalize disclosure of the patient record"
   "provide clinical judgment"
   "provide medical advice"
   "authorize a prescription refill"
   "authorize the prescription refill"])

(defn- mentions-scope-exclusion? [text]
  (let [norm (str/lower-case (or text ""))]
    (boolean (some #(str/includes? norm %) scope-exclusion-terms))))

(def ^:private always-escalate-ops #{:flag-privacy-concern})

(defn- hard-violations [{:keys [request proposal]} provider-record appt]
  (let [{:keys [op appointment-id rationale]} proposal
        filing? (= :log-appointment-record op)]
    (cond-> []
      (nil? provider-record)
      (conj {:rule :no-provider :detail "未登録 provider/practice record"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation
             :detail "effect は :propose のみ許可（governor は診療記録の開示確定・臨床判断・処方許可を直接実行しない）"})

      (not (contains? closed-ops op))
      (conj {:rule :op-not-allowlisted :detail (str "op " op " は closed allowlist に無い")})

      (contains? scope-excluded-ops op)
      (conj {:rule :scope-excluded-op
             :detail "患者診療記録の開示確定・臨床判断（助言含む）・処方(refill)許可はこのactorの権限外 — 常時 hard permanent block"})

      (mentions-scope-exclusion? rationale)
      (conj {:rule :scope-excluded-language
             :detail "proposal の rationale に開示確定/臨床判断/処方許可を示す文言が含まれる"})

      (and filing? appointment-id (nil? appt))
      (conj {:rule :unknown-appointment :detail "未登録 appointment への filing 提案は不可"})

      (and filing? appt (not= (:provider-id appt) (:provider-id request)))
      (conj {:rule :appointment-wrong-provider :detail "appointment が別 provider のもの"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `medsecretary.store/Store`. Pure — never mutates
  the store, never finalizes a disclosure, clinical judgment or
  prescription authorization."
  [request context proposal store]
  (let [provider-record (store/provider store (:provider-id request))
        appt (some->> (:appointment-id proposal) (store/appointment store))
        hard (hard-violations {:request request :proposal proposal}
                              provider-record appt)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        always-risky? (contains? always-escalate-ops (:op proposal))
        cost-over? (and (= :coordinate-supply-order (:op proposal))
                        (number? (:cost proposal))
                        (> (:cost proposal) supply-cost-ceiling))]
    {:ok? (and (not hard?) (not low?) (not always-risky?) (not cost-over?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky? cost-over?))}))
