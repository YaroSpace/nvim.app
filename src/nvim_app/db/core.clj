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
  ([sql] (query! (get-ds) sql jdbc/execute!))
  ([ds sql cmd]
   (let [raw (sql/format sql)]
     (try
       (cmd ds raw)
       (catch Exception e
         (let [error (ex-message e)]
           (log/error "Failed to execute query: " raw error)
           (tap> [raw e])
           (throw e)))))))

(defn query-one!
  ([sql] (query-one! (get-ds) sql))
  ([ds sql]
   (query! ds sql jdbc/execute-one!)))

(defn select [table & [key value]]
  (query!
   (cond-> {:select :* :from [table]}
     (and key (not= :where key)) (assoc :where [:= key value])
     (= :where key) (assoc :where value))))

(defn select-one [table & [key value]]
  (first (select table key value)))

(defn insert! [table & {:keys [columns values]}]
  (query-one! (cond-> {:insert-into table
                       :values values
                       :returning :*}
                columns (assoc :columns columns))))

(defn update! [table & {:keys [values where]}]
  (query-one!
   {:update table
    :set values
    :where (into [:=] where)}))

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

(defn get-migration-config []
  {:store :database
   :migration-dir "database/migrations"
   :db (:datasource (get-ds))})

(defn reset!! []
  (jdbc/execute! (get-ds) ["DROP SCHEMA public CASCADE"])
  (jdbc/execute! (get-ds) ["CREATE SCHEMA public"])
  (migratus.core/reset (get-migration-config)))

(defn migration-down! []
  (let [config (get-migration-config)]
    (migratus/rollback config)
    (migratus.core/pending-list config)))

(defn migration-up! []
  (let [config (get-migration-config)]
    (migratus/migrate config)
    (migratus.core/pending-list config)))

(defn migration-create! [name]
  (let [config (get-migration-config)]
    (migratus/create config name)
    (migratus.core/pending-list config)))

(comment
  (migration-create! "user-table-add-watched")
  (migration-up!)
  (migration-down!)
  (do
    (reset!!)
    (migration-up!)))
