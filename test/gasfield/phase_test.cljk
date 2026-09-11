(ns gasfield.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:well/extract`/`:production/settle` must NEVER be a
  member of any phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [gasfield.phase :as phase]))

(deftest well-extract-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in any future entry, auto-commits a real gas extraction"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :well/extract))
          (str "phase " n " must not auto-commit :well/extract")))))

(deftest production-settle-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in any future entry, auto-commits a real production settlement"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :production/settle))
          (str "phase " n " must not auto-commit :production/settle")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":well/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:well/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :well/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :well/extract} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :production/settle} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :well/intake} :commit)))))
