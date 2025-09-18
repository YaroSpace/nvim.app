(ns nvim-app.components.pedestal.handlers.repos
  (:require
   [nvim-app.db.core :as db]
   [nvim-app.db.repo :as repo]
   [nvim-app.db.user :as users]
   [nvim-app.specs :as specs]
   [nvim-app.components.pedestal.handlers.core :refer [ok redirect invalid-params]]
   [nvim-app.components.pedestal.views.repos :as repos]
   [nvim-app.components.pedestal.views.preview :as preview]
   [nvim-app.utils :as u]
   [io.pedestal.http.route :refer [url-for]]
   [clojure.spec.alpha :as s]))

(def repos-index
  {:name :repos-index
   :enter (fn [{:keys [request] :as context}]
            (let [{:keys [query-params]} request]
              (assoc context :response
                     {:status 200
                      :body (repos/main request query-params)})))})

(def repo-update
  {:name :repo-update
   :enter (fn [{:keys [request] :as context}]
            (let [{:keys [form-params]} request
                  {:keys [repo category-edit description-edit hidden-edit]} form-params

                  repo-old (db/select-one :repos :repo repo)
                  repo-new (merge
                            {:description description-edit
                             :hidden (= "true" hidden-edit)
                             :url (:url repo-old)
                             :dirty true}
                            (when-let [category_id (:id (db/select-one
                                                         :categories :name category-edit))]
                              {:category_id category_id}))

                  result (when (and (specs/conform! ::specs/repo repo-new)
                                    (not (every? (fn [[k v]] (= v (get repo-old k))) repo-new)))
                           (db/update! :repos repo-new
                                       :where [:= :repo repo]))]

              (assoc context :response
                     (redirect (url-for :repos-page :params form-params)
                               {:status 303
                                :flash (when result
                                         {:success {:title "Saved"
                                                    :message "Plugin data updated"}})}))))})

(def repos-page
  {:name :repos-page
   :enter
   (fn [{:keys [request] :as context}]
     (let [{:keys [query-params accept user]} request
           conformed-params (s/conform ::specs/repos-page-params query-params)]

       (if (s/invalid? conformed-params)
         (invalid-params context (str "Invalid query parameters: "
                                      (specs/format-problems
                                       ::specs/repos-page-params query-params)))

         (let [{:keys [q category sort page limit]
                :or {page 1 limit 10}} conformed-params

               offset (* (dec page) limit)
               matched (repo/search-repos q category sort offset limit user)
               categories (into (sorted-set) (map :name (db/select :categories)))
               total  (int (Math/ceil (/ (:total (first matched) 1) limit)))]

           (assoc context :response
                  {:status  200
                   :body (if (= (:field accept) "application/json")
                           matched
                           (repos/plugins-list request
                                               matched
                                               (assoc query-params
                                                      :categories categories
                                                      :total total
                                                      :page page
                                                      :limit limit)))})))))})

(def user-watch-toggle
  {:name :watch-toggle
   :enter
   (fn [{:keys [request] :as context}]
     (let [{:keys [user form-params]} request]
       (when-let [repo (and user (:repo form-params))]
         (users/toggle-watched! (:id user) repo))

       (assoc context :response
              (redirect (url-for :repos-page :params form-params)
                        {:status 303}))))})

(def preview
  {:name :preview
   :enter
   (fn [{:keys [request] :as context}]
     (let [id (specs/conform! :repo/id (-> request :query-params :id))]
       (if (u/make-preview id)
         (assoc context :response
                (ok (preview/index id)))
         (assoc context :response
                {:status 404}))))})
