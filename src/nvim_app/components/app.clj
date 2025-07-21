(ns nvim-app.components.app
  (:require
   [nvim-app.state :refer [app-system-atom]]
   [nvim-app.db.core :as db]
   [nvim-app.awesome :as awesome]
   [com.stuartsierra.component :as component]
   [clojure.tools.logging :as log]))

(defrecord App []
  component/Lifecycle

  (start [this]
    (log/info "Starting nvim-app")
    (reset! app-system-atom this)
    (db/run-migrations!)

    (when (db/db-empty?)
      (awesome/update-plugins!))
    this)

  (stop [this]
    (log/info "Stopping nvim-app")
    this))
