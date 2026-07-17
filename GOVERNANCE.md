# Governance

`cloud-itonami-isco-3344` is an OSS open-occupation blueprint. Governance covers
both code and the operator model.

## Maintainers

Maintainers may merge changes that preserve these invariants:

- the Advisor cannot directly dispatch robot actions, finalize a disclosure,
  provide clinical judgment, or authorize a prescription/refill.
- Medical Secretary Governor remains independent of the advisor.
- hard policy violations (including the permanent scope exclusion) cannot be
  overridden by human approval.
- every commit, hold and approval path is auditable.
- real patient/provider/operator data stays outside Git.

## Decision Records

Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification, scope
exclusion or license should add or update an ADR.

## Operator Governance

Anyone may fork and operate independently. itonami.cloud certification is a
separate trust mark and should require security, audit, support and data-flow
review.

Certified operators can lose certification for:

- bypassing policy checks
- mishandling patient/provider/operator data
- misrepresenting certification status
- failing to respond to security incidents
- hiding material changes to customer-facing operation
- attempting to widen scope into disclosure finalization, clinical judgment
  or prescription/refill authorization
