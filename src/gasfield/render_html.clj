(ns gasfield.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave5 rollout ledger): this repo's `docs/samples/operator-console.html`
  was previously a hand-typed static placeholder with no generator at
  all. This namespace drives the REAL actor stack (`gasfield.operation` ->
  `gasfield.governor` -> `gasfield.store`) through a scenario adapted from
  this repo's own `gasfield.sim` demo driver (`clojure -M:dev:run`,
  confirmed by actually running it before this file was written --
  every id and disposition below matches `gasfield.store/demo-data`'s
  seeded wells and `gasfield.governor`'s own documented checks exactly,
  so it was safe to reuse rather than author from scratch), trimmed to
  a representative subset (the phase-3 auto-commit `:well/intake`, the
  full escalate+approve lifecycle for one well across
  `:reservoir/assess` / `:well/extract` / `:production/settle` -- the
  latter two ALWAYS escalate, never auto, at any phase -- and six
  distinct HARD-hold reasons that never reach a human) and rendered
  deterministically -- no invented numbers, no timestamps in the page
  content, byte-identical across reruns against the same seed (verified
  by diffing two consecutive runs before shipping).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [kotoba.lang.text :as str]
            [gasfield.store :as store]
            [gasfield.operation :as op]
            [langgraph.graph :as g]))

;; ----------------------------- harness (unchanged across every repo
;; in this cluster -- do not rewrite, only copy) -----------------------

