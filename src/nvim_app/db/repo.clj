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
        "stars_week" [[[:- :stars :stars_week] :desc]]
        "stars_month" [[[:- :stars :stars_month] :desc]]
        "updated" [[:updated :desc]]
        "created" [[:created :desc]])))

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
  (let [{:keys [name owner description archived
                stars stars_week stars_month
                topics created updated]} repo
        default-date (Timestamp/from (Instant/parse "1970-01-01T00:00:00Z"))]

    (db/query-one!
     (let [-description (or description "")
           -stars (or stars 0)
           -topics (str/join " " (or topics ""))
           -archived (or archived false)
           -created (or created default-date)
           -updated (or updated default-date)]

       {:insert-into :repos
        :values [(assoc repo  :repo (str owner "/" name)
                        :description -description
                        :stars -stars
                        :stars_week 10000
                        :stars_month 10000
                        :topics -topics
                        :archived -archived
                        :created -created
                        :updated -updated)]

        :on-conflict [:repo]
        :do-update-set (cond-> {:topics -topics
                                :updated -updated
                                :created -created
                                :archived -archived
                                :description -description
                                :stars -stars}
                         stars_week (assoc :stars_week stars_week)
                         stars_month (assoc :stars_week stars_month))}))))

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
                     [:ts_rank :tsv [:plainto_tsquery [:inline "english"] q]]
                     [:ts_rank :topics_tsv [:plainto_tsquery [:inline "english"] q]]
                     [:similarity :description q]
                     [:similarity :topics q]
                     [:similarity :repo q]
                     [:similarity :categories.name q]] :desc]])
       :limit limit
       :offset offset}))))
