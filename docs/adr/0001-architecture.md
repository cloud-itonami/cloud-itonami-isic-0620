# ADR-0001: GasFieldAdvisor ⊣ Gas Well Safety Governor architecture

## Status

Accepted. `cloud-itonami-isic-0620` promoted from `:blueprint` to
`:implemented` in the `kotoba-lang/industry` registry.

## Context

`cloud-itonami-isic-0620` publishes an OSS business blueprint for community
natural-gas extraction (gas-well intake, per-jurisdiction well-construction /
well-control / sour-service regulatory assessment, gas extraction, and
production settlement). Like every prior actor in this fleet, the blueprint
alone is not an implementation: this ADR records the governed-actor
architecture that promotes it to real, tested code, following the same
langgraph StateGraph + independent Governor + Phase 0->3 rollout pattern
established by `cloud-itonami-isic-6511` (life insurance) and applied across
the fleet.

This actor is the upstream natural-gas sibling of `cloud-itonami-isic-0610`
(crude petroleum extraction, ISIC 0610) -- the closest fleet sibling by shared
well-safety discipline -- and was modeled on it: the same dual-actuation
sequential shape (extract first, settle later, on the SAME well entity), the
same self-contained physical range-check suite, the same Store protocol +
MemStore/DatomicStore parity. The domain difference is the fluid (natural gas
vs crude oil), which changes the well-composition fields (methane / CO2 / sour
H2S by volume percent, not API gravity / sulfur / BS&W water cut) and replaces
the crude-specific BS&W water-cut gate with a CO2-corrosion gate relevant to
gas service.

Like `cloud-itonami-isic-0610` and `cloud-itonami-isic-0810` (quarrying),
this vertical has NO bespoke domain capability library in `kotoba-lang` to
wrap (verified: no `kotoba-lang/natural-gas`-style repo exists, and
`kotoba-lang/robotics` is the generic cross-cutting robotics contract every
cloud-itonami vertical already uses, not a domain-specific library for this
vertical). This build therefore uses self-contained domain logic -- the same
pattern the majority of this fleet's actors use, and the explicit
differentiator from `cloud-itonami-isic-4920` (which wraps a pre-existing
`kotoba-lang/logistics` library). The gas-well-safety range checks (reservoir-
pressure window, annular/MAASP, CO2 corrosion, H2S/IDLH) live as pure
functions in `gasfield.registry` and are re-verified independently by the
governor.

This blueprint's own `:itonami.blueprint/governor` keyword,
`:gas-well-safety-governor`, is grep-verified UNIQUE fleet-wide -- no naming-
collision precedent question, a fresh independent build.

## Decision

### Decision 1: fresh governor identity, no reuse precedent needed

`:gas-well-safety-governor` is grep-verified unique across every
`blueprint.edn` in this fleet. This build follows the SAME governed-actor
architecture as every prior actor, but with its own distinct governor identity.

### Decision 2: self-contained domain logic (no `kotoba-lang/natural-gas` to wrap)

Unlike `cloud-itonami-isic-4920` (freight, which delegates tracking-number
validation to a real, pre-existing `kotoba-lang/logistics` capability
library), this natural-gas-extraction vertical has NO pre-existing natural-gas
capability library to delegate gas-well-safety validation to. The four
physical range checks (reservoir-pressure window, annular-vs-MAASP, CO2
corrosion, H2S-vs-IDLH) are therefore pure functions defined in
`gasfield.registry` and called directly by `gasfield.governor` -- the SAME
'reuse a capability's own validated function' discipline
`retailops.governor`'s ean13 check establishes for a capability library,
here applied to this vertical's OWN pure registry functions rather than a
separate library. No literal code is shared with any sibling (different
domain), but the discipline is the same.

### Decision 3: dual-actuation shape, SEQUENTIAL on the SAME `gas-well` entity

Unlike the retail sibling's `order` entity (distinguished by `:kind`,
alternative sale-or-reorder actions), this vertical's `extract` and `settle`
actuation events apply SEQUENTIALLY to the SAME `gas-well` -- a gas
extraction happens first (flow started against a live reservoir), production
settlement happens later (royalty / royalty-volume finalization, custody
transfer), on the same gas-well record. This matches the repair-shop
cluster's `ticket`, the quarrying cluster's `extraction`, and the crude
sibling's `well` shape (two real-world acts, in order, on one entity).
`high-stakes` is `#{:well/extract :production/settle}`; neither ever
auto-commits at any phase.

### Decision 4: the gas-well-safety physical range-check suite -- honest reapplications of established fleet disciplines

The four physical range checks the governor runs on every `:well/extract` are
each an honest reapplication of an established fleet discipline to an
upstream-natural-gas value, documented as such rather than claimed as novel
inventions (the same convention `cloud-itonami-isic-0162`'s Decision 3
establishes for `dose-matches-claim?`):

