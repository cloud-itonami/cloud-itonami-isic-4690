(ns shosha.governor
  "Shosha Trading Governor -- the independent compliance layer that
  earns the ShoshaAdvisor the right to commit. The LLM has no notion
  of jurisdictional export-control / sanctions law, whether a
  counterparty's credit has actually been cleared, whether contract
  terms are actually on file, whether the goods/technology being
  brokered have actually been export-control classified, whether OFAC
  / equivalent sanctions screening has actually been passed, or when
  an act stops being a draft and becomes a real cross-border shipment
  dispatch or a real invoice settlement, so this MUST be a separate
  system able to *reject* a proposal and fall back to HOLD.

  Like the fuel-wholesale sibling's own governor, this non-specialized
  (general/diversified) wholesale-trade vertical has NO pre-existing
  general-trading capability library to delegate to -- so the domain
  checks (credit-clearance, contract-on-file, export-control
  classification, sanctions-screening) are direct entity boolean reads
  off the `trade-order` record, evaluated directly here, NOT delegated
  to a separate library's validated function.

  `:itonami.blueprint/governor` is `:shosha-trading-governor`, grep-
  verified UNIQUE fleet-wide -- no naming-collision precedent
  question, a fresh independent build following the SAME governed-
  actor architecture (langgraph StateGraph + independent Governor +
  Phase 0->3 rollout) established by `cloud-itonami-isic-6511` and
  applied by the fuel-wholesale sibling `cloud-itonami-isic-4671`.

  Six checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them. The confidence/actuation gate is SOFT: it asks
  a human to look (low confidence / actuation), and the human may
  approve -- but see `shosha.phase`: for `:stake :shipment/dispatch`/
  `:invoice/settle` (a real shipment dispatch or invoice settlement) NO
  phase ever allows auto-commit either. Two independent layers agree
  that actuation is always a human call.

    1. Spec-basis                  -- did the jurisdiction proposal cite
                                       an OFFICIAL source
                                       (`shosha.facts`), or invent one?
    2. Evidence incomplete         -- for `:shipment/dispatch`/
                                       `:invoice/settle`, has the
                                       jurisdiction actually been
                                       verified with a full counterparty-
                                       diligence + trade-control
                                       evidence checklist on file?
    3. Credit uncleared            -- for `:shipment/dispatch`, the
                                       counterparty's credit has NOT been
                                       cleared (the leasing collateral-
                                       coverage discipline, applied to
                                       counterparty credit). Evaluated
                                       before dispatch.
    4. Contract missing            -- for `:shipment/dispatch`, no
                                       contract-terms are on file for the
                                       order. Evaluated before dispatch.
    5. Export-license uncleared    -- for `:shipment/dispatch`, the
                                       goods/technology's export-control
                                       classification (ECCN/HS-code) has
                                       NOT been cleared -- the defining
                                       regulatory exposure of a general-
                                       trading house (unlike a single-
                                       commodity excise), evaluated
                                       before dispatch. THIS check has no
                                       analog in the fuel-wholesale
                                       sibling: it is this vertical's own
                                       domain content, not a rename.
    6. Counterparty sanctions flag
       unresolved                  -- for `:shipment/dispatch` and
                                       `:invoice/settle`, the counterparty
                                       has NOT passed OFAC / equivalent
                                       sanctions screening -- a HARD,
                                       un-overridable hold. Evaluated
                                       UNCONDITIONALLY at both actuation
                                       ops.
    7. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:shipment/dispatch`/
                                       `:invoice/settle` (REAL acts)
                                       -> escalate.

  Two more guards, double-dispatch/double-invoice prevention, are
  enforced but NOT listed as numbered HARD checks above because they
  need no upstream comparison at all -- `already-dispatched-violations`/
  `already-invoiced-violations` refuse to dispatch/invoice the SAME
  trade-order twice, off dedicated `:dispatched?`/`:invoiced?` facts
  (never a `:status` value) -- the SAME 'check a dedicated boolean, not
  status' discipline every prior governor's guards establish, informed
  by `cloud-itonami-isic-6492`'s status-lifecycle bug (ADR-2607071320)."
  (:require [shosha.facts :as facts]
            [shosha.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Dispatching a real cross-border shipment (a logistics-coordination
  referral handing the goods to a licensed freight forwarder / customs
  broker) and settling a real trade invoice (real money moving between
  counterparty and trading house) are the two real-world actuation
  events this actor performs -- a two-member set, matching every
  sibling's own dual-actuation shape."
  #{:shipment/dispatch :invoice/settle})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:contract/verify` (or `:shipment/dispatch`/`:invoice/settle`)
  proposal with no spec-basis citation is a HARD violation -- never
  invent a jurisdiction's export-control / sanctions requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:contract/verify :shipment/dispatch :invoice/settle} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:shipment/dispatch`/`:invoice/settle`, the jurisdiction's
  required counterparty-diligence + trade-control evidence (credit-
  clearance record, contract/PO, sanctions-screening record, export-
  control classification record) must actually be satisfied -- do not
  trust the advisor's self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:shipment/dispatch :invoice/settle} op)
    (let [to (store/trade-order st subject)
          assessment (store/assessment-of st subject)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      (:jurisdiction to) (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(信用審査記録/契約書またはPO/制裁スクリーニング記録/輸出管理該非判定記録)が充足していない状態での提案"}]))))

(defn- credit-uncleared-violations
  "For `:shipment/dispatch`, refuses to dispatch a shipment to a
  counterparty whose credit has NOT been cleared -- counterparty credit
  not cleared (the leasing collateral-coverage discipline, applied to
  counterparty credit). Evaluated ahead of the logistics-coordination
  referral."
  [{:keys [op subject]} st]
  (when (= op :shipment/dispatch)
    (let [to (store/trade-order st subject)]
      (when (not (true? (:credit-cleared? to)))
        [{:rule :credit-uncleared
          :detail (str subject " の取引先信用審査(credit-clearance)が未了 -- 出荷提案は進められない")}]))))

(defn- contract-missing-violations
  "For `:shipment/dispatch`, refuses to dispatch a shipment when no
  contract-terms are on file for the order."
  [{:keys [op subject]} st]
  (when (= op :shipment/dispatch)
    (let [to (store/trade-order st subject)]
      (when (or (nil? (:contract-terms to)) (= "" (:contract-terms to)))
        [{:rule :contract-missing
          :detail (str subject " に契約条項(contract-terms)の記録が無い -- 出荷提案は進められない")}]))))

(defn- export-license-uncleared-violations
  "For `:shipment/dispatch`, refuses to dispatch a shipment whose
  export-control classification (ECCN/HS-code determination -- is the
  good/technology controlled or dual-use, and if so is it licensed?)
  has NOT been cleared. This is the check with NO analog in the fuel-
  wholesale sibling: a non-specialized (general/diversified) trading
  house brokers UNRELATED commodity categories order to order, so its
  defining regulatory exposure is cross-border export-control
  classification, not a single-commodity excise."
  [{:keys [op subject]} st]
  (when (= op :shipment/dispatch)
    (let [to (store/trade-order st subject)]
      (when (not (true? (:export-license-cleared? to)))
        [{:rule :export-license-uncleared
          :detail (str subject " の輸出管理該非判定/許可(export-control classification)が未了 -- 出荷提案は進められない")}]))))

(defn- counterparty-sanctions-flag-unresolved-violations
  "For `:shipment/dispatch` and `:invoice/settle`, an unresolved
  sanctions-screening flag -- the counterparty has NOT passed OFAC /
  equivalent sanctions screening -- is a HARD, un-overridable hold.
  Evaluated UNCONDITIONALLY at both actuation ops: neither the goods
  ship nor money settles against an unscreened counterparty."
  [{:keys [op subject]} st]
  (when (contains? #{:shipment/dispatch :invoice/settle} op)
    (let [to (store/trade-order st subject)]
      (when (not (true? (:sanctions-screened? to)))
        [{:rule :counterparty-sanctions-flag-unresolved
          :detail (str subject " の取引先制裁スクリーニング(OFAC等)が未了 -- 出荷・請求提案は進められない")}]))))

(defn- already-dispatched-violations
  "For `:shipment/dispatch`, refuses to dispatch the SAME trade-order
  twice, off a dedicated `:dispatched?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :shipment/dispatch)
    (when (store/trade-order-already-dispatched? st subject)
      [{:rule :already-dispatched
        :detail (str subject " は既に出荷済み")}])))

(defn- already-invoiced-violations
  "For `:invoice/settle`, refuses to settle the SAME trade-order's
  invoice twice, off a dedicated `:invoiced?` fact (never a `:status`
  value)."
  [{:keys [op subject]} st]
  (when (= op :invoice/settle)
    (when (store/trade-order-already-invoiced? st subject)
      [{:rule :already-invoiced
        :detail (str subject " は既に請求済み")}])))

(defn check
  "Censors a ShoshaAdvisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (credit-uncleared-violations request st)
                           (contract-missing-violations request st)
                           (export-license-uncleared-violations request st)
                           (counterparty-sanctions-flag-unresolved-violations request st)
                           (already-dispatched-violations request st)
                           (already-invoiced-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
