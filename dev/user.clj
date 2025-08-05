(ns user
  (:require
   [clojure.tools.namespace.repl :as repl]
   [spy.reader]
   [clojure.test :as test]
   [nrepl.core :as nrepl]
   [clojure.string :as str]
   [nvim-app.state])

  (:import
   [ch.qos.logback.classic Level]
   [org.slf4j LoggerFactory]))

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

(defn start-reveal []
  (require 'vlaaad.reveal)
  (add-tap ((resolve `vlaaad.reveal/ui))))

(defn p>
  ([data]
   (p> "\\n|\n" data))
  ([sep data]
   (doseq [line (str/split (str data) (re-pattern sep))]
     (println line))))

(defn tap> [data-or-label & label]
  (let [data (or (first label) data-or-label)]
    (when label (clojure.core/tap> data-or-label))
    (clojure.core/tap> data)
    (clojure.core/tap> "================================================>")
    data))

;; Require into all namespaces
(defn require-user-helpers []
  (doseq [ns-sym (all-ns)]
    (when (str/starts-with? (str ns-sym) "nvim-app")
      (intern ns-sym 'tap> tap>)
      (intern ns-sym 'p> p>))))

(comment
  (require-user-helpers)
  (start-reveal)
  (discover-test-namespaces)
  (refresh-and-test)
  (send-to-repl {:code "(+ 1 2)" :ns "user" :op "eval"} 7000)
  (set-log-level "com.zaxxer.hikari" :warn)

  nvim-app.state/app-system-atom)
