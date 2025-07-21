(ns nvim-app.components.pedestal.core
  (:require
   [nvim-app.components.pedestal.routes :as r]
   [com.stuartsierra.component :as component]
   [io.pedestal.http :as http]
   [io.pedestal.interceptor :as interceptor]
   [io.pedestal.http.content-negotiation :as content-negotiation]
   [cheshire.core :as json]
   [clojure.tools.logging :as log]
   [clojure.string :as str]))

(def supported-types ["text/html"
                      "application/edn"
                      "application/json"
                      "text/plain"])
(def CSP-policy
  (str/join " "
            ["default-src 'self';"
             (str/join " " ["script-src 'self' 'unsafe-inline'"
                            "https://cdn.tailwindcss.com"
                            "https://github.com" "https://github.githubassets.com"
                            "https://cdn.jsdelivr.net;"])
             "style-src 'self' 'unsafe-inline'"]))

(def content-negotiation-interceptor
  (content-negotiation/negotiate-content supported-types))

(def csp-interceptor
  (interceptor/interceptor
   {:name ::csp
    :leave (fn [context]
             (update-in context [:response :headers]
                        merge {"Content-Security-Policy" CSP-policy}))}))

(defn accepted-type
  [context]
  (get-in context [:request :accept :field] "text/plain"))

(defn transform-content
  [body content-type]
  (case content-type
    "text/html" body
    "text/plain" body
    "application/edn" (pr-str body)
    "application/json" (json/encode body)))

(defn coerce-to
  [response content-type]
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

(def exception-interceptor
  (interceptor/interceptor
   {:name ::exception-handler
    :error (fn [context exception]
             (let [exception-type (-> exception class .getName)
                   exception-message (.getMessage exception)]

               (log/error :msg (str "Exception occurred: " exception-message))

               (tap> exception)
               (assoc context :response
                      {:status 500
                       :body {:error true
                              :exception-type exception-type
                              :message exception-message}})))}))

(defn get-inject-dependencies-interceptor
  [component]
  (interceptor/interceptor
   {:name ::inject-dependencies
    :enter (fn [context]
             (assoc context :dependencies component))}))

(defn service-map [config]
  {::http/routes r/routes
   ::http/resource-path "/public"
   ::http/type :jetty
   ::http/port (-> config :server :port)
   ::http/host (-> config :server :host)
   ::http/join? false})

(defrecord PedestalComponent [config]
  component/Lifecycle

  (start [this]
    (log/info (str "Starting Pedestal component on port: " (-> config :server :port)))
    (let [server (-> (service-map config)
                     (http/default-interceptors)
                     (update ::http/interceptors
                             concat [exception-interceptor
                                     (get-inject-dependencies-interceptor this)
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
  (->PedestalComponent {:server (:server config)}))
