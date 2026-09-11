# cloud-itonami-isic-4690

Open Business Blueprint for **ISIC Rev.5 4690**: Non-specialized
Wholesale Trade -- trade-order intake, per-jurisdiction counterparty-
diligence / export-control / sanctions regulatory verification,
cross-border shipment dispatch, and invoice settlement for a
non-specialized (general/diversified) wholesale trading house.

This repository publishes a general-trading actor -- trade-order
intake, per-jurisdiction contract / export-control / sanctions
regulatory verification, cross-border shipment dispatch and invoice
settlement -- as an OSS business that any qualified operator can fork,
deploy, run, improve and sell, so a regional trading house never
surrenders counterparty, credit, export-control and trade data to a
closed trade-finance / ERP SaaS.

**What is a "non-specialized wholesale trader"?** ISIC 4690 is the
classification for a general/diversified trading company -- a
Japanese-style **sogo-shosha** (総合商社, e.g. Mitsubishi Corp / Mitsui
/ Marubeni / Sumitomo / Itochu / Sojitz): a firm that intermediates
trade across MULTIPLE unrelated commodity/product categories in the
SAME order book (steel today, foodstuffs tomorrow, machine tools the
day after), unlike a specialized wholesaler (ISIC 4610-4669, e.g. this
fleet's own fuel-wholesale sibling `cloud-itonami-isic-4671`, ISIC
4671). Because it is not tied to one commodity, its defining
regulatory exposure is not a single-commodity excise -- it is
**cross-border export control and sanctions compliance**, evaluated
per shipment regardless of which commodity category the order happens
to be in.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet, including the fuel-wholesale sibling
`cloud-itonami-isic-4671` -- here it is **ShoshaAdvisor ⊣ Shosha
Trading Governor**. This blueprint's own
`:itonami.blueprint/governor` keyword, `:shosha-trading-governor`, is a
UNIQUE keyword fleet-wide (grep-verified: no other blueprint declares
it) -- a fresh, independent build.

**Unlike the freight sibling (`cloud-itonami-isic-4920`, which wraps a
pre-existing bespoke capability library `kotoba-lang/logistics`), this
vertical is SELF-CONTAINED**, the same shape the fuel-wholesale
sibling uses: there is no `kotoba-lang/shosha` to delegate
general-trading validation to, so the credit-clearance / contract-on-
file / export-control-classification / sanctions-screening checks live
as direct entity boolean reads in `shosha.governor` (off dedicated
`:credit-cleared?` / `:contract-terms` / `:export-license-cleared?` /
`:sanctions-screened?` facts on the `trade-order` record), rather than
wrapping an external capability library's own validated function.

