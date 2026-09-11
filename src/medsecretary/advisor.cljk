(ns medsecretary.advisor
  "Medical Secretary Advisor — the advisor named in this repository's
  README, proposing a scheduling/filing coordination operation (log an
  appointment/chart filing-status update, schedule a patient
  appointment, flag a PHI-disclosure or consent concern, coordinate a
  supply order) from a provider's appointment book and filing queue.
  Swappable mock/llm; the advisor ONLY proposes —
  `medsecretary.governor` independently enforces the closed op
  allowlist, the provider/appointment basis, the scope exclusion (never
  finalize record disclosure, never provide clinical judgment, never
  authorize a prescription/refill) and always escalates
  `:flag-privacy-concern` and above-threshold supply orders. Modeled on
  cloud-itonami-isco-3313's advisor.

  A proposal: {:op :log-appointment-record|:schedule-patient-operation|
                    :flag-privacy-concern|:coordinate-supply-order
               :effect :propose :provider-id str
               :appointment-id str? :filing-status kw?
               :requested-time str? :concern-type kw?
               :supply-item str? :cost number?
               :stake kw :confidence n :rationale str}

  The advisor never proposes clinical content — only scheduling/filing
  metadata (a filing-status keyword, a requested time, a concern-type
  tag, a supply-item label and cost). The `:rationale` template is kept
  free of the governor's scope-exclusion action-phrases (see
  `medsecretary.governor` docstring) by construction — see
  `medsecretary.governor-test/default-mock-advisor-proposals-never-self-trip-scope-exclusion`
  for the dedicated regression test guarding this."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])))

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake provider-id appointment-id filing-status
                              requested-time concern-type supply-item cost]
                       :as request}]
  {:op op
   :effect :propose
   :provider-id provider-id
   :appointment-id appointment-id
   :filing-status filing-status
   :requested-time requested-time
   :concern-type concern-type
   :supply-item supply-item
   :cost cost
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for provider " (:provider-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a medical-secretary scheduling and filing advisor. Given a
   request, propose an :op, the :provider-id, and only scheduling/filing
   metadata (:appointment-id, :filing-status, :requested-time,
   :concern-type, :supply-item, :cost as applicable) — never clinical
   content. Never propose to finalize disclosure of a patient's medical
   record to a third party, to provide clinical judgment or advice, or
   to authorize a prescription/refill — those are permanently outside
   this actor's scope regardless of confidence. Always flag PHI-
   disclosure or consent concerns via :flag-privacy-concern rather than
   acting on them; that op and any supply order above the cost
   threshold always require human sign-off regardless of confidence.")

(defn- parse-proposal [content]
  (try
    (let [p (edn/read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
