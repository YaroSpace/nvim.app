(ns nvim-app.db.core
  (:require
   [nvim-app.state :refer [app-config app-system-atom]]
   [next.jdbc :as jdbc]
   [honey.sql :as sql]
   [migratus.core :as migratus]
   [clojure.tools.logging :as log]))

(defn get-ds []
  (:database-component @app-system-atom))

(defn query!
  ([sql] (query! (get-ds) sql jdbc/execute!))
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

; TODO: add exception handling

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
    :where where}))

(defn run-migrations! [dc]
  (log/info "Running database migrations...")
  (let [config {:store :database
                :migration-dir "database/migrations"
                :db dc}]
    (try
      (migratus/migrate config)
      (catch Exception e
        (throw (ex-info "Failed to run database migrations" e))))))

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
  (alter-var-root #'app-config update-in [:db-spec :logging?] not)
  (:logging? (:db-spec app-config)))

(comment
  (toggle-logging)
  (migration-create! "app-table-create")
  (migration-up!)
  (migration-down!)
  (do
    (reset!!)
    (migration-up!))
  (map #(dissoc % :tsv :topics_tsv) (take 10 (select :repos)))
  (update! :users :values {:role "admin"} :where [:id 1])
  (select :categories)
  (select-one :app)
  (insert! :app :values [{:data [:lift {:test "value"}]}]))
