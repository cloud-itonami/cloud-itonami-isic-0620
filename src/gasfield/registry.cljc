(ns gasfield.registry
  "Pure-function gas-extraction + production-settlement record
  construction -- an append-only gas-well book-of-record draft -- AND the
  pure gas-well-safety range-check functions the Gas Well Safety Governor
  calls to re-verify a gas well's own physical ground truth before any
  extraction.

  Unlike `freightops`/4920's own registry (which delegates tracking-
  number validation to a real, pre-existing bespoke capability library
  `kotoba-lang/logistics`), this natural-gas-extraction vertical has NO
  pre-existing capability library to wrap -- there is no 'kotoba-lang/
  natural-gas' to call. So this namespace is self-contained: the range
  checks (reservoir pressure two-sided window, annular pressure vs
  MAASP, CO2 corrosion vs the declared ceiling, H2S vs IDLH) are pure
  functions defined HERE, not delegated. The actor layer adds the
  governed proposal/approval loop on top; the governor calls these same
  pure functions to INDEPENDENTLY re-verify the gas well's own recorded
  values before any real-world extraction, rather than trusting the
  advisor's self-reported confidence.

  Like every sibling actor's registry, there is no single international
  reference-number standard for a gas-extraction or production-settlement
  record -- every operator/jurisdiction assigns its own reference
  format. This namespace does NOT invent one beyond a jurisdiction-
  scoped sequence number; it validates the record's required fields,
  the same honest, non-fabricating discipline `gasfield.facts` uses.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real SCADA/production-accounting system. It builds the
  RECORD an operator would keep, not the act of extracting gas from a
  real well or settling real production itself (that is `gasfield.
  operation`'s `:well/extract`/`:production/settle`, always human-gated
  -- see README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the operator's act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

;; ----------------------------- gas-well-safety range checks (pure) -----------------------------
;;
;; The Gas Well Safety Governor calls these to INDEPENDENTLY re-verify the
;; gas well's own recorded physical values before authorizing an extraction.
;; Each returns true when the value is provably OUTSIDE the safe envelope --
;; the conservative well-safety choice, matching the two-sided-tolerance
;; discipline of the aerospace siblings and the ratio/threshold
;; discipline of the fabrication siblings: a value that cannot be
;; certified inside the safe envelope is treated as a violation, not as
;; 'unknown therefore ok'. Missing data -> violation (cannot verify safe
;; to extract).

(defn pressure-out-of-range?
  "Two-sided reservoir-pressure window (the aerospace two-sided-tolerance
  pattern, applied to subsurface pressure): the gas well's measured
  reservoir pressure must stay within its declared safe operating window
  [min, max]. Actual below min risks formation damage / water-or-gas
  coning; above max risks formation fracture and loss of zonal isolation
  -- the precursor to an underground blowout. Missing any bound ->
  unsafe (cannot verify the safe window before opening the well to
  flow)."
  [actual min max]
  (cond
    (or (nil? actual) (nil? min) (nil? max)) true
    (or (< actual min) (> actual max))       true
    :else                                    false))

(defn well-integrity-annular-pressure-excessive?
  "Annular pressure vs Maximum Allowable Annular Seat Pressure (MAASP)
  -- the fabrication 'measured ratio exceeds rated limit' pattern,
  applied to well integrity. When the annulus pressure exceeds MAASP,
  the casing shoe can be lifted off its seat and zonal isolation is
  lost: this is a true blowout precursor, evaluated ahead of any
  surface flow-rate signal. Missing either bound -> unsafe."
  [annular-pressure-mpa maasp-mpa]
  (cond
    (or (nil? annular-pressure-mpa) (nil? maasp-mpa)) true
    (> annular-pressure-mpa maasp-mpa)                true
    :else                                             false))

(defn co2-corrosive?
  "CO2 content (vol%) vs the well's declared CO2-corrosion ceiling --
  the fabrication 'measured ratio exceeds rated limit' pattern, applied
  to sweet (CO2-driven) corrosion of carbon-steel tubulars. Producing
  above the declared CO2 ceiling without corrosion mitigation
  (inhibition / corrosion-resistant alloys) risks metal-loss, pinhole
  leaks and tubing/casing rupture; it is an extraction-readiness gate.
  `co2-corrosion-max` is the operator-declared maximum CO2 content
  (vol%) for carbon-steel service before corrosion mitigation becomes
  mandatory. Missing either value -> unsafe."
  [co2-percent co2-corrosion-max]
  (cond
    (or (nil? co2-percent) (nil? co2-corrosion-max)) true
    (> co2-percent co2-corrosion-max)                true
    :else                                            false))

(defn h2s-toxic?
  "Hydrogen-sulfide content (vol%) vs the jurisdiction's Immediately
  Dangerous to Life or Health (IDLH) threshold, also expressed as a
  volume percent -- the fabrication 'measured value exceeds rated limit'
  pattern, applied to sour-service toxicity. Extracting from a gas well
  whose H2S content exceeds the IDLH without the sour-service controls
  the evidence checklist demands exposes the crew to a lethal gas. Both
  arguments are in volume percent (the NIOSH IDLH for H2S of 50 ppm is
  expressed as 0.005 vol% by `gasfield.facts`). Missing either value ->
  unsafe."
  [h2s-percent idlh-percent]
  (cond
    (or (nil? h2s-percent) (nil? idlh-percent)) true
    (> h2s-percent idlh-percent)                true
    :else                                       false))

;; ----------------------------- record construction -----------------------------

(defn register-well-extract
  "Validate + construct the GAS-EXTRACTION registration DRAFT -- the
  operator's own legal act of opening a real gas well to flow against a
  live reservoir. Pure function -- does not touch any real SCADA or
  production system; it builds the RECORD an operator would keep.
  `gasfield.governor` independently re-verifies the gas well's own
  reservoir-pressure window, annular-pressure/MAASP ratio, CO2
  corrosion, H2S/IDLH and integrity-flag ground truth, and blocks a
  double-extraction of the same gas well, before this is ever allowed
  to commit."
  [well-id jurisdiction sequence]
  (when-not (and well-id (not= well-id ""))
    (throw (ex-info "well-extract: well_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "well-extract: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "well-extract: sequence must be >= 0" {})))
  (let [extract-number (str (str/upper-case jurisdiction) "-EXTRACT-" (zero-pad sequence 6))
        record {"record_id" extract-number
                "kind" "well-extract-draft"
                "well_id" well-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "extract_number" extract-number
     "certificate" (unsigned-certificate "WellExtract" extract-number extract-number)}))

(defn register-production-settlement
  "Validate + construct the PRODUCTION-SETTLEMENT registration DRAFT --
  the operator's own legal act of settling a real production period
  (royalty / royalty-volume finalization, custody transfer). Pure
  function -- does not touch any real production-accounting system; it
  builds the RECORD an operator would keep. `gasfield.governor`
  independently re-verifies the gas well's own evidence completeness and
  blocks a double-settlement of the same gas well, before this is ever
  allowed to commit."
  [well-id jurisdiction sequence]
  (when-not (and well-id (not= well-id ""))
    (throw (ex-info "production-settlement: well_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "production-settlement: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "production-settlement: sequence must be >= 0" {})))
  (let [settlement-number (str (str/upper-case jurisdiction) "-PROD-" (zero-pad sequence 6))
        record {"record_id" settlement-number
                "kind" "production-settlement-draft"
                "well_id" well-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "settlement_number" settlement-number
     "certificate" (unsigned-certificate "ProductionSettlement" settlement-number settlement-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
