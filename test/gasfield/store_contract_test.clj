(ns gasfield.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a
  configuration change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the
  sibling actor."
  (:require [clojure.test :refer [deftest is testing]]
            [gasfield.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "JPN" (:jurisdiction (store/gas-well s "well-1"))))
      (is (= "Akita Gas Co" (:operator (store/gas-well s "well-1"))))
      (is (= 85.0 (:methane-percent (store/gas-well s "well-1"))))
      (is (= "ATL" (:jurisdiction (store/gas-well s "well-2"))))
      (is (= 50.0 (:reservoir-pressure-mpa-actual (store/gas-well s "well-3"))) "well-3 pressure out of range")
      (is (= 30.0 (:annular-pressure-mpa (store/gas-well s "well-4"))) "well-4 annular above MAASP")
      (is (= 3.0 (:co2-percent (store/gas-well s "well-5"))) "well-5 co2 above corrosion ceiling")
      (is (= 0.01 (:h2s-percent (store/gas-well s "well-6"))) "well-6 h2s toxic")
      (is (true? (:integrity-flag-raised? (store/gas-well s "well-7"))))
      (is (false? (:integrity-flag-resolved? (store/gas-well s "well-7"))))
      (is (false? (:gas-extracted? (store/gas-well s "well-1"))))
      (is (false? (:production-settled? (store/gas-well s "well-1"))))
      (is (= ["well-1" "well-2" "well-3" "well-4" "well-5" "well-6" "well-7"]
             (mapv :id (store/all-gas-wells s))))
      (is (nil? (store/assessment-of s "well-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/extract-history s)))
      (is (= [] (store/production-history s)))
      (is (zero? (store/next-extract-sequence s "JPN")))
      (is (zero? (store/next-production-sequence s "JPN")))
      (is (false? (store/gas-well-already-extracted? s "well-1")))
      (is (false? (store/gas-well-already-settled? s "well-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :well/upsert
                                 :value {:id "well-1" :operator "Akita Gas Co"}})
        (is (= "Akita Gas Co" (:operator (store/gas-well s "well-1"))))
        (is (= "JPN" (:jurisdiction (store/gas-well s "well-1"))) "unrelated field preserved"))
      (testing "assessment payloads commit and read back"
        (store/commit-record! s {:effect :assessment/set :path ["well-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/assessment-of s "well-1"))))
      (testing "gas extraction drafts a record and advances the extract sequence"
        (store/commit-record! s {:effect :well/mark-extracted :path ["well-1"]})
        (is (= "JPN-EXTRACT-000000" (get (first (store/extract-history s)) "record_id")))
        (is (= "well-extract-draft" (get (first (store/extract-history s)) "kind")))
        (is (true? (:gas-extracted? (store/gas-well s "well-1"))))
        (is (= 1 (count (store/extract-history s))))
        (is (= 1 (store/next-extract-sequence s "JPN")))
        (is (true? (store/gas-well-already-extracted? s "well-1"))))
      (testing "production settlement drafts a record and advances the production sequence"
        (store/commit-record! s {:effect :well/mark-settled :path ["well-1"]})
        (is (= "JPN-PROD-000000" (get (first (store/production-history s)) "record_id")))
        (is (= "production-settlement-draft" (get (first (store/production-history s)) "kind")))
        (is (true? (:production-settled? (store/gas-well s "well-1"))))
        (is (= 1 (count (store/production-history s))))
        (is (= 1 (store/next-production-sequence s "JPN")))
        (is (true? (store/gas-well-already-settled? s "well-1"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/gas-well s "nope")))
    (is (= [] (store/all-gas-wells s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/extract-history s)))
    (is (= [] (store/production-history s)))
    (is (zero? (store/next-extract-sequence s "JPN")))
    (is (zero? (store/next-production-sequence s "JPN")))
    (store/with-gas-wells s {"x" {:id "x" :field-name "f" :well-name "x-1" :operator "c"
                              :methane-percent 85.0 :co2-percent 0.5 :co2-corrosion-max 2.0
                              :h2s-percent 0.0005
                              :reservoir-pressure-mpa-actual 30.0
                              :reservoir-pressure-mpa-min 20.0 :reservoir-pressure-mpa-max 45.0
                              :annular-pressure-mpa 10.0 :maasp-mpa 25.0
                              :flow-rate-mcm-day 50
                              :integrity-flag-raised? false :integrity-flag-resolved? false
                              :gas-extracted? false :production-settled? false
                              :jurisdiction "JPN" :status :intake}})
    (is (= "c" (:operator (store/gas-well s "x"))))))
