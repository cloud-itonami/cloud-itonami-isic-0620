(ns gasfield.registry-test
  (:require [clojure.test :refer [deftest is]]
            [gasfield.registry :as r]))

;; ----------------------------- range-check pure functions -----------------------------

(deftest pressure-two-sided-window
  (is (not (r/pressure-out-of-range? 30.0 20.0 45.0)) "in-window -> ok")
  (is (not (r/pressure-out-of-range? 20.0 20.0 45.0)) "at min boundary -> ok")
  (is (not (r/pressure-out-of-range? 45.0 20.0 45.0)) "at max boundary -> ok")
  (is (r/pressure-out-of-range? 50.0 20.0 45.0) "above max -> out of range")
  (is (r/pressure-out-of-range? 10.0 20.0 45.0) "below min -> out of range")
  (is (r/pressure-out-of-range? nil 20.0 45.0) "missing actual -> unsafe")
  (is (r/pressure-out-of-range? 30.0 nil 45.0) "missing min -> unsafe"))

(deftest annular-pressure-vs-maasp
  (is (not (r/well-integrity-annular-pressure-excessive? 10.0 25.0)) "below MAASP -> ok")
  (is (not (r/well-integrity-annular-pressure-excessive? 25.0 25.0)) "at MAASP -> ok")
  (is (r/well-integrity-annular-pressure-excessive? 30.0 25.0) "above MAASP -> excessive")
  (is (r/well-integrity-annular-pressure-excessive? nil 25.0) "missing annular -> unsafe")
  (is (r/well-integrity-annular-pressure-excessive? 10.0 nil) "missing MAASP -> unsafe"))

(deftest co2-corrosion-vs-ceiling
  (is (not (r/co2-corrosive? 0.5 2.0)) "below ceiling -> ok")
  (is (not (r/co2-corrosive? 2.0 2.0)) "at ceiling -> ok")
  (is (r/co2-corrosive? 3.0 2.0) "above ceiling -> corrosive")
  (is (r/co2-corrosive? nil 2.0) "missing co2 -> unsafe")
  (is (r/co2-corrosive? 0.5 nil) "missing ceiling -> unsafe"))

(deftest h2s-vs-idlh
  (is (not (r/h2s-toxic? 0.0005 0.005)) "below IDLH -> ok")
  (is (not (r/h2s-toxic? 0.005 0.005)) "at IDLH -> ok")
  (is (r/h2s-toxic? 0.01 0.005) "above IDLH -> toxic")
  (is (r/h2s-toxic? nil 0.005) "missing percent -> unsafe")
  (is (r/h2s-toxic? 0.01 nil) "missing IDLH -> unsafe"))

;; ----------------------------- register-well-extract -----------------------------

(deftest extract-is-a-draft-not-a-real-extraction
  (let [result (r/register-well-extract "well-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest extract-assigns-extract-number
  (let [result (r/register-well-extract "well-1" "JPN" 7)]
    (is (= (get result "extract_number") "JPN-EXTRACT-000007"))
    (is (= (get-in result ["record" "well_id"]) "well-1"))
    (is (= (get-in result ["record" "kind"]) "well-extract-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest extract-validation-rules
  (is (thrown? Exception (r/register-well-extract "" "JPN" 0)))
  (is (thrown? Exception (r/register-well-extract "well-1" "" 0)))
  (is (thrown? Exception (r/register-well-extract "well-1" "JPN" -1))))

;; ----------------------------- register-production-settlement -----------------------------

(deftest settlement-is-a-draft-not-a-real-settlement
  (let [result (r/register-production-settlement "well-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest settlement-assigns-settlement-number
  (let [result (r/register-production-settlement "well-1" "JPN" 7)]
    (is (= (get result "settlement_number") "JPN-PROD-000007"))
    (is (= (get-in result ["record" "well_id"]) "well-1"))
    (is (= (get-in result ["record" "kind"]) "production-settlement-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest settlement-validation-rules
  (is (thrown? Exception (r/register-production-settlement "" "JPN" 0)))
  (is (thrown? Exception (r/register-production-settlement "well-1" "" 0)))
  (is (thrown? Exception (r/register-production-settlement "well-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-well-extract "well-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-well-extract "well-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-EXTRACT-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-EXTRACT-000001" (get-in hist2 [1 "record_id"])))))
