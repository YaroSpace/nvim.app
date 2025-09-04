(ns nvim-app.components.database
  (:require
   [nvim-app.db.core :as db]
   [com.stuartsierra.component :as component]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [hikari-cp.core :refer [make-datasource]]
   [clojure.tools.logging :as log])
  (:import
   [java.sql Array]
   [java.time LocalTime]
   [java.time.format  DateTimeFormatter]
   [com.zaxxer.hikari HikariDataSource]))

(extend-protocol rs/ReadableColumn
  Array
  (read-column-by-label [^Array v _]    (vec (.getArray v)))
  (read-column-by-index [^Array v _ _]  (vec (.getArray v)))

  org.postgresql.util.PGobject
  (read-column-by-label [^org.postgresql.util.PGobject v _]
    (.getValue v))
  (read-column-by-index [^org.postgresql.util.PGobject v _ _]
    (.getValue v)))

(defn db-logger
  ([_ params]
   (let [formatter (DateTimeFormatter/ofPattern "HH:mm:ss")]
     (log/info (str (.format (LocalTime/now) formatter) ": " params))
     (System/currentTimeMillis)))

  ([_ state result]
   (when-not (instance? Throwable result)
     (let [out (if (map? result) result (count result))]
       (log/info "Duration: " (/ (- (System/currentTimeMillis) state) 1000) "(s):  => " out)))))

(defrecord DatabaseComponent [config raw-ds datasource]
  component/Lifecycle

  (start [this]
    (log/info (str "Starting database on: " (:jdbc-url config)))

    (if datasource
      this
      (try
        (let [^HikariDataSource ds (make-datasource config)
              ds-with-opts (jdbc/with-options ds {:builder-fn rs/as-unqualified-maps})]

          (db/run-migrations! {:datasource ds})
          (db/query-one! ds [:raw "SET pg_trgm.similarity_threshold = 0.16"])
          (assoc this
                 :raw-ds ds
                 :datasource (if (:logging? config)
                               (jdbc/with-logging ds-with-opts db-logger db-logger)
                               ds-with-opts)))
        (catch Exception e
          (throw (ex-info "Failed to start database component" e))))))

  (stop [this]
    (log/info "Stopping DatabaseComponent")

    (when raw-ds (.close ^HikariDataSource raw-ds))
    (assoc this :datasource nil)))

(defn new [config]
  (map->DatabaseComponent {:config (:db-spec config)}))
