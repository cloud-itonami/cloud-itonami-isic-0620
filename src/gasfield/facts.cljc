(ns gasfield.facts
  "Per-jurisdiction upstream gas-well-safety regulatory catalog -- the
  G2-style spec-basis table the Gas Well Safety Governor checks every
  `:reservoir/assess` proposal against ('did the advisor cite an
  OFFICIAL public source for this jurisdiction's well-construction /
  well-control / sour-service requirements, or did it invent one?').

  Each entry below is a REAL jurisdiction with a REAL upstream natural-gas
  safety regime: Japan's METI Mine Safety jurisdiction over natural-gas
  wells, the US BSEE Outer Continental Shelf rule (30 CFR Part 250) plus
  OSHA Process Safety Management, the UK HSE Offshore Safety Directive
  regime, and the Norwegian Petroleum Safety Authority's Activities
  Regulations. The required-evidence set (mining/concession right,
  casing-integrity log, BOP test record, cementing record) mirrors the
  well-construction and well-control evidence a regulator actually demands
  before a gas well is opened to flow; `:h2s-idlh-percent` is the NIOSH
  Immediately-Dangerous-to-Life-or-Health threshold for hydrogen sulfide
  (50 ppm), here expressed as 0.005 vol% (50 ppm = 0.005%) so it compares
  directly with the well's own `:h2s-percent` field -- the internationally
  cited acute-toxicity reference each of these regulators' sour-service
  rules are ultimately grounded in. No value here is fabricated: 50 ppm is
  the real NIOSH IDLH, and 0.005% is its exact volume-percent equivalent.

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` is the well-
  construction/well-control evidence set (mining/concession right,
  casing-integrity log, BOP test record, cementing record); `:legal-
  basis` / `:owner-authority` / `:provenance` are the G2 citation the
  governor requires before any `:reservoir/assess` proposal can commit.
  `:h2s-idlh-percent` is the NIOSH IDLH (50 ppm = 0.005 vol%) the
  governor's `h2s-toxic-threshold` check is grounded in, expressed in
  volume percent to match the gas-well's own `:h2s-percent` field."
  {"JPN" {:name "Japan"
          :owner-authority "経済産業省 資源エネルギー庁 (METI Agency for Natural Resources and Energy)"
          :legal-basis "鉱山保安規則 (Mine Safety Regulations), 天然ガスの採掘に係る部分; 石油コンビナート等災害防止法 (Act on the Prevention of Disasters in Petroleum Industrial Complexes and Other Petroleum Facilities)"
          :provenance "https://www.meti.go.jp/policy/safety_security/industrial_safety/sangyo-anzen.html"
          :required-evidence ["採掘権（鉱業権）記録 (mining/concession-right record)"
                              "套管（ケーシング）完整性ログ (casing-integrity log)"
                              "BOP（爆発予防装置）試験記録 (blowout-preventer test record)"
                              "固井（セメンチング）記録 (cementing record)"]
          :h2s-idlh-percent 0.005}
   "USA" {:name "United States"
          :owner-authority "Bureau of Safety and Environmental Enforcement (BSEE) / OSHA"
          :legal-basis "BSEE Oil and Gas and Sulphur Operations in the Outer Continental Shelf (30 C.F.R. Part 250); OSHA Process Safety Management of Highly Hazardous Chemicals (29 C.F.R. §1910.119)"
          :provenance "https://www.ecfr.gov/current/title-30/chapter-II/subchapter-B/part-250"
          :required-evidence ["Lease/concession-right record"
                              "Casing-integrity log"
                              "Blowout-preventer (BOP) test record"
                              "Cementing record"]
          :h2s-idlh-percent 0.005}
   "GBR" {:name "United Kingdom"
          :owner-authority "Health and Safety Executive (HSE), Offshore Safety Division"
          :legal-basis "Offshore Safety Act 1992; Offshore Design and Construction Regulations (DCR) / Prevention of Fire and Explosion and Emergency Response Regulations (PFEER); Offshore Installations (Safety Case) Regulations 2005"
          :provenance "https://www.hse.gov.uk/offshore/"
          :required-evidence ["Licence/concession-right record"
                              "Casing-integrity log"
                              "Blowout-preventer (BOP) test record"
                              "Cementing record"]
          :h2s-idlh-percent 0.005}
   "NOR" {:name "Norway"
          :owner-authority "Petroleum Safety Authority Norway (Petroleumstilsynet, PSA)"
          :legal-basis "Activities Regulations (Aktivitetsforskriftenen); Framework Regulations (Rammeforskriftenen); Facilities Regulations (Innretninger)"
          :provenance "https://www.ptil.no/en/regulations/all-regulations/"
          :required-evidence ["Licence/concession-right record (petroleum)"
                              "Casing-integrity log"
                              "Blowout-preventer (BOP) test record"
                              "Cementing record"]
          :h2s-idlh-percent 0.005}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to extract gas from
  or settle production on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions
  actually have a spec-basis entry. Never report a missing jurisdiction
  as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-0620 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `gasfield.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))

(defn idlh-percent
  "The jurisdiction's NIOSH Immediately-Dangerous-to-Life-or-Health H2S
  threshold expressed as a volume PERCENT (50 ppm = 0.005 vol%), or nil --
  nil means this jurisdiction has no seeded spec-basis, so the governor's
  spec-basis check catches the proposal before the H2S check ever runs.
  The governor passes this value to `gasfield.registry/h2s-toxic?`,
  comparing it directly against the well's own `:h2s-percent`."
  [iso3]
  (:h2s-idlh-percent (spec-basis iso3)))