> **Why an actor layer at all?** An LLM is great at drafting an order
> summary, normalizing records, and reading a credit file -- but it
> has **no notion of which jurisdiction's export-control / sanctions
> law is official, no license to dispatch a real cross-border shipment
> to a counterparty or settle a real trade invoice, and no way to know
> on its own whether the counterparty's credit has actually been
> cleared, whether contract terms are actually on file, whether the
> goods/technology being brokered have actually been export-control
> classified, or whether OFAC / equivalent sanctions screening has
> actually been passed**. Letting it dispatch a shipment or settle an
> invoice directly invites fabricated regulatory citations, a shipment
> leaving for an uncreditworthy or unscreened counterparty, an
> unlicensed dual-use good crossing a border, and an invoice settling
> against a sanctioned party -- exposing the operator to real
> enforcement and financial liability, for whoever runs it. This
> project seals the ShoshaAdvisor into a single node and wraps it with
> an independent **Shosha Trading Governor**, a human **approval
> workflow**, and an immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers trade-order intake through contract / export-control
/ sanctions regulatory verification, cross-border shipment dispatch
and invoice settlement. It does **not**, by itself, hold any trading
licence, export authorization or operating authority required to run a
general-trading business in a given jurisdiction, and it does not
claim to. It also does not perform the actual freight forwarding,
customs clearance, or route optimization itself, or judge trading-book
economics -- freight-forwarder selection / route optimization (the
blueprint's own `:optimization` technology) is a follow-up slice, not
in this R0. Whoever deploys and operates a live instance (a qualified
trading supervisor) supplies any jurisdiction-specific operating
authority, the real freight-forwarder / customs-broker integration and
the real ERP / accounts-receivable integrations, and bears that
jurisdiction's liability -- the software supplies the governed,
spec-cited, audited execution scaffold so that operator does not have
to build the compliance layer from scratch.

### Actuation

**Dispatching a real cross-border shipment to a counterparty and
settling a real trade invoice are never autonomous, at any phase, by
construction.** Two independent layers enforce this
(`shosha.governor`'s `:shipment/dispatch`/`:invoice/settle`
high-stakes gate and `shosha.phase`'s phase table, which never puts
either op in any phase's `:auto` set) -- see `shosha.phase`'s
docstring and `test/shosha/phase_test.cljk`'s
`shipment-dispatch-never-auto-at-any-phase`/
`invoice-settle-never-auto-at-any-phase`. The actor may draft, check
and recommend; a human trading supervisor is always the one who
actually dispatches a shipment or settles an invoice. Grounded in
trade-compliance doctrine (the same discipline every regulator in
`shosha.facts` codifies: a real shipment dispatch and a real invoice
settlement are human sign-off acts) -- a genuine DUAL-actuation shape,
applied SEQUENTIALLY to the SAME trade-order (dispatch first, invoice
settlement later), unlike `retailops`/4711's own `:kind`-distinguished
alternative-action shape.

## The core contract

```
trade-order intake + jurisdiction facts (shosha.facts, spec-cited)
        |
        v
   ┌───────────────────────┐   proposal      ┌───────────────────────┐
   │ ShoshaAdvisor          │ ─────────────▶ │ Shosha Trading Governor│  (independent system)
   │ (sealed)               │  + citations    │ spec-basis · evidence- │
   └───────────────────────┘                 │ incomplete · credit-   │
          │                 commit ◀┼ uncleared · contract-missing ·│
          │                         │ export-license-uncleared ·     │
    record + ledger        escalate ┼ counterparty-sanctions-flag-   │
          │              (ALWAYS for│ unresolved · already-dispatched│
          │       :shipment/       │ · already-invoiced             │
          │       dispatch/        └───────────────────────┘
          │       :invoice/
          │       settle)
          ▼
      human approval
```

**The ShoshaAdvisor never dispatches a cross-border shipment to a
counterparty or settles an invoice the Shosha Trading Governor would
reject, and never does so without a human sign-off.** Hard violations
(fabricated regulatory requirements; unsupported evidence; an
uncleared counterparty credit; no contract-terms on file; an
unresolved export-control classification; an unresolved sanctions-
screening flag; a double dispatch/invoice) force **hold** and *cannot*
be approved past; a clean dispatch/invoice proposal still always
routes to a human.

## Run

```bash
kbb -M:dev:run     # walk one clean shipment-dispatch + invoice lifecycle, plus seven HARD-hold cases, through the actor
kbb -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
kbb -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

Unlike most `cloud-itonami-isic-*` verticals in this fleet (which are
built on the premise that a robot performs the physical domain work --
see the fuel-wholesale sibling's own loading-rack valve robot), **this
actor sets `:itonami.blueprint/robotics false` and omits `:robotics`
from `:required-technologies` entirely.** A non-specialized
(general/diversified) trading house does not operate a fixed physical
asset comparable to a fuel-wholesale rack. A `:shipment/dispatch` here
is a **logistics-coordination referral**: handing the goods off to a
LICENSED, independently-operated freight forwarder / customs broker,
who performs the actual physical loading, carriage and customs
clearance entirely outside this actor's operating boundary -- there is
no robot this actor's governor is in a position to gate. This follows
real, existing precedent in this fleet: `cloud-itonami-isic-6910`
(Global Incorporation Actor) sets `robotics false` for the same
reason (digital/paperwork work with no actor-controlled physical
domain), itself following `cloud-itonami-isic-6310` (HR SaaS)'s own
precedent (ADR-2607011000). See `docs/business-model.md`'s "Robotics
Premise" section and `docs/adr/0001-architecture.md` Decision 10 for
the full reasoning.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Shosha Trading Governor, dispatch/invoice draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`4690`). Like the fuel-wholesale sibling, this vertical is NOT backed
by a separate bespoke domain capability lib: the general-trading checks
(credit-clearance, contract-on-file, export-control classification,
sanctions-screening) are direct entity boolean reads in
`shosha.governor`, on top of the generic identity/forms/dmn/bpmn/
audit-ledger stack -- with NO `:robotics` in the stack at all (see
Robotics premise, above).

## Layout

| File | Role |
|---|---|
| `src/shosha/store.cljk` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + shipment AND invoice history (dual history). The double-actuation guard checks dedicated `:dispatched?`/`:invoiced?` booleans rather than a `:status` value |
| `src/shosha/registry.cljk` | Shipment/invoice draft records (record construction only -- the Shosha Trading Governor's checks are direct entity booleans, so there are no pure range-check functions to host here, unlike the crude sibling's registry) |
| `src/shosha/facts.cljk` | Per-jurisdiction export-control / sanctions catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/shosha/shoshaadvisor.cljk` | **ShoshaAdvisor** -- `mock-advisor` ‖ `llm-advisor`; intake/contract-verification/dispatch/invoice proposals |
| `src/shosha/governor.cljk` | **Shosha Trading Governor** -- 6 HARD checks (spec-basis · evidence-incomplete · credit-uncleared · contract-missing · export-license-uncleared · counterparty-sanctions-flag-unresolved) + 2 double-actuation guards + 1 soft (confidence/actuation gate) |
| `src/shosha/phase.cljk` | **Phase 0→3** -- read-only → assisted intake → assisted verify → supervised (dispatch/invoice always human; order intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/shosha/operation.cljk` | **OperationActor** -- langgraph StateGraph |
| `src/shosha/sim.cljk` | demo driver |
| `test/shosha/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers trade-order intake through contract / export-control
/ sanctions regulatory verification, cross-border shipment dispatch
and invoice settlement -- the core governed lifecycle:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Trade-order intake + per-jurisdiction evidence checklisting, HARD-gated on an official spec-basis citation (`:order/intake`/`:contract/verify`) | Real freight-forwarder/customs-broker integration, route optimization and trading-book economics |
| Cross-border shipment dispatch, HARD-gated on full evidence, a credit-cleared counterparty, contract-terms on file, a cleared export-control classification, a passed sanctions screen and no double-dispatch (`:shipment/dispatch`) | |
| Invoice settlement, HARD-gated on full evidence, a passed sanctions screen and no double-invoice (`:invoice/settle`) | |
| Immutable audit ledger for every intake/verification/dispatch/invoice decision | |

Extending coverage is additive: add the next gate (e.g. a letter-of-
credit reconciliation check) as its own governed op with its own HARD
checks and tests, following the SAME "an independent governor
re-verifies against the actor's own records before any real-world act"
pattern this repo's flagship ops already establish.

## Jurisdiction coverage (honest)

`shosha.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `shosha.facts/catalog` --
currently 4 seeded (JPN, USA, GBR, DEU) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `shosha.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to
make coverage look bigger.

## Maturity

`:implemented` -- `ShoshaAdvisor` + `Shosha Trading Governor` run as
real, tested code (see `Run` above), following the SAME governed-actor
architecture as the other prior actors across this fleet, with its own
distinct, independently-named governor and its own direct-entity-
boolean general-trading checks. See `docs/adr/0001-architecture.md` for
the design.

## License

Code and implementation templates are AGPL-3.0-or-later.
