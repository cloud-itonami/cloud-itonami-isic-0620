# Operator Guide

## First Deployment
1. Register operators, fields, gas wells, and production superintendents.
2. Import gas-well, reservoir, and production history.
3. Seed the per-jurisdiction spec-basis catalog (`gasfield.facts`) for the
   jurisdictions you actually operate in, citing real official sources only.
4. Run read-only spec-basis validation per jurisdiction.
5. Configure integrity-flag escalation and production-accounting accounts.
6. Publish a dry-run settlement and audit export.

## Minimum Production Controls
- spec-basis validation before any assessment, extraction, or settlement
- full well-construction / well-control evidence (mining right, casing log,
  BOP test, cementing record) before any extraction
- reservoir-pressure window, annular/MAASP, CO2-corrosion, H2S/IDLH and
  integrity-flag checks before any extraction
- integrity-flag escalation gate
- audit export for every extraction, settlement, and hold
- backup manual extraction and production-settlement process

## A Day in the Life: Intake → Assess → Extract → Settle → Audit

Community Natural Gas Extraction (ISIC 0620, `cloud-itonami-isic-0620`)
runs on the same intake / advise / govern / decide / commit-or-hold loop as
every itonami blueprint, but here the loop is concrete: a regional producer
needs to bring a gas well (say, an onshore gas well in the Minami-Akita
field) from intake through reservoir safety assessment to a gas extraction
and a production settlement. Walking through one gas well, end to end:

1. **Intake.** The operator books the gas well through `:forms`: field name,
   well name, operator, jurisdiction, and the gas well's own physical record
   (methane and CO2 composition with its declared CO2-corrosion ceiling, H2S
   content, measured reservoir pressure with its safe window [min, max],
   annular pressure and MAASP, flow rate). This creates a gas-well record at
   `:well/intake` status. The GasFieldAdvisor only normalizes the patch; it
   does not invent the field name, operator, jurisdiction, or any physical
   value.
2. **Assess.** The GasFieldAdvisor drafts a per-jurisdiction well-
   construction / well-control / sour-service evidence checklist
   (`:reservoir/assess`) from `gasfield.facts`, citing the jurisdiction's
   official spec-basis (owner authority, legal basis, provenance) and listing
   the required evidence (mining/concession right, casing-integrity log, BOP
   test record, cementing record). The `:gas-well-safety-governor` sign-off
   gate must clear: it checks the jurisdiction actually has an official
   spec-basis on file (never invent one). A jurisdiction with no spec-basis is
   a HARD hold at the governor node -- it never even reaches a human. This
   assessment always escalates to a human for approval; it is never auto.
3. **Extract.** Before the gas well can be opened to flow, the
   `:gas-well-safety-governor` sign-off gate runs the full HARD check set
   against the gas well's own ground truth: the spec-basis exists, the
   evidence checklist is complete, the measured reservoir pressure is inside
   `[min, max]`, the annular pressure is below MAASP, the CO2 content is below
   its corrosion ceiling, the H2S content is below the NIOSH IDLH
   (50 ppm = 0.005 vol%), no integrity flag is open, and the gas well has not
   already been extracted. Any failure is a HARD hold that a human cannot
   override. If every check is clean, the proposal STILL always escalates to a
   human production superintendent -- a `:well/extract` never auto-commits at
   any phase. On approval, the extraction record is drafted
   (`<JURISDICTION>-EXTRACT-000001`) and the gas well's `:gas-extracted?`
   flag is set.
4. **Settle.** Once gas has actually been extracted, the production period is
   settled (`:production/settle`): royalty / royalty-volume finalization and
   custody transfer. The governor re-checks the spec-basis, the evidence
   completeness, and that this gas well's production has not already been
   settled. As with the extraction, a clean settlement STILL always escalates
   to a human production superintendent -- `:production/settle` never
   auto-commits. On approval the settlement record is drafted
   (`<JURISDICTION>-PROD-000001`) and the gas well's `:production-settled?`
   flag is set.
5. **Audit.** The assessment, the extraction sign-off, the extraction record,
   the settlement sign-off, and the settlement record are all appended to the
   `:audit-ledger` -- immutable and exportable, so a royalty or custody
   dispute can be traced back to the exact spec-basis citation, evidence
   checklist, and superintendent sign-off that authorized the extraction and
   settlement. If something is wrong with the gas well (a pressure anomaly, a
   casing concern, a sour-service or CO2-corrosion excursion), that gets
   raised as an integrity flag and routed through the escalation gate instead
   of being silently suppressed -- an extraction for that gas well then waits
   on governor sign-off of the flag's resolution.

Any deviation from this loop is exactly what the Trust Controls in
`docs/business-model.md` exist to catch: a gas well assessed against a
fabricated spec-basis, an extraction started with incomplete evidence or
outside the reservoir-pressure window, an integrity flag suppressed to force
an extraction through, or a settlement posted without a human sign-off.

## Feel the Decision Gate: `clojure -M:dev:run`

This vertical has no companion playable prototype yet (unlike the freight
sibling's `itonami/freight-dispatch` game). The fastest hands-on way to feel
why the `:gas-well-safety-governor` gate exists is the bundled demo, which
walks one clean gas well through intake → assess → extract → settle (each
extract/settle pausing for human approval) and then exercises every
HARD-hold failure mode in isolation:

- a jurisdiction with no official spec-basis → HOLD (`:no-spec-basis`),
- a measured reservoir pressure outside the safe window → HOLD
  (`:pressure-out-of-range`),
- an annular pressure above MAASP → HOLD
  (`:well-integrity-annular-pressure-excessive`),
- a CO2 content above the corrosion ceiling → HOLD (`:co2-corrosion-threshold`),
- an H2S content above the IDLH → HOLD (`:h2s-toxic-threshold`),
- an unresolved integrity flag → HOLD (`:integrity-flag-unresolved`),
- a double extraction of the same gas well → HOLD (`:already-extracted`),
- a double settlement of the same gas well → HOLD (`:already-settled`).

Each HOLD settles at the governor node and never reaches a human approver --
the same failure mode the audit ledger is built to catch and the minimum
production controls above are built to prevent. It is not a substitute for
those controls, but it is the fastest way for a new operator (or a reviewer)
to feel, hands-on, why the gate exists before touching a real deployment.

## Certification
Certified operators must prove spec-basis-grounded assessment, evidence-backed
extraction readiness (reservoir pressure, annular/MAASP, CO2 corrosion,
H2S/IDLH, integrity flag), and human review for every extraction- and
settlement-affecting action.
