# ADR-0001: ShoshaAdvisor ⊣ Shosha Trading Governor architecture

## Status

Accepted. `cloud-itonami-isic-4690` published `:implemented` in this
fleet's governed-actor architecture.

## Context

`cloud-itonami-isic-4690` publishes an OSS business blueprint for
non-specialized wholesale trade (trade-order intake, per-jurisdiction
counterparty / contract / export-control / sanctions regulatory
verification, cross-border shipment dispatch, and invoice settlement)
-- the Japanese-style sogo-shosha archetype (Mitsubishi Corp / Mitsui /
Marubeni / Sumitomo / Itochu / Sojitz): a firm that intermediates trade
across MULTIPLE unrelated commodity/product categories in the SAME
order book, unlike the specialized wholesalers already registered at
ISIC 4610-4669 (including this fleet's own fuel-wholesale sibling,
`cloud-itonami-isic-4671`, ISIC 4671). Like every prior actor in this
fleet, the blueprint alone is not an implementation: this ADR records
the governed-actor architecture that promotes it to real, tested code,
following the same langgraph StateGraph + independent Governor + Phase
0->3 rollout pattern established by `cloud-itonami-isic-6511` (life
insurance) and applied by the fuel-wholesale sibling
`cloud-itonami-isic-4671`.

Like the fuel-wholesale sibling and `cloud-itonami-isic-0162`
(community agronomy), this vertical has NO bespoke domain capability
library in `kotoba-lang` to wrap (verified: no `kotoba-lang/shosha`-
style repo exists). This build therefore uses self-contained domain
logic -- the same pattern the majority of this fleet's actors use, and
the explicit differentiator from `cloud-itonami-isic-4920` (which
wraps a pre-existing `kotoba-lang/logistics` library). The general-
trading checks (credit-clearance, contract-on-file, export-control
classification, sanctions-screening) are direct entity boolean reads
in `shosha.governor`, off dedicated `:credit-cleared?` / `:contract-
terms` / `:export-license-cleared?` / `:sanctions-screened?` facts on
the `trade-order` record -- NO pure range-check functions are needed.

This blueprint's own `:itonami.blueprint/governor` keyword,
`:shosha-trading-governor`, is grep-verified UNIQUE fleet-wide -- no
naming-collision precedent question, a fresh independent build.

## Decision

### Decision 1: fresh governor identity, no reuse precedent needed

`:shosha-trading-governor` is grep-verified unique across every
`blueprint.edn` in this fleet. This build follows the SAME
governed-actor architecture as every prior actor, but with its own
distinct governor identity.

### Decision 2: self-contained domain logic, direct entity booleans (no `kotoba-lang/shosha` to wrap, and no range-check functions to host)

Unlike `cloud-itonami-isic-4920` (freight, which delegates tracking-
number validation to a real, pre-existing `kotoba-lang/logistics`
capability library), and unlike the crude-extraction sibling (which
hosts pure physical range-check functions in its registry because its
governor re-verifies measured physical values), this general-trading
vertical needs NEITHER: there is no pre-existing general-trading
capability library to delegate to, AND the governor's domain checks
(credit-clearance, contract-on-file, export-control classification,
sanctions-screening) are direct entity boolean reads off the
`trade-order` record's own dedicated facts -- not measured-value-vs-
limit range comparisons. So `shosha.registry` is RECORD CONSTRUCTION
ONLY (no range-check functions), and `shosha.governor` reads the
order's booleans directly. No literal code is shared with any sibling
(different domain), but the 'governor re-verifies against the actor's
own records before any real-world act' discipline is the same.

### Decision 3: dual-actuation shape, SEQUENTIAL on the SAME `trade-order` entity

