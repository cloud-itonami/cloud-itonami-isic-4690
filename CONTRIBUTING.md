# Contributing

`cloud-itonami-isic-4690` accepts contributions to the OSS blueprint, the
Shosha Trading Governor, decision-rule tests, documentation and operator
model.

## Development
The capability layer is SELF-CONTAINED. There is no pre-existing bespoke
general-trading (non-specialized wholesale) capability library to wrap; the
counterparty-credit / contract-on-file / export-control-classification /
sanctions-screening checks live directly in `shosha.governor`. This repo
holds the business blueprint, the langgraph-clj actor and the operator
contracts.

```bash
clojure -M:dev:test
clojure -M:lint
```

## Rules
- Do not commit real counterparty, credit, sanctions-screening or trade data.
- Keep shipment dispatch and invoice settlement behind the Shosha Trading
  Governor.
- Treat cross-border trade workflows as high-risk: add tests for spec-basis,
  evidence completeness, credit clearance, contract-on-file, export-control
  classification, sanctions screening and audit logging.
- Never fabricate a jurisdiction's export-control or sanctions requirements
  in `shosha.facts` -- cite a real official source or leave the jurisdiction
  out of the catalog.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests
PRs should describe: what behavior changed, which governor invariant is
affected, how it was tested, whether operator or certification docs need
updates.
