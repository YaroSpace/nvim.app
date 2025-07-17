(ns nvim-app.components.pedestal.core
  (:require
   [nvim-app.components.pedestal.routes :as r]

   [com.stuartsierra.component :as component]
   [io.pedestal.http :as http]
   [io.pedestal.interceptor :as interceptor]
   [io.pedestal.http.content-negotiation :as content-negotiation]
   [cheshire.core :as json]

   [clojure.tools.logging :as log]))

(def supported-types ["text/html"
                      "application/edn"
                      "application/json"
                      "text/plain"])

(def content-negotiation-interceptor
  (content-negotiation/negotiate-content supported-types))

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

(defrecord PedestalComponent [config]
  component/Lifecycle

  (start [component]
    (log/info "Starting PedestalComponent")

    (let [server (->
                  {::http/routes r/routes
                   ::http/type :jetty
                   ::http/port (-> config :server :port)
                   ::http/join? false}

                  (http/default-interceptors)
                  (update ::http/interceptors
                          concat [exception-interceptor
                                  (get-inject-dependencies-interceptor component)
                                  coerce-body-interceptor
                                  content-negotiation-interceptor])

                  (http/create-server)
                  (http/start))]
      (assoc component ::server server)))

  (stop [component]
    (log/info "Stopping PedestalComponent")

    (when-let [server (::server component)]
      (http/stop server)
      (assoc component ::server nil))))

(defn new-pedestal-component
  [config]
  (map->PedestalComponent {:config config}))
