(ns nvim-app.db.plugin
  (:require
   [nvim-app.db.core :as db]
   [honey.sql :as sql]
   [clojure.string :as str]))

(defn get-plugins []
  (db/query! {:select [:* [:plugins.id :id] [:categories.name :category]]
              :from   [:plugins]
              :join   [:categories [:= :plugins.category_id :categories.id]]
              :order-by [[:categories.name :asc] [:plugins.repo :asc]]}))

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

(comment
  (search-plugins "dev-")
  (take 10 (get-plugins))
  (plugins-count)
  (sql/format {:select [:%count.*]  :from [:plugins]})
  (some identity [false 2]))
