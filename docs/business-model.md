# Business Model: Independent Medical Secretarial Scheduling & Filing Coordination Practice

## Classification

- Repository: `cloud-itonami-isco-3344`
- ISCO-08: `3344`
- Occupation: Medical Secretaries
- Social impact: healthcare-administration-support, patient-privacy-protection, local-jobs

## Customer

- independent medical practices
- clinics and provider groups

## Offer

- appointment scheduling coordination
- chart-filing-status logging (metadata only — never clinical content)
- PHI-disclosure/consent concern flagging
- medical-office supply order coordination

## Revenue

- monthly retainer
- per-appointment coordination fee

## Trust Controls

- provider/practice provenance verified before any action
- this actor never finalizes disclosure of a patient's medical record to any
  third party, never provides medical/clinical judgment or advice, and never
  authorizes a prescription/refill — permanently outside scope, enforced by a
  closed op-allowlist/denylist and a defense-in-depth rationale-text scan
- PHI-disclosure-risk and consent concerns are always flagged for human
  review, never auto-resolved
- supply-order coordination above the registered cost threshold always
  requires human sign-off
- scheduling and filing records are auditable, not editable, and contain only
  scheduling/filing metadata — never clinical content
