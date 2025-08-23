(ns user
  (:require
   [spy.reader]
   [nrepl.core :as nrepl]
   [clojure.repl :as t]
   [clojure.tools.namespace.repl :as repl]
   [clojure.test :as test]
   [clojure.string :as str]
   [rebel-readline.clojure.main :as main]
   [puget.printer :as puget]
   [hashp.preload]
   [user.java :refer :all])

  (:import
   [ch.qos.logback.classic Level]
   [org.slf4j LoggerFactory]))

(defn hashp-setup []
  (alter-var-root #'hashp.config/*hashp-output* (constantly *out*)))

(hashp-setup)

(defn patch-rebel-readline []
  (println "Patching rebel-readline to use puget for syntax highlighting")
  (alter-var-root #'main/syntax-highlight-prn
                  (fn [_]
                    (fn [value]
                      (puget/pprint value)))))

(patch-rebel-readline)

(defn test-namespaces []
  ['component.real-world-clojure-api.info-handler-test])

(defn discover-test-namespaces []
  (->> (all-ns)
       (map ns-name)
       (filter #(re-find #"-test$" (str %)))
       (map symbol)))

(defn run-tests []
  (apply test/run-tests (discover-test-namespaces)))

(defn refresh []
  (repl/refresh))

(defn refresh-and-test []
  (repl/refresh :after 'user/run-tests))

(defn reset []
  (when-not (find-ns 'dev) (require 'dev))
  ((resolve 'dev/reset)))

(defn set-log-level [logger-name level]
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
  ([data]
   (pn> "\\n|\n" data))
  ([sep data]
   (doseq [line (str/split (str data) (re-pattern sep))]
     (println line))))

(defn tap>> [data-or-label & [data]]
  (let [data (or data data-or-label)]
    (when data (clojure.core/tap> data-or-label))
    (clojure.core/tap> data)
    (clojure.core/tap> "================================================>")
    data))

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

(comment
  (print-java-members `java.sql.Timestamp :public-only true)
  (discover-test-namespaces)
  (refresh-and-test)
  (send-to-repl {:code "(+ 1 2)" :ns "user" :op "eval"} 7000)
  (set-log-level "com.zaxxer.hikari" :warn))