- `pressure-out-of-range?` reapplies the **aerospace two-sided-tolerance**
  discipline to subsurface pressure: the gas well's measured reservoir
  pressure must stay inside its declared safe window `[min, max]`. Below `min`
  risks formation damage / water-or-gas coning; above `max` risks formation
  fracture and loss of zonal isolation (an underground-blowout precursor).
- `well-integrity-annular-pressure-excessive?` reapplies the **fabrication
  measured-ratio-vs-rated-limit** discipline to the annulus vs the Maximum
  Allowable Annular Surface Pressure (MAASP). Annular pressure above MAASP can
  lift the casing shoe off its seat and lose zonal isolation: a true (surface)
  blowout precursor, evaluated ahead of any flow-rate signal.
- `co2-corrosive?` reapplies the **fabrication measured-ratio-vs-rated-limit**
  discipline to the gas well's CO2 content vs the operator-declared
  `co2-corrosion-max` (the maximum CO2 content in vol% for carbon-steel
  service before corrosion mitigation -- inhibition / corrosion-resistant
  alloys -- becomes mandatory) -- a sweet-CO2-corrosion / tubing-rupture
  extraction-readiness gate, NOT a toxicity gate. This replaces the crude
  sibling's BS&W water-cut gate, which is crude-specific and not meaningful
  for a gas stream.
- `h2s-toxic?` reapplies the **fabrication measured-value-vs-rated-limit**
  discipline to the gas well's H2S content vs the jurisdiction's NIOSH
  Immediately-Dangerous-to-Life-or-Health threshold, grounded in the real
  NIOSH IDLH for hydrogen sulfide of **50 ppm = 0.005 vol%** (both arguments
  are in volume percent, since for a natural-gas stream H2S is reported as a
  volume percent of the gas) -- the internationally cited acute-toxicity
  reference each regulator's sour-service rules are ultimately grounded in.

Each returns `true` when the value is provably OUTSIDE the safe envelope; the
conservative well-safety choice, missing data is a violation (cannot verify
safe to extract). All four are evaluated UNCONDITIONALLY on every
`:well/extract`. No new unconditional-evaluation ordinals are claimed: every
check in this suite is a discipline-reapplication, documented per Decision 3
of `cloud-itonami-isic-0162`.

### Decision 5: `integrity-flag-unresolved?` -- the open-flag-unresolved discipline

An integrity flag raised by the proposal itself or already on file, and not
yet resolved, is a HARD, un-overridable hold. This reuses the SAME
open-flag-unresolved discipline the freight sibling's
`delivery-exception-unresolved?` check (and the parksafety sibling's flag
checks) establish -- an open concern cannot be silently suppressed to force
an extraction or settlement through. Evaluated UNCONDITIONALLY on every
`:well/extract`.

### Decision 6: dedicated double-actuation-guard booleans

`:gas-extracted?` / `:production-settled?` are dedicated booleans on the
`gas-well` record, never a single `:status` value -- the same discipline
every prior governor's guards establish, informed by `cloud-itonami-isic-6492`'s
real status-lifecycle bug (ADR-2607071320).

### Decision 7: Store protocol, MemStore + DatomicStore parity

`gasfield.store/Store` is implemented by both `MemStore` (atom-backed,
default for dev/tests/demo) and `DatomicStore` (`langchain.db`-backed),
proven to satisfy the same contract in
`test/gasfield/store_contract_test.clj`. The ledger stays append-only on
every backend: which gas well was screened for a reservoir pressure outside
its window, an annular pressure above MAASP, a CO2 content above the
corrosion ceiling, an H2S concentration above the IDLH, or an open
integrity flag, which gas well had gas extracted, which production was
settled, on what jurisdictional basis, approved by whom -- always a query
over an immutable log.

### Decision 8: Phase 0->3 with `:well/extract`/`:production/settle` NEVER auto

`gasfield.phase`'s phase table puts `:well/intake` (no direct capital risk)
in phase 3's `:auto` set as its only member; `:well/extract` and
`:production/settle` are deliberately ABSENT from every phase's `:auto` set,
including phase 3 -- a permanent structural fact. `gasfield.governor`'s
high-stakes gate enforces the same invariant independently: two layers agree
that actuation is always a human production superintendent's call.

### Decision 9: mock + LLM advisor pair

`gasfield.gasfieldadvisor` provides a deterministic `mock-advisor` (default,
runs offline) and an `llm-advisor` backed by a `langchain.model/ChatModel`.
The LLM advisor's EDN proposal is parsed defensively: any parse/shape failure
yields a safe low-confidence noop so the governor escalates/holds -- an LLM
hiccup can never auto-extract a gas well or auto-settle production.

