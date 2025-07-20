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
