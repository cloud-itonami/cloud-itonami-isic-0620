(ns gasfield.gasfieldadvisor
  "GasFieldAdvisor client -- the *contained intelligence node* for the
  natural-gas-extraction actor.

  It normalizes gas-well intake, drafts a per-jurisdiction well-construction/
  well-control/sour-service evidence checklist, drafts the gas-extraction
  action, and drafts the production-settlement action. CRITICAL: it is a
  smart-but-untrusted advisor. It returns a *proposal* (with a rationale
  + the fields it cited), never a committed record or a real extraction/
  settlement. Every output is censored downstream by `gasfield.governor`
  before anything touches the SSoT, and `:well/extract`/`:production/settle`
  proposals NEVER auto-commit at any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :well/extract | :production/settle | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
           [clojure.string :as str]
           [gasfield.facts :as facts]
           [gasfield.registry :as registry]
           [gasfield.store :as store]
           [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the field/well name, operator or jurisdiction. High
  confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "ガス坑井記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :well/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- assess-reservoir
  "Per-jurisdiction well-construction/well-control/sour-service evidence
  checklist draft. `:no-spec?` injects the failure mode we must defend
  against: proposing a checklist for a jurisdiction with NO official
  spec-basis in `gasfield.facts` -- the Gas Well Safety Governor must
  reject this (never invent a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [w (store/gas-well db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction w))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "gasfield.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :assessment/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :assessment/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- propose-extract
  "Draft the actual GAS-EXTRACTION action -- opening a real gas well to
  flow against a live reservoir. ALWAYS `:stake :well/extract` -- this
  is a REAL-WORLD act (an autonomous gas-wellhead/workover robot
  physically opens the tree, or an operator does), never a draft the
  actor may auto-run. See README `Actuation`: no phase ever adds this
  op to a phase's `:auto` set (`gasfield.phase`); the governor also
  always escalates on `:well/extract`. Two independent layers agree,
  deliberately."
  [db {:keys [subject]}]
  (let [w (store/gas-well db subject)
        reservoir-ok? (and w (not (registry/pressure-out-of-range?
                                    (:reservoir-pressure-mpa-actual w)
                                    (:reservoir-pressure-mpa-min w)
                                    (:reservoir-pressure-mpa-max w))))
        annular-ok? (and w (not (registry/well-integrity-annular-pressure-excessive?
                                  (:annular-pressure-mpa w) (:maasp-mpa w))))
        co2-ok? (and w (not (registry/co2-corrosive?
                              (:co2-percent w) (:co2-corrosion-max w))))
        idlh (facts/idlh-percent (:jurisdiction w))
        h2s-ok? (and w (not (registry/h2s-toxic? (:h2s-percent w) idlh)))
        integrity-clear? (and w (or (not (:integrity-flag-raised? w))
                                    (:integrity-flag-resolved? w)))]
    {:summary    (str subject " 向け採掘提案"
                      (when w (str " (operator=" (:operator w) ")")))
     :rationale  (if w
                   (str "reservoir-in-window?=" reservoir-ok?
                        " annular<MAASP?=" annular-ok?
                        " co2-ok?=" co2-ok?
                        " h2s<IDLH?=" h2s-ok?
                        " integrity-clear?=" integrity-clear?)
                   "gas wellが見つかりません")
     :cites      (if w [subject] [])
     :effect     :well/mark-extracted
     :value      {:well-id subject}
     :stake      :well/extract
     :confidence (if (and reservoir-ok? annular-ok? co2-ok? h2s-ok? integrity-clear?)
                   0.9 0.3)}))

(defn- propose-settlement
  "Draft the actual PRODUCTION-SETTLEMENT action -- settling a real
  production period (royalty / royalty-volume finalization, custody
  transfer). ALWAYS `:stake :production/settle` -- this is a REAL-WORLD
  act (real money / real royalty volumes move between operator and
  royalty owner), never a draft the actor may auto-run. See README
  `Actuation`: no phase ever adds this op to a phase's `:auto` set
  (`gasfield.phase`); the governor also always escalates on
  `:production/settle`. Two independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [w (store/gas-well db subject)
        extracted? (and w (:gas-extracted? w))
        integrity-clear? (and w (or (not (:integrity-flag-raised? w))
                                    (:integrity-flag-resolved? w)))]
    {:summary    (str subject " 向け生産精算提案"
                      (when w (str " (operator=" (:operator w) ")")))
     :rationale  (if w
                   (str "gas-extracted?=" extracted?
                        " integrity-clear?=" integrity-clear?)
                   "gas wellが見つかりません")
     :cites      (if w [subject] [])
     :effect     :well/mark-settled
     :value      {:well-id subject}
     :stake      :production/settle
     :confidence (if (and extracted? integrity-clear?) 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :well/intake         (normalize-intake db request)
    :reservoir/assess    (assess-reservoir db request)
    :well/extract        (propose-extract db request)
    :production/settle   (propose-settlement db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは地域天然ガス採掘事業者の採掘・生産精算エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。"
       "説明や前置きは一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:well/upsert|:assessment/set|:well/mark-extracted|"
       ":well/mark-settled) "
       ":stake(:well/extract か :production/settle か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域のガス坑井安全要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"
       "坑層圧力・環状圧力・CO2組成・H2S濃度・インテグリティフラグの状態を偽って報告してはいけません。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :reservoir/assess  {:well (store/gas-well st subject)}
    :well/extract      {:well (store/gas-well st subject)}
    :production/settle {:well (store/gas-well st subject)}
    {:well (store/gas-well st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Gas Well Safety Governor
  escalates/holds -- an LLM hiccup can never auto-extract a gas well or
  auto-settle production."
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
  {:t          :gasfieldadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