Like the fuel-wholesale sibling's `fuel-order` entity (and the
crude-extraction sibling's `well` entity, and the repair-shop
cluster's `ticket` shape), this vertical's `dispatch` and `settle`
actuation events apply SEQUENTIALLY to the SAME `trade-order` -- a
cross-border shipment dispatch happens first (goods leave for the
freight forwarder), invoice settlement happens later (the money side
of the trade, custody / financial transfer), on the same order record.
This matches the repair-shop / quarrying / crude-extraction /
fuel-wholesale clusters' sequential shape (two real-world acts, in
order, on one entity), unlike the retail sibling's `:kind`-
distinguished alternative-action shape. `high-stakes` is `#{:shipment/
dispatch :invoice/settle}`; neither ever auto-commits at any phase.

### Decision 4: the general-trading checks -- direct entity booleans, documented as such, PLUS one check with no analog in the fuel-wholesale sibling

The four domain checks the governor runs on `:shipment/dispatch`
(credit-uncleared, contract-missing, export-license-uncleared, and --
at both actuation ops -- counterparty-sanctions-flag-unresolved) are
each a direct boolean read off a dedicated fact on the `trade-order`
record, documented as such rather than as measured-value range
comparisons:

- `credit-uncleared` reads the dedicated `:credit-cleared?` fact and
  refuses dispatch when credit has NOT been cleared -- the leasing
  collateral-coverage discipline, applied to counterparty credit.
- `contract-missing` reads the dedicated `:contract-terms` fact and
  refuses dispatch when no contract-terms are on file -- a shipment
  never leaves for the freight forwarder against an undocumented
  trade.
