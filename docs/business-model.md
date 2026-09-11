# Business Model: Non-specialized Wholesale Trade

## Classification
- Repository: `cloud-itonami-isic-4690`
- ISIC Rev.5: `4690` — non-specialized wholesale trade
- Domain: `commerce/general-trading`
- Social impact: trade compliance, cross-border market access, transparency
- Governor: `:shosha-trading-governor`
- License: AGPL-3.0-or-later

## Scope
This actor covers trade-order intake through per-jurisdiction
counterparty / contract / export-control / sanctions regulatory
verification, cross-border shipment dispatch (a logistics-coordination
referral -- handing the goods off to a licensed freight forwarder /
customs broker; this actor does not itself operate a warehouse, a
fixed loading rack, or any cargo-handling robot), and invoice
settlement (the money side of a general-trading transaction, custody /
financial transfer) for a non-specialized (general/diversified)
wholesale trading house -- the Japanese-style sogo-shosha archetype
(Mitsubishi Corp / Mitsui / Marubeni / Sumitomo / Itochu / Sojitz): a
firm that intermediates trade across MULTIPLE unrelated commodity/
product categories in the SAME order book, unlike the specialized
wholesalers already registered at ISIC 4610-4669 (including this
fleet's own fuel-wholesale sibling, `cloud-itonami-isic-4671`, ISIC
4671). It does **not**, by itself, hold any trading licence, export
authorization or operating authority required to run a general-trading
business in a given jurisdiction, perform the actual freight
forwarding / customs clearance, or judge trading-book economics
(freight-forwarder selection and trading-book optimization is a
follow-up slice, not this R0). Whoever deploys a live instance supplies
the jurisdiction-specific operating authority, the real freight-
forwarder / customs-broker integration and ERP / accounts-receivable
integrations, and bears that jurisdiction's liability -- the software
supplies the governed, spec-cited, audited execution scaffold so the
operator does not have to build the compliance layer from scratch.

## Customer
- regional and independent general/diversified trading houses
- import/export merchants leaving closed trade-finance / ERP SaaS
- SME manufacturers and producers who need a trading-house partner for
  market access without surrendering their own trade data
- counterparties, banks and regulators who need an auditable, spec-cited
  trade record

## Offer
- trade-order intake and directory management across UNRELATED
  commodity categories (steel, foodstuffs, textiles, chemicals,
  machinery, and beyond -- never limited to one product line)
- per-jurisdiction contract / export-control / sanctions regulatory
  verification with an official spec-basis citation
- cross-border shipment dispatch (logistics-coordination referral)
  gated on full evidence, a credit-cleared counterparty, contract-terms
  on file, a cleared export-control classification and a passed
  sanctions screen
- invoice settlement (custody / financial transfer) with double-invoice
  prevention
- evidence checklisting (credit-clearance record, contract/PO,
  sanctions-screening record, export-control classification record)
- export-control and sanctions exception workflows
- role-based access and immutable audit ledger

## Revenue
- self-host setup fee
- managed hosting subscription per trading desk
- support retainer with SLA
- ERP and accounts-receivable integration
- trade-finance / letter-of-credit referral fee (out of scope for this
  R0's governed actuation, but a natural monetization surface on top of
  the audited order book)

## The `:shosha-trading-governor` Decision Rule

