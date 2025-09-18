(ns nvim-app.db.repo
  (:require
   [nvim-app.db.core :as db]
   [nvim-app.specs :as specs]
   [honey.sql.pg-ops :refer [atat]]
   [clojure.string :as str]
   [clojure.tools.logging :as log]))

(defn order-by [sort]
  (if (str/blank? sort) []
      (case sort
        "name" []
        "stars" [[:stars :desc]]
        "stars_week" [[[:- :stars :stars_week] :desc]]
        "stars_month" [[[:- :stars :stars_month] :desc]]
        "updated" [[:updated :desc]]
        "created" [[:created :desc]]
        [[:stars :desc]])))

(defn user-watched [user]
  (let [watched (:watched (db/select-one :users :id (:id user)))]
    [:= :repo [:any (into-array String watched)]]))

(defn filter-by-category [user category]
  (cond
    (= "archived" category) [:= :archived true]
    (and (= "watched" category) user) (user-watched user)
    :else [:= :categories.name category]))

(defn get-repos
  ([]
   (get-repos nil nil nil nil))
  ([category sort offset limit & [user]]
   (db/query!
    (cond-> {:select [:* [:repos.id :id] [:repos.name :name] [:categories.name :category]
                      [[:over [[:count :*] {} :total]]]
                      (when user [(user-watched user) :watched])]
             :from   [:repos]
             :left-join   [:categories [:= :repos.category_id :categories.id]]
             :order-by (concat (order-by sort)
                               [[:repos.name :asc]])}

      (seq category) (assoc :where (filter-by-category user category))
      offset (assoc :offset offset)
      limit (assoc :limit limit)))))

(defn upsert-repo! [repo]
  (let [conformed-repo (specs/conform! ::specs/repo repo)]
    (db/query-one!
     {:insert-into :repos
      :values [conformed-repo]
      :on-conflict [:repo]
      :do-update-set (select-keys conformed-repo
                                  [:description :topics
                                   :stars :stars_week :stars_month
                                   :updated :archived])})))

(defn upsert-repos! [repos]
  (let [result (for [repo repos]
                 (upsert-repo! repo))]
    (log/info "Updated" (count result) "Github repos in DB")))

(defn search-repos
  ([]
   (search-repos "" nil nil nil nil))
  ([q category sort offset limit & [user]]
   (if (str/blank? q)
     (get-repos category sort offset limit user)
     (db/query!
      {:select [:* [:repos.id :id] [:repos.name :name] [:categories.name :category]
                [[:over [[:count :*] {} :total]]]
                (when user [(user-watched user) :watched])]
       :from   [:repos]
       :left-join   [:categories [:= :repos.category_id :categories.id]]

       :where
       [:and (if (seq category) (filter-by-category user category) true)
        [:or
         [:% :repo q] [:% :description q] [:% :categories.name q]
         [atat :tsv [:plainto_tsquery [:inline "english"] q]]
         [atat :topics_tsv [:plainto_tsquery [:inline "english"] q]]]]

       :order-by (concat
                  (order-by sort)
                  [[[:greatest
                     [:similarity :repo q]
                     [:similarity :description q]
                     [:similarity :topics q]
                     [:similarity :categories.name q]
                     [:ts_rank :tsv [:plainto_tsquery [:inline "english"] q]]
                     [:ts_rank :topics_tsv [:plainto_tsquery [:inline "english"] q]]]
                    :desc]])
       :limit limit
       :offset offset}))))

(defn with-duplicate-urls []
  (let [url-regex "https?://github.com/([^/]+/[^/]+).*"]
    (->>
     (db/query!
      {:select [[:r1.id :id1] [:r2.id :id2]]
       :from [[:repos :r1]]
       :join [[:repos :r2]
              [:and
               [:< :r1.id :r2.id]
               [:=
                [:lower [:regexp_replace :r1.url url-regex "\\1"]]
                [:lower [:regexp_replace :r2.url url-regex "\\1"]]]]]})
     (mapcat vals))))

(defn with-duplicate-names []
  (->>
   (db/query!
    {:select [:r1.id]
     :from [[:repos :r1]]
     :join [[:repos :r2]
            [:and
             [:!= :r1.id :r2.id]
             [:= :r1.name :r2.name]
             [:= :r1.stars 0] [:= :r1.topics "awesome"]]]})
   (map :id)))

(defn renamed-repos []
  (->>
   (db/query!
    {:select [:id]
     :from [:repos]
     :where  [:and [:ilike :url "%github.com%"]
              [:= :stars 0] [:= :topics "awesome"]]})
   (map :id)))
