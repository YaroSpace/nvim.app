(ns nvim-app.components.xtdb
  (:require
   [com.stuartsierra.component :as component]
   [next.jdbc :as jdbc]
   [xtdb.next.jdbc :as xt]
   [hikari-cp.core :refer [make-datasource]]
   [clojure.tools.logging :as log])
  (:import
   [com.zaxxer.hikari HikariDataSource]))

(defrecord XtdbComponent [config raw-ds datasource]
  component/Lifecycle

  (start [this]
    (log/info (str "Starting XTDB on: " (:jdbc-url config)))

    (if datasource
      this
      (try
        (let [^HikariDataSource ds (make-datasource config)
              ds-with-opts (jdbc/with-options ds {:builder-fn xt/builder-fn})]
          (try
            (assoc this :raw-ds ds :datasource ds-with-opts)
            (catch Exception e
              (.close ^HikariDataSource ds)
              (throw e))))

        (catch Exception e
          (throw (ex-info "Failed to start XTDB component" {:message (ex-message e)} e))))))

  (stop [this]
    (log/info "Stopping XTDB Component")
    (when raw-ds (.close ^HikariDataSource raw-ds)
          (assoc this :datasource nil))))

(defn new [config]
  (map->XtdbComponent {:config (:xtdb-spec config)}))
