(ns nvim-app.components.app
  (:require
   [nvim-app.state :refer [app-system-atom]]
   [nvim-app.github :as github]
   [com.stuartsierra.component :as component]
   [clojure.tools.logging :as log]))

(defrecord App [config]
  component/Lifecycle

  (start [this]
    (log/info "Starting nvim-app")
    (reset! app-system-atom this)

    (when (:update-on-start? config)
      (github/update-all!))

    this)

  (stop [this]
    (log/info "Stopping nvim-app")
    (reset! app-system-atom nil)
    (assoc this :app nil)
    this))
