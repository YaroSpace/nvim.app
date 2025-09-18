(ns nvim-app.db.core
  (:require
   [nvim-app.state :refer [app-config app-system-atom alter-in-app-config!]]
   [next.jdbc :as jdbc]
   [honey.sql :as sql]
   [migratus.core :as migratus]
   [clojure.tools.logging :as log]))

(defn get-ds []
  (:database-component @app-system-atom))

(defn query!
  ([sql] (query! (get-ds) sql jdbc/execute!))
  ([ds sql] (query! ds sql jdbc/execute!))
  ([ds sql cmd]
   (let [raw (volatile! nil)]
     (try
       (vreset! raw (sql/format sql))
       (let [result (cmd ds @raw)]
         (when (-> app-config :db-spec :logging?)
           (log/warn "Executed query: " @raw)
           (log/warn "Result: " result))
         result)
       (catch Exception e
         (let [raw (or @raw sql)]
           (throw (ex-info "Database query failed" {:query raw} e))))))))

(defn query-one!
  ([sql] (query-one! (get-ds) sql))
  ([ds sql]
   (query! ds sql jdbc/execute-one!)))

(defn select
  "
  Select rows from table. Optionally specify a key-value pair to filter results.
   Arguments:
    - `table` table name (keyword)
    - `key` column name (keyword) or :where for custom where clause (optional)
    - `value` value to match or where clause (optional)
   Returns a vector of rows.
  "
  [table & [key value]]
  (query!
   (cond-> {:select :* :from [table]}
     (and key (not= :where key)) (assoc :where [:= key value])
     (= :where key) (assoc :where value))))

(defn select-one
  "
  Select a single row from table. Optionally specify a key-value pair to filter results.
   Arguments:
    - `table` table name (keyword)
    - `key` column name (keyword) or :where for custom where clause (optional)
    - `value` value to match or where clause (optional)
   Returns a single row or nil if no match found.
  "
  [table & [key value]]
  (first (select table key value)))

(defn insert!
  "Insert rows into table. Optionally specify columns.
   Arguments:
    - `table` table name (keyword)
    - `:values` vector of values to insert (required)
    - `:columns` vector of column names (keywords) (optional)
   Returns inserted rows.
  "
  [table values & {:keys [columns]}]
  (query-one! (cond-> {:insert-into table
                       :values values
                       :returning :*}
                columns (assoc :columns columns))))

(defn update!
  "Update rows in table.
    Arguments:
      - `table` table name (keyword)
      - `:values` map of column-value pairs to set (required)
      - `:where` vector representing SQL WHERE clause (optional)
    Returns updated row.
  "
  [table values & {:keys [where]}]
  (query-one! (cond-> {:update table
                       :set values}
                where (assoc :where where))))

(defn run-migrations! [dc]
  (log/info "Running database migrations...")
  (let [config {:store :database
                :migration-dir "database/migrations"
                :db dc}]
    (try
      (migratus/migrate config)
      (catch Exception e
        (let [msg (ex-message e)]
          (throw (ex-info "Failed to run database migrations" {:message msg} e)))))))

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

(defn toggle-logging []
  (let [current (-> app-config :db-spec :logging?)]
    (alter-in-app-config! [:db-spec :logging?] (not current))
    (not current)))

(comment
  (toggle-logging)
  (migration-create! "app-table-create")
  (migration-up!)
  (migration-down!)
  (do
    (reset!!)
    (migration-up!))
  (map #(dissoc % :tsv :topics_tsv) (take 10 (select :repos)))
  (update! :users {:role "admin"} :where [:id 1])
  (select :categories)
  (select-one :app))
