(ns nvim-app.components.app
  (:require
   [nvim-app.state :refer [app-system-atom]]
   [nvim-app.github :as github]
   [nvim-app.db.core :as db]
   [nvim-app.components.sched :as sched]
   [com.stuartsierra.component :as component]
   [clojure.tools.logging :as log]))

(defn app-stats []
  {:records (db/count :repos)
   :updates (sched/completed-tasks)})

(defn dev? []
  (= :dev (get-in @app-system-atom [:app :env])))

(defrecord App [app]
  component/Lifecycle

  (start [this]
    (log/info "Starting nvim-app")
    (reset! app-system-atom this)

    (when (:update-on-start? app)
      (github/update-all!))

    this)

  (stop [this]
    (log/info "Stopping nvim-app")
    (reset! app-system-atom nil)
    (assoc this :app nil)
    this))
