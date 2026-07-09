# cloud-itonami-isic-0620

Open Business Blueprint for **ISIC Rev.5 0620**: Natural Gas Extraction --
gas-well intake, per-jurisdiction well-construction/well-control/sour-
service regulatory assessment, gas extraction, and production settlement
for a community operator.

This repository publishes a natural-gas-extraction actor -- gas-well
intake, per-jurisdiction gas-well-safety regulatory assessment, gas
extraction and production settlement -- as an OSS business that any
qualified operator can fork, deploy, run, improve and sell, so a
regional producer never surrenders well-integrity and production-
accounting data to a closed SCADA/production-AI SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is **GasFieldAdvisor ⊣ Gas
Well Safety Governor**. This blueprint's own
`:itonami.blueprint/governor` keyword, `:gas-well-safety-governor`, is a
UNIQUE keyword fleet-wide (grep-verified: no other blueprint declares
it) -- a fresh, independent build, modeled on the closest sibling
`cloud-itonami-isic-0610` (crude petroleum extraction, ISIC 0610).

**Unlike `cloud-itonami-isic-4920` (which wraps a pre-existing
bespoke capability library `kotoba-lang/logistics`), this vertical is
SELF-CONTAINED**: there is no `kotoba-lang/natural-gas` to delegate
gas-well-safety validation to, so the reservoir-pressure / annular-vs-
MAASP / CO2-corrosion / H2S-vs-IDLH range checks live as pure functions
in `gasfield.registry` and are re-verified independently by the
governor, rather than wrapping an external capability library's own
validated function.

> **Why an actor layer at all?** An LLM is great at drafting a gas-well
> summary, normalizing records, and reading a pressure gauge -- but it
> has **no notion of which jurisdiction's well-construction/well-
> control/sour-service law is official, no license to open a real gas
> well to flow against a live reservoir or settle real production, and
> no way to know on its own whether the measured reservoir pressure
> actually lies inside the declared safe window, whether the annulus
> pressure is actually below the MAASP, whether the CO2 content is
> actually below the corrosion ceiling, or whether the H2S
> concentration is actually below the IDLH**. Letting it extract gas
> or settle production directly invites fabricated regulatory
> citations, a gas well flowing with a lost zonal-isolation envelope or
> a corroded tubing string, and an extraction starting into a sour
> column above the IDLH -- exposing the crew to a lethal blowout and
> the operator to real liability, for whoever runs it. This project
> seals the GasFieldAdvisor into a single node and wraps it with an
> independent **Gas Well Safety Governor**, a human **approval
> workflow**, and an immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers gas-well intake through well-construction/well-
control/sour-service regulatory assessment, gas extraction and
production settlement. It does **not**, by itself, hold any
mining/concession right or operating authority required to run a
natural-gas-extraction business in a given jurisdiction, and it does
not claim to. It also does not perform the actual physical
drilling/workover itself, or judge reservoir economics -- reservoir
simulation and recovery-factor optimization (the blueprint's own
`:optimization` technology) is a follow-up slice, not in this R0.
Whoever deploys and operates a live instance (a qualified production
superintendent/operator) supplies any jurisdiction-specific operating
authority, the real gas-wellhead/workover-robot dispatch integration
and the real SCADA/production-accounting integrations, and bears that
jurisdiction's liability -- the software supplies the governed,
spec-cited, audited execution scaffold so that operator does not have
to build the compliance layer from scratch.

### Actuation