## Alternatives considered

- **Wrapping a bespoke `kotoba-lang/natural-gas` capability library.**
  Considered and explicitly ruled out: no such library exists, and
  `kotoba-lang/robotics` is generic, not natural-gas-specific. Forcing a
  false capability-library integration would be dishonest; this build
  correctly uses self-contained domain logic instead.
- **Reusing the crude sibling's BS&W water-cut gate verbatim.** Rejected:
  a basic-sediment-and-water water cut is a crude-oil lift-readiness concept
  and is not meaningful for a gas stream, where the relevant carbon-steel
  integrity concern is CO2-driven sweet corrosion. The honest
  domain-appropriate replacement is the CO2-corrosion gate
  (`co2-corrosive?`), which reapplies the same fabrication ratio discipline
  to a gas-relevant value.
- **A `:kind`-distinguished entity** (matching the retail sibling's `order`
  shape). Rejected: extraction and settlement happen SEQUENTIALLY on the
  SAME gas well in this domain, not as alternative actions -- the
  repair-shop / quarrying / crude cluster's sequential shape is the honest
  match here.
- **Claiming genuinely-new unconditional-evaluation ordinals for the physical
  range checks.** Rejected: each check reapplies an established fleet
  discipline (aerospace two-sided-tolerance, fabrication ratio/value-vs-rated-
  limit, open-flag-unresolved) to a new domain. Per `cloud-itonami-isic-0162`
  Decision 3's convention, these are documented as honest
  discipline-reapplications, not claimed as novel inventions -- the same
  honesty discipline that forbids fabricating coverage also forbids
  over-claiming novelty.
- **Building reservoir simulation / recovery-factor optimization in this R0.**
  Rejected in favor of a scoped R0 slice (the `:optimization` capability is
  correctly marked required, the integration is a follow-up), consistent with
  this fleet's 'extending coverage is additive' convention.

## Consequences

- Natural-gas-extraction (ISIC 0620) sibling immediately following the crude
  `cloud-itonami-isic-0610` sibling it was modeled on; ~95 cloud-itonami
  fleet actors now populated, all on the same governed-actor architecture.
- Establishes the gas-well-safety physical range-check suite as honest
  reapplications of established fleet disciplines (two-sided-tolerance,
  ratio/value-vs-rated-limit, open-flag-unresolved) to upstream natural gas
  -- no genuinely-new-concept check (the CO2-corrosion gate replaces the
  crude-specific water-cut gate with the domain-appropriate gas-relevant
  one), all discipline-reuse documented as such per
  `cloud-itonami-isic-0162` Decision 3.
- `MemStore` || `DatomicStore` parity is proven by
  `test/gasfield/store_contract_test.clj`.
- 41 tests / 205 assertions pass; lint is clean; the demo
  (`clojure -M:dev:run`) walks one clean extract + settlement lifecycle,
  plus eight HARD-hold scenarios (no spec-basis, reservoir pressure,
  annular/MAASP, CO2 corrosion, H2S/IDLH, integrity flag, double extraction,
  double settlement), end-to-end.
- `blueprint.edn` required no field-sync fixes (already correct) -- only the
  `:maturity` flip itself.

## References

- `cloud-itonami-isic-0610/docs/adr/0001-architecture.md` (crude sibling;
  the closest fleet sibling by shared well-safety discipline -- this actor
  was modeled on it)
- `cloud-itonami-isic-6511/docs/adr/0001-architecture.md` (origin of the
  general governed-actor architecture pattern)
- `cloud-itonami-isic-4920/docs/adr/0001-architecture.md` (freight sibling;
  contrast: wraps a pre-existing `kotoba-lang/logistics` capability library)
- `cloud-itonami-isic-0162/docs/adr/0001-architecture.md` (origin of the
  'honest reapplication, documented as such' convention this build follows
  for its physical range checks)
- 鉱山保安規則 (Mine Safety Regulations), 天然ガスの採掘; 石油コンビナート等災害防止法 (Japan, METI)
- BSEE Oil and Gas and Sulphur Operations in the Outer Continental Shelf, 30 C.F.R. Part 250; OSHA Process Safety Management, 29 C.F.R. §1910.119 (US)
- Offshore Safety Act 1992; Offshore DCR / PFEER; Offshore Installations (Safety Case) Regulations 2005 (UK, HSE)
- Activities Regulations (Aktivitetsforskriftenen); Framework Regulations; Facilities Regulations (Norway, Petroleum Safety Authority)
- NIOSH Immediately Dangerous to Life or Health (IDLH) value for hydrogen sulfide: 50 ppm (= 0.005 vol%, expressed in volume percent to compare directly with the gas-well's own `:h2s-percent` field)
