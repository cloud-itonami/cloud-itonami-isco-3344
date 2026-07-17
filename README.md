# cloud-itonami-isco-3344

Open Occupation Blueprint for **ISCO-08 3344**: Medical Secretaries.

This repository designs a forkable OSS business for an independent medical secretarial scheduling and filing coordination practice: a medical office scheduling and filing robot manages appointment coordination and chart-filing-status logistics under a governor-gated actor, so the practice keeps its own scheduling/filing records instead of renting a closed practice-management SaaS.

**Maturity: `:implemented`.** `src/medsecretary/` implements the
`MedicalSecretaryActor` as a `langgraph.graph/state-graph`
(`medsecretary.actor`) wired to a `Medical Secretary Advisor`
(`medsecretary.advisor`) and an independent `MedicalSecretaryGovernor`
(`medsecretary.governor`), following the itonami actor pattern
(ADR-2607011000): `:intake -> :advise -> :govern -> :decide -+-> :commit
(:ok?) +-> :request-approval (:escalate?, human-in-the-loop interrupt)
+-> :hold (:hard?)`. 25 tests / 58 assertions green (`clojure -M:test`).

HARD invariants (always hold, never overridable): provider/practice
provenance (the provider or practice record must be independently
verified/registered before any action), no-actuation (`:effect` must
be `:propose`), a closed op-allowlist, a registered-appointment basis
for any filing proposal, and — the strictest guardrail in this
repository — a **permanent scope exclusion**: this actor never
finalizes disclosure of a patient's medical record to any third party,
never provides medical/clinical judgment or advice, and never
authorizes a prescription/refill. That exclusion is enforced twice,
independently: by op-keyword denylist AND by a defense-in-depth scan of
proposal rationale text for the finalization/execution action-phrase
(never a bare noun, to avoid false-positiving on legitimate text — see
`medsecretary.governor` docstring).

Always-escalate ops (human sign-off regardless of confidence, mapping
this repo's Trust Controls in
[`docs/business-model.md`](docs/business-model.md)):
`:flag-privacy-concern` (surfacing a PHI-disclosure-risk or consent
concern always escalates — never in any auto-commit path) and
`:coordinate-supply-order` above the registered cost threshold.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical/administrative domain work**. Here a medical office scheduling and filing robot performs appointment coordination, chart-filing-status logging and supply-order coordination under an actor that proposes actions and an independent **Medical Secretary Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions (such as flagging a PHI-disclosure/consent concern, or a supply order above the registered cost threshold) require human sign-off. This actor coordinates SCHEDULING/FILING LOGISTICS ONLY — it is never a source of clinical judgment, prescription authority, or disclosure authority, and it never stores or exposes actual clinical content, only scheduling/filing metadata.

## Core Contract

```text
provider appointment book + chart filing queue + supply request
        |
        v
Medical Secretary Advisor -> Medical Secretary Governor -> log/schedule/coordinate, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses,
suppress an operating record, finalize disclosure of a patient's
medical record, provide clinical judgment, authorize a
prescription/refill, or disclose sensitive data without governor
approval and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `3344`). Required capabilities:

- :robotics
- :identity
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
