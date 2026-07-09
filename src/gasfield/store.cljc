(ns gasfield.store
  "SSoT for the natural-gas-extraction actor, behind a `Store` protocol so
  the backend is a swap, not a rewrite -- the same seam every prior
  `cloud-itonami-isic-*` actor in this fleet uses.

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/gasfield/store_contract_test.clj), which is the whole point:
  the actor, the Gas Well Safety Governor and the audit ledger never know
  which SSoT they run on.

  Unlike `retailops`/4711's own `order` entity (distinguished by
  `:kind`), this vertical's `extract` and `settle` actuation events
  apply SEQUENTIALLY to the SAME `gas-well` -- a gas extraction happens
  first (flow started against a live reservoir), production settlement
  happens later, on the same gas-well record. This matches the repair-shop
  cluster's own `ticket` shape more closely (two real-world acts, in
  order, on one entity), with dedicated double-actuation-guard booleans
  (`:gas-extracted?`/`:production-settled?`, never a `:status` value).

  The ledger stays append-only on every backend: 'which gas well was
  screened for a reservoir pressure outside its safe window, an annular
  pressure above its MAASP, a CO2 content above its corrosion ceiling,
  an H2S concentration above the IDLH, or an unresolved integrity flag,
  which gas well had gas extracted, which production was settled, on
  what jurisdictional basis, approved by whom' is always a query over an
  immutable log -- the audit trail a regulator, a royalty owner, or an
  operator trusting a gas-extraction actor needs, and the evidence an
  operator needs if an extraction or a settlement is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
           [gasfield.registry :as registry]
           [langchain.db :as d]))

