(ns nvim-app.components.pedestal.core
  (:require
   [nvim-app.components.pedestal.routes :as r]

   [com.stuartsierra.component :as component]
   [io.pedestal.http :as http]
   [io.pedestal.interceptor :as interceptor]
   [io.pedestal.http.content-negotiation :as content-negotiation]

   [clojure.tools.logging :as log]))

(def content-negotiation-interceptor
  (content-negotiation/negotiate-content ["application/json"]))

(def exception-interceptor
  (interceptor/interceptor
   {:name ::exception-handler
    :error (fn [context exception]
             (let [exception-type (-> exception class .getName)
                   exception-message (.getMessage exception)]

               (log/error :msg (str "Exception occurred: " exception-message))

               (assoc context :response
                      {:status 500
                       :headers {"Content-Type" "application/json"}
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
