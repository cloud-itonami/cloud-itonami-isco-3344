(ns medsecretary.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [medsecretary.store :as store]
            [medsecretary.advisor :as advisor]
            [medsecretary.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-provider! st {:provider-id "provider-1" :name "Riverside Family Medicine"})
    (store/register-appointment! st {:appointment-id "appt-1" :provider-id "provider-1"
                                     :status :scheduled})
    st))

(defn- filing-op [appointment-id]
  {:op :log-appointment-record :effect :propose :provider-id "provider-1"
   :appointment-id appointment-id :filing-status :filed
   :confidence 0.9 :stake :low})

(def ^:private req {:provider-id "provider-1"})

(deftest ok-for-registered-provider-and-appointment-filing
  (let [st (fresh-store)
        v (governor/check req {} (filing-op "appt-1") st)]
    (is (:ok? v))
    (is (not (:hard? v)))
    (is (not (:escalate? v)))))

(deftest hard-on-unregistered-provider
  (let [st (fresh-store)
        v (governor/check {:provider-id "nobody"} {} (filing-op "appt-1") st)]
    (is (:hard? v))
    (is (some #(= :no-provider (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (filing-op "appt-1") :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest hard-on-op-not-allowlisted
  (let [st (fresh-store)
        v (governor/check req {} (assoc (filing-op "appt-1") :op :unknown-op) st)]
    (is (:hard? v))
    (is (some #(= :op-not-allowlisted (:rule %)) (:violations v)))))

(deftest hard-on-scope-excluded-op-finalize-record-disclosure
  (testing "finalizing disclosure of a patient's medical record to a third party is permanently outside this actor's scope"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (filing-op "appt-1") :op :finalize-record-disclosure
                                          :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-op (:rule %)) (:violations v)))
      (is (not (:escalate? v)) "a hard permanent block is never merely escalated"))))

(deftest hard-on-scope-excluded-op-provide-clinical-judgment
  (testing "providing clinical judgment or advice is permanently outside this actor's scope"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (filing-op "appt-1") :op :provide-clinical-judgment
                                          :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-op (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-op-authorize-prescription-refill
  (testing "authorizing a prescription/refill is permanently outside this actor's scope"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (filing-op "appt-1") :op :authorize-prescription-refill
                                          :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-op (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-language-in-rationale
  (testing "defense-in-depth: an otherwise-allowlisted op with scope-exclusion action-phrase language in its rationale is still hard-blocked"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (filing-op "appt-1")
                                          :rationale "proposed to finalize the disclosure of the patient record to the requesting insurer")
                            st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-language (:rule %)) (:violations v))))))

(deftest hard-on-unknown-appointment-for-filing
  (let [st (fresh-store)
        v (governor/check req {} (filing-op "appt-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-appointment (:rule %)) (:violations v)))))

(deftest hard-on-appointment-wrong-provider
  (let [st (fresh-store)]
    (store/register-provider! st {:provider-id "provider-2" :name "Other Clinic"})
    (let [v (governor/check {:provider-id "provider-2"} {} (filing-op "appt-1") st)]
      (is (:hard? v))
      (is (some #(= :appointment-wrong-provider (:rule %)) (:violations v))))))

(deftest scheduling-a-new-appointment-does-not-require-a-pre-existing-one
  (testing "scheduling (unlike filing) creates the appointment, so no appointment-basis check applies"
    (let [st (fresh-store)
          v (governor/check req {} {:op :schedule-patient-operation :effect :propose
                                    :provider-id "provider-1" :appointment-id "appt-new"
                                    :requested-time "2026-08-01T09:00:00Z"
                                    :confidence 0.9 :stake :low} st)]
      (is (:ok? v)))))

(deftest always-escalates-flag-privacy-concern-even-at-high-confidence
  (testing "a 'flag a concern' op must always escalate and never be auto-commit-eligible"
    (let [st (fresh-store)
          v (governor/check req {} {:op :flag-privacy-concern :effect :propose
                                    :provider-id "provider-1" :concern-type :disclosure-risk
                                    :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v))
      (is (not (:ok? v))))))

(deftest escalates-supply-order-above-cost-threshold-even-at-high-confidence
  (let [st (fresh-store)
        v (governor/check req {} {:op :coordinate-supply-order :effect :propose
                                  :provider-id "provider-1" :supply-item "exam-table-paper"
                                  :cost (inc governor/supply-cost-ceiling)
                                  :confidence 0.99 :stake :low} st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest ok-supply-order-at-or-below-cost-threshold
  (testing "the cost threshold is inclusive"
    (let [st (fresh-store)
          v (governor/check req {} {:op :coordinate-supply-order :effect :propose
                                    :provider-id "provider-1" :supply-item "gauze"
                                    :cost governor/supply-cost-ceiling
                                    :confidence 0.9 :stake :low} st)]
      (is (:ok? v)))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (filing-op "appt-1") :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest default-mock-advisor-proposals-never-self-trip-scope-exclusion
  (testing "regression guard for the sibling-track self-tripping bug: the governor's own
           scope-exclusion rationale scan must never match the mock advisor's default
           rationale for any of the four legitimate, allowlisted ops"
    (let [st (fresh-store)
          requests [{:op :log-appointment-record :provider-id "provider-1" :stake :low
                     :appointment-id "appt-1" :filing-status :filed}
                    {:op :schedule-patient-operation :provider-id "provider-1" :stake :low
                     :appointment-id "appt-new" :requested-time "2026-08-01T09:00:00Z"}
                    {:op :flag-privacy-concern :provider-id "provider-1" :stake :low
                     :concern-type :disclosure-risk}
                    {:op :coordinate-supply-order :provider-id "provider-1" :stake :low
                     :supply-item "gauze" :cost 50}]]
      (doseq [request requests]
        (let [proposal (advisor/-advise (advisor/mock-advisor) st request)
              v (governor/check {:provider-id "provider-1"} {} proposal st)]
          (is (not (some #(= :scope-excluded-language (:rule %)) (:violations v)))
              (str "op " (:op request) " self-tripped the scope-exclusion rationale scan: "
                   (:rationale proposal))))))))
