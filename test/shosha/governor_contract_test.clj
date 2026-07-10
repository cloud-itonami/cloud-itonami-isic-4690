(ns shosha.governor-contract-test
  "The governor contract as executable tests. The single invariant
  under test:

    ShoshaAdvisor never dispatches a cross-border shipment or settles
    an invoice the Shosha Trading Governor would reject,
    `:shipment/dispatch`/`:invoice/settle` NEVER auto-commit at any
    phase, `:order/intake` (no direct capital risk) MAY auto-commit
    when clean, and every decision (commit OR hold) leaves exactly one
    ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [shosha.store :as store]
            [shosha.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :trading-supervisor :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Walks `subject` through contract verify -> approve, leaving a
  contract assessment on file. Uses distinct thread-ids per call site
  by suffixing `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :contract/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :order/intake :subject "to-1"
                   :patch {:id "to-1" :counterparty "Sendai Trading Co"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Sendai Trading Co" (:counterparty (store/trade-order db "to-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest contract-verify-always-needs-approval
  (testing "contract verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :contract/verify :subject "to-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/assessment-of db "to-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a contract/verify proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :contract/verify :subject "to-2"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assessment-of db "to-2")) "no assessment written"))))

(deftest dispatch-without-assessment-is-held
  (testing "shipment/dispatch before any contract verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :shipment/dispatch :subject "to-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest credit-uncleared-is-held-and-unoverridable
  (testing "a counterparty whose credit has not been cleared -> HOLD, and never reaches request-approval -- the leasing collateral-coverage discipline applied to counterparty credit"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "to-3")
          res (exec-op actor "t5" {:op :shipment/dispatch :subject "to-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:credit-uncleared} (-> (store/ledger db) last :basis)))
      (is (empty? (store/shipment-history db))))))

(deftest contract-missing-is-held-and-unoverridable
  (testing "an order with no contract-terms on file -> HOLD, and never reaches request-approval"
    (let [[db actor] (fresh)
          _ (verify! actor "t6pre" "to-4")
          res (exec-op actor "t6" {:op :shipment/dispatch :subject "to-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:contract-missing} (-> (store/ledger db) last :basis)))
      (is (empty? (store/shipment-history db))))))

(deftest counterparty-sanctions-flag-unresolved-is-held-and-unoverridable
  (testing "a counterparty that has not passed OFAC / equivalent sanctions screening -> HOLD, and never reaches request-approval (evaluated at both dispatch and invoice)"
    (let [[db actor] (fresh)
          _ (verify! actor "t7pre" "to-5")
          res (exec-op actor "t7" {:op :shipment/dispatch :subject "to-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:counterparty-sanctions-flag-unresolved} (-> (store/ledger db) last :basis)))
      (is (empty? (store/shipment-history db))))))

(deftest export-license-uncleared-is-held-and-unoverridable
  (testing "a trade-order whose export-control classification (ECCN/HS-code) has not been cleared -> HOLD, and never reaches request-approval -- the check with NO analog in the fuel-wholesale sibling, this vertical's own domain content"
    (let [[db actor] (fresh)
          _ (verify! actor "t7blicpre" "to-6")
          res (exec-op actor "t7blic" {:op :shipment/dispatch :subject "to-6"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:export-license-uncleared} (-> (store/ledger db) last :basis)))
      (is (empty? (store/shipment-history db))))))

(deftest shipment-dispatch-always-escalates-then-human-decides
  (testing "a clean, fully-verified, credit-cleared, contract-on-file, export-license-cleared, sanctions-screened order still ALWAYS interrupts for human approval -- :shipment/dispatch is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t8pre" "to-1")
          r1 (exec-op actor "t8" {:op :shipment/dispatch :subject "to-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, shipment record drafted"
        (let [r2 (approve! actor "t8")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:dispatched? (store/trade-order db "to-1"))))
          (is (= 1 (count (store/shipment-history db))) "one draft shipment record"))))))

(deftest invoice-settle-always-escalates-then-human-decides
  (testing "a clean, fully-verified, already-dispatched order still ALWAYS interrupts for human approval -- :invoice/settle is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t9pre" "to-1")
          _ (exec-op actor "t9dispatch" {:op :shipment/dispatch :subject "to-1"} operator)
          _ (approve! actor "t9dispatch")
          r1 (exec-op actor "t9" {:op :invoice/settle :subject "to-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, invoice record drafted"
        (let [r2 (approve! actor "t9")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:invoiced? (store/trade-order db "to-1"))))
          (is (= 1 (count (store/invoice-history db))) "one draft invoice record"))))))

(deftest shipment-dispatch-double-dispatch-is-held
  (testing "dispatching the same trade-order twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t10pre" "to-1")
          _ (exec-op actor "t10a" {:op :shipment/dispatch :subject "to-1"} operator)
          _ (approve! actor "t10a")
          res (exec-op actor "t10" {:op :shipment/dispatch :subject "to-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-dispatched} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/shipment-history db))) "still only the one earlier shipment"))))

(deftest invoice-settle-double-invoice-is-held
  (testing "settling the same trade-order's invoice twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t11pre" "to-1")
          _ (exec-op actor "t11dispatch" {:op :shipment/dispatch :subject "to-1"} operator)
          _ (approve! actor "t11dispatch")
          _ (exec-op actor "t11a" {:op :invoice/settle :subject "to-1"} operator)
          _ (approve! actor "t11a")
          res (exec-op actor "t11" {:op :invoice/settle :subject "to-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-invoiced} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/invoice-history db))) "still only the one earlier invoice"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :order/intake :subject "to-1"
                          :patch {:id "to-1" :counterparty "Sendai Trading Co"}} operator)
      (exec-op actor "b" {:op :contract/verify :subject "to-2"} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
