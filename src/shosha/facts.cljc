(ns shosha.facts
  "Per-jurisdiction cross-border export-control / sanctions regulatory
  catalog -- the G2-style spec-basis table the Shosha Trading Governor
  checks every `:contract/verify` proposal against ('did the advisor
  cite an OFFICIAL public source for this jurisdiction's export-control
  / sanctions requirements, or did it invent one?').

  A non-specialized (general/diversified) wholesale trading house --
  the Japanese-style sogo-shosha archetype -- brokers trade across
  MULTIPLE unrelated commodity/product categories in the SAME order
  book (steel today, foodstuffs tomorrow, machinery the day after),
  unlike a specialized wholesaler (ISIC 4610-4669, e.g. the fuel-
  wholesale sibling `cloud-itonami-isic-4671`). Its defining
  regulatory exposure is therefore NOT a single-commodity excise or
  licence -- it is CROSS-BORDER TRADE CONTROL: export-control
  classification (is the good/technology being brokered controlled or
  dual-use, and if so is it licensed?) plus sanctions / denied-party
  screening, evaluated per jurisdiction regardless of which commodity
  category the order happens to be in.

  Each entry below is a REAL jurisdiction with a REAL export-control /
  sanctions regime: Japan's 外国為替及び外国貿易法 (Foreign Exchange
  and Foreign Trade Act, FEFTA) administered by METI's security-trade-
  control division, the US Export Administration Regulations (15
  C.F.R. Parts 730-774, administered by the Bureau of Industry and
  Security / BIS) plus OFAC (Treasury) sanctions programs, the UK
  Export Control Order 2008 (administered by the Export Control Joint
  Unit / ECJU) plus OFSI (HM Treasury) financial sanctions, and
  Germany's BAFA (Bundesamt für Wirtschaft und Ausfuhrkontrolle)
  enforcement of Regulation (EU) 2021/821 (the EU dual-use export-
  control recast, directly applicable in every EU member state) plus
  the national Außenwirtschaftsgesetz (AWG) / Außenwirtschaftsverordnung
  (AWV). The required-evidence set (credit-clearance record,
  contract / purchase order, sanctions-screening (OFAC/equivalent)
  record, export-control classification (ECCN/HS-code) record) mirrors
  the counterparty-diligence + trade-control evidence a general-trading
  compliance function actually demands before a shipment is dispatched
  and an invoice is settled.

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` is the counterparty-
  diligence + trade-control evidence set (credit-clearance record,
  contract/PO, sanctions-screening record, export-control
  classification record); `:legal-basis` / `:owner-authority` /
  `:provenance` are the G2 citation the governor requires before any
  `:contract/verify` proposal can commit."
  {"JPN" {:name "JPN"
          :owner-authority "経済産業省 (METI) 貿易経済協力局 安全保障貿易管理課"
          :legal-basis "外国為替及び外国貿易法 (Foreign Exchange and Foreign Trade Act, FEFTA); 輸出貿易管理令 (Export Trade Control Order)"
          :provenance "https://www.meti.go.jp/policy/anpo/"
          :required-evidence ["credit-clearance record"
                              "contract/PO"
                              "sanctions-screening (OFAC/equivalent) record"
                              "export-control classification (ECCN/HS-code) record"]}
   "USA" {:name "USA"
          :owner-authority "Bureau of Industry and Security (BIS), U.S. Department of Commerce / OFAC (U.S. Treasury)"
          :legal-basis "Export Administration Regulations (15 C.F.R. Parts 730-774); OFAC sanctions programs (31 C.F.R. Chapter V)"
          :provenance "https://www.bis.doc.gov/"
          :required-evidence ["credit-clearance record"
                              "contract/PO"
                              "sanctions-screening (OFAC/equivalent) record"
                              "export-control classification (ECCN/HS-code) record"]}
   "GBR" {:name "GBR"
          :owner-authority "Export Control Joint Unit (ECJU), Department for Business and Trade / Office of Financial Sanctions Implementation (OFSI), HM Treasury"
          :legal-basis "Export Control Order 2008 (SI 2008/3231); UK financial sanctions regulations"
          :provenance "https://www.gov.uk/guidance/beginners-guide-to-export-controls"
          :required-evidence ["credit-clearance record"
                              "contract/PO"
                              "sanctions-screening (OFAC/equivalent) record"
                              "export-control classification (ECCN/HS-code) record"]}
   "DEU" {:name "DEU"
          :owner-authority "Bundesamt für Wirtschaft und Ausfuhrkontrolle (BAFA)"
          :legal-basis "Regulation (EU) 2021/821 (dual-use export-control recast); Außenwirtschaftsgesetz (AWG) / Außenwirtschaftsverordnung (AWV)"
          :provenance "https://www.bafa.de/"
          :required-evidence ["credit-clearance record"
                              "contract/PO"
                              "sanctions-screening (OFAC/equivalent) record"
                              "export-control classification (ECCN/HS-code) record"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to dispatch a
  shipment or settle an invoice on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions
  actually have a spec-basis entry. Never report a missing jurisdiction
  as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-4690 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `shosha.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
