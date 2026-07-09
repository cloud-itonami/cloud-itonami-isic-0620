# Business Model: Community Natural Gas Extraction

## Classification
- Repository: `cloud-itonami-isic-0620`
- ISIC Rev.5: `0620` — extraction of natural gas
- Domain: `upstream/gas-extraction`
- Social impact: crew safety, environmental protection, transparency
- Governor: `:gas-well-safety-governor`
- License: AGPL-3.0-or-later

## Scope
This actor covers gas-well intake through per-jurisdiction well-construction /
well-control / sour-service regulatory assessment, gas extraction (opening a
real gas well to flow against a live reservoir), and production settlement
(royalty / royalty-volume finalization and custody transfer) for a community
natural-gas producer. It does **not**, by itself, hold any mining or concession
right or operating authority required to run a natural-gas-extraction business
in a given jurisdiction, perform the actual physical drilling or workover, or
judge reservoir economics (reservoir simulation and recovery-factor
optimization is a follow-up slice, not this R0). Whoever deploys a live
instance supplies the jurisdiction-specific operating authority, the real
gas-wellhead/workover-robot and SCADA/production-accounting integrations, and
bears that jurisdiction's liability -- the software supplies the governed,
spec-cited, audited execution scaffold so the operator does not have to build
the compliance layer from scratch.

## Customer
- regional and community natural-gas producers and field operators
- independent operators and marginal/onshore-field operators leaving closed
  SCADA / production-AI SaaS
- national-oil-company subsidiaries running community gas fields
- royalty owners and regulators who need an auditable, spec-cited gas-well
  record

## Offer
- gas-well intake and directory management
- per-jurisdiction well-construction / well-control / sour-service regulatory
  assessment with an official spec-basis citation
- gas extraction (starting flow) gated on full evidence and a clean
  well-integrity / sour-service / CO2-corrosion envelope
- production settlement (royalty / royalty-volume finalization, custody
  transfer) with double-settlement prevention
- evidence checklisting (mining/concession right, casing-integrity log, BOP
  test record, cementing record)
- integrity-flag and exception workflows
- role-based access and immutable audit ledger

## Revenue
- self-host setup fee
- managed hosting subscription per operator / field
- support retainer with SLA
- SCADA and production-accounting integration

## The `:gas-well-safety-governor` Decision Rule

This blueprint's `:itonami.blueprint/governor` is `:gas-well-safety-governor`.
It is the single authority that stands between "a gas well could be opened to
flow" and "a gas well is allowed to flow," and between "a production period
could be settled" and "it is allowed to settle." Every rule it enforces is
traceable to the domain (Community Natural Gas Extraction, ISIC 0620) and to
the three `:social-impact` tags in `blueprint.edn` (`:safety`,
`:environmental-protection`, `:transparency`).

This is the rule the companion contract test
(`test/gasfield/governor_contract_test.clj`) encodes end-to-end: the
GasFieldAdvisor never extracts gas from a gas well or settles production the
Gas Well Safety Governor would reject, `:well/extract` and
`:production/settle` NEVER auto-commit at any phase, `:well/intake` (no direct
capital risk) MAY auto-commit when clean, and every decision (commit OR hold)
leaves exactly one ledger fact.

**Authorizes a gas extraction (`:well/extract`) or production settlement
(`:production/settle`) only when ALL of the following hold:**

1. **An official spec-basis citation exists for the jurisdiction** -- the
   governor will not authorize any `:reservoir/assess`, `:well/extract`, or
   `:production/settle` proposal whose jurisdiction has no entry in the
   `gasfield.facts` catalog (`:no-spec-basis`). This is the direct enforcement
   of `:transparency`: a jurisdiction whose well-construction / well-control /
   sour-service requirements cannot be traced to an OFFICIAL public source is
   never guessed. The advisor must not fabricate a jurisdiction's requirements.
2. **The jurisdiction's required evidence is fully on file** -- for an
   extraction or settlement the gas well's jurisdiction must have been assessed
   with a complete well-construction / well-control evidence checklist on
   record: the mining/concession right, the casing-integrity log, the
   blowout-preventer (BOP) test record, and the cementing record
   (`:evidence-incomplete`). This protects `:safety` and
   `:environmental-protection`: a gas well that cannot prove zonal isolation
   and well-control readiness never flows.
