(ns capital-session-test (:require [cloud.itonami.capital-cli :as capital] ["node:fs" :as fs] ["node:os" :as os] ["node:path" :as path] ["node:assert/strict" :as assert]))
(let [dir (fs/mkdtempSync (path/join (os/tmpdir) "itonami-capital-cli-test-")) p (path/join dir "session.json") victim (path/join dir "victim")]
 (try
  (with-redefs [capital/session-path (fn [] p)]
   (capital/save-session! {:cookie "fixture-only"})
   (assert/equal (bit-and (.-mode (fs/statSync p)) 511) 384)
   (assert/equal (:cookie (capital/read-session)) "fixture-only")
   (fs/chmodSync p 420) (assert/throws #(capital/read-session))
   (fs/unlinkSync p) (fs/writeFileSync victim "preserve") (fs/symlinkSync victim p)
   (assert/throws #(capital/read-session)) (assert/throws #(capital/save-session! {:cookie "no"}))
   (assert/equal (fs/readFileSync victim "utf8") "preserve")
   (assert/throws #(capital/flags ["--project"]))
   (println "PASS owner-only session, unsafe file rejection, no symlink overwrite, missing argument rejection"))
  (finally (fs/rmSync dir #js {:recursive true :force true}))))
