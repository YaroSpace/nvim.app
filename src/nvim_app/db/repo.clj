(ns nvim-app.db.repo
  (:require
   [nvim-app.db.core :as db]
   [honey.sql.pg-ops :refer [atat]]
   [clojure.string :as str]
   [clojure.tools.logging :as log]))

(defn repos-count []
  (:count (db/query-one! {:select [:%count.*] :from [:repos]})))

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
    (cond-> {:select [:* ;; [:categories.name :category]
                      [(repos-count) :total]]
             :from   [:repos]
             ; :join   [:categories [:= :plugins.category_id :categories.id]]
             ; :left-join [:github [:= :plugins.github_id :github.id]]
             :order-by (concat (order-by sort)
                               [[:name :asc]])}
      offset (assoc :offset offset)
      limit (assoc :limit limit)))))

(defn upsert-repo! [repo]
  (let [{:keys [name owner url description
                stars topics created updated]} repo]
    (db/query-one!
     {:insert-into :repos
      :values [{:name name
                :owner owner
                :repo (str owner "/" name)
                :url url
                :description (or description "")
                :stars stars
                :topics (str/join " " (or topics ""))
                :created created
                :updated updated}]

      :on-conflict [:repo]
      :do-update-set {:stars stars
                      :topics (str/join " " (or topics ""))
                      :updated updated
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
           {:select [:*] ;[:categories.name :category]]
            :from   [:repos]
            ;:join   [:categories [:= :plugins.category_id :categories.id]]
                    ;:github [:= :plugins.github_id :github.id]
            :where  [:or
                     [:% :repo q] [:% :description q] ;[:% :categories.name q]
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
                              [:similarity :repo q]]]])
                                    ;[:similarity :categories.name q]] :desc]]
                :limit limit
                :offset offset}))))))
