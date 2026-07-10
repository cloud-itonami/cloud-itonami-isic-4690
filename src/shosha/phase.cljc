(ns shosha.phase
  "Phase 0->3 staged rollout for the general-trading (non-specialized
  wholesale) actor.

    Phase 0  read-only        -- no writes, still governor-gated.
    Phase 1  assisted-intake  -- trade-order intake allowed, every write
                                 needs human approval.
    Phase 2  assisted-verify  -- adds contract-verification writes,
                                 still approval.
    Phase 3  supervised auto  -- governor-clean, high-confidence
                                 `:order/intake` (no capital risk
                                 yet) may auto-commit. `:shipment/
                                 dispatch`/`:invoice/settle` NEVER
                                 auto-commit, at any phase.

  `:shipment/dispatch`/`:invoice/settle` are deliberately ABSENT from
  every phase's `:auto` set, including phase 3 -- a permanent
  structural fact, not a rollout milestone still to come. Dispatching a
  real cross-border shipment (a logistics-coordination referral handing
  the goods to a licensed freight forwarder / customs broker -- this
  general-trading actor does not operate physical loading hardware
  itself, see README `Robotics premise`) and settling a real trade
  invoice (real money moving between counterparty and trading house)
  are the two real-world commercial acts this actor performs; both are
  always a human trading supervisor's call. `shosha.governor`'s
  `:shipment/dispatch`/`:invoice/settle` high-stakes gate enforces the
  same invariant independently -- two layers, not one, agree on this.
  Like every prior sibling's phase 3 `:auto` set, this domain has only
  ONE member (`:order/intake`) -- no separate no-capital-risk lifecycle
  distinct from the trade-order itself.")

(def read-ops  #{})
(def write-ops #{:order/intake :contract/verify :shipment/dispatch :invoice/settle})

;; NOTE the invariant: `:shipment/dispatch`/`:invoice/settle` are members
;; of `write-ops` (governor-gated like any write) but are NEVER members
;; of any phase's `:auto` set below. Do not add them there.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed to
  auto-commit when governor-clean>}."
  {0 {:label "read-only"        :writes #{}                                                :auto #{}}
   1 {:label "assisted-intake"  :writes #{:order/intake}                                    :auto #{}}
   2 {:label "assisted-verify"  :writes #{:order/intake :contract/verify}                   :auto #{}}
   3 {:label "supervised-auto"  :writes write-ops
      :auto #{:order/intake}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE (:phase-approval),
    even if the governor was clean.
  - `:shipment/dispatch`/`:invoice/settle` are never auto-eligible at
    any phase, so they always escalate once the governor clears them
    (or hold if the governor doesn't)."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (contains? read-ops op)              {:disposition governor-disposition :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map a Shosha Trading Governor verdict to a base disposition before
  the phase gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