**Extracting gas from a real well and settling real production are
never autonomous, at any phase, by construction.** Two independent
layers enforce this (`gasfield.governor`'s `:well/extract`/`:production/
settle` high-stakes gate and `gasfield.phase`'s phase table, which never
puts either op in any phase's `:auto` set) -- see `gasfield.phase`'s
docstring and `test/gasfield/phase_test.clj`'s
`well-extract-never-auto-at-any-phase`/`production-settle-never-auto-at-
any-phase`. The actor may draft, check and recommend; a human production
superintendent is always the one who actually opens a gas well to flow
or settles a production period. Grounded in well-control doctrine (the
same discipline every regulator in `gasfield.facts` codifies: a real
extraction and a real settlement are human sign-off acts) -- a genuine
DUAL-actuation shape, applied SEQUENTIALLY to the SAME gas well
(extraction first, settlement later), unlike `retailops`/4711's own
`:kind`-distinguished alternative-action shape.

## The core contract

```
gas-well intake + jurisdiction facts (gasfield.facts, spec-cited)
        |
        v
   ┌───────────────────────┐   proposal      ┌───────────────────────────┐
   │ GasFieldAdvisor       │ ─────────────▶ │ Gas Well Safety Governor   │  (independent system)
   │ (sealed)              │  + citations    │ spec-basis · evidence-     │
   └───────────────────────┘                 │ incomplete · pressure-     │
          │                 commit ◀┼ out-of-range (two-sided       │
          │                         │ tolerance) · annular-          │
    record + ledger        escalate ┼ pressure-excessive (ratio) ·   │
          │              (ALWAYS for│ co2-corrosion-threshold ·       │
          │       :well/extract/    │ h2s-toxic-threshold ·           │
          │       :production/      │ integrity-flag-unresolved ·     │
          │       settle)           │ already-extracted ·             │
          ▼                          │ already-settled                │
      human approval                 └───────────────────────────┘
```

**The GasFieldAdvisor never extracts gas from a gas well or settles
production the Gas Well Safety Governor would reject, and never does so
without a human sign-off.** Hard violations (fabricated regulatory
requirements; unsupported evidence; a reservoir pressure outside the
safe window; an annular pressure above MAASP; a CO2 content above the
corrosion ceiling; an H2S concentration above the IDLH; an unresolved
integrity flag; a double extraction/settlement) force **hold** and
*cannot* be approved past; a clean extraction/settlement proposal still
always routes to a human.

## Run

```bash
clojure -M:dev:run     # walk one clean extract + settlement lifecycle, plus six HARD-hold cases, through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here an autonomous gas-wellhead or
workover robot performs the physical act of opening a gas well tree to
flow (and eventually closing it), under the actor, gated by the
independent **Gas Well Safety Governor**. The governor never dispatches
hardware itself: an extraction-clearing action must have cleared the
same sign-off a human production superintendent would need. This
restates the fleet-wide robotics premise three ways (ADR-2607011000):
the blueprint declares `:robotics true`, the README names the robot
that performs the physical act, and the Gas Well Safety Governor is the
independent gate that robot's command must pass -- a robot may turn
the valve, but only after the governor and a human superintendent
both agree it is safe to.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Gas Well Safety Governor, extract/settlement draft records, audit ledger |
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
`0620`). Unlike the freight sibling, this vertical is NOT backed by a
separate bespoke domain capability lib: the gas-well-safety range
checks (reservoir-pressure window, annular-vs-MAASP, CO2 corrosion,
H2S-vs-IDLH) are self-contained pure functions in `gasfield.registry`,
on top of the generic robotics/identity/forms/dmn/bpmn/audit-ledger
stack.

## Layout

| File | Role |
|---|---|
| `src/gasfield/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + extract AND production history (dual history). The double-actuation guard checks dedicated `:gas-extracted?`/`:production-settled?` booleans rather than a `:status` value |
| `src/gasfield/registry.cljc` | Extract/settlement draft records, plus the self-contained gas-well-safety range-check pure functions (`pressure-out-of-range?`, `well-integrity-annular-pressure-excessive?`, `co2-corrosive?`, `h2s-toxic?`) the governor re-verifies against -- no external capability library to delegate to |
| `src/gasfield/facts.cljc` | Per-jurisdiction well-construction/well-control/sour-service catalog with an official spec-basis citation + NIOSH H2S IDLH per entry (expressed as 0.005 vol% = 50 ppm), honest coverage reporting |
| `src/gasfield/gasfieldadvisor.cljc` | **GasFieldAdvisor** -- `mock-advisor` ‖ `llm-advisor`; intake/reservoir-assessment/extract/settlement proposals |
| `src/gasfield/governor.cljc` | **Gas Well Safety Governor** -- 7 HARD checks (spec-basis · evidence-incomplete · pressure-out-of-range, the aerospace two-sided-tolerance discipline · well-integrity-annular-pressure-excessive, the fabrication ratio discipline · h2s-toxic-threshold · co2-corrosion-threshold · integrity-flag-unresolved) + 2 double-actuation guards + 1 soft (confidence/actuation gate) |
| `src/gasfield/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted assess → supervised (extract/settlement always human; gas-well intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/gasfield/operation.cljc` | **OperationActor** -- langgraph StateGraph |
| `src/gasfield/sim.cljc` | demo driver |
| `test/gasfield/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers gas-well intake through well-construction/well-
control/sour-service regulatory assessment, gas extraction and
production settlement -- the core governed lifecycle:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Gas-well intake + per-jurisdiction evidence checklisting, HARD-gated on an official spec-basis citation (`:well/intake`/`:reservoir/assess`) | Real SCADA/gas-wellhead-robot integration, reservoir simulation and recovery-factor optimization |
| Gas extraction, HARD-gated on full evidence, an in-window reservoir pressure, an annular pressure below MAASP, a CO2 content below the corrosion ceiling, an H2S below the IDLH and no open integrity flag, plus a double-extraction guard (`:well/extract`) | |
| Production settlement, HARD-gated on full evidence and no double-settlement (`:production/settle`) | |
| Immutable audit ledger for every intake/assessment/extract/settlement decision | |

Extending coverage is additive: add the next gate (e.g. a flaring-
permit check) as its own governed op with its own HARD checks and
tests, following the SAME "an independent governor re-verifies against
the actor's own records before any real-world act" pattern this repo's
flagship ops already establish.

## Jurisdiction coverage (honest)

`gasfield.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `gasfield.facts/catalog` --
currently 4 seeded (JPN, USA, GBR, NOR) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `gasfield.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to
make coverage look bigger.

## Maturity

`:implemented` -- `GasFieldAdvisor` + `Gas Well Safety Governor` run as
real, tested code (see `Run` above), promoted from the
originally-published `:blueprint`-tier scaffold, following the SAME
governed-actor architecture as the other prior actors across this
fleet, with its own distinct, independently-named governor and its own
self-contained gas-well-safety range checks. See
`docs/adr/0001-architecture.md` for the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
