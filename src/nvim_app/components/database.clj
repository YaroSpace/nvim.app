(ns nvim-app.components.database
  (:require
   [com.stuartsierra.component :as component]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [hikari-cp.core :refer [make-datasource]]
   [clojure.tools.logging :as log])

  (:import [com.zaxxer.hikari HikariDataSource]))

(defrecord DatabaseComponent [db-spec raw-ds datasource]
  component/Lifecycle

  (start [this]
    (log/info (str "Starting database on: " (:jdbc-url db-spec)))

    (if datasource
      this
      (let [^HikariDataSource ds (make-datasource db-spec)]
        (assoc this
               :raw-ds ds
               :datasource (jdbc/with-options ds {:builder-fn rs/as-unqualified-maps})))))

  (stop [this]
    (log/info "Stopping DatabaseComponent")

    (when raw-ds (.close ^HikariDataSource raw-ds))
    (assoc this :datasource nil)))

(defn new [config]
  (map->DatabaseComponent {:db-spec (:db-spec config)}))
