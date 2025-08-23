(ns nvim-app.components.pedestal.handlers
  (:require
   [nvim-app.components.app :as app]
   [nvim-app.db.core :as db]
   [nvim-app.db.repo :as repo]
   [nvim-app.db.user :as users]
   [nvim-app.views.repos :as repos]
   [nvim-app.views.news :as news]
   [nvim-app.views.about :as about]
   [nvim-app.views.not-found :as not-found]
   [nvim-app.utils :as u]
   [io.pedestal.http.route :as route]))
   ; [schema.core :as s]

(defn response
  ([status]
   (response status nil))

  ([status body & response]
   (cond-> {:status status}
     body    (assoc :body body)
     response (merge response))))

(def ok (partial response 200))

(defn redirect [location & [response]]
  (cond-> {:status 302
           :headers {"Location" location}}
    response (merge response)))

(defn not-found [context]
  (assoc context :response
         {:status 404
          :headers {"Content-Type" "text/html"}
          :body (not-found/index)}))

(def news-index
  {:name :news-index
   :enter
   (fn [{:keys [request] :as context}]
     (assoc context :response
            (ok (news/index request))))})

(def about-index
  {:name :news-index
   :enter
   (fn [{:keys [request] :as context}]
     (assoc context :response
            (ok (about/index request))))})

(def repos-page
  {:name :repos-page
   :enter
   (fn [{:keys [request] :as context}]
     (let [{:keys [query-params session accept user]} request
           {:keys [q category sort page limit] :or {page "1" limit "10"}} query-params

           page   (parse-long page)
           limit  (parse-long limit)
           offset (* (dec page) limit)

           matched (repo/search-repos q category sort offset limit user)
           categories (map :name (db/select :categories))

           total  (int (Math/ceil (/ (or (:total (first matched)) 0) limit)))]

       (assoc context :response
              {:status  200
               :body (cond
                       (= (:field accept) "application/json") matched
                       :else (repos/plugins-list request
                                                 matched
                                                 (assoc query-params
                                                        :categories categories
                                                        :page page
                                                        :limit limit
                                                        :total total)))
               :session (assoc session :params query-params)})))})

(def repos-index
  {:name :repos-index
   :enter (fn [{:keys [request] :as context}]
            (assoc context :response
                   {:status 200
                    :body (repos/main request (-> request :session :params))}))})

(def github-login
  {:name :github-login
   :enter
   (fn [{:keys [request] :as context}]
     (if (:user request)
       (assoc context :response
              {:status 204})

       (let [{:keys [auth-url client-id redirect-uri scope]} (:github app/app-config)]
         (assoc context :response
                (redirect (str auth-url
                               "?client_id=" client-id
                               "&redirect_uri=" redirect-uri
                               "&scope=" scope))))))})

(def github-callback
  {:name :github-login
   :enter
   (fn [{:keys [request] :as context}]
     (let [session (:session request)
           code (get-in request [:params :code])
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
                          :verbose true)

           {:keys [id login email name html_url avatar_url]} (:body user-response)

           user (or (db/select-one :users
                                   :where [:= :github_id id])
                    (db/insert! :users
                                :values [{:github_id id
                                          :username login
                                          :email email
                                          :name name
                                          :url html_url
                                          :avatar_url avatar_url}]))]

       (assoc context :response
              (redirect "/" {:session (assoc session :user
                                             (:id user))}))))})

; TODO: add error handling for auth calls

(def user-watch-toggle
  {:name :watch-toggle
   :enter
   (fn [{:keys [request] :as context}]
     (let [{:keys [user query-params]} request]
       (when-let [repo  (and user (:repo query-params))]
         (users/toggle-watched! (:id user) repo))

       (assoc context :response
              (redirect (route/url-for :repos-page
                                       :params query-params)
                        {:status 303}))))})

(comment
  "
```http
http://localhost:6080/repos-page
Accept: application/json

```
")

