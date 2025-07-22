(ns nvim-app.db.plugin
  (:require
   [nvim-app.db.core :as db]
   [honey.sql :as sql]
   [honey.sql.pg-ops :refer [atat]]
   [clojure.string :as str]))

(defn get-plugins
  ([]
   (get-plugins nil nil))
  ([offset limit]
   (db/query!
    (cond-> {:select [:* [:plugins.id :id] [:categories.name :category]]
             :from   [:plugins]
             :join   [:categories [:= :plugins.category_id :categories.id]]
             :order-by [[:categories.name :asc] [:plugins.repo :asc]]}
      offset (assoc :offset offset)
      limit (assoc :offset limit)))))

(defn get-or-create-category-id [category]
  (let [result (db/query-one! {:select :id
                               :from :categories
                               :where [:= :name category]})]
    (if (seq result)
      (:id result)
      (let [res (db/query-one! {:insert-into :categories
                                :columns [:name]
                                :values [[category]]
                                :returning :id})]
        (:id res)))))

(defn upsert-plugin! [{:keys [category repo url description]}]
  (let [category-id (get-or-create-category-id category)
        existing (db/query-one! {:select [:id]
                                 :from [:plugins]
                                 :where [:and
                                         [:= :repo repo]
                                         [:= :url url]]})]
    (if (seq existing)
      (db/query! {:update :plugins
                  :set {:description description
                        :category_id category-id}
                  :where [:= :id (:id existing)]
                  :returning :*})
      (db/query! {:insert-into :plugins
                  :columns [:category_id :repo :url :description]
                  :values [[category-id repo url description]]
                  :returning :*}))))

(defn upsert-plugins! [plugins]
  (doseq [plugin plugins]
    (upsert-plugin!  plugin)))

(defn plugins-count []
  (:count (db/query-one! {:select [:%count.*] :from [:plugins]})))

(defn search-plugins [query]
  (let [plugins (get-plugins)
        matches? #(str/includes? (str/lower-case %) (str/lower-case query))]

    (if (str/blank? query)
      plugins
      (filter #(or (matches? (:repo %)) (matches? (:description %)))
              plugins))))

(defn search-tsv-plugins [q offset limit]
  (if (str/blank? q)
    (get-plugins)
    (db/query!
     {:select   [:id :repo :description]
      :from     [:plugins]
      :where    [atat :tsv [:plainto_tsquery [:inline "english"] q]]
      :order-by [[[:ts_rank_cd :tsv [:plainto_tsquery [:inline "english"] q]] :desc]]
      :limit    limit
      :offset   offset})))

(defn search-trm-plugins [q offset limit]
  (db/query-one! [:raw  "SET pg_trgm.similarity_threshold = 0.18"])
  (if (str/blank? q)
    (get-plugins)
    (db/query!
     {:select [:plugins.id :categories.name :repo :url :description
               [[:ts_rank :tsv [:plainto_tsquery [:inline "english"] q]] :rank]
               [[:similarity :description q] :sim]]
      :from   [:plugins]
      :join   [:categories [:= :plugins.category_id :categories.id]]
      :where  [:or
               [:% :repo q] [:% :description q] [:% :categories.name q]
               [atat :tsv [:plainto_tsquery [:inline "english"] q]]]
      :order-by [[[:greatest
                   [:ts_rank :tsv [:plainto_tsquery [:inline "english"] q]]
                   [:similarity :description q]
                   [:similarity :categories.name q]] :desc]]
      :limit  limit
      :offset offset})))

(comment
   ; (let [q "q" offset 1 limit 1]
   ;   (sql/format))
  (search-trm-plugins "code actions" 1 3000)
  (db/query-one! [:raw  "SET pg_trgm.similarity_threshold = 0.20"])
  (search-plugins "")
  (take 10 (get-plugins))
  (plugins-count)
  (sql/format {:select [:%count.*]  :from [:plugins]})
  (some identity [false 2]))
