(ns nvim-app.db.core
  (:require
   [nvim-app.state :refer [nvim-app-system-atom]]

   [next.jdbc :as jdbc]
   [honey.sql :as sql]
   [clojure.tools.logging :as log])

  (:import
   [org.flywaydb.core Flyway]))

(defn get-ds []
  (:database-component @nvim-app-system-atom))

(defn query!
  ([sql] (query! (get-ds) sql))
  ([ds sql] (jdbc/execute! ds (sql/format sql))))

(defn query-one!
  ([sql] (query-one! (get-ds) sql))
  ([ds sql] (jdbc/execute-one! ds (sql/format sql))))

(defn db-empty? []
  (not
   (seq (query! {:select :table_name
                 :from :information_schema.tables
                 :where [:and
                         [:= :table_type "BASE TABLE"]
                         [:= :table_schema "public"]
                         [:not= :table_name "schema_version"]]}))))

(defn run-migrations! []
  (log/info "Running database migrations...")
  (.migrate
   (.. (Flyway/configure)
       (dataSource (:raw-ds (get-ds)))
       (locations (into-array ["classpath:database/migrations"]))
       (table "schema_version")
       (load))))
