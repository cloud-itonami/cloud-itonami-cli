#!/usr/bin/env nbb
;; The three files this CLI pins are produced by cloud-itonami-app, not here.
;;
;; `resources/cloud-itonami-app.commands.edn` is derived from that app's own
;; route table by its `route-scan`; `cli-aliases.edn` and `defaults.edn` are
;; the app's too. Splitting the front end out of the app made those copies,
;; and a copy of a generated file is the thing that drifts.
;;
;; So this answers one question -- are the pinned copies the ones the app
;; produces today -- with three outcomes rather than two:
;;
;;   exit 0  identical
;;   exit 1  drift, and it names which file and both digests
;;   exit 2  REFUSED: the app checkout was not found, so nothing was compared
;;
;; The third is the point. A checker that cannot find its input and says
;; nothing is a checker that reports a pass for every future drift. Refusing
;; to answer is worth more than an answer that was never computed.
(ns verify-pinned-surface
  (:require ["node:fs" :as fs]
            ["node:path" :as path]
            ["node:crypto" :as crypto]))

(def here (path/dirname (path/dirname (js/require.resolve "./verify-pinned-surface.cljs"))))

(def pinned
  ["cloud-itonami-app.commands.edn"
   "cloud-itonami-app.cli-aliases.edn"
   "cloud-itonami-app.defaults.edn"
   ;; Added 2026-09-07, after the splash rendered "vunknown" from this
   ;; repository. `bin/itonami` reads the version from this file and says
   ;; `unknown` out loud rather than guessing — which is right, and which meant
   ;; the missing file produced a correct-looking screen instead of an error.
   ;; The split missed it because the other three are named for the tables they
   ;; carry and this one is not.
   "cloud-itonami-version.edn"])

(defn- digest [p]
  (when (fs/existsSync p)
    (-> (crypto/createHash "sha256")
        (.update (fs/readFileSync p))
        (.digest "hex")
        (subs 0 16))))

;; An explicitly named app directory is authoritative: if `--app` or the
;; environment variable points somewhere without resources/, that is an
;; answer -- "the tree you named is not an app checkout" -- and falling back
;; to the sibling would turn a wrong path into a pass. Only the implicit
;; sibling default is allowed to be absent.
;;
;; Measured 2026-09-07: the first version of this function did fall back, and
;; the control that was supposed to prove REFUSED works returned exit 0.
(defn- app-directory []
  (let [argv (js->clj (.slice js/process.argv 2))
        flag (second (drop-while #(not= % "--app") argv))
        named (or flag (aget js/process.env "CLOUD_ITONAMI_APP_DIR"))
        chosen (or named (path/resolve here ".." "cloud-itonami-app"))]
    (when (fs/existsSync (path/join chosen "resources"))
      chosen)))

(let [app (app-directory)]
  (if-not app
    (do (println "REFUSED: no cloud-itonami-app checkout found.")
        (println "  Looked for a sibling ../cloud-itonami-app, $CLOUD_ITONAMI_APP_DIR, and --app <dir>.")
        (println "  Nothing was compared, so this is not a pass.")
        (js/process.exit 2))
    (let [rows (for [n pinned
                     :let [mine (digest (path/join here "resources" n))
                           theirs (digest (path/join app "resources" n))]]
                 {:name n :mine mine :theirs theirs :same? (and mine theirs (= mine theirs))})
          missing (remove #(and (:mine %) (:theirs %)) rows)
          drifted (filter #(and (:mine %) (:theirs %) (not (:same? %))) rows)]
      (println (str "app: " app))
      (doseq [{:keys [name mine theirs same?]} rows]
        (println (str "  " (if same? "same " "DIFF ") name
                      "  cli=" (or mine "-") "  app=" (or theirs "-"))))
      (cond
        (seq missing)
        (do (println (str "REFUSED: " (count missing) " file(s) could not be read on one side."))
            (js/process.exit 2))
        (seq drifted)
        (do (println (str "DRIFT: " (count drifted) " pinned file(s) differ from the app that generates them."))
            (println "  Refresh them from the app checkout, or say in the commit why the pin stays behind.")
            (js/process.exit 1))
        :else
        (do (println (str "OK: " (count rows) " pinned files match the app.")) (js/process.exit 0))))))
