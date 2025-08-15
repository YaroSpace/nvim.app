(ns nvim-app.components.sched
  (:require
   [nvim-app.github :as github]
   [com.stuartsierra.component :as component]
   [clojure.tools.logging :as log]
   [nvim-app.state :as state])
  (:import
   [java.util.concurrent Executors TimeUnit ScheduledThreadPoolExecutor]))

(defn schedule-task [scheduler task interval]
  (.scheduleAtFixedRate scheduler task 1 interval TimeUnit/HOURS)
  (log/info "Scheduled task" (str task) "to run every" interval "hr"))

(defn update-repos! []
  (log/info "Scheduler: Updating Github repositories")
  (github/update-all!))

(defn start-tasks [config scheduler]
  (schedule-task scheduler update-repos! (:update-repos-interval-hr config)))

(defn completed-tasks
  ([]
   (completed-tasks (:scheduler (:sched @state/app-system-atom))))
  ([^ScheduledThreadPoolExecutor scheduler]
   (.getCompletedTaskCount  scheduler)))

(defrecord SchedComponent [config scheduler]
  component/Lifecycle

  (start [this]
    (if scheduler
      scheduler
      (if (:enable config)
        (let [scheduler (Executors/newScheduledThreadPool 1)]
          (log/info "Starting scheduler")
          (start-tasks config scheduler)
          (assoc this :scheduler scheduler))
        this)))

  (stop [this]
    (when-let [scheduler (:scheduler this)]
      (log/info "Stopping scheduler")
      (.shutdown scheduler)
      (assoc this :scheduler nil))))

(defn new [config]
  (map->SchedComponent {:config (:sched config)}))
