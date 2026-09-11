# Operator Guide

## First Deployment
1. Register trading desks, trading supervisors, and trade-orders.
2. Import trade-order, counterparty, credit, export-control and
   sanctions history.
3. Seed the per-jurisdiction spec-basis catalog (`shosha.facts`) for
   the jurisdictions you actually trade in, citing real official sources
   only.
4. Run read-only spec-basis validation per jurisdiction.
5. Configure export-control / sanctions / credit escalation and
   accounts-receivable accounts.
6. Publish a dry-run dispatch/invoice and audit export.

## Minimum Trading Controls
- spec-basis validation before any verification, dispatch, or invoice
- full counterparty-diligence + trade-control evidence (credit-
  clearance record, contract/PO, export-control classification record,
  sanctions-screening record) before any dispatch
- credit-clearance, contract-on-file, export-control-classification
  and sanctions-screening checks before any dispatch; sanctions-
  screening before any invoice
- export-control / sanctions / credit escalation gate
- audit export for every dispatch, invoice, and hold
- backup manual dispatch and invoicing process

## A Day in the Life: Intake → Verify → Dispatch → Settle → Audit

Non-specialized Wholesale Trade (ISIC 4690,
`cloud-itonami-isic-4690`) runs on the same intake / advise / govern /
decide / commit-or-hold loop as every itonami blueprint, but here the
loop is concrete: a regional trading house needs to bring a trade-order
(say, a shipment of precision machine tools to a counterparty in Japan)
from intake through contract verification to a cross-border shipment
dispatch and an invoice settlement. Walking through one order, end to
end:

1. **Intake.** The trader books the trade-order through `:forms`:
   order-id, commodity-category, counterparty, price, contract-terms,
   jurisdiction, and the order's own diligence record (credit-
   cleared?, export-license-cleared?, sanctions-screened?). This
   creates a trade-order record at `:order/intake` status. The
   ShoshaAdvisor only normalizes the patch; it does not invent the
   order-id, counterparty, jurisdiction, commodity category, or any
   commercial/diligence value.
2. **Verify.** The ShoshaAdvisor drafts a per-jurisdiction contract /
   export-control / sanctions evidence checklist (`:contract/verify`)
   from `shosha.facts`, citing the jurisdiction's official spec-basis
   (owner authority, legal basis, provenance) and listing the required
   evidence (credit-clearance record, contract/PO, export-control
   classification record, sanctions-screening record). The
   `:shosha-trading-governor` sign-off gate must clear: it checks the
   jurisdiction actually has an official spec-basis on file (never
   invent one). A jurisdiction with no spec-basis is a HARD hold at
   the governor node -- it never even reaches a human. This
   verification always escalates to a human for approval; it is never
   auto.
3. **Dispatch.** Before a shipment can leave for the freight forwarder,
   the `:shosha-trading-governor` sign-off gate runs the full HARD
   check set against the order's own ground truth: the spec-basis
   exists, the evidence checklist is complete, the counterparty's
   credit has been cleared, contract-terms are on file, the
   goods/technology's export-control classification has been cleared,
   the counterparty has passed sanctions screening, and the order has
   not already been dispatched. Any failure is a HARD hold that a
   human cannot override. If every check is clean, the proposal STILL
   always escalates to a human trading supervisor -- a `:shipment/
   dispatch` never auto-commits at any phase. On approval, the
   shipment record is drafted (`<JURISDICTION>-SHIPMENT-000001`), the
   order's `:dispatched?` flag is set, and the actual carriage is
   handed off to a licensed freight forwarder / customs broker --
   outside this actor's operating boundary (see README `Robotics
   premise`).
4. **Settle.** Once the shipment has actually been dispatched, the
   invoice is settled (`:invoice/settle`): the money side of the
   trade, custody / financial transfer. The governor re-checks the
   spec-basis, the evidence completeness, the sanctions screening, and
   that this order's invoice has not already been settled. As with the
   dispatch, a clean invoice STILL always escalates to a human trading
   supervisor -- `:invoice/settle` never auto-commits. On approval the
   invoice record is drafted (`<JURISDICTION>-INVOICE-000001`) and the
   order's `:invoiced?` flag is set.
5. **Audit.** The verification, the dispatch sign-off, the shipment
   record, the invoice sign-off, and the invoice record are all
   appended to the `:audit-ledger` -- immutable and exportable, so a
   counterparty or regulatory dispute can be traced back to the exact
   spec-basis citation, evidence checklist, and supervisor sign-off
   that authorized the dispatch and invoice. If something is wrong
   with the counterparty (a credit deterioration, an export-control
   classification gap, a sanctions hit, a contract gap), that gets
   raised as an export-control / sanctions / credit flag and routed
   through the escalation gate instead of being silently suppressed --
   a dispatch for that order then waits on governor sign-off of the
   flag's resolution.

Any deviation from this loop is exactly what the Trust Controls in
`docs/business-model.md` exist to catch: an order verified against a
fabricated spec-basis, a dispatch started with incomplete evidence, an
uncleared counterparty credit, a contract gap, an unresolved export-
control classification, a sanctions screening suppressed to force a
dispatch through, or an invoice posted without a human sign-off.

## Feel the Decision Gate: `kbb -M:dev:run`

This vertical has no companion playable prototype. The fastest hands-on
way to feel why the `:shosha-trading-governor` gate exists is the
bundled demo, which walks one clean trade-order through intake → verify
→ dispatch → settle (each dispatch/settle pausing for human approval)
and then exercises every HARD-hold failure mode in isolation:

- a jurisdiction with no official spec-basis → HOLD (`:no-spec-basis`),
- a counterparty whose credit has not been cleared → HOLD
  (`:credit-uncleared`),
- an order with no contract-terms on file → HOLD (`:contract-missing`),
- an order whose export-control classification has not been cleared →
  HOLD (`:export-license-uncleared`),
- a counterparty that has not passed sanctions screening → HOLD
  (`:counterparty-sanctions-flag-unresolved`),
- a double dispatch of the same order → HOLD (`:already-dispatched`),
- a double invoice of the same order → HOLD (`:already-invoiced`).

Each HOLD settles at the governor node and never reaches a human
approver -- the same failure mode the audit ledger is built to catch and
the minimum trading controls above are built to prevent. It is not a
substitute for those controls, but it is the fastest way for a new
operator (or a reviewer) to feel, hands-on, why the gate exists before
touching a real deployment.

## Certification
Certified operators must prove spec-basis-grounded verification,
evidence-backed dispatch readiness (credit-clearance, contract-on-file,
export-control classification, sanctions-screening), and human review
for every dispatch- and invoice-affecting action.
