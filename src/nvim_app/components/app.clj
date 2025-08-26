(ns nvim-app.components.app
  (:require
   [nvim-app.state :refer [app-system-atom]]
   [nvim-app.github :as github]
   [nvim-app.db.core :as db]
   [nvim-app.logging :refer [wrap-logging-with-notifications]]
   [nvim-app.components.sched :as sched]
   [com.stuartsierra.component :as component]
   [clojure.tools.logging :as log]))

(defn app-stats []
  {:records (count (db/select :repos))
   :updates (sched/completed-tasks)
   :users (count (db/select :users))})

(defrecord App [config app]
  component/Lifecycle

  (start [this]
    (log/info "Starting nvim-app")

    (reset! app-system-atom this)
    (alter-var-root #'nvim-app.state/app-config (constantly config))
    (alter-var-root #'nvim-app.state/dev? (constantly (= :dev (-> config :app :env))))

    (when (-> config :telegram :enable)
      (wrap-logging-with-notifications))

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

(comment
  (alter-var-root #'nvim-app.state/app-config #(assoc-in % [:db-spec :logging?] false)))
