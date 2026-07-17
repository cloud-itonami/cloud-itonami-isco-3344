(ns medsecretary.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [medsecretary.actor :as actor]
            [medsecretary.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-provider! st {:provider-id "provider-1" :name "Riverside Family Medicine"})
    (store/register-appointment! st {:appointment-id "appt-1" :provider-id "provider-1"
                                     :status :scheduled})
    st))

(deftest commits-a-registered-appointment-filing
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:provider-id "provider-1" :op :log-appointment-record :stake :low
                 :appointment-id "appt-1" :filing-status :filed}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "provider-1"))))))

(deftest holds-a-filing-against-unknown-appointment
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:provider-id "provider-1" :op :log-appointment-record :stake :low
                 :appointment-id "appt-ghost" :filing-status :filed}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "provider-1")))))

(deftest commits-a-patient-scheduling-proposal
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:provider-id "provider-1" :op :schedule-patient-operation :stake :low
                 :appointment-id "appt-2" :requested-time "2026-08-01T09:00:00Z"}
        result (actor/run-request! graph request {} "thread-3")]
    (is (= :done (:status result)))
    (is (= 1 (count (store/records-of st "provider-1"))))))

(deftest holds-a-scope-excluded-clinical-judgment-request
  (testing "the actor never commits, and never even offers for human approval, an op that
           provides clinical judgment, authorizes a prescription refill, or finalizes
           record disclosure — hard permanent block regardless of confidence"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          request {:provider-id "provider-1" :op :provide-clinical-judgment :stake :high}
          result (actor/run-request! graph request {} "thread-4")]
      (is (= :done (:status result)))
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/records-of st "provider-1"))))))

(deftest holds-a-scope-excluded-prescription-authorization-request
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:provider-id "provider-1" :op :authorize-prescription-refill :stake :high}
        result (actor/run-request! graph request {} "thread-5")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "provider-1")))))

(deftest holds-a-scope-excluded-disclosure-finalization-request
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:provider-id "provider-1" :op :finalize-record-disclosure :stake :high}
        result (actor/run-request! graph request {} "thread-6")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "provider-1")))))

(deftest interrupts-then-approves-a-privacy-concern-flag-on-human-approval
  (testing "flag-privacy-concern always escalates, even though it is a legitimate, allowlisted op"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          request {:provider-id "provider-1" :op :flag-privacy-concern :stake :low
                   :concern-type :disclosure-risk}
          interrupted (actor/run-request! graph request {} "thread-7")]
      (is (= :interrupted (:status interrupted)))
      (is (empty? (store/records-of st "provider-1")))
      (let [resumed (actor/approve! graph "thread-7")]
        (is (= :done (:status resumed)))
        (is (= 1 (count (store/records-of st "provider-1"))))))))

(deftest commits-a-below-threshold-supply-order
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:provider-id "provider-1" :op :coordinate-supply-order :stake :low
                 :supply-item "gauze" :cost 50}
        result (actor/run-request! graph request {} "thread-8")]
    (is (= :done (:status result)))
    (is (= 1 (count (store/records-of st "provider-1"))))))

(deftest interrupts-then-approves-an-above-threshold-supply-order
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:provider-id "provider-1" :op :coordinate-supply-order :stake :low
                 :supply-item "exam-table" :cost 5000}
        interrupted (actor/run-request! graph request {} "thread-9")]
    (is (= :interrupted (:status interrupted)))
    (let [resumed (actor/approve! graph "thread-9")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "provider-1")))))))