This blueprint's `:itonami.blueprint/governor` is `:shosha-trading-
governor`. It is the single authority that stands between "a
cross-border shipment could be dispatched to a counterparty" and "it
is allowed to leave for the freight forwarder," and between "an
invoice could be settled" and "it is allowed to settle." Every rule it
enforces is traceable to the domain (Non-specialized Wholesale Trade,
ISIC 4690) and to the three `:social-impact` tags in `blueprint.edn`
(`:trade-compliance`, `:cross-border-market-access`, `:transparency`).

This is the rule the companion contract test
(`test/shosha/governor_contract_test.cljk`) encodes end-to-end: the
ShoshaAdvisor never dispatches a cross-border shipment to a
counterparty or settles an invoice the Shosha Trading Governor would
reject, `:shipment/dispatch` and `:invoice/settle` NEVER auto-commit
at any phase, `:order/intake` (no direct capital risk) MAY auto-commit
when clean, and every decision (commit OR hold) leaves exactly one
ledger fact.

**Authorizes a cross-border shipment dispatch (`:shipment/dispatch`) or
invoice settlement (`:invoice/settle`) only when ALL of the following
hold:**

1. **An official spec-basis citation exists for the jurisdiction** -- the
   governor will not authorize any `:contract/verify`, `:shipment/
   dispatch`, or `:invoice/settle` proposal whose jurisdiction has no
   entry in the `shosha.facts` catalog (`:no-spec-basis`). This is the
   direct enforcement of `:transparency`: a jurisdiction whose export-
   control / sanctions requirements cannot be traced to an OFFICIAL
   public source is never guessed. The advisor must not fabricate a
   jurisdiction's requirements.
2. **The jurisdiction's required evidence is fully on file** -- for a
   dispatch or invoice the order's jurisdiction must have been verified
   with a complete counterparty-diligence + trade-control evidence
   checklist on record: the credit-clearance record, the contract /
   purchase order, the sanctions-screening (OFAC / equivalent) record,
   and the export-control classification (ECCN/HS-code) record
   (`:evidence-incomplete`). This protects `:trade-compliance`: an
   order that cannot prove counterparty and trade-control diligence
   never dispatches.
3. **The counterparty's credit has been cleared** -- the governor reads
   the dedicated `:credit-cleared?` fact on the order and refuses to
   dispatch a shipment when credit has NOT been cleared (the leasing
   collateral-coverage discipline, applied to counterparty credit)
   (`:credit-uncleared`). Evaluated at `:shipment/dispatch`.
4. **Contract-terms are on file** -- the governor refuses to dispatch
   when no `:contract-terms` are recorded for the order
   (`:contract-missing`). A shipment never leaves for the freight
   forwarder against an undocumented trade. Evaluated at `:shipment/
   dispatch`.
5. **The goods/technology's export-control classification has been
   cleared** -- the governor reads the dedicated `:export-license-
   cleared?` fact and refuses to dispatch when the classification
   (is the good/technology controlled or dual-use, and if so is it
   licensed?) has NOT been cleared (`:export-license-uncleared`).
   Evaluated at `:shipment/dispatch`. **This is the check with no
   analog in the fuel-wholesale sibling** -- a non-specialized
   (general/diversified) trading house's defining regulatory exposure
   is cross-border export-control classification, not a single-
   commodity excise, because it brokers UNRELATED commodity categories
   order to order.
6. **The counterparty has passed OFAC / equivalent sanctions screening**
   -- the governor reads the dedicated `:sanctions-screened?` fact and
   treats an unresolved sanctions-screening flag as a HARD, un-
   overridable hold (`:counterparty-sanctions-flag-unresolved`). Neither
   goods nor money move against an unscreened counterparty. Evaluated
   UNCONDITIONALLY at both `:shipment/dispatch` and `:invoice/settle`.
7. **The order has not already been dispatched, and the invoice has not
   already been settled** -- a double dispatch of the same order is
   refused off a dedicated `:dispatched?` fact, and a double invoice off
   a dedicated `:invoiced?` fact (never a `:status` value), the double-
   actuation guard every sibling actor in this fleet enforces
   (`:already-dispatched` / `:already-invoiced`).

**Rejects (HOLD, un-overridable, never even reaches a human) when any of
the above fail.** A proposal with no spec-basis, incomplete evidence, an
uncleared counterparty credit, no contract-terms on file, an unresolved
export-control classification, an unresolved sanctions-screening flag,
or a double dispatch/invoice is held at the governor node -- a human
approver cannot override these, by construction.

**Always escalates to a human (never auto-commits) for `:shipment/
dispatch` and `:invoice/settle`**, even when every check above is clean.
Handing a real cross-border shipment off to a freight forwarder /
customs broker and settling a real trade invoice (real money moving
between counterparty and trading house) are the two real-world
actuation events this actor performs; both are always a human trading
supervisor's call. This is enforced by TWO independent layers that
agree on purpose: the governor's confidence / actuation SOFT gate (a
`:shipment/dispatch` / `:invoice/settle` stake always escalates) and
`shosha.phase`'s phase table, which never puts either op in any
phase's `:auto` set. The `:trade-compliance` tag is enforced upstream
of the governor, in the contract-verification evidence step -- the
governor's job is dispatch/invoice authorization integrity, not
trading-book optimization.

## Required Technologies

`blueprint.edn`'s `:itonami.blueprint/required-technologies` for this business,
and what each one is actually load-bearing for here (not a generic capability
list):

| Technology | What it is FOR in Non-specialized Wholesale Trade |
|---|---|
| `:identity` | Trader, trading-supervisor and counterparty identity plus role-based access, so the governor's sign-off is tied to *who* authorized a dispatch or invoice, not just *that* someone did. |
| `:forms` | Structured intake for trade-order booking, per-jurisdiction evidence capture (credit-clearance record, contract/PO, export-control classification record, sanctions-screening record), and export-control / sanctions exception submission -- the data the Decision Rule above actually evaluates comes in through these forms. |
| `:dmn` | Encodes the `:shosha-trading-governor` Decision Rule itself (spec-basis, evidence completeness, credit-clearance, contract-on-file, export-control classification, sanctions-screening, the double-actuation guards, the actuation gate) as an evaluable decision table rather than code buried in application logic -- this is what makes the governor auditable and swappable per-deployment. |
| `:bpmn` | Orchestrates the intake -> verify -> dispatch -> settle -> audit loop end-to-end (see `docs/operator-guide.md`) across trade-order intake, contract verification, cross-border shipment dispatch, and invoice settlement, including the export-control / sanctions escalation gate. |
| `:audit-ledger` | The immutable record of every verification, dispatch, invoice, export-control flag, sanctions flag, and hold -- this is what "an auditable, spec-cited trade record for every dispatch and invoice" (Trust Controls, below) actually means in practice, and the evidence an operator needs if a dispatch or an invoice is later disputed by a counterparty or regulator. |
| `:optimization` | Freight-forwarder selection and trading-book optimization -- selects the profitable fulfillment strategy across the diversified order book. This R0 build deliberately scopes optimization OUT (see README `Business-process coverage`); the capability is correctly marked required, the integration is a follow-up slice. |

There is NO bespoke `:shosha` capability library in this stack (unlike
the freight sibling's `:logistics`), and unlike the fuel-wholesale
sibling this vertical also carries NO `:robotics` technology at all
(see Robotics Premise, below): the general-trading checks (credit-
clearance, contract-on-file, export-control classification, sanctions-
screening) are direct entity boolean reads in `shosha.governor`, on top
of the generic identity/forms/dmn/bpmn/audit-ledger stack (see
Capability layer).

## Trust Controls
- a jurisdiction with no official spec-basis can never be verified,
  dispatched, or invoiced against
- a shipment dispatch never starts with incomplete counterparty-
  diligence + trade-control evidence
- a shipment dispatch never starts with an uncleared counterparty
  credit, no contract-terms on file, an unresolved export-control
  classification, or an unresolved sanctions-screening flag
- an invoice never settles against an unresolved sanctions-screening flag
- export-control / sanctions / credit flags cannot be silently suppressed
- the same order can never be dispatched or invoiced twice
- a shipment dispatch or invoice never auto-commits; both always need a
  human trading supervisor
- every dispatch and invoice (commit OR hold) leaves exactly one
  immutable ledger fact
- counterparty, credit, sanctions and trade data stays outside Git

## Implementation notes (`:implemented`)

The Decision Rule above is implemented faithfully by `shosha.governor`
as eight HARD checks (a human approver cannot override them) plus one
SOFT gate:

- `spec-basis-violations` -- the spec-basis check above, evaluated on
  every `:contract/verify`, `:shipment/dispatch`, and `:invoice/settle`.
- `evidence-incomplete-violations` -- the evidence-completeness check
  above, for `:shipment/dispatch` / `:invoice/settle`.
- `credit-uncleared-violations` -- the counterparty-credit check above
  (the leasing collateral-coverage discipline applied to counterparty
  credit); evaluated on every `:shipment/dispatch`.
- `contract-missing-violations` -- the contract-on-file check above;
  evaluated on every `:shipment/dispatch`.
- `export-license-uncleared-violations` -- the export-control-
  classification check above; evaluated on every `:shipment/dispatch`.
  This check has NO analog in the fuel-wholesale sibling's governor --
  it is this vertical's own domain content, reflecting a general-
  trading house's defining regulatory exposure.
- `counterparty-sanctions-flag-unresolved-violations` -- the sanctions-
  screening check above (the same open-flag-unresolved discipline the
  freight sibling's delivery-exception-unresolved check establishes);
  evaluated unconditionally on both `:shipment/dispatch` and
  `:invoice/settle`.
- `already-dispatched-violations` / `already-invoiced-violations` -- the
  double-actuation guards above, off dedicated `:dispatched?` /
  `:invoiced?` booleans (never a `:status` value), the same discipline
  every sibling governor's guards establish.
- the confidence floor / actuation SOFT gate -- low confidence, OR a
  `:shipment/dispatch` / `:invoice/settle` stake, escalates to a human;
  and `shosha.phase` independently never auto-commits either op at any
  phase.

Unlike the crude-extraction sibling's governor (which calls pure
physical range-check functions in its registry), this governor needs no
range-check functions at all: its domain checks read the `trade-order`
record's own dedicated booleans directly. `:shipment/dispatch` and
`:invoice/settle` are the two real-world actuation events
(`#{:shipment/dispatch :invoice/settle}`), applied SEQUENTIALLY to the
SAME trade-order (dispatch first, invoice settlement later) rather than
the retail sibling's `:kind`-distinguished alternative-action shape --
the same sequential dual-actuation shape the repair-shop, quarrying,
crude-extraction and fuel-wholesale clusters use. Neither ever auto-
commits at any phase. Freight-forwarder selection and trading-book
optimization (the `:optimization` line above) is a follow-up slice, not
in this R0 build -- see README `Business-process coverage`.

