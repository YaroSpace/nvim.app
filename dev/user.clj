(ns user
  (:require
   [clojure.tools.namespace.repl :as repl]
   [spy.reader]

   [clojure.test :as test]
   [nrepl.core :as nrepl])

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
      (first (nrepl/message client {:op data})))))

(comment
  (discover-test-namespaces)
  (refresh-and-test)
  (set-log-level "com.zaxxer.hikari" :warn))