3. **The measured reservoir pressure stays inside its declared safe window
   `[min, max]`** -- the governor INDEPENDENTLY re-verifies the gas well's own
   recorded reservoir pressure against its two-sided safe operating envelope
   (`gasfield.registry/pressure-out-of-range?`, the aerospace two-sided-
   tolerance discipline applied to subsurface pressure). Below `min` risks
   formation damage / water-or-gas coning; above `max` risks formation fracture
   and loss of zonal isolation -- the precursor to an underground blowout
   (`:pressure-out-of-range`).
4. **The annular pressure is below the Maximum Allowable Annular Surface
   Pressure (MAASP)** -- the governor INDEPENDENTLY re-verifies the gas well's
   own annulus pressure against its MAASP via the pure function
   `gasfield.registry/well-integrity-annular-pressure-excessive?` (the
   fabrication measured-ratio-vs-rated-limit discipline). Annular pressure
   above MAASP can lift the casing shoe off its seat and lose zonal isolation:
   this is a true (surface) blowout precursor, evaluated ahead of any
   flow-rate signal (`:well-integrity-annular-pressure-excessive`).
5. **The H2S content is below the NIOSH Immediately-Dangerous-to-Life-or-
   Health (IDLH) threshold (50 ppm = 0.005 vol%)** -- the governor
   INDEPENDENTLY re-verifies the gas well's H2S content (a volume percent)
   against the jurisdiction's IDLH, also expressed as a volume percent
   (`gasfield.registry/h2s-toxic?`, the fabrication measured-value-vs-rated-
   limit discipline, applied to sour-service toxicity). Extracting from a gas
   well whose H2S exceeds the IDLH without the sour-service controls the
   evidence checklist demands exposes the crew to a lethal gas
   (`:h2s-toxic-threshold`).
6. **The CO2 content is below the declared corrosion ceiling** -- the governor
   INDEPENDENTLY re-verifies the gas well's CO2 content against its
   operator-declared `co2-corrosion-max` (`gasfield.registry/co2-corrosive?`,
   the fabrication measured-ratio-vs-rated-limit discipline, applied to sweet
   CO2 corrosion). Producing above the declared CO2 ceiling for carbon-steel
   service without corrosion mitigation (inhibition / corrosion-resistant
   alloys) risks metal-loss, pinhole leaks and tubing/casing rupture
   (`:co2-corrosion-threshold`).
7. **No unresolved integrity flag is open on the gas well** -- an integrity
   flag raised by this proposal itself or already on file, and not yet
   resolved, is a hard, un-overridable hold (`:integrity-flag-unresolved`).
   Integrity concerns cannot be silently suppressed to force an extraction or
   settlement through.
8. **The gas well has not already been extracted, and production has not
   already been settled** -- a double extraction of the same gas well is
   refused off a dedicated `:gas-extracted?` fact, and a double settlement off
   a dedicated `:production-settled?` fact (never a `:status` value), the
   double-actuation guard every sibling actor in this fleet enforces
   (`:already-extracted` / `:already-settled`).

**Rejects (HOLD, un-overridable, never even reaches a human) when any of the
above fail.** A proposal with no spec-basis, incomplete evidence, a reservoir
pressure outside its window, an annular pressure above MAASP, a CO2 content
above the corrosion ceiling, an H2S concentration above the IDLH, an open
integrity flag, or a double extraction/settlement is held at the governor node
-- a human approver cannot override these, by construction.

**Always escalates to a human (never auto-commits) for `:well/extract` and
`:production/settle`**, even when every check above is clean. Extracting gas
from a real well (starting flow against a live reservoir) and settling real
production (real money and real royalty volumes moving between operator and
royalty owner) are the two real-world actuation events this actor performs;
both are always a human production superintendent's call. This is enforced by
TWO independent layers that agree on purpose: the governor's confidence /
actuation SOFT gate (a `:well/extract` / `:production/settle` stake always
escalates) and `gasfield.phase`'s phase table, which never puts either op in
any phase's `:auto` set. The `:environmental-protection` tag is enforced
upstream of the governor, in the reservoir-assessment evidence step -- the
governor's job is extraction/settlement authorization integrity, not
recovery-factor optimization.

## Required Technologies

