(ns user
  (:require
   [nrepl.core :as nrepl]
   [clojure.tools.namespace.repl :as repl]
   [rebel-readline.clojure.main :as main]
   [clj-commons.pretty.repl :as pretty-repl]
   [clj-commons.format.exceptions :as pretty]
   [puget.printer :as puget]
   [hashp.preload]
   [hashp.config]
   [user.java :refer :all]
   [clojure.test :as test]
   [clojure.tools.logging :as log]
   [clojure.string :as str])
   ; [kaocha.repl :as k]

  (:import
   [ch.qos.logback.classic Level]
   [org.slf4j LoggerFactory]))

(defn hashp-setup []
  (alter-var-root #'hashp.config/*hashp-output* (constantly *out*)))

(defn pretty-exceptions []
  (pretty-repl/install-pretty-exceptions))

(defn ex-format [e]
  (pretty/format-exception e))

(defn patch-rebel-readline []
  (println "Patching rebel-readline to use puget for syntax highlighting")
  (alter-var-root #'main/syntax-highlight-prn
                  (fn [_]
                    (fn [value]
                      (puget/cprint value)))))

(defn test-namespaces []
  ['component.real-world-clojure-api.info-handler-test])

(defn discover-test-namespaces []
  (->> (all-ns)
       (map ns-name)
       (filter #(str/includes? % "-test"))
       (filter #(str/includes? % "nvim-app"))
       (filter #(str/includes? % "unit"))
       (map symbol)))

(defn run-tests []
  (apply test/run-tests (discover-test-namespaces)))
  ; (k/run :unit)
  ; (k/run :integration))

(defn refresh-and-test []
  (repl/refresh :after 'user/run-tests))

(defn system-reset []
  (try
    (when-not (find-ns 'dev) (require 'dev))
    ((resolve 'dev/reset))
    (catch Exception _)))

(defn reset []
  (repl/refresh)
  (hashp-setup)
  (system-reset))

(defn set-log-level! [logger-name level]
  (let [logger (LoggerFactory/getLogger logger-name)]
    (.setLevel ^ch.qos.logback.classic.Logger logger (Level/valueOf (name level)))))

(defn get-methods [obj]
  {:type (type obj)
   :class-methods (map #(.getName %) (.getMethods (class obj)))})

(defn send-to-repl [data port]
  (with-open [conn (nrepl/connect :port port)]
    (let [client (nrepl/client conn 1000)]
      (first (nrepl/message client data)))))

(def cp> puget.printer/cprint)

(defn pn>
  "Print and strip newlines"
  ([data]
   (pn> "\\n|\n" data))
  ([sep data]
   (doseq [line (str/split (str data) (re-pattern sep))]
     (println line))))

(def *tap nil)

(defn tap>>
  "Tap with optinal label and save last tapped value to *tap"
  [data-or-label & [data]]

  (let [data* (or data data-or-label)]
    (when data (clojure.core/tap> data-or-label))
    (clojure.core/tap> data*)
    (clojure.core/tap> "================================================>")
    (alter-var-root #'*tap (constantly data*))
    data*))

(defn portal-start []
  (require '[portal.api :as inspect])
  (comment
    ;; Open a portal inspector window
    (inspect/open)
    ;; Add portal as a tap> target over nREPL connection
    (add-tap inspect/submit)
    ;; Clear all values in the portal inspector window
    (inspect/clear)
    ;; Close the inspector
    (inspect/close)))

;; Require into all namespaces
(defn require-user-helpers []
  (doseq [ns-sym (all-ns)]
    (when (str/starts-with? (str ns-sym) "nvim-app")
      (intern ns-sym 'tap> tap>)
      (intern ns-sym 'p> pn>))))

(def pretty-exceptions-tap-handler
  (fn [x]
    (when (instance? Throwable x)
      (log/fatal (ex-format x)))))

(defn setup-user []
  (repl/disable-reload! (the-ns 'user))
  ; (repl/disable-reload! (the-ns 'dev))

  (patch-rebel-readline)
  (hashp-setup)
  ; (portal-start))

  ; (remove-tap pretty-exceptions-tap-handler))
  (add-tap pretty-exceptions-tap-handler))

(setup-user)

(comment
  (log/fatal (ex-format *e))
  (print-java-members java.sql.Timestamp :public-only true)
  (java-members-apropos "modifi" java.io.File :public-only true)
  (discover-test-namespaces)
  (refresh-and-test)
  (send-to-repl {:code "(+ 1 2)" :ns "user" :op "eval"} 7000)
  (set-log-level! "com.zaxxer.hikari" :warn))