## Capability layer

Like the fuel-wholesale sibling (`cloud-itonami-isic-4671`), this
vertical is SELF-CONTAINED: there is no `kotoba-lang/shosha` to
delegate general-trading validation to. The credit-clearance /
contract-on-file / export-control-classification / sanctions-screening
checks live as direct entity boolean reads in `shosha.governor` (off
dedicated `:credit-cleared?` / `:contract-terms` / `:export-license-
cleared?` / `:sanctions-screened?` facts on the `trade-order` record)
-- this vertical's governor needs no pure range-check functions at all
(contrast the crude sibling, whose registry hosts its physical range
checks), because its domain checks ARE direct boolean reads.

## Jurisdiction coverage (honest)

`shosha.facts/catalog` currently seeds 4 jurisdictions with an
official spec-basis, each a REAL regime built on CROSS-BORDER EXPORT-
CONTROL / SANCTIONS law (not customs/excise, the fuel-wholesale
sibling's basis) -- Japan (METI's security-trade-control division,
外国為替及び外国貿易法 / FEFTA; 輸出貿易管理令), the United States
(the Bureau of Industry and Security's Export Administration
Regulations, 15 C.F.R. Parts 730-774, plus OFAC sanctions programs),
the United Kingdom (the Export Control Joint Unit's Export Control
Order 2008 plus OFSI financial sanctions), and Germany (BAFA's
enforcement of Regulation (EU) 2021/821, the EU dual-use export-
control recast, plus the national Außenwirtschaftsgesetz / AWG). This
is a starting catalog to prove the governor contract end-to-end, not a
claim of global coverage (4 of ~194 jurisdictions worldwide). Adding a
jurisdiction is additive: one map entry in `shosha.facts/catalog`,
citing a real official source -- never fabricate a jurisdiction's
requirements to make coverage look bigger.