`blueprint.edn`'s `:itonami.blueprint/required-technologies` for this business,
and what each one is actually load-bearing for here (not a generic capability
list):

| Technology | What it is FOR in Community Natural Gas Extraction |
|---|---|
| `:robotics` | The autonomous gas-wellhead/workover robot that performs the physical act of opening a gas well tree to flow (and eventually closing it). The governor never dispatches hardware itself: an extraction-clearing action must have cleared the same sign-off a human production superintendent would need (see Robotics Premise). |
| `:identity` | Operator, production-superintendent, and crew identity plus role-based access, so the governor's sign-off is tied to *who* authorized an extraction or settlement, not just *that* someone did. |
| `:forms` | Structured intake for gas-well booking, per-jurisdiction evidence capture (mining right, casing log, BOP test, cementing record), and integrity-flag submission -- the data the Decision Rule above actually evaluates comes in through these forms. |
| `:dmn` | Encodes the `:gas-well-safety-governor` Decision Rule itself (spec-basis, evidence completeness, the four physical range checks, the integrity flag, the double-actuation guards, the actuation gate) as an evaluable decision table rather than code buried in application logic -- this is what makes the governor auditable and swappable per-deployment. |
| `:bpmn` | Orchestrates the intake -> assess -> extract -> settle -> audit loop end-to-end (see `docs/operator-guide.md`) across gas-well intake, reservoir assessment, gas extraction, and production settlement, including the integrity-flag escalation gate. |
| `:audit-ledger` | The immutable record of every assessment, extraction, settlement, integrity flag, and hold -- this is what "an auditable, spec-cited gas-well record for every extraction and settlement" (Trust Controls, below) actually means in practice, and the evidence an operator needs if an extraction or settlement is later disputed by a royalty owner or regulator. |
| `:optimization` | Reservoir simulation and recovery-factor optimization -- selects the recovery strategy for a field. This R0 build deliberately scopes optimization OUT (see README `Business-process coverage`); the capability is correctly marked required, the integration is a follow-up slice. |

There is NO bespoke `:natural-gas` capability library in this stack (unlike the
freight sibling's `:logistics`): the gas-well-safety range checks (reservoir-
pressure window, annular/MAASP, CO2 corrosion, H2S/IDLH) are self-contained
pure functions in `gasfield.registry`, on top of the generic
robotics/identity/forms/dmn/bpmn/audit-ledger stack (see Capability layer).

## Trust Controls
- a jurisdiction with no official spec-basis can never be assessed, extracted
  from, or settled against
- an extraction never starts with incomplete well-construction / well-control
  evidence
- an extraction never starts outside the reservoir-pressure window, above the
  annular MAASP, above the CO2 corrosion ceiling, above the H2S IDLH, or with
  an open integrity flag
- integrity flags cannot be silently suppressed
- the same gas well can never be extracted or settled twice
- an extraction or settlement never auto-commits; both always need a human
  production superintendent
- every extraction and settlement (commit OR hold) leaves exactly one
  immutable ledger fact
- gas-well, reservoir, and production data stays outside Git

## Implementation notes (`:implemented`)

The Decision Rule above is implemented faithfully by `gasfield.governor` as
nine HARD checks (a human approver cannot override them) plus one SOFT gate:

- `spec-basis-violations` -- the spec-basis check above, evaluated on every
  `:reservoir/assess`, `:well/extract`, and `:production/settle`.
- `evidence-incomplete-violations` -- the evidence-completeness check above,
  for `:well/extract` / `:production/settle`.
- `pressure-out-of-range-violations` -- the two-sided reservoir-pressure
  window above, an honest reapplication of the aerospace two-sided-tolerance
  discipline to subsurface pressure; evaluated unconditionally on every
  `:well/extract`.
- `well-integrity-annular-pressure-excessive-violations` -- the annular/MAASP
  check above, an honest reapplication of the fabrication measured-ratio-vs-
  rated-limit discipline; a true blowout precursor, evaluated unconditionally
  on every `:well/extract`.
- `h2s-toxic-threshold-violations` -- the H2S/IDLH check above, an honest
  reapplication of the fabrication measured-value-vs-rated-limit discipline
  to sour-service toxicity, grounded in the NIOSH IDLH (50 ppm = 0.005 vol%);
  evaluated unconditionally on every `:well/extract`.
