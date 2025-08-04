(ns nvim-app.db.core
  (:require
   [nvim-app.state :refer [app-system-atom]]
   [next.jdbc :as jdbc]
   [honey.sql :as sql]
   [migratus.core :as migratus]
   [clojure.tools.logging :as log]))

(defn get-ds []
  (:database-component @app-system-atom))

(defn query!
  ([sql] (query! (get-ds) sql))
  ([ds sql]
   (try
     (jdbc/execute! ds (sql/format sql))
     (catch Exception e
       (let [error (ex-message e)]
         (log/error "Failed to execute query: " error)
         (throw e))))))

(defn query-one!
  ([sql] (query-one! (get-ds) sql))
  ([ds sql]
   (try
     (jdbc/execute-one! ds (sql/format sql))
     (catch Exception e
       (let [error (ex-message e)]
         (log/error "Failed to execute query: " error)
         (tap> e)
         (throw e))))))

(defn db-empty? []
  (empty? (query! {:select :table_name
                   :from :information_schema.tables
                   :where [:and
                           [:= :table_type "BASE TABLE"]
                           [:= :table_schema "public"]
                           [:not= :table_name "schema_version"]]})))

(defn run-migrations! [dc]
  (log/info "Running database migrations...")
  (let [config {:store :database
                :migration-dir "database/migrations"
                :db dc}]
    (try
      (migratus/migrate config)
      (catch Exception e
        (let [error (ex-message e)]
          (log/error "Failed to run database migrations: " error))))))

(defn reset!! [dc]
  (let [config {:store :database
                :migration-dir "database/migrations"
                :db dc}]
    (migratus.core/reset config)))
  ; (jdbc/execute! (get-ds) ["DROP SCHEMA public CASCADE"])
  ; (jdbc/execute! (get-ds) ["CREATE SCHEMA public"]))

(comment
  (let [config {:store :database
                :migration-dir "database/migrations"
                :db (:datasource (get-ds))}]
    ; (migratus/create config "create-github2-table"))
    ; (migratus/rollback config))
    (migratus/migrate config))
    ; (migratus.core/pending-list config))

  (reset!! (get-ds)))
