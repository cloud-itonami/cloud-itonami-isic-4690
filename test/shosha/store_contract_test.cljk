(ns shosha.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a
  configuration change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the
  sibling actor."
  (:require [clojure.test :refer [deftest is testing]]
            [shosha.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "JPN" (:jurisdiction (store/trade-order s "to-1"))))
      (is (= "Sendai Trading Co" (:counterparty (store/trade-order s "to-1"))))
      (is (= "Steel products" (:commodity-category (store/trade-order s "to-1"))))
      (is (= "ATL" (:jurisdiction (store/trade-order s "to-2"))))
      (is (false? (:credit-cleared? (store/trade-order s "to-3"))) "to-3 credit not cleared")
      (is (nil? (:contract-terms (store/trade-order s "to-4"))) "to-4 no contract-terms")
      (is (false? (:sanctions-screened? (store/trade-order s "to-5"))) "to-5 sanctions not screened")
      (is (false? (:export-license-cleared? (store/trade-order s "to-6"))) "to-6 export-license not cleared")
      (is (false? (:dispatched? (store/trade-order s "to-1"))))
      (is (false? (:invoiced? (store/trade-order s "to-1"))))
      (is (= ["to-1" "to-2" "to-3" "to-4" "to-5" "to-6"]
             (mapv :id (store/all-trade-orders s))))
      (is (nil? (store/assessment-of s "to-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/shipment-history s)))
      (is (= [] (store/invoice-history s)))
      (is (zero? (store/next-shipment-sequence s "JPN")))
      (is (zero? (store/next-invoice-sequence s "JPN")))
      (is (false? (store/trade-order-already-dispatched? s "to-1")))
      (is (false? (store/trade-order-already-invoiced? s "to-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :order/upsert
                                 :value {:id "to-1" :counterparty "Sendai Trading Co"}})
        (is (= "Sendai Trading Co" (:counterparty (store/trade-order s "to-1"))))
        (is (= "JPN" (:jurisdiction (store/trade-order s "to-1"))) "unrelated field preserved"))
      (testing "contract-assessment payloads commit and read back"
        (store/commit-record! s {:effect :contract-assessment/set :path ["to-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/assessment-of s "to-1"))))
      (testing "shipment dispatch drafts a record and advances the shipment sequence"
        (store/commit-record! s {:effect :order/mark-dispatched :path ["to-1"]})
        (is (= "JPN-SHIPMENT-000000" (get (first (store/shipment-history s)) "record_id")))
        (is (= "shipment-dispatch-draft" (get (first (store/shipment-history s)) "kind")))
        (is (true? (:dispatched? (store/trade-order s "to-1"))))
        (is (= 1 (count (store/shipment-history s))))
        (is (= 1 (store/next-shipment-sequence s "JPN")))
        (is (true? (store/trade-order-already-dispatched? s "to-1"))))
      (testing "invoice settlement drafts a record and advances the invoice sequence"
        (store/commit-record! s {:effect :order/mark-invoiced :path ["to-1"]})
        (is (= "JPN-INVOICE-000000" (get (first (store/invoice-history s)) "record_id")))
        (is (= "trade-invoice-draft" (get (first (store/invoice-history s)) "kind")))
        (is (true? (:invoiced? (store/trade-order s "to-1"))))
        (is (= 1 (count (store/invoice-history s))))
        (is (= 1 (store/next-invoice-sequence s "JPN")))
        (is (true? (store/trade-order-already-invoiced? s "to-1"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/trade-order s "nope")))
    (is (= [] (store/all-trade-orders s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/shipment-history s)))
    (is (= [] (store/invoice-history s)))
    (is (zero? (store/next-shipment-sequence s "JPN")))
    (is (zero? (store/next-invoice-sequence s "JPN")))
    (store/with-trade-orders s {"x" {:id "x" :order-id "TO-X" :commodity-category "Steel products"
                                     :counterparty "c" :price 1250000.00
                                     :contract-terms "CIF, net 45 days"
                                     :credit-cleared? true :sanctions-screened? true
                                     :export-license-cleared? true
                                     :dispatched? false :invoiced? false
                                     :jurisdiction "JPN" :status :intake
                                     :shipment-number nil :invoice-number nil}})
    (is (= "c" (:counterparty (store/trade-order s "x"))))))
