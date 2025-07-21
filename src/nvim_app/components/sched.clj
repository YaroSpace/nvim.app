(ns nvim-app.components.sched
  (:require [nvim-app.config :as config]
            [com.stuartsierra.component :as component]
            [clojure.tools.logging :as log]))

(defrecord SchedComponent [config]
  component/Lifecycle

  (start [this])

  (stop [this]))