- `export-license-uncleared` reads the dedicated `:export-license-
  cleared?` fact and refuses dispatch when the export-control
  classification (ECCN/HS-code -- is the good/technology controlled or
  dual-use, and if so is it licensed?) has NOT been cleared. **This
  check has NO analog in the fuel-wholesale sibling's governor** -- it
  is this vertical's own domain content, not a find/replace rename of
  an existing check. A non-specialized (general/diversified) trading
  house brokers UNRELATED commodity categories order to order (steel
  today, machine tools tomorrow), so its defining regulatory exposure
  is cross-border export-control classification per shipment, not a
  single-commodity excise (the fuel-wholesale sibling's basis).
- `counterparty-sanctions-flag-unresolved` reads the dedicated
  `:sanctions-screened?` fact and treats an unresolved sanctions-
  screening flag as a HARD hold, evaluated UNCONDITIONALLY at both
  `:shipment/dispatch` and `:invoice/settle` -- neither goods nor
  money move against an unscreened counterparty. This reapplies the
  SAME open-flag-unresolved discipline the freight sibling's
  delivery-exception-unresolved check (and the fuel-wholesale
  sibling's counterparty-sanctions-flag-unresolved check) establish.

Each fires when the fact is provably in its unsafe state; missing /
false reads as a violation (cannot verify safe to dispatch). No new
unconditional-evaluation ordinals are claimed beyond the sanctions
check: it is a discipline-reapplication, documented per Decision 5.

### Decision 5: `counterparty-sanctions-flag-unresolved?` -- the open-flag-unresolved discipline

An unresolved sanctions-screening flag -- the counterparty has not
passed OFAC / equivalent sanctions screening -- is a HARD,
un-overridable hold. This reuses the SAME open-flag-unresolved
discipline the freight sibling's `delivery-exception-unresolved?`
check (and the fuel-wholesale sibling's own sanctions check) establish
-- an open concern cannot be silently suppressed to force a dispatch
or invoice through. Evaluated UNCONDITIONALLY at both `:shipment/
dispatch` and `:invoice/settle`.

### Decision 6: dedicated double-actuation-guard booleans

`:dispatched?` / `:invoiced?` are dedicated booleans on the
`trade-order` record, never a single `:status` value -- the same
discipline every prior governor's guards establish, informed by
`cloud-itonami-isic-6492`'s real status-lifecycle bug
(ADR-2607071320).

### Decision 7: Store protocol, MemStore + DatomicStore parity

`shosha.store/Store` is implemented by both `MemStore` (atom-backed,
default for dev/tests/demo) and `DatomicStore` (`langchain.db`-backed),
proven to satisfy the same contract in
`test/shosha/store_contract_test.cljk`. The ledger stays append-only on
every backend: which trade-order was verified for a jurisdiction with
no official spec-basis, which counterparty had credit-uncleared / no
contract / an unresolved export-control classification / an unresolved
sanctions-screening flag, which order had a shipment dispatched, which
invoice was settled, on what jurisdictional basis, approved by whom --
always a query over an immutable log.

### Decision 8: Phase 0->3 with `:shipment/dispatch`/`:invoice/settle` NEVER auto

`shosha.phase`'s phase table puts `:order/intake` (no direct capital
risk) in phase 3's `:auto` set as its only member; `:shipment/
dispatch` and `:invoice/settle` are deliberately ABSENT from every
phase's `:auto` set, including phase 3 -- a permanent structural fact.
`shosha.governor`'s high-stakes gate enforces the same invariant
independently: two layers agree that actuation is always a human
trading supervisor's call.

### Decision 9: mock + LLM advisor pair

`shosha.shoshaadvisor` provides a deterministic `mock-advisor`
(default, runs offline) and an `llm-advisor` backed by a
`langchain.model/ChatModel`. The LLM advisor's EDN proposal is parsed
defensively: any parse/shape failure yields a safe low-confidence noop
so the governor escalates/holds -- an LLM hiccup can never auto-
dispatch a shipment or auto-settle an invoice.

### Decision 10: `:robotics false`, following the ISIC 6910 / ISIC 6310 precedent, NOT the fuel-wholesale sibling's `robotics true`

Unlike every prior `cloud-itonami-isic-*` sibling in the physical-
commodity clusters (fuel-wholesale, crude-extraction, quarrying), a
non-specialized general-trading house does not operate a fixed
physical asset (a loading rack, a wellhead, a quarry face) this
actor's governor could gate a robot command against. `:shipment/
dispatch` here is a logistics-coordination referral -- handing the
goods to a licensed, independently-operated freight forwarder /
customs broker who performs the actual carriage entirely outside this
actor's operating boundary. `blueprint.edn` therefore sets
`:itonami.blueprint/robotics false` and OMITS `:robotics` from
`:required-technologies` altogether, following the REAL, existing
precedent set by `cloud-itonami-isic-6910` (Global Incorporation
Actor) -- itself following `cloud-itonami-isic-6310` (HR SaaS)'s
precedent -- rather than inventing a new exception. See
`docs/business-model.md`'s Robotics Premise section for the full
reasoning.

## Alternatives considered

- **Wrapping a bespoke `kotoba-lang/shosha` capability library.**
  Considered and explicitly ruled out: no such library exists. Forcing
  a false capability-library integration would be dishonest; this
  build correctly uses self-contained domain logic instead.
- **Hosting pure range-check functions in the registry** (as the crude
  sibling does). Considered and ruled out: the general-trading domain
  checks are direct entity booleans (credit cleared? contract on file?
  export-control classified? sanctions screened?), not measured-value-
  vs-limit range comparisons, so there are no range checks to host.
  `shosha.registry` is record construction only.
- **A `:kind`-distinguished entity** (matching the retail sibling's
  `order` shape). Rejected: shipment dispatch and invoice settlement
  happen SEQUENTIALLY on the SAME trade-order in this domain, not as
  alternative actions -- the repair-shop / quarrying / crude-
  extraction / fuel-wholesale cluster's sequential shape is the honest
  match here.
- **Retaining `robotics true` by analogy to the fuel-wholesale
  sibling.** Rejected: this vertical has no fixed physical asset a
  robot could operate under this actor's own governor, unlike a fuel-
  wholesale rack. Retrofitting the robotics premise onto a third-party
  freight-forwarder referral would misrepresent an act this actor does
  not perform and cannot gate. The honest precedent is `cloud-itonami-
  isic-6910`/`6310`'s `robotics false`, not the fuel-wholesale
  sibling's `robotics true`.
- **Folding export-control classification into the evidence checklist
  only, with no dedicated HARD check.** Considered: `shosha.facts`
  already lists an export-control classification record as required
  evidence, so `evidence-incomplete-violations` would catch a missing
  determination indirectly. Rejected as insufficient on its own: the
  fuel-wholesale sibling's own credit-uncleared / contract-missing
  checks demonstrate this fleet's convention of surfacing each
  materially distinct compliance failure as its OWN named HARD check
  (with its own `:rule` keyword and its own test), not folding it
  silently into the generic evidence-completeness check -- a genuinely
  new domain concern (export-control classification) earns a genuinely
  new named check, `export-license-uncleared`.
- **Building freight-forwarder selection / trading-book optimization
  in this R0.** Rejected in favor of a scoped R0 slice (the
  `:optimization` capability is correctly marked required, the
  integration is a follow-up), consistent with this fleet's 'extending
  coverage is additive' convention.

## Consequences

- Fresh independent actor in this fleet, following the SAME
  governed-actor architecture as every prior sibling.
- Establishes the general-trading checks as direct entity boolean
  reads (no pure range-check functions needed), an honest structural
  differentiator from the crude-extraction sibling's registry-hosted
  physical range checks.
- Adds `export-license-uncleared` as a genuinely new HARD check with
  no analog in the fuel-wholesale sibling, reflecting this vertical's
  own defining regulatory exposure (cross-border export control across
  unrelated commodity categories, not a single-commodity excise).
- Sets `robotics false` (omitting `:robotics` from
  `:required-technologies` entirely), following the `cloud-itonami-
  isic-6910`/`6310` precedent rather than the fuel-wholesale sibling's
  `robotics true` -- an honest, precedent-grounded departure, not an
  oversight.
- `MemStore` || `DatomicStore` parity is proven by
  `test/shosha/store_contract_test.cljk`.
- 36 tests / 174 assertions pass; lint is clean; the demo
  (`clojure -M:dev:run`) walks one clean dispatch + invoice lifecycle,
  plus seven HARD-hold scenarios (no spec-basis, credit-uncleared,
  contract-missing, export-license-uncleared, sanctions, double
  dispatch, double invoice), end-to-end.

## References

- `cloud-itonami-isic-6511/docs/adr/0001-architecture.md` (origin of the
  general governed-actor architecture pattern)
- `cloud-itonami-isic-4671/docs/adr/0001-architecture.md` (fuel-
  wholesale sibling; this build's closest architectural precedent --
  contrast: `robotics true`, single-commodity excise basis, no
  export-license-uncleared check)
- `cloud-itonami-isic-4920/docs/adr/0001-architecture.md` (freight
  sibling; contrast: wraps a pre-existing `kotoba-lang/logistics`
  capability library)
- `cloud-itonami-isic-0610/docs/adr/0001-architecture.md` (crude-
  extraction sibling; contrast: hosts pure physical range-check
  functions in its registry, which this vertical does NOT need)
- `cloud-itonami-isic-6910` / `cloud-itonami-isic-6310` (origin of the
  `robotics false` precedent this build follows, for digital/paperwork
  verticals with no actor-controlled physical asset)
- `cloud-itonami-isic-0162/docs/adr/0001-architecture.md` (origin of
  the 'honest reapplication, documented as such' convention this build
  follows for its sanctions open-flag-unresolved check)
- ADR-2607011000 (cloud-itonami robotics premise, and its documented
  ISIC 6310 exemption)
- 外国為替及び外国貿易法 (Foreign Exchange and Foreign Trade Act,
  FEFTA); 輸出貿易管理令 (Export Trade Control Order) (Japan, METI
  安全保障貿易管理課)
- Export Administration Regulations (15 C.F.R. Parts 730-774, U.S.
  Department of Commerce, Bureau of Industry and Security); OFAC
  sanctions programs (31 C.F.R. Chapter V, U.S. Treasury)
- Export Control Order 2008 (SI 2008/3231, UK, Export Control Joint
  Unit); UK financial sanctions regulations (OFSI, HM Treasury)
- Regulation (EU) 2021/821 (dual-use export-control recast); Außen-
  wirtschaftsgesetz (AWG) / Außenwirtschaftsverordnung (AWV) (Germany,
  BAFA)
