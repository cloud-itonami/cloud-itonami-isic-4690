(ns shosha.store
  "SSoT for the general-trading (non-specialized wholesale) actor,
  behind a `Store` protocol so the backend is a swap, not a rewrite --
  the same seam every prior `cloud-itonami-isic-*` actor in this fleet
  uses.

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/shosha/store_contract_test.clj), which is the whole point: the
  actor, the Shosha Trading Governor and the audit ledger never know
  which SSoT they run on.

  Like the fuel-wholesale sibling's own `fuel-order` entity, this
  vertical's `dispatch` and `settle` actuation events apply
  SEQUENTIALLY to the SAME `trade-order` -- a shipment dispatch (a
  logistics-coordination referral handing the goods to a licensed
  freight forwarder / customs broker, NOT a robot-operated act at a
  fixed physical rack) happens first, invoice settlement happens later,
  on the same order record. Dedicated double-actuation-guard booleans
  (`:dispatched?`/`:invoiced?`, never a `:status` value) enforce the
  same discipline every sibling governor's guards establish.

  Unlike a specialized wholesaler, a `trade-order` here also carries a
  free-text `:commodity-category` -- the demo data deliberately spans
  UNRELATED categories (steel, foodstuffs, textiles, chemicals,
  machinery) on the SAME order book, the honest shape of a
  non-specialized (general/diversified) trading house.

  The ledger stays append-only on every backend: 'which trade-order was
  verified for a jurisdiction with no official spec-basis, which
  counterparty had credit-uncleared / no contract / an unresolved
  export-control classification / an unresolved sanctions-screening
  flag, which order had a shipment dispatched, which invoice was
  settled, on what jurisdictional basis, approved by whom' is always a
  query over an immutable log -- the audit trail a regulator, a
  counterparty, or an operator trusting a general-trading actor needs,
  and the evidence an operator needs if a shipment or an invoice is
  later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [shosha.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (trade-order [s id])
  (all-trade-orders [s])
  (assessment-of [s trade-order-id] "committed contract assessment, or nil")
  (ledger [s])
  (shipment-history [s] "the append-only shipment history (shosha.registry drafts)")
  (invoice-history [s] "the append-only invoice history (shosha.registry drafts)")
  (next-shipment-sequence [s jurisdiction] "next shipment-number sequence for a jurisdiction")
  (next-invoice-sequence [s jurisdiction] "next invoice-number sequence for a jurisdiction")
  (trade-order-already-dispatched? [s trade-order-id] "has a shipment already been dispatched for this order?")
  (trade-order-already-invoiced? [s trade-order-id] "has this order's invoice already been settled?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-trade-orders [s trade-orders] "replace/seed the trade-order directory (map id->trade-order)"))

;; ----------------------------- demo data -----------------------------

(defn- base-order
  "The neutral, clean trade-order shape (every field in its safe
  state), so each demo order below isolates exactly ONE failure mode
  by overriding a single field."
  [overrides]
  (merge {:id "to-1" :order-id "TO-2026-0001" :commodity-category "Steel products"
          :counterparty "Sendai Trading Co" :price 1250000.00
          :contract-terms "CIF, net 45 days"
          :credit-cleared? true :sanctions-screened? true :export-license-cleared? true
          :dispatched? false :invoiced? false
          :jurisdiction "JPN" :status :intake
          :shipment-number nil :invoice-number nil}
         overrides))

(defn demo-data
  "A small, self-contained trade-order set covering both actuation
  lifecycles (shipment dispatch, invoice settlement) plus the Shosha
  Trading Governor's own checks, so the actor + tests run offline. Each
  violation order isolates exactly ONE failure mode (the rest stay
  clean), and each spans a DIFFERENT commodity category -- the honest
  shape of a non-specialized (general/diversified) trading house that
  never puts all its trade through one commodity line, following the
  'exercise the failure mode directly, never only via a happy-path
  actuation' discipline every sibling governor's demo data establishes."
  []
  {:trade-orders
   (into {}
         (for [o [(base-order {:id "to-1" :order-id "TO-2026-0001"})
                  (base-order {:id "to-2" :order-id "TO-2026-0002"
                               :commodity-category "Industrial machinery"
                               :counterparty "Atlantis Trading Ltd"
                               :jurisdiction "ATL"})
                  (base-order {:id "to-3" :order-id "TO-2026-0003"
                               :commodity-category "Frozen foodstuffs"
                               :counterparty "Cedar Provisions Co"
                               :credit-cleared? false})
                  (base-order {:id "to-4" :order-id "TO-2026-0004"
                               :commodity-category "Textiles"
                               :counterparty "Delta Weavers BV"
                               :contract-terms nil})
                  (base-order {:id "to-5" :order-id "TO-2026-0005"
                               :commodity-category "Specialty chemicals"
                               :counterparty "Eagle Chemicals SA"
                               :sanctions-screened? false})
                  (base-order {:id "to-6" :order-id "TO-2026-0006"
                               :commodity-category "Precision machine tools"
                               :counterparty "Falcon Precision KK"
                               :export-license-cleared? false})]]
           [(:id o) o]))})

;; ----------------------------- shared commit logic -----------------------------

(defn- dispatch-order!
  "Backend-agnostic `:order/mark-dispatched` -- looks up the trade-order
  via the protocol and drafts the shipment record, and returns
  {:result .. :trade-order-patch ..} for the caller to persist."
  [s trade-order-id]
  (let [to (trade-order s trade-order-id)
        seq-n (next-shipment-sequence s (:jurisdiction to))
        result (registry/register-shipment-record trade-order-id (:jurisdiction to) seq-n)]
    {:result result
     :trade-order-patch {:dispatched? true
                         :shipment-number (get result "shipment_number")}}))

(defn- invoice-order!
  "Backend-agnostic `:order/mark-invoiced` -- looks up the trade-order
  via the protocol and drafts the invoice record, and returns
  {:result .. :trade-order-patch ..} for the caller to persist."
  [s trade-order-id]
  (let [to (trade-order s trade-order-id)
        seq-n (next-invoice-sequence s (:jurisdiction to))
        result (registry/register-invoice-record trade-order-id (:jurisdiction to) seq-n)]
    {:result result
     :trade-order-patch {:invoiced? true
                         :invoice-number (get result "invoice_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (trade-order [_ id] (get-in @a [:trade-orders id]))
  (all-trade-orders [_] (sort-by :id (vals (:trade-orders @a))))
  (assessment-of [_ trade-order-id] (get-in @a [:assessments trade-order-id]))
  (ledger [_] (:ledger @a))
  (shipment-history [_] (:shipments @a))
  (invoice-history [_] (:invoices @a))
  (next-shipment-sequence [_ jurisdiction] (get-in @a [:shipment-sequences jurisdiction] 0))
  (next-invoice-sequence [_ jurisdiction] (get-in @a [:invoice-sequences jurisdiction] 0))
  (trade-order-already-dispatched? [_ trade-order-id] (boolean (get-in @a [:trade-orders trade-order-id :dispatched?])))
  (trade-order-already-invoiced? [_ trade-order-id] (boolean (get-in @a [:trade-orders trade-order-id :invoiced?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :order/upsert
      (swap! a update-in [:trade-orders (:id value)] merge value)

      :contract-assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :order/mark-dispatched
      (let [trade-order-id (first path)
            {:keys [result trade-order-patch]} (dispatch-order! s trade-order-id)
            jurisdiction (:jurisdiction (trade-order s trade-order-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:shipment-sequences jurisdiction] (fnil inc 0))
                       (update-in [:trade-orders trade-order-id] merge trade-order-patch)
                       (update :shipments registry/append result))))
        result)

      :order/mark-invoiced
      (let [trade-order-id (first path)
            {:keys [result trade-order-patch]} (invoice-order! s trade-order-id)
            jurisdiction (:jurisdiction (trade-order s trade-order-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:invoice-sequences jurisdiction] (fnil inc 0))
                       (update-in [:trade-orders trade-order-id] merge trade-order-patch)
                       (update :invoices registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-trade-orders [s trade-orders] (when (seq trade-orders) (swap! a assoc :trade-orders trade-orders)) s))

(defn seed-db
  "A MemStore seeded with the demo trade-order set. The deterministic default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {}
                           :ledger [] :shipment-sequences {} :shipments []
                           :invoice-sequences {} :invoices []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (assessment payloads, ledger facts,
  shipment/invoice records) are stored as EDN strings so
  `langchain.db` doesn't expand them into sub-entities -- the same
  convention every sibling actor's store uses."
  {:trade-order/id                       {:db/unique :db.unique/identity}
   :assessment/trade-order-id            {:db/unique :db.unique/identity}
   :ledger/seq                           {:db/unique :db.unique/identity}
   :shipment/seq                         {:db/unique :db.unique/identity}
   :invoice/seq                          {:db/unique :db.unique/identity}
   :shipment-sequence/jurisdiction       {:db/unique :db.unique/identity}
   :invoice-sequence/jurisdiction        {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

;; Every trade-order field is stored as its own Datomic attr so a
;; governor pull reads the exact ground truth (no blob decode). Boolean
;; fields are coerced on read so a missing attr reads back as false
;; (parity with MemStore). [field-key tx-attr boolean?]
(def ^:private trade-order-fields
  [[:id :trade-order/id false]
   [:order-id :trade-order/order-id false]
   [:commodity-category :trade-order/commodity-category false]
   [:counterparty :trade-order/counterparty false]
   [:price :trade-order/price false]
   [:contract-terms :trade-order/contract-terms false]
   [:credit-cleared? :trade-order/credit-cleared? true]
   [:sanctions-screened? :trade-order/sanctions-screened? true]
   [:export-license-cleared? :trade-order/export-license-cleared? true]
   [:dispatched? :trade-order/dispatched? true]
   [:invoiced? :trade-order/invoiced? true]
   [:jurisdiction :trade-order/jurisdiction false]
   [:status :trade-order/status false]
   [:shipment-number :trade-order/shipment-number false]
   [:invoice-number :trade-order/invoice-number false]])

(defn- trade-order->tx [to]
  (reduce (fn [tx [k attr _bool?]]
            (let [v (get to k)]
              (cond-> tx (some? v) (assoc attr v))))
          {:trade-order/id (:id to)}
          trade-order-fields))

(def ^:private trade-order-pull (mapv second trade-order-fields))

(defn- pull->trade-order [m]
  (when (:trade-order/id m)
    (reduce (fn [to [k attr bool?]]
              (let [v (get m attr)]
                (cond
                  bool?        (assoc to k (boolean v))
                  (some? v)    (assoc to k v)
                  :else        to)))
            {:id (:trade-order/id m)}
            trade-order-fields)))

(defrecord DatomicStore [conn]
  Store
  (trade-order [_ id]
    (pull->trade-order (d/pull (d/db conn) trade-order-pull [:trade-order/id id])))
  (all-trade-orders [_]
    (->> (d/q '[:find [?id ...] :where [?e :trade-order/id ?id]] (d/db conn))
         (map #(pull->trade-order (d/pull (d/db conn) trade-order-pull [:trade-order/id %])))
         (sort-by :id)))
  (assessment-of [_ trade-order-id]
    (dec* (d/q '[:find ?p . :in $ ?toid
                :where [?a :assessment/trade-order-id ?toid] [?a :assessment/payload ?p]]
              (d/db conn) trade-order-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (shipment-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :shipment/seq ?s] [?e :shipment/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (invoice-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :invoice/seq ?s] [?e :invoice/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-shipment-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :shipment-sequence/jurisdiction ?j] [?e :shipment-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-invoice-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :invoice-sequence/jurisdiction ?j] [?e :invoice-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (trade-order-already-dispatched? [s trade-order-id]
    (boolean (:dispatched? (trade-order s trade-order-id))))
  (trade-order-already-invoiced? [s trade-order-id]
    (boolean (:invoiced? (trade-order s trade-order-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :order/upsert
      (d/transact! conn [(trade-order->tx value)])

      :contract-assessment/set
      (d/transact! conn [{:assessment/trade-order-id (first path) :assessment/payload (enc payload)}])

      :order/mark-dispatched
      (let [trade-order-id (first path)
            {:keys [result trade-order-patch]} (dispatch-order! s trade-order-id)
            jurisdiction (:jurisdiction (trade-order s trade-order-id))
            next-n (inc (next-shipment-sequence s jurisdiction))]
        (d/transact! conn
                     [(trade-order->tx (assoc trade-order-patch :id trade-order-id))
                      {:shipment-sequence/jurisdiction jurisdiction :shipment-sequence/next next-n}
                      {:shipment/seq (count (shipment-history s)) :shipment/record (enc (get result "record"))}])
        result)

      :order/mark-invoiced
      (let [trade-order-id (first path)
            {:keys [result trade-order-patch]} (invoice-order! s trade-order-id)
            jurisdiction (:jurisdiction (trade-order s trade-order-id))
            next-n (inc (next-invoice-sequence s jurisdiction))]
        (d/transact! conn
                     [(trade-order->tx (assoc trade-order-patch :id trade-order-id))
                      {:invoice-sequence/jurisdiction jurisdiction :invoice-sequence/next next-n}
                      {:invoice/seq (count (invoice-history s)) :invoice/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-trade-orders [s trade-orders]
    (when (seq trade-orders) (d/transact! conn (mapv trade-order->tx (vals trade-orders)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:trade-orders ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [trade-orders]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-trade-orders s trade-orders))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo trade-order set -- the Datomic-backed
  analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
