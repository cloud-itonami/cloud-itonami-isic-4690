(ns shosha.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:shipment/dispatch`/`:invoice/settle` must NEVER be a
  member of any phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [shosha.phase :as phase]))

(deftest shipment-dispatch-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in any future entry, auto-commits a real cross-border shipment dispatch"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :shipment/dispatch))
          (str "phase " n " must not auto-commit :shipment/dispatch")))))

(deftest invoice-settle-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in any future entry, auto-commits a real trade invoice settlement"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :invoice/settle))
          (str "phase " n " must not auto-commit :invoice/settle")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":order/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:order/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :order/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :shipment/dispatch} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :invoice/settle} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :order/intake} :commit)))))
