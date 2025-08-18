(ns nvim-app.components.pedestal.handlers
  (:require
   [nvim-app.components.app :as app]
   [nvim-app.db.core :as db]
   [nvim-app.db.repo :as repo]
   [nvim-app.views.repos :as repos]
   [nvim-app.views.news :as news]
   [nvim-app.views.about :as about]
   [nvim-app.views.not-found :as not-found]
   [nvim-app.utils :as u]))
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
     (let [{:keys [accept query-params session]} request
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
                        :session (merge session {:params query-params})))))})

(def repos-index
  {:name :repos-index
   :enter (fn [{:keys [request] :as context}]
            (assoc context :response
                   (ok (repos/main
                        (get-in request [:session :params])))))})

(def github-callback
  {:name :github-login
   :enter
   (fn [{:keys [request] :as context}]
     (let [session (:session request)
           user (or
                 (:user session)
                 (let [code (get-in request [:params :code])
                       {:keys [client-id client-secret token-url user-url]} (:github app/app-config)
                       token-response (u/fetch-request
                                       {:method :post :url token-url
                                        :accept :json :content-type :json
                                        :form-params {:client_id client-id
                                                      :client_secret client-secret
                                                      :code code}}
                                       :verbose true)
                       access-token (-> token-response :body :access_token)

                       user-response (u/fetch-request
                                      {:method :get :url user-url
                                       :headers {"Authorization" (str "token " access-token)}}
                                      :verbose true)]

                   (select-keys (:body user-response)
                                [:id :login :email :avatar_url :name])))]

       (assoc context :response {:status 302
                                 :headers {"Location" "/"}
                                 :session (assoc session :user user)})))})

