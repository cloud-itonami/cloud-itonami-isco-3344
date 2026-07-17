# Operator Guide

## First Deployment

1. Define the operator's service area and intake process.
2. Define consent and purpose categories for scheduling/filing metadata
   (never clinical content).
3. Run synthetic operating cases.
4. Enable human-reviewed sign-off for `:high`/`:safety-critical` actions
   (`:flag-privacy-concern`, above-threshold `:coordinate-supply-order`).
5. Measure operating outcomes and audit coverage.

## Minimum Production Controls

- consent and disclosure log
- safety-critical escalation path for PHI-disclosure/consent concerns
- provenance for all operating records
- human review for high-risk cases
- audit export for all gated actions
- a hard technical block preventing any disclosure finalization, clinical
  judgment/advice, or prescription/refill authorization from ever being
  auto-committed or approval-eligible

## Certification

Certified operators must prove that the governor gates every
safety-critical robot action, that safety-critical risks escalate to
humans, and that the actor's permanent scope exclusion (no disclosure
finalization, no clinical judgment, no prescription/refill
authorization) cannot be bypassed by any operator configuration.
