# Security Policy

This project handles medical secretarial scheduling/filing operating
workflows that touch protected health information (PHI) metadata. Treat
vulnerabilities as potentially high impact even when the demo data is
synthetic.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure
- real patient, provider or operator data exposure
- authorization bypass
- Medical Secretary Governor bypass
- audit-ledger tampering
- over-disclosure in reports or exports
- any path that finalizes disclosure of a patient's medical record, provides
  clinical judgment, or authorizes a prescription/refill
- unsafe robot action dispatch

## Reporting

Use GitHub private vulnerability reporting when available for the repository.
If that is unavailable, contact the repository maintainers through the
cloud-itonami organization before publishing details.

Include:

- affected commit or version
- reproduction steps
- expected and actual behavior
- impact on patient/provider data, policy enforcement or audit logging
- suggested fix, if known

## Production Guidance

- Store secrets outside Git.
- Keep real patient/provider/operator data outside this repository.
- Run policy tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for operators and service accounts.
