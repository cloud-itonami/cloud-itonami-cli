(ns cloud.itonami.capital-cli
 (:require ["node:fs" :as fs] ["node:path" :as path] ["node:os" :as os] ["node:child_process" :as cp] [kotoba.lang.text :as str]))
(def origin "https://app.itonami.cloud")
(defn session-path [] (path/join (os/homedir) ".cloud-itonami" "capital-session.json"))
(defn read-session []
 (let [p (session-path)]
  (if (fs/existsSync p)
   (do (when (or (.isSymbolicLink (fs/lstatSync p)) (not (.isFile (fs/lstatSync p))) (> (.-size (fs/lstatSync p)) 16000)) (throw (js/Error. "Invalid session file"))) (when (not= 0 (bit-and (.-mode (fs/statSync p)) 63)) (throw (js/Error. "capital-session.json must have mode 0600")))
       (js->clj (js/JSON.parse (fs/readFileSync p "utf8")) :keywordize-keys true)) {})))
(defn save-session! [data]
 (let [p (session-path)] (fs/mkdirSync (path/dirname p) #js {:recursive true :mode 448})
  (when (and (fs/existsSync p) (.isSymbolicLink (fs/lstatSync p))) (throw (js/Error. "Refusing a symlink session file")))
  (let [tmp (str p "." (js/crypto.randomUUID))] (fs/writeFileSync tmp (js/JSON.stringify (clj->js data)) #js {:mode 384 :flag "wx"}) (fs/renameSync tmp p))))
(defn request! [route data]
 (let [s (read-session)]
  (-> (js/fetch (str origin route) (clj->js (cond-> {:method (if data "POST" "GET") :redirect "error" :headers {"origin" origin "content-type" "application/json" "cookie" (or (:cookie s) "")}}
      data (assoc :body (js/JSON.stringify (clj->js data))))))
      (.then (fn [response]
       (-> (.json response) (.then (fn [raw]
        (let [result (js->clj raw :keywordize-keys true)]
         (when-not (.-ok response) (throw (js/Error. (or (:error result) (str "HTTP " (.-status response))))))
         (when-let [cookies (seq (.getSetCookie (.-headers response)))]
          (save-session! (assoc s :cookie (str/join "; " (map #(first (str/split % #";")) cookies)))))
        (js->clj raw :keywordize-keys true)))))))))
)
(defn flags [args] (when (odd? (count args)) (throw (js/Error. "Each flag requires a value"))) (into {} (map (fn [[k v]] [(keyword (str/replace k #"^--" "")) v]) (partition 2 args))))
(defn run! [args]
 (let [[command & rest] args f (flags rest)]
  (case command
   "status" (request! (str "/api/capital?project=" (js/encodeURIComponent (:project f)) (when (:vault f) (str "&vault=" (js/encodeURIComponent (:vault f))))) nil)
   "challenge" (-> (request! "/api/auth/web3/challenge" {:address (:address f) :chainId 8453})
                   (.then (fn [result] (save-session! (assoc (read-session) :challenge (:id result))) (select-keys result [:id :message]))))
   "login" (-> (request! "/api/auth/web3/login" {:id (:challenge (read-session)) :signature (:signature f)}) (.then (fn [_] {:status "authenticated"})))
   "logout" (-> (request! "/api/auth/logout" {}) (.then (fn [_] (fs/rmSync (session-path) #js {:force true}) {:status "signed-out"})))
   "prepare" (let [input (js->clj (js/JSON.parse (fs/readFileSync (:data f) "utf8")) :keywordize-keys true)] (request! "/api/capital" input))
   "confirm" (request! "/api/capital" {:action "confirm" :id (:id f) :transactionHash (:transaction-hash f)})
   "open" (let [url (str origin "/ja/#capital=" (js/encodeURIComponent (:project f)) (when (:intent f) (str "&intent=" (js/encodeURIComponent (:intent f)))))]
            (cp/spawn "open" #js [url] #js {:stdio "ignore"}) (js/Promise.resolve {:url url}))
   (js/Promise.resolve {:usage ["itonami capital status --project org/repo" "itonami capital challenge --address 0x..." "itonami capital login --signature 0x..." "itonami capital prepare --data action.json" "itonami capital open --project org/repo --intent intent-id" "itonami capital confirm --id intent-id --transaction-hash 0x..." "itonami capital logout"] :signing "The CLI does not hold a wallet key or sign transactions. Sign the single-use login message and each financial transaction in your wallet."}))))
