(ns shosha.registry-test
  (:require [clojure.test :refer [deftest is]]
            [shosha.registry :as r]))

;; The general-trading domain checks (credit-clearance, contract-on-file,
;; export-control classification, sanctions-screening) are direct entity
;; booleans in the governor, NOT pure registry range functions -- so this
;; registry has NO range-check suite to test. Only record construction
;; is here.

;; ----------------------------- register-shipment-record -----------------------------

(deftest shipment-is-a-draft-not-a-real-dispatch
  (let [result (r/register-shipment-record "to-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest shipment-assigns-shipment-number
  (let [result (r/register-shipment-record "to-1" "JPN" 7)]
    (is (= (get result "shipment_number") "JPN-SHIPMENT-000007"))
    (is (= (get-in result ["record" "trade_order_id"]) "to-1"))
    (is (= (get-in result ["record" "kind"]) "shipment-dispatch-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest shipment-validation-rules
  (is (thrown? Exception (r/register-shipment-record "" "JPN" 0)))
  (is (thrown? Exception (r/register-shipment-record "to-1" "" 0)))
  (is (thrown? Exception (r/register-shipment-record "to-1" "JPN" -1))))

;; ----------------------------- register-invoice-record -----------------------------

(deftest invoice-is-a-draft-not-a-real-invoice
  (let [result (r/register-invoice-record "to-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest invoice-assigns-invoice-number
  (let [result (r/register-invoice-record "to-1" "JPN" 7)]
    (is (= (get result "invoice_number") "JPN-INVOICE-000007"))
    (is (= (get-in result ["record" "trade_order_id"]) "to-1"))
    (is (= (get-in result ["record" "kind"]) "trade-invoice-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest invoice-validation-rules
  (is (thrown? Exception (r/register-invoice-record "" "JPN" 0)))
  (is (thrown? Exception (r/register-invoice-record "to-1" "" 0)))
  (is (thrown? Exception (r/register-invoice-record "to-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-shipment-record "to-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-shipment-record "to-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-SHIPMENT-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-SHIPMENT-000001" (get-in hist2 [1 "record_id"])))))
