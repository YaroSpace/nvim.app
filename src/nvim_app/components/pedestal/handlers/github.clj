(ns nvim-app.components.pedestal.handlers.github
  (:require
   [nvim-app.state :refer [app-config]]
   [nvim-app.db.user :as users]
   [nvim-app.specs :as specs]
   [nvim-app.components.pedestal.handlers.core :refer [redirect]]
   [nvim-app.utils :as u :refer [ex-format pretty-format]]
   [io.pedestal.http.route :as route :refer [url-for]]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.tools.logging :as log]))

(def github-login
  {:name :github-login
   :enter
   (fn [{:keys [request] :as context}]
     (if (:user request)
       (assoc context :response
              {:status 204})

       (let [{:keys [auth-url client-id redirect-uri scope]} (:github app-config)]
         (assoc context :response
                (redirect (str auth-url
                               "?client_id=" client-id
                               "&redirect_uri=" redirect-uri
                               "&scope=" scope))))))})
(def github-logout
  {:name :github-logout
   :enter
   (fn [{:keys [request] :as context}]
     (assoc context :response
            (redirect (url-for :home)
                      {:status 303
                       :session (assoc (:session request) :user nil)})))})

(defn get-access-token [code]
  (let [{:keys [client-id client-secret token-url]} (:github app-config)]
    (u/fetch-request
     {:method :post :url token-url
      :accept :json :content-type :json
      :form-params {:client_id client-id
                    :client_secret client-secret
                    :code code}}
     :throw-exceptions true)))

(defn get-user-info [access-token]
  (u/fetch-request
   {:method :get :url (-> (:github app-config) :user-url)
    :headers {"Authorization" (str "token " access-token)}}
   :throw-exceptions true))

(def github-callback
  {:name :github-login
   :enter
   (fn [{:keys [request] :as context}]
     (let [session (:session request)
           code (get-in request [:params :code])]
       (try
         (let [token-response (get-access-token code)
               access-token (-> token-response :body :access_token)
               user-response (when access-token (get-user-info access-token))

               {:keys [id login email name html_url avatar_url]} (:body user-response)

               user (users/get-or-create! {:github_id id
                                           :username login
                                           :email email
                                           :name name
                                           :url html_url
                                           :avatar_url avatar_url})]

           (assoc context
                  :request (dissoc request :query-params) ; to avoid reseting query params 
                  :response (redirect (url-for :home)
                                      {:session (assoc session :user (:id user))
                                       :flash {:success
                                               {:title "Login Successful"
                                                :message (format "Welcome, %s!" (:name user))}}})))
         (catch Exception e
           ; TODO: use slingshot and catch only HTTP and validation exceptions
           (log/errorf "Error during GitHub OAuth: %s" (ex-format e))
           (let [response (ex-data e)
                 errors (->>
                         (:errors response {:message (ex-message e)})
                         (vals) (remove nil?) (str/join ":"))]

             (assoc context
                    :request (dissoc request :query-params)
                    :response (redirect (url-for :home)
                                        {:session (assoc session :user nil)
                                         :flash {:error
                                                 {:title "Errors logging in"
                                                  :message errors}}})))))))})
