(ns nvim-app.components.pedestal.core
  (:require
   [nvim-app.state :refer [app-config dev?]]
   [nvim-app.db.core :as db]
   [nvim-app.components.pedestal.routes :as r]
   [nvim-app.components.pedestal.handlers :as h]
   [com.stuartsierra.component :as component]
   [io.pedestal.http :as http]
   [io.pedestal.interceptor :as interceptor]
   [io.pedestal.http.content-negotiation :as content-negotiation]
   [ring.middleware.session.cookie :refer [cookie-store]]
   [io.pedestal.http.ring-middlewares :as ring-middlewares]
   [io.pedestal.http.body-params :as body-params]
   [cheshire.core :as json]
   [cheshire.generate :refer [add-encoder encode-str]]
   [clojure.tools.logging :as log]
   [clojure.string :as str])
  (:import [org.postgresql.util PGobject]))

(add-encoder PGobject encode-str)

(def CSP-policy
  (str/join "; " ["default-src 'self'"
                  "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://www.googletagmanager.com 
                   https://*.googletagmanager.com"

                  "connect-src 'self' https://*.google-analytics.com https://*.analytics.google.com 
                   https://*.googletagmanager.com https://github.com"

                  "img-src 'self' https://*.google-analytics.com https://*.googletagmanager.com 
                   https://*.githubusercontent.com"
                  "style-src 'self' 'unsafe-inline'"]))

(def csp-interceptor
  (interceptor/interceptor
   {:name ::csp
    :leave (fn [context]
             (update-in context [:response :headers]
                        merge {"Content-Security-Policy" CSP-policy}))}))

(def supported-types ["text/html"
                      "application/edn"
                      "application/json"
                      "text/plain"])

(def content-negotiation-interceptor
  (content-negotiation/negotiate-content supported-types))

(defn accepted-type [context]
  (get-in context [:request :accept :field] "text/plain"))

(defn transform-content [body content-type]
  (case content-type
    "text/html" body
    "text/plain" body
    "application/edn" (pr-str body)
    "application/json" (json/encode body)))

(defn coerce-to [response content-type]
  (-> response
      (update :body transform-content content-type)
      (assoc-in [:headers "Content-Type"] content-type)))

(def coerce-body-interceptor
  (interceptor/interceptor
   {:name ::coerce-body
    :leave
    (fn [context]
      (cond-> context
        (nil? (get-in context [:response :headers "Content-Type"]))
        (update-in [:response] coerce-to (accepted-type context))))}))

(def not-found-interceptor
  {:name :not-found
   :leave (fn [context]
            (if (-> context :response :status)
              context
              (h/not-found context)))})

(def exception-interceptor
  (interceptor/interceptor
   {:name ::exception
    :error (fn [context exception]
             (let [exception-type (-> exception class .getName)
                   exception-message (.getMessage exception)]

               (log/error :msg (str "Exception occurred: " exception-message))
               (when dev?
                 (tap> exception))

               (assoc context :response
                      (cond-> {:status 500
                               :headers {"Content-Type" "application/json"}}
                        dev? (assoc :body
                                    {:error true
                                     :exception-type exception-type
                                     :message exception-message
                                     :exception exception})))))}))

(defn inject-dependencies-interceptor
  [component]
  (interceptor/interceptor
   {:name ::inject-dependencies
    :enter (fn [context]
             (assoc context :dependencies component))}))

(def auth-interceptor
  (interceptor/interceptor
   {:name ::inject-auth
    :enter (fn [{:keys [request] :as context}]
             (if-let [user (and (-> app-config :app :features :auth)
                                (-> request :session :user))]
               (assoc-in context [:request :user] (db/select-one :users :id user))
               context))}))

(defn service-map [config]
  {::http/routes r/routes
   ::http/resource-path "/public"
   ::http/type :jetty
   ::http/port (:port config)
   ::http/host (:host config)
   ::http/not-found-interceptor not-found-interceptor
   ::http/enable-session {:cookie-name "nvim-app-session"
                          :store (cookie-store (when (string? (:cookie-key config))
                                                 {:key (.getBytes (:cookie-key config))}))
                          :cookie-attrs {:max-age 2592000 :path "/" :http-only true :secure true}}
   ::http/join? false})

(defrecord PedestalComponent [config]
  component/Lifecycle

  (start [this]
    (log/info (str "Starting Pedestal component on port: " (:port config)))
    (let [server (-> (service-map config)
                     (http/default-interceptors)
                     (update ::http/interceptors
                             concat [exception-interceptor
                                     (inject-dependencies-interceptor this)
                                     auth-interceptor
                                     (ring-middlewares/flash)
                                     (body-params/body-params)
                                     coerce-body-interceptor
                                     content-negotiation-interceptor
                                     csp-interceptor])
                     (http/create-server)
                     (http/start))]
      (assoc this ::server server)))

  (stop [this]
    (log/info "Stopping PedestalComponent")

    (when-let [server (::server this)]
      (http/stop server)
      (assoc this ::server nil))))

(defn new
  [config]
  (->PedestalComponent (:server config)))