(def ^:private operator
  {:actor-id "op-1" :actor-role :production-superintendent :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach, using ONLY real well ids from
  `gasfield.store/demo-data`:

  well-1 (Minami-Akita/Akita-1, JPN, clean) walks the full clean
  lifecycle: a `:well/intake` directory-normalization patch is a
  phase-3, no-capital-risk auto-commit (governor clean, `:well/intake`
  is the ONLY op in phase 3's `:auto` set); `:reservoir/assess` (JPN
  has a real spec-basis in `gasfield.facts`) ALWAYS escalates (not
  auto-eligible at any phase) and is approved by a human production
  superintendent, after which `:well/extract` and `:production/settle`
  -- the two REAL-WORLD actuation events this actor performs (opening a
  real gas well to flow against a live reservoir / settling real
  production) -- ALSO ALWAYS escalate (the governor's own
  `high-stakes` gate AND the phase table agree, independently, that
  actuation is never auto, at any phase) and are each approved,
  producing one draft gas-extraction record (`JPN-EXTRACT-000000`) and
  one draft production-settlement record (`JPN-PROD-000000`).

  Then six DISTINCT HARD-hold reasons, none of which ever reach a
  human (a human approver cannot override a HARD violation), each
  isolating exactly ONE gas-well-safety failure mode:
    - well-2 (jurisdiction ATL, not in `gasfield.facts/catalog`):
      `:reservoir/assess` HARD-holds on `:no-spec-basis` -- the advisor
      may not invent a jurisdiction's well-construction/well-control
      requirements.
    - well-3 (reservoir pressure 50.0 MPa, outside its declared safe
      window [20.0, 45.0] MPa): assessed+approved first so evidence is
      on file and this hold is isolated to the pressure check alone,
      then `:well/extract` HARD-holds on `:pressure-out-of-range`.
    - well-4 (annular pressure 30.0 MPa > MAASP 25.0 MPa): assessed
      +approved, then `:well/extract` HARD-holds on
      `:well-integrity-annular-pressure-excessive` -- a true blowout
      precursor.
    - well-5 (CO2 3.0 vol% > corrosion ceiling 2.0 vol%): assessed
      +approved, then `:well/extract` HARD-holds on
      `:co2-corrosion-threshold`.
    - well-6 (H2S 0.01 vol% > JPN IDLH 0.005 vol% / 50 ppm): assessed
      +approved, then `:well/extract` HARD-holds on
      `:h2s-toxic-threshold`.
    - well-7 (`:integrity-flag-raised? true`, unresolved): assessed
      +approved, then `:well/extract` HARD-holds on
      `:integrity-flag-unresolved`.

  Returns the resulting store -- every field `render` below reads is
  real governor/store output, not a hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]

    ;; well-1: clean directory-normalization patch -- phase-3
    ;; auto-commit, no capital risk yet.
    (exec! actor "w1-intake" {:op :well/intake :subject "well-1"
                               :patch {:id "well-1" :operator "Akita Gas Co"}})

    ;; well-1: per-jurisdiction well-construction/well-control/
    ;; sour-service regulatory assessment (JPN has a real spec-basis)
    ;; -- ALWAYS escalates, approved by a human.
    (exec! actor "w1-assess" {:op :reservoir/assess :subject "well-1"})
    (approve! actor "w1-assess")

    ;; well-1: REAL gas extract (opens the well to flow against a live
    ;; reservoir) -- ALWAYS escalates regardless of phase or
    ;; confidence, approved by a human production superintendent.
    (exec! actor "w1-extract" {:op :well/extract :subject "well-1"})
    (approve! actor "w1-extract")

    ;; well-1: REAL production settlement (royalty/royalty-volume
    ;; finalization) -- ALWAYS escalates, approved by a human.
    (exec! actor "w1-settle" {:op :production/settle :subject "well-1"})
    (approve! actor "w1-settle")

    ;; well-2 (ATL): no official spec-basis in gasfield.facts -> HARD
    ;; hold on :no-spec-basis, never reaches a human.
    (exec! actor "w2-assess" {:op :reservoir/assess :subject "well-2"})

    ;; well-3: assess JPN first (clean escalate+approve) so evidence is
    ;; on file and the reservoir-pressure hold below is isolated.
    (exec! actor "w3-assess" {:op :reservoir/assess :subject "well-3"})
    (approve! actor "w3-assess")

    ;; well-3: reservoir pressure 50.0 MPa outside safe window
    ;; [20.0, 45.0] MPa -> HARD hold on :pressure-out-of-range, never
    ;; reaches a human.
    (exec! actor "w3-extract" {:op :well/extract :subject "well-3"})

    ;; well-4: assess+approve, then annular pressure 30.0 MPa exceeds
    ;; MAASP 25.0 MPa -> HARD hold on
    ;; :well-integrity-annular-pressure-excessive.
    (exec! actor "w4-assess" {:op :reservoir/assess :subject "well-4"})
    (approve! actor "w4-assess")
    (exec! actor "w4-extract" {:op :well/extract :subject "well-4"})

    ;; well-5: assess+approve, then CO2 3.0 vol% exceeds the 2.0 vol%
    ;; corrosion ceiling -> HARD hold on :co2-corrosion-threshold.
    (exec! actor "w5-assess" {:op :reservoir/assess :subject "well-5"})
    (approve! actor "w5-assess")
    (exec! actor "w5-extract" {:op :well/extract :subject "well-5"})

    ;; well-6: assess+approve, then H2S 0.01 vol% exceeds JPN's 0.005
    ;; vol% IDLH -> HARD hold on :h2s-toxic-threshold.
    (exec! actor "w6-assess" {:op :reservoir/assess :subject "well-6"})
    (approve! actor "w6-assess")
    (exec! actor "w6-extract" {:op :well/extract :subject "well-6"})

    ;; well-7: assess+approve, then an unresolved integrity flag ->
    ;; HARD hold on :integrity-flag-unresolved.
    (exec! actor "w7-assess" {:op :reservoir/assess :subject "well-7"})
    (approve! actor "w7-assess")
    (exec! actor "w7-extract" {:op :well/extract :subject "well-7"})

    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger subject-id]
  (last (filter #(= (:subject %) subject-id) ledger)))

(defn- status-cell [ledger subject-id]
  (let [f (last-fact-for ledger subject-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (-> f :violations first :rule)]
        (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- well-row [ledger {:keys [id field-name well-name operator jurisdiction
                                 gas-extracted? production-settled?]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc id) (esc field-name) (esc well-name) (esc operator) (esc jurisdiction)
          (if gas-extracted? "<span class=\"ok\">extracted</span>" "not extracted")
          (if production-settled? "<span class=\"ok\">settled</span>" "not settled")
          (status-cell ledger id)))

(defn- ledger-row [{:keys [t op subject disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc subject)
          (esc (or (some->> basis (map #(if (keyword? %) (name %) %)) (str/join ", "))
                   (some-> disposition name) ""))))

(defn- record-row [prefix {:strs [record_id well_id jurisdiction kind immutable]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc prefix) (esc record_id) (esc well_id) (esc jurisdiction)
          (if immutable "<span class=\"ok\">immutable draft</span>" (esc kind))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own op contract
  ;; (`gasfield.governor`/`gasfield.phase`) -- documentation of fixed
  ;; behavior, not runtime telemetry, so it is legitimately
  ;; hand-described rather than derived from a live run.
  ["        <tr><td><code>:well/intake</code></td><td><span class=\"ok\">phase-3 auto-commit when clean, no capital risk yet -- the ONLY auto-eligible op in this domain</span></td></tr>"
   "        <tr><td><code>:reservoir/assess</code></td><td><span class=\"warn\">ALWAYS human approval &middot; spec-basis independently checked against <code>gasfield.facts</code>, never fabricated</span></td></tr>"
   "        <tr><td><code>:well/extract</code></td><td><span class=\"warn\">ALWAYS human approval &middot; real-world actuation (opens a real gas well to flow against a live reservoir) &middot; reservoir-pressure window, annular-pressure/MAASP, CO2 corrosion, H2S/IDLH, integrity flag and double-extract guard ALL independently re-verified, never auto at any phase</span></td></tr>"
   "        <tr><td><code>:production/settle</code></td><td><span class=\"warn\">ALWAYS human approval &middot; real-world actuation (royalty/royalty-volume finalization) &middot; evidence completeness + double-settlement guard independently enforced, never auto at any phase</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        wells (store/all-gas-wells db)
        well-rows (str/join "\n" (map (partial well-row ledger) wells))
        ledger-rows (str/join "\n" (map ledger-row ledger))
        extract-rows (str/join "\n" (map (partial record-row "gas-extract") (store/extract-history db)))
        production-rows (str/join "\n" (map (partial record-row "production-settlement") (store/production-history db)))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-0620 &middot; extraction of natural gas</title><style>"
   (jp-go-dds.skin/dds+skin)
   "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Extraction of natural gas (ISIC 0620) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · gas extract/production settlement always human-approved</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Gas wells</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>gasfield.store</code> via <code>gasfield.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Well</th><th>Field</th><th>Well name</th><th>Operator</th><th>Jurisdiction</th><th>Gas extract</th><th>Production settlement</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     well-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Draft gas-extract / production-settlement records</h2>\n"
     "    <p class=\"muted\">Unsigned drafts only — the operator's own act of signing is outside this actor's authority (see README <code>Actuation</code>).</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Kind</th><th>Record id</th><th>Well</th><th>Jurisdiction</th><th>Status</th></tr></thead>\n"
     "      <tbody>\n"
     extract-rows (when (seq extract-rows) "\n")
     production-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Gas Well Safety Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden by a human approver. Jurisdiction spec-basis, reservoir-pressure window, annular-pressure/MAASP, CO2 corrosion, H2S/IDLH and integrity-flag ground truth are independently recomputed, never trusted from the advisor's proposal; a real gas extract or production settlement is always a human production superintendent's call, at every rollout phase.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Subject</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/extract-history db)) "extract drafts,"
             (count (store/production-history db)) "production drafts )")))
