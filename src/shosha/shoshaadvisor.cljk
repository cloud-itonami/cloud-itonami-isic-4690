(ns shosha.shoshaadvisor
  "ShoshaAdvisor client -- the *contained intelligence node* for the
  general-trading (non-specialized wholesale, ISIC 4690) actor -- the
  Japanese-style sogo-shosha archetype.

  It normalizes trade-order intake, drafts a per-jurisdiction
  counterparty-diligence / export-control / sanctions evidence
  checklist, drafts the shipment-dispatch action, and drafts the
  invoice-settlement action. CRITICAL: it is a smart-but-untrusted
  advisor. It returns a *proposal* (with a rationale + the fields it
  cited), never a committed record or a real dispatch/settlement.
  Every output is censored downstream by `shosha.governor` before
  anything touches the SSoT, and `:shipment/dispatch`/`:invoice/
  settle` proposals NEVER auto-commit at any phase -- see README
  `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :shipment/dispatch | :invoice/settle | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [kotoba.lang.text :as str]
            [shosha.facts :as facts]
            [shosha.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the order-id, counterparty, jurisdiction, commodity
  category or any physical/commercial value. High confidence, low
  stakes."
  [_db {:keys [patch]}]
  {:summary    (str "商社トレードオーダー記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :order/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- verify-contract
  "Per-jurisdiction counterparty-diligence / export-control / sanctions
  evidence checklist draft. `:no-spec?` injects the failure mode we
  must defend against: proposing a checklist for a jurisdiction with
  NO official spec-basis in `shosha.facts` -- the Shosha Trading
  Governor must reject this (never invent a jurisdiction's
  requirements)."
  [db {:keys [subject no-spec?]}]
  (let [to (store/trade-order db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction to))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "shosha.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :contract-assessment/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :contract-assessment/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- propose-dispatch
  "Draft the actual SHIPMENT-DISPATCH action -- handing a real
  cross-border shipment off to a licensed freight forwarder / customs
  broker (a logistics-coordination referral, not a robot-operated act
  -- this actor does not operate physical loading hardware, see
  README `Robotics premise`). ALWAYS `:stake :shipment/dispatch` --
  this is a REAL-WORLD act, never a draft the actor may auto-run. See
  README `Actuation`: no phase ever adds this op to a phase's `:auto`
  set (`shosha.phase`); the governor also always escalates on
  `:shipment/dispatch`. Two independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [to (store/trade-order db subject)
        credit-ok? (and to (true? (:credit-cleared? to)))
        contract-ok? (and to (some? (:contract-terms to))
                          (not= "" (:contract-terms to)))
        export-ok? (and to (true? (:export-license-cleared? to)))
        sanctions-ok? (and to (true? (:sanctions-screened? to)))]
    {:summary    (str subject " 向け出荷提案"
                      (when to (str " (counterparty=" (:counterparty to)
                                    ", commodity=" (:commodity-category to) ")")))
     :rationale  (if to
                   (str "credit-cleared?=" credit-ok?
                        " contract-on-file?=" contract-ok?
                        " export-license-cleared?=" export-ok?
                        " sanctions-screened?=" sanctions-ok?)
                   "trade-orderが見つかりません")
     :cites      (if to [subject] [])
     :effect     :order/mark-dispatched
     :value      {:trade-order-id subject}
     :stake      :shipment/dispatch
     :confidence (if (and credit-ok? contract-ok? export-ok? sanctions-ok?) 0.9 0.3)}))

(defn- propose-invoice
  "Draft the actual INVOICE-SETTLEMENT action -- settling a real trade
  invoice (the money side of a general-trading transaction, custody/
  financial transfer). ALWAYS `:stake :invoice/settle` -- this is a
  REAL-WORLD act (real money moves between counterparty and trading
  house), never a draft the actor may auto-run. See README
  `Actuation`: no phase ever adds this op to a phase's `:auto` set
  (`shosha.phase`); the governor also always escalates on
  `:invoice/settle`. Two independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [to (store/trade-order db subject)
        dispatched? (and to (:dispatched? to))
        sanctions-ok? (and to (true? (:sanctions-screened? to)))]
    {:summary    (str subject " 向け請求提案"
                      (when to (str " (counterparty=" (:counterparty to) ")")))
     :rationale  (if to
                   (str "dispatched?=" dispatched?
                        " sanctions-screened?=" sanctions-ok?)
                   "trade-orderが見つかりません")
     :cites      (if to [subject] [])
     :effect     :order/mark-invoiced
     :value      {:trade-order-id subject}
     :stake      :invoice/settle
     :confidence (if (and dispatched? sanctions-ok?) 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :order/intake       (normalize-intake db request)
    :contract/verify    (verify-contract db request)
    :shipment/dispatch  (propose-dispatch db request)
    :invoice/settle     (propose-invoice db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは非専門商社(総合商社)の出荷・請求エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。"
       "説明や前置きは一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:order/upsert|:contract-assessment/set|:order/mark-dispatched|"
       ":order/mark-invoiced) "
       ":stake(:shipment/dispatch か :invoice/settle か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の輸出管理・制裁要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"
       "取引先信用審査・契約有無・輸出管理該非判定・制裁スクリーニングの状態を偽って"
       "報告してはいけません。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :contract/verify   {:trade-order (store/trade-order st subject)}
    :shipment/dispatch {:trade-order (store/trade-order st subject)}
    :invoice/settle    {:trade-order (store/trade-order st subject)}
    {:trade-order (store/trade-order st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Shosha Trading Governor
  escalates/holds -- an LLM hiccup can never auto-dispatch a shipment
  or auto-settle an invoice."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :shoshaadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
