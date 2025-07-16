(ns nvim-app.components.pedestal.core
  (:require
   [nvim-app.components.pedestal.routes :as r]
   [nvim-app.components.pedestal.handlers :as h]

   [com.stuartsierra.component :as component]
   [io.pedestal.http :as http]
   [io.pedestal.interceptor :as interceptor]
   [io.pedestal.http.content-negotiation :as content-negotiation]

   [clojure.tools.logging :as log]))

(def content-negotiation-interceptor
  (content-negotiation/negotiate-content ["application/json"]))

(defn inject-dependencies
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
                          concat [h/exception-handler
                                  (inject-dependencies component)
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