## Maturity

`:implemented` -- `ShoshaAdvisor` + `Shosha Trading Governor` run as
real, tested code (`clojure -M:dev:test`: 36 tests / 174 assertions, 0
failures; lint clean), following the SAME governed-actor architecture
as the other prior actors across this fleet, with its own distinct,
independently-named governor and its own direct-entity-boolean
general-trading checks (including the export-control-classification
check with no analog in any sibling). See `docs/adr/0001-architecture.md`
for the design.

## Robotics Premise

`blueprint.edn` sets `:itonami.blueprint/robotics false`, and
`:itonami.blueprint/required-technologies` does NOT list `:robotics`
at all -- a deliberate, honest departure from the fuel-wholesale
sibling's `robotics true`. A non-specialized (general/diversified)
trading house does not operate a fixed physical asset comparable to a
fuel-wholesale rack: there is no loading-rack valve, no single-site
cargo-handling apparatus this actor's governor could gate a robot
command against. A `:shipment/dispatch` in this domain is a
**logistics-coordination referral** -- handing the goods off to a
LICENSED, independently-operated freight forwarder / customs broker,
who performs (or subcontracts) the actual physical loading, carriage
and customs clearance, entirely outside this actor's operating
boundary. Retrofitting the robotics premise onto that referral act
would be dishonest: there is no robot this actor's governor is in a
position to gate.

This follows real, existing precedent in this fleet rather than
inventing an exception: `cloud-itonami-isic-6910` (Global Incorporation
Actor, company formation) sets `robotics false` with the same reasoning
-- "会社設立代行は物理領域作業を伴わないデジタル/書類業務であり
（company-incorporation agency work is digital/paperwork work with no
physical-domain labor）" -- explicitly following `cloud-itonami-isic-
6310` (HR SaaS)'s own precedent of being out of scope for the robotics
premise retrofit (ADR-2607011000). A general-trading house's core
governed lifecycle here -- counterparty verification, contract review,
export-control classification, sanctions screening, invoice settlement
-- is the same category of digital/paperwork compliance work, with the
one physical act (actual carriage) explicitly delegated to a third
party this actor's governor does not, and should not pretend to,
control.
