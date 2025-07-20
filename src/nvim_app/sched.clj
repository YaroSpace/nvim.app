(ns nvim-app.sched
  (:require [nvim-app.config :as config]
            [com.stuartsierra.component :as component]
            [clojure.tools.logging :as log]))

(defrecord ReplComponent [config]
  component/Lifecycle

  (start [this])

  (stop [this]))

(defn new-repl-component [config]
  (map->ReplComponent (:sched config)))
