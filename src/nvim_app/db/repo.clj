(ns nvim-app.db.repo
  (:require
   [nvim-app.db.core :as db]
   [honey.sql.pg-ops :refer [atat]]
   [clojure.string :as str]
   [clojure.tools.logging :as log])
  (:import
   [java.time Instant]
   [java.sql Timestamp]))

(defn order-by [sort]
  (if (str/blank? sort) []
      (case sort
        "stars" [[:stars :desc]]
        "updated" [[:updated :desc]]
        "created" [[:created :desc]])))

(defn get-repos
  ([]
   (get-repos nil nil nil))
  ([sort offset limit]
   (db/query!
    (cond-> {:select [:* [:repos.id :id] [:repos.name :name] [:categories.name :category]
                      [(db/count :repos) :total]]
             :from   [:repos]
             :left-join   [:categories [:= :repos.category_id :categories.id]]
             :order-by (concat (order-by sort)
                               [[:repos.name :asc]])}
      offset (assoc :offset offset)
      limit (assoc :limit limit)))))

(defn upsert-repo! [repo]
  (let [{:keys [name owner url description
                stars topics created updated]} repo
        default-date (Timestamp/from (Instant/parse "1970-01-01T00:00:00Z"))]

    (db/query-one!
     {:insert-into :repos
      :values [{:name name
                :owner owner
                :repo (str owner "/" name)
                :url url
                :description (or description "")
                :stars (or stars 0)
                :topics (str/join " " (or topics ""))
                :created (or created default-date)
                :updated (or updated default-date)}]

      :on-conflict [:repo]
      :do-update-set {:stars (or stars 0)
                      :topics (str/join " " (or topics ""))
                      :updated (or updated default-date)
                      :description (or description "")}})))

(defn upsert-repos! [repos]
  (let [result (for [repo repos]
                 (upsert-repo! repo))]
    (log/info "Updated" (count result) "Github repos in DB")))

(defn search-repos
  ([]
   (search-repos "" nil nil nil))
  ([q sort offset limit]
   (if (str/blank? q)
     (get-repos sort offset limit)
     (let [search-query
           {:select [:* [:repos.id :id] [:repos.name :name] [:categories.name :category]]
            :from   [:repos]
            :left-join   [:categories [:= :repos.category_id :categories.id]]
            :where  [:or
                     [:% :repo q] [:% :description q] [:% :categories.name q]
                     [atat :tsv [:plainto_tsquery [:inline "english"] q]]
                     [atat :topics_tsv [:plainto_tsquery [:inline "english"] q]]]}

           count (db/query-one! (merge search-query {:select [:%count.*]}))]

       (db/query!
        (merge search-query
               {:select (conj (:select search-query) [(:count count) :total])
                :order-by (concat
                           (order-by sort)
                           [[[:greatest
                              [:ts_rank :tsv [:plainto_tsquery [:inline "english"] q]]
                              [:ts_rank :topics_tsv [:plainto_tsquery [:inline "english"] q]]
                              [:similarity :description q]
                              [:similarity :topics q]
                              [:similarity :repo q]
                              [:similarity :categories.name q]] :desc]])
                :limit limit
                :offset offset}))))))
