# Contributing

`cloud-itonami-isco-3344` accepts contributions to the OSS actor, policy tests,
documentation, examples and open occupation blueprint.

## Development

```bash
clojure -M:dev:test
clojure -M:lint
```

Keep changes small and include tests for policy, audit, store or disclosure
behavior.

## Rules

- Do not commit real patient, provider or operating data.
- Keep production writes and disclosures behind Medical Secretary Governor.
- Never weaken, remove or bypass the scope exclusion (no disclosure
  finalization, no clinical judgment/advice, no prescription/refill
  authorization) — that invariant is permanent, not tunable.
- Treat this occupation's workflows as high-risk: add tests for permission,
  purpose, safety and audit logging.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests

PRs should describe:

- what behavior changed
- which policy invariant is affected
- how it was tested
- whether operator or certification docs need updates