- `co2-corrosion-threshold-violations` -- the CO2 corrosion check above, an
  honest reapplication of the fabrication ratio discipline to sweet CO2
  corrosion; evaluated on every `:well/extract`.
- `integrity-flag-unresolved-violations` -- the open-integrity-flag check
  above (the same open-flag-unresolved discipline the freight sibling's
  delivery-exception-unresolved check establishes); evaluated unconditionally
  on every `:well/extract`.
- `already-extracted-violations` / `already-settled-violations` -- the
  double-actuation guards above, off dedicated `:gas-extracted?` /
  `:production-settled?` booleans (never a `:status` value), the same
  discipline every sibling governor's guards establish.
- the confidence floor / actuation SOFT gate -- low confidence, OR a
  `:well/extract` / `:production/settle` stake, escalates to a human; and
  `gasfield.phase` independently never auto-commits either op at any phase.

`:well/extract` and `:production/settle` are the two real-world actuation
events (`#{:well/extract :production/settle}`), applied SEQUENTIALLY to the
SAME gas well (extraction first, settlement later) rather than the retail
sibling's `:kind`-distinguished alternative-action shape -- the same
sequential dual-actuation shape the repair-shop and quarrying clusters use.
Neither ever auto-commits at any phase. Reservoir simulation and
recovery-factor optimization (the `:optimization` line above) is a follow-up
slice, not in this R0 build -- see README `Business-process coverage`.

## Capability layer

Unlike `cloud-itonami-isic-4920` (which wraps a pre-existing bespoke
capability library `kotoba-lang/logistics`), this vertical is SELF-CONTAINED:
there is no `kotoba-lang/natural-gas` to delegate gas-well-safety validation
to. The reservoir-pressure / annular-vs-MAASP / CO2-corrosion / H2S-vs-IDLH
range checks live as pure functions in `gasfield.registry` and are re-verified
independently by the governor, rather than wrapping an external capability
library's own validated function -- the same 'reuse a capability's own
validated function' discipline, here applied to this vertical's OWN pure
registry functions.

## Jurisdiction coverage (honest)

`gasfield.facts/catalog` currently seeds 4 jurisdictions with an official
spec-basis, each a REAL regime: Japan (METI Mine Safety Regulations /
鉱山保安規則 over natural-gas wells), the United States (BSEE, 30 C.F.R. Part
250, plus OSHA Process Safety Management), the United Kingdom (HSE Offshore
Safety Division), and Norway (Petroleum Safety Authority, Activities
Regulations). The NIOSH H2S IDLH (50 ppm = 0.005 vol%) is the internationally
cited acute-toxicity reference each of these regulators' sour-service rules
are ultimately grounded in. This is a starting catalog to prove the governor
contract end-to-end, not a claim of global coverage (4 of ~194 jurisdictions
worldwide). Adding a jurisdiction is additive: one map entry in
`gasfield.facts/catalog`, citing a real official source -- never fabricate a
jurisdiction's requirements to make coverage look bigger.

## Maturity

`:implemented` -- `GasFieldAdvisor` + `Gas Well Safety Governor` run as real,
tested code (`clojure -M:dev:test`: 41 tests / 205 assertions, 0 failures;
lint clean), promoted from the originally-published `:blueprint`-tier
scaffold, following the SAME governed-actor architecture as the other prior
actors across this fleet, with its own distinct, independently-named governor
and its own self-contained gas-well-safety range checks. See
`docs/adr/0001-architecture.md` for the history and design.

## Robotics Premise

`blueprint.edn` sets `:itonami.blueprint/robotics true`. In this domain an
autonomous gas-wellhead or workover robot performs the physical act of opening
a gas well tree to flow (and eventually closing it), under the actor, gated by
the independent **Gas Well Safety Governor**. The governor never dispatches
hardware itself: an extraction-clearing action must have cleared the same
sign-off a human production superintendent would need. A robot may turn the
valve, but only after the governor (every HARD check clean) and a human
superintendent both agree it is safe to -- the same
operating-state-machine-gated-by-governor premise every cloud-itonami vertical
restates (ADR-2607011000): the blueprint declares `:robotics true`, the README
names the robot that performs the physical act, and the Gas Well Safety
Governor is the independent gate that robot's command must pass.
