(ns medsecretary.store
  "SSoT for the ISCO-08 3344 medical secretarial scheduling/filing
  coordination practice actor (itonami actor pattern, ADR-2607011000 /
  CLAUDE.md Actors section; README's 'Robotics premise' — a medical
  office scheduling and filing robot performs appointment coordination,
  chart-filing-status logging and supply-order coordination under this
  advisor/governor pair, which never dispatches hardware itself and
  never finalizes disclosure of a patient's medical record, provides
  clinical judgment, or authorizes a prescription/refill). Modeled on
  cloud-itonami-isco-3313's accountingsupport.store.

  This actor is SCHEDULING/FILING LOGISTICS ONLY — it never stores or
  exposes actual clinical content, only scheduling/filing metadata:

    provider    — a registered/independently-verified provider or
                  practice record (:provider-id, :name). No action may
                  proceed without one.
    appointment — a registered patient-appointment resource
                  {:appointment-id :provider-id :status}. `:status` is
                  scheduling metadata only (e.g. :scheduled, :completed,
                  :cancelled) — never clinical content. Appointment
                  filing (`:log-appointment-record`) requires the cited
                  appointment to already be registered and to belong to
                  the requesting provider; scheduling a NEW appointment
                  (`:schedule-patient-operation`) does not.
    record      — a committed operating record (a logged filing-status
                  update, a scheduling confirmation, a flagged privacy
                  concern, or a supply-order coordination) — written
                  ONLY via commit-record!. Never contains clinical
                  content, only the metadata fields on the proposal.
    ledger      — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (provider [s provider-id])
  (appointment [s appointment-id])
  (records-of [s provider-id])
  (ledger [s])
  (register-provider! [s provider])
  (register-appointment! [s appt])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (provider [_ provider-id] (get-in @a [:providers provider-id]))
  (appointment [_ appointment-id] (get-in @a [:appointments appointment-id]))
  (records-of [_ provider-id] (filter #(= provider-id (:provider-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-provider! [s prov]
    (swap! a assoc-in [:providers (:provider-id prov)] prov) s)
  (register-appointment! [s appt]
    (swap! a assoc-in [:appointments (:appointment-id appt)] appt) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:providers {} :appointments {} :records [] :ledger []}
                                   seed)))))
