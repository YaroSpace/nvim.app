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

(def app-config {})

(defn dev? []
  (= :dev (-> app-config :app :env)))

(defrecord App [config app]
  component/Lifecycle

  (start [this]
    (log/info "Starting nvim-app")

    (reset! app-system-atom this)
    (alter-var-root #'app-config (constantly config))

    (when (:update-on-start? app)
      (github/update-all!))

    this)

  (stop [this]
    (log/info "Stopping nvim-app")
    (reset! app-system-atom nil)
    (assoc this :app nil)
    this))

(defn new
  [config]
  (->App config (:app config)))
