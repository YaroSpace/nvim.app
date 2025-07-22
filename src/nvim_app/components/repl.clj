(ns nvim-app.components.repl
  (:require [nvim-app.config :as config]
            [com.stuartsierra.component :as component]
            [nrepl.server :as nrepl]
            [clojure.tools.logging :as log]))

(defrecord ReplComponent [config server]
  component/Lifecycle

  (start [this]
    (if server
      this
      (let [config (:repl (config/read-config))]
        (if (:enable config)
          (let [repl (nrepl/start-server :bind "0.0.0.0" :port (:port config))]
            (log/info (str "Starting REPL server on port: " (:server-socket repl)))
            (assoc this :server repl))
          {:disabled true}))))

  (stop [this]
    (when server
      (log/info "Stopping REPL")
      (nrepl/stop-server server)
      (assoc this :server nil))))

(defn new [config]
  (map->ReplComponent (:repl config)))
