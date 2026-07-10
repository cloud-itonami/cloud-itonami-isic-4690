# Governance

`cloud-itonami-isic-4690` is an OSS open-business blueprint for a
non-specialized (general/diversified) wholesale trading house -- the
Japanese-style sogo-shosha archetype (Mitsubishi Corp / Mitsui / Marubeni /
Sumitomo / Itochu / Sojitz).

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a trade-order whose jurisdiction has no official export-control /
  sanctions spec-basis can never be verified, dispatched or invoiced.
- the Shosha Trading Governor remains independent of the advisor.
- hard governor violations (a fabricated spec-basis, incomplete
  counterparty-diligence evidence, an uncleared counterparty credit, a
  missing contract, an unresolved export-control classification, an
  unresolved sanctions-screening flag, a double shipment dispatch or a
  double invoice) cannot be overridden by human approval.
- every intake, verification, dispatch, settlement and hold is auditable.
- counterparty, credit, sanctions and trade data stays outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or license
should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is a
separate trust mark and should require security, audit and data-flow review.

Certified operators can lose certification for:
- bypassing shipment-dispatch or invoice-settlement policy checks
- mishandling counterparty, credit or sanctions-screening data
- misrepresenting certification status
- failing to respond to security incidents