(defprotocol Store
  (gas-well [s id])
  (all-gas-wells [s])
  (assessment-of [s well-id] "committed reservoir safety assessment, or nil")
  (ledger [s])
  (extract-history [s] "the append-only gas-extraction history (gasfield.registry drafts)")
  (production-history [s] "the append-only production-settlement history (gasfield.registry drafts)")
  (next-extract-sequence [s jurisdiction] "next extract-number sequence for a jurisdiction")
  (next-production-sequence [s jurisdiction] "next production-number sequence for a jurisdiction")
  (gas-well-already-extracted? [s well-id] "has gas already been extracted from this gas well?")
  (gas-well-already-settled? [s well-id] "has this gas well's production already been settled?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-gas-wells [s wells] "replace/seed the gas-well directory (map id->gas-well)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained gas-well set covering both actuation lifecycles
  (extract, settlement) plus the governor's own gas-well-safety checks,
  so the actor + tests run offline. Each violation gas well isolates
  exactly ONE failure mode (the rest stay clean) following the 'exercise
  the failure mode directly, never only via a happy-path actuation'
  discipline every sibling governor's demo data establishes."
  []
  {:wells
   {"well-1" {:id "well-1" :field-name "Minami-Akita" :well-name "Akita-1"
              :operator "Akita Gas Co"
              :methane-percent 85.0 :co2-percent 0.5 :co2-corrosion-max 2.0
              :h2s-percent 0.0005
              :reservoir-pressure-mpa-actual 30.0
              :reservoir-pressure-mpa-min 20.0 :reservoir-pressure-mpa-max 45.0
              :annular-pressure-mpa 10.0 :maasp-mpa 25.0
              :flow-rate-mcm-day 50
              :integrity-flag-raised? false :integrity-flag-resolved? false
              :gas-extracted? false :production-settled? false
              :jurisdiction "JPN" :status :intake}
    "well-2" {:id "well-2" :field-name "Atlantis-Field" :well-name "Atlantis-1"
              :operator "Atlantis Drilling"
              :methane-percent 85.0 :co2-percent 0.5 :co2-corrosion-max 2.0
              :h2s-percent 0.0005
              :reservoir-pressure-mpa-actual 30.0
              :reservoir-pressure-mpa-min 20.0 :reservoir-pressure-mpa-max 45.0
              :annular-pressure-mpa 10.0 :maasp-mpa 25.0
              :flow-rate-mcm-day 50
              :integrity-flag-raised? false :integrity-flag-resolved? false
              :gas-extracted? false :production-settled? false
              :jurisdiction "ATL" :status :intake}
    "well-3" {:id "well-3" :field-name "Minami-Akita" :well-name "Akita-3"
              :operator "Akita Gas Co"
              :methane-percent 85.0 :co2-percent 0.5 :co2-corrosion-max 2.0
              :h2s-percent 0.0005
              :reservoir-pressure-mpa-actual 50.0
              :reservoir-pressure-mpa-min 20.0 :reservoir-pressure-mpa-max 45.0
              :annular-pressure-mpa 10.0 :maasp-mpa 25.0
              :flow-rate-mcm-day 50
              :integrity-flag-raised? false :integrity-flag-resolved? false
              :gas-extracted? false :production-settled? false
              :jurisdiction "JPN" :status :intake}
    "well-4" {:id "well-4" :field-name "Minami-Akita" :well-name "Akita-4"
              :operator "Akita Gas Co"
              :methane-percent 85.0 :co2-percent 0.5 :co2-corrosion-max 2.0
              :h2s-percent 0.0005
              :reservoir-pressure-mpa-actual 30.0
              :reservoir-pressure-mpa-min 20.0 :reservoir-pressure-mpa-max 45.0
              :annular-pressure-mpa 30.0 :maasp-mpa 25.0
              :flow-rate-mcm-day 50
              :integrity-flag-raised? false :integrity-flag-resolved? false
              :gas-extracted? false :production-settled? false
              :jurisdiction "JPN" :status :intake}
    "well-5" {:id "well-5" :field-name "Minami-Akita" :well-name "Akita-5"
              :operator "Akita Gas Co"
              :methane-percent 82.0 :co2-percent 3.0 :co2-corrosion-max 2.0
              :h2s-percent 0.0005
              :reservoir-pressure-mpa-actual 30.0
              :reservoir-pressure-mpa-min 20.0 :reservoir-pressure-mpa-max 45.0
              :annular-pressure-mpa 10.0 :maasp-mpa 25.0
              :flow-rate-mcm-day 50
              :integrity-flag-raised? false :integrity-flag-resolved? false
              :gas-extracted? false :production-settled? false
              :jurisdiction "JPN" :status :intake}
    "well-6" {:id "well-6" :field-name "Minami-Akita" :well-name "Akita-6"
              :operator "Akita Gas Co"
              :methane-percent 80.0 :co2-percent 0.5 :co2-corrosion-max 2.0
              :h2s-percent 0.01
              :reservoir-pressure-mpa-actual 30.0
              :reservoir-pressure-mpa-min 20.0 :reservoir-pressure-mpa-max 45.0
              :annular-pressure-mpa 10.0 :maasp-mpa 25.0
              :flow-rate-mcm-day 50
              :integrity-flag-raised? false :integrity-flag-resolved? false
              :gas-extracted? false :production-settled? false
              :jurisdiction "JPN" :status :intake}
    "well-7" {:id "well-7" :field-name "Minami-Akita" :well-name "Akita-7"
              :operator "Akita Gas Co"
              :methane-percent 85.0 :co2-percent 0.5 :co2-corrosion-max 2.0
              :h2s-percent 0.0005
              :reservoir-pressure-mpa-actual 30.0
              :reservoir-pressure-mpa-min 20.0 :reservoir-pressure-mpa-max 45.0
              :annular-pressure-mpa 10.0 :maasp-mpa 25.0
              :flow-rate-mcm-day 50
              :integrity-flag-raised? true :integrity-flag-resolved? false
              :gas-extracted? false :production-settled? false
              :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- extract-gas-well!
  "Backend-agnostic `:well/mark-extracted` -- looks up the gas well via the
  protocol and drafts the gas-extraction record, and returns {:result ..
  :well-patch ..} for the caller to persist."
  [s well-id]
  (let [w (gas-well s well-id)
        seq-n (next-extract-sequence s (:jurisdiction w))
        result (registry/register-well-extract well-id (:jurisdiction w) seq-n)]
    {:result result
     :well-patch {:gas-extracted? true
                  :extract-number (get result "extract_number")}}))

(defn- settle-production!
  "Backend-agnostic `:well/mark-settled` -- looks up the gas well via the
  protocol and drafts the production-settlement record, and returns
  {:result .. :well-patch ..} for the caller to persist."
  [s well-id]
  (let [w (gas-well s well-id)
        seq-n (next-production-sequence s (:jurisdiction w))
        result (registry/register-production-settlement well-id (:jurisdiction w) seq-n)]
    {:result result
     :well-patch {:production-settled? true
                  :settlement-number (get result "settlement_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (gas-well [_ id] (get-in @a [:wells id]))
  (all-gas-wells [_] (sort-by :id (vals (:wells @a))))
  (assessment-of [_ well-id] (get-in @a [:assessments well-id]))
  (ledger [_] (:ledger @a))
  (extract-history [_] (:extracts @a))
  (production-history [_] (:production @a))
  (next-extract-sequence [_ jurisdiction] (get-in @a [:extract-sequences jurisdiction] 0))
  (next-production-sequence [_ jurisdiction] (get-in @a [:production-sequences jurisdiction] 0))
  (gas-well-already-extracted? [_ well-id] (boolean (get-in @a [:wells well-id :gas-extracted?])))
  (gas-well-already-settled? [_ well-id] (boolean (get-in @a [:wells well-id :production-settled?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :well/upsert
      (swap! a update-in [:wells (:id value)] merge value)

      :assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :well/mark-extracted
      (let [well-id (first path)
            {:keys [result well-patch]} (extract-gas-well! s well-id)
            jurisdiction (:jurisdiction (gas-well s well-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:extract-sequences jurisdiction] (fnil inc 0))
                       (update-in [:wells well-id] merge well-patch)
                       (update :extracts registry/append result))))
        result)

      :well/mark-settled
      (let [well-id (first path)
            {:keys [result well-patch]} (settle-production! s well-id)
            jurisdiction (:jurisdiction (gas-well s well-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:production-sequences jurisdiction] (fnil inc 0))
                       (update-in [:wells well-id] merge well-patch)
                       (update :production registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-gas-wells [s wells] (when (seq wells) (swap! a assoc :wells wells)) s))

(defn seed-db
  "A MemStore seeded with the demo gas-well set. The deterministic default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {}
                           :ledger [] :extract-sequences {} :extracts []
                           :production-sequences {} :production []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (assessment payloads, ledger facts, extract/
  production records) are stored as EDN strings so `langchain.db`
  doesn't expand them into sub-entities -- the same convention every
  sibling actor's store uses."
  {:gas-well/id                        {:db/unique :db.unique/identity}
   :assessment/gas-well-id             {:db/unique :db.unique/identity}
   :ledger/seq                         {:db/unique :db.unique/identity}
   :extract/seq                        {:db/unique :db.unique/identity}
   :production/seq                     {:db/unique :db.unique/identity}
   :extract-sequence/jurisdiction      {:db/unique :db.unique/identity}
   :production-sequence/jurisdiction   {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

;; Every gas-well field is stored as its own Datomic attr so a governor
;; pull reads the exact ground truth (no blob decode). Boolean fields
;; are coerced on read so a missing attr reads back as false (parity
;; with MemStore). [field-key tx-attr boolean?]
(def ^:private gas-well-fields
  [[:id :gas-well/id false]
   [:field-name :gas-well/field-name false]
   [:well-name :gas-well/well-name false]
   [:operator :gas-well/operator false]
   [:methane-percent :gas-well/methane-percent false]
   [:co2-percent :gas-well/co2-percent false]
   [:co2-corrosion-max :gas-well/co2-corrosion-max false]
   [:h2s-percent :gas-well/h2s-percent false]
   [:reservoir-pressure-mpa-actual :gas-well/reservoir-pressure-mpa-actual false]
   [:reservoir-pressure-mpa-min :gas-well/reservoir-pressure-mpa-min false]
   [:reservoir-pressure-mpa-max :gas-well/reservoir-pressure-mpa-max false]
   [:annular-pressure-mpa :gas-well/annular-pressure-mpa false]
   [:maasp-mpa :gas-well/maasp-mpa false]
   [:flow-rate-mcm-day :gas-well/flow-rate-mcm-day false]
   [:integrity-flag-raised? :gas-well/integrity-flag-raised? true]
   [:integrity-flag-resolved? :gas-well/integrity-flag-resolved? true]
   [:gas-extracted? :gas-well/gas-extracted? true]
   [:production-settled? :gas-well/production-settled? true]
   [:jurisdiction :gas-well/jurisdiction false]
   [:status :gas-well/status false]
   [:extract-number :gas-well/extract-number false]
   [:settlement-number :gas-well/settlement-number false]])

(defn- gas-well->tx [w]
  (reduce (fn [tx [k attr _bool?]]
            (let [v (get w k)]
              (cond-> tx (some? v) (assoc attr v))))
          {:gas-well/id (:id w)}
          gas-well-fields))

(def ^:private gas-well-pull (mapv second gas-well-fields))

(defn- pull->gas-well [m]
  (when (:gas-well/id m)
    (reduce (fn [w [k attr bool?]]
              (let [v (get m attr)]
                (cond
                  bool?        (assoc w k (boolean v))
                  (some? v)    (assoc w k v)
                  :else        w)))
            {:id (:gas-well/id m)}
            gas-well-fields)))

(defrecord DatomicStore [conn]
  Store
  (gas-well [_ id]
    (pull->gas-well (d/pull (d/db conn) gas-well-pull [:gas-well/id id])))
  (all-gas-wells [_]
    (->> (d/q '[:find [?id ...] :where [?e :gas-well/id ?id]] (d/db conn))
         (map #(pull->gas-well (d/pull (d/db conn) gas-well-pull [:gas-well/id %])))
         (sort-by :id)))
  (assessment-of [_ well-id]
    (dec* (d/q '[:find ?p . :in $ ?wid
                :where [?a :assessment/gas-well-id ?wid] [?a :assessment/payload ?p]]
              (d/db conn) well-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (extract-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :extract/seq ?s] [?e :extract/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (production-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :production/seq ?s] [?e :production/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-extract-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :extract-sequence/jurisdiction ?j] [?e :extract-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-production-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :production-sequence/jurisdiction ?j] [?e :production-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (gas-well-already-extracted? [s well-id]
    (boolean (:gas-extracted? (gas-well s well-id))))
  (gas-well-already-settled? [s well-id]
    (boolean (:production-settled? (gas-well s well-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :well/upsert
      (d/transact! conn [(gas-well->tx value)])

      :assessment/set
      (d/transact! conn [{:assessment/gas-well-id (first path) :assessment/payload (enc payload)}])

      :well/mark-extracted
      (let [well-id (first path)
            {:keys [result well-patch]} (extract-gas-well! s well-id)
            jurisdiction (:jurisdiction (gas-well s well-id))
            next-n (inc (next-extract-sequence s jurisdiction))]
        (d/transact! conn
                     [(gas-well->tx (assoc well-patch :id well-id))
                      {:extract-sequence/jurisdiction jurisdiction :extract-sequence/next next-n}
                      {:extract/seq (count (extract-history s)) :extract/record (enc (get result "record"))}])
        result)

      :well/mark-settled
      (let [well-id (first path)
            {:keys [result well-patch]} (settle-production! s well-id)
            jurisdiction (:jurisdiction (gas-well s well-id))
            next-n (inc (next-production-sequence s jurisdiction))]
        (d/transact! conn
                     [(gas-well->tx (assoc well-patch :id well-id))
                      {:production-sequence/jurisdiction jurisdiction :production-sequence/next next-n}
                      {:production/seq (count (production-history s)) :production/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-gas-wells [s wells]
    (when (seq wells) (d/transact! conn (mapv gas-well->tx (vals wells)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:wells ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [wells]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-gas-wells s wells))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo gas-well set -- the Datomic-backed
  analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
