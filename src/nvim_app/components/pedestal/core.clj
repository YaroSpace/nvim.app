(ns nvim-app.components.pedestal.core
  (:require
   [nvim-app.state :refer [dev?]]
   [nvim-app.db.core :as db]
   [nvim-app.components.pedestal.routes :as r]
   [nvim-app.components.pedestal.handlers :as h]
   [nvim-app.utils :refer [ex-format]]
   [com.stuartsierra.component :as component]
   [io.pedestal.http :as http]
   [io.pedestal.http.content-negotiation :as content-negotiation]
   [io.pedestal.http.body-params :as body-params]
   [io.pedestal.http.route :as route]
   [io.pedestal.http.ring-middlewares :as ring-middlewares]
   [io.pedestal.interceptor :as interceptor]
   [ring.middleware.session.cookie :refer [cookie-store]]
   [cheshire.core :as json]
   [cheshire.generate :refer [add-encoder encode-str]]
   [clojure.tools.logging :as log]
   [clojure.string :as str])
  (:import
   [org.postgresql.util PGobject]))

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
            (let [url-for-fn (delay (route/url-for-routes r/routes))]
              (if (-> context :response :status)
                context
                (with-bindings {#'route/*url-for* url-for-fn}
                  (h/not-found context)))))})

(def exception-interceptor
  (interceptor/interceptor
   {:name ::exception
    :error (fn [context exception]
             (let [ex-formatted (ex-format exception)]
               (if dev?
                 (tap> exception)
                 (log/error ex-formatted))

               (assoc context :response
                      (cond-> {:status 500
                               :headers {"Content-Type" "application/json"}}
                        dev? (assoc :body ex-formatted)))))}))

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
             (let [_ (:session request)]
               (if-let [user (-> request :session :user)]
                 (assoc-in context [:request :user] (db/select-one :users :id user))
                 context)))}))

(def request-interceptor
  ; add mode, view and query params from session if not present
  (interceptor/interceptor
   {:name ::enrich-request
    :enter (fn [{:keys [request] :as context}]
             (let [{:keys [session query-params]} request
                   query-params (or query-params (:query-params session))]
               (assoc context :request
                      (merge request
                             {:query-params query-params
                              :mode (:mode query-params (:mode session))
                              :view (:view query-params (:view session))}))))

    ; save mode, view and query params to session
    :leave (fn [{:keys [request response] :as context}]
             (let [{:keys [query-params session]} request]
               (assoc-in context [:response :session]
                         (merge session
                                (:session response)
                                {:query-params (or query-params (:query-params session))}
                                (select-keys request [:mode :view])))))}))
(defn cookie-params [config]
  {:cookie-name "nvim-app-session"
   :store (cookie-store (when (string? (:cookie-key config))
                          {:key (.getBytes (:cookie-key config))}))
   :cookie-attrs {:max-age 2592000 :path "/"
                  :http-only true :secure true}})

(defn service-map [config]
  {::http/routes r/routes
   ::http/resource-path "/public"
   ::http/type :jetty
   ::http/port (:port config)
   ::http/host (:host config)
   ::http/not-found-interceptor not-found-interceptor
   ::http/enable-session (cookie-params config)
   ::http/join? false})

(defrecord PedestalComponent [config]
  component/Lifecycle

  (start [this]
    (log/info (str "Starting Pedestal component on port: " (:port config)))
    (let [server (-> (service-map config)
                     (http/default-interceptors)
                     (update ::http/interceptors
                             #(concat [exception-interceptor] %
                                      [(inject-dependencies-interceptor this)
                                       auth-interceptor
                                       request-interceptor
                                       (ring-middlewares/flash)
                                       (body-params/body-params)
                                       coerce-body-interceptor
                                       content-negotiation-interceptor
                                       csp-interceptor]))
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
