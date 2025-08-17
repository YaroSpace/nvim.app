(ns nvim-app.components.pedestal.handlers
  (:require
   [nvim-app.db.core :as db]
   [nvim-app.db.repo :as repo]
   [nvim-app.views.repos :as repos]
   [nvim-app.views.news :as news]
   [nvim-app.views.about :as about]
   [nvim-app.views.not-found :as not-found]))
   ; [schema.core :as s]

(defn response
  ([status]
   (response status nil))

  ([status body & {:keys [session]}]
   (cond-> {:status status}
     body    (assoc :body body)
     session (assoc :session session))))

(def ok (partial response 200))

(defn not-found [context]
  (assoc context :response
         {:status 404
          :headers {"Content-Type" "text/html"}
          :body (not-found/index)}))

(def news-index
  {:name :news-index
   :enter
   (fn [context]
     (assoc context :response
            (ok (news/index))))})

(def about-index
  {:name :news-index
   :enter
   (fn [context]
     (assoc context :response
            (ok (about/index))))})

(def repos-page
  {:name :repos-page
   :enter
   (fn [{:keys [request] :as context}]
     (let [{:keys [accept query-params]} request
           {:keys [q category sort page limit] :or {page "1" limit "10"}} query-params

           page   (parse-long page)
           limit  (parse-long limit)
           offset (* (dec page) limit)

           matched (repo/search-repos q category sort offset limit)
           categories (map :name (db/select :categories))

           total  (int (Math/ceil (/ (or (:total (first matched)) 0) limit)))]

       (assoc context :response
              (response 200
                        (cond-> matched
                          (not= (:type accept) "application/json")
                          (repos/plugins-list (assoc query-params
                                                     :categories categories
                                                     :page page
                                                     :limit limit
                                                     :total total)))
                        :session {:params query-params}))))})

(def repos-index
  {:name :repos-index
   :enter (fn [{:keys [request] :as context}]
            (assoc context :response
                   (ok (repos/main
                        (get-in request [:session :params])))))})

