(ns shosha.registry
  "Pure-function shipment + invoice record construction -- an
  append-only general-trading book-of-record draft.

  Like the fuel-wholesale sibling's own registry, this non-specialized
  (general/diversified) wholesale-trade vertical's Shosha Trading
  Governor needs NO registry range-check functions at all: its domain
  checks (credit-uncleared, contract-missing, export-license-uncleared,
  counterparty-sanctions-flag-unresolved) are direct entity boolean
  reads in `shosha.governor`, off dedicated `:credit-cleared?` /
  `:contract-terms` / `:export-license-cleared?` / `:sanctions-
  screened?` facts on the `trade-order` record. So this namespace is
  RECORD CONSTRUCTION ONLY -- no pure range checks to host here.

  Like every sibling actor's registry, there is no single international
  reference-number standard for a shipment or an invoice record --
  every operator/jurisdiction assigns its own reference format. This
  namespace does NOT invent one beyond a jurisdiction-scoped sequence
  number; it validates the record's required fields, the same honest,
  non-fabricating discipline `shosha.facts` uses.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real freight-forwarder/customs-broker/billing system. It
  builds the RECORD an operator would keep, not the act of dispatching
  a real shipment or settling a real invoice itself (that is
  `shosha.operation`'s `:shipment/dispatch`/`:invoice/settle`, always
  human-gated -- see README `Actuation`)."
  (:require [kotoba.lang.text :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the operator's act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

;; ----------------------------- record construction -----------------------------

(defn register-shipment-record
  "Validate + construct the SHIPMENT-DISPATCH registration DRAFT -- the
  operator's own legal act of handing the goods off to a licensed
  freight forwarder / customs broker for cross-border carriage. Pure
  function -- does not touch any real freight-forwarder or customs
  system; it builds the RECORD an operator would keep. `shosha.
  governor` independently re-verifies the counterparty's credit-
  clearance, contract-on-file, export-control-classification,
  sanctions-screening and evidence-completeness ground truth, and
  blocks a double-dispatch of the same trade-order, before this is
  ever allowed to commit."
  [trade-order-id jurisdiction sequence]
  (when-not (and trade-order-id (not= trade-order-id ""))
    (throw (ex-info "shipment: trade_order_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "shipment: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "shipment: sequence must be >= 0" {})))
  (let [shipment-number (str (str/upper jurisdiction) "-SHIPMENT-" (zero-pad sequence 6))
        record {"record_id" shipment-number
                "kind" "shipment-dispatch-draft"
                "trade_order_id" trade-order-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "shipment_number" shipment-number
     "certificate" (unsigned-certificate "ShipmentDispatch" shipment-number shipment-number)}))

(defn register-invoice-record
  "Validate + construct the TRADE-INVOICE registration DRAFT -- the
  operator's own legal act of settling a real trade invoice (the money
  side of a general-trading transaction, custody/financial transfer).
  Pure function -- does not touch any real billing or accounts-
  receivable system; it builds the RECORD an operator would keep.
  `shosha.governor` independently re-verifies the export-control-
  classification, sanctions-screening and evidence-completeness ground
  truth, and blocks a double-invoice of the same trade-order, before
  this is ever allowed to commit."
  [trade-order-id jurisdiction sequence]
  (when-not (and trade-order-id (not= trade-order-id ""))
    (throw (ex-info "invoice: trade_order_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "invoice: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "invoice: sequence must be >= 0" {})))
  (let [invoice-number (str (str/upper jurisdiction) "-INVOICE-" (zero-pad sequence 6))
        record {"record_id" invoice-number
                "kind" "trade-invoice-draft"
                "trade_order_id" trade-order-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "invoice_number" invoice-number
     "certificate" (unsigned-certificate "TradeInvoice" invoice-number invoice-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
