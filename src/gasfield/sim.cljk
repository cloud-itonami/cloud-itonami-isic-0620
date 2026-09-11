(ns gasfield.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean gas well through
  intake -> reservoir safety assessment -> gas extraction (escalate/
  approve/commit) -> production settlement (escalate/approve/commit),
  then shows HARD-hold scenarios: a jurisdiction with no spec-basis,
  a reservoir pressure outside its safe window, an annular pressure
  above MAASP, a CO2 content above the corrosion ceiling, an H2S
  concentration above the IDLH, an unresolved integrity flag, a double
  extraction, and a double settlement.

  Like every sibling actor's new checks, this actor's gas-well-safety
  checks (`pressure-out-of-range?`, `well-integrity-annular-pressure-
  excessive?`, `co2-corrosive?`, `h2s-toxic?`, `integrity-flag-
  unresolved?`) are evaluated directly at `:well/extract` time rather
  than via a separate screening op -- a real extraction decision
  validates reservoir pressure, annular pressure, CO2 corrosion, H2S
  and an open integrity flag at the point of the act itself, not as a
  discrete pre-screening ceremony. Each check is still exercised
  directly and independently below, one gas well per HARD-hold
  scenario, following the SAME 'exercise the failure mode directly,
  never only via a happy-path actuation' discipline `parksafety`'s
  ADR-2607071922 Decision 5 and every sibling since establish."
  (:require [langgraph.graph :as g]
            [gasfield.store :as store]
            [gasfield.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :production-superintendent :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== well/intake well-1 (JPN, clean) ==")
    (println (exec-op actor "t1" {:op :well/intake :subject "well-1"
                                  :patch {:id "well-1" :operator "Akita Gas Co"}} operator))

    (println "== reservoir/assess well-1 (escalates -- human approves) ==")
    (println (exec-op actor "t2" {:op :reservoir/assess :subject "well-1"} operator))
    (println (approve! actor "t2"))

    (println "== well/extract well-1 (always escalates -- :well/extract) ==")
    (let [r (exec-op actor "t3" {:op :well/extract :subject "well-1"} operator)]
      (println r)
      (println "-- human production superintendent approves --")
      (println (approve! actor "t3")))

    (println "== production/settle well-1 (always escalates -- :production/settle) ==")
    (let [r (exec-op actor "t4" {:op :production/settle :subject "well-1"} operator)]
      (println r)
      (println "-- human production superintendent approves --")
      (println (approve! actor "t4")))

    (println "== reservoir/assess well-2 (no spec-basis -> HARD hold) ==")
    (println (exec-op actor "t5" {:op :reservoir/assess :subject "well-2"} operator))

    (println "== reservoir/assess well-3 (escalates -- human approves; sets up the pressure test) ==")
    (println (exec-op actor "t6" {:op :reservoir/assess :subject "well-3"} operator))
    (println (approve! actor "t6"))

    (println "== well/extract well-3 (reservoir pressure out of range -> HARD hold) ==")
    (println (exec-op actor "t7" {:op :well/extract :subject "well-3"} operator))

    (println "== reservoir/assess well-4 (escalates -- human approves; sets up the annular-pressure test) ==")
    (println (exec-op actor "t8" {:op :reservoir/assess :subject "well-4"} operator))
    (println (approve! actor "t8"))

    (println "== well/extract well-4 (annular pressure above MAASP -> HARD hold) ==")
    (println (exec-op actor "t9" {:op :well/extract :subject "well-4"} operator))

    (println "== reservoir/assess well-5 (escalates -- human approves; sets up the CO2-corrosion test) ==")
    (println (exec-op actor "t10" {:op :reservoir/assess :subject "well-5"} operator))
    (println (approve! actor "t10"))

    (println "== well/extract well-5 (CO2 above corrosion ceiling -> HARD hold) ==")
    (println (exec-op actor "t11" {:op :well/extract :subject "well-5"} operator))

    (println "== reservoir/assess well-6 (escalates -- human approves; sets up the H2S test) ==")
    (println (exec-op actor "t12" {:op :reservoir/assess :subject "well-6"} operator))
    (println (approve! actor "t12"))

    (println "== well/extract well-6 (H2S above IDLH -> HARD hold) ==")
    (println (exec-op actor "t13" {:op :well/extract :subject "well-6"} operator))

    (println "== reservoir/assess well-7 (escalates -- human approves; sets up the integrity-flag test) ==")
    (println (exec-op actor "t14" {:op :reservoir/assess :subject "well-7"} operator))
    (println (approve! actor "t14"))

    (println "== well/extract well-7 (unresolved integrity flag -> HARD hold) ==")
    (println (exec-op actor "t15" {:op :well/extract :subject "well-7"} operator))

    (println "== well/extract well-1 AGAIN (double-extract -> HARD hold) ==")
    (println (exec-op actor "t16" {:op :well/extract :subject "well-1"} operator))

    (println "== production/settle well-1 AGAIN (double-settlement -> HARD hold) ==")
    (println (exec-op actor "t17" {:op :production/settle :subject "well-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft well-extract records ==")
    (doseq [r (store/extract-history db)] (println r))

    (println "== draft production-settlement records ==")
    (doseq [r (store/production-history db)] (println r))))
