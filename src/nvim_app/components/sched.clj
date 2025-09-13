(ns nvim-app.components.sched
  (:require
   [nvim-app.github :as github]
   [nvim-app.state :as state]
   [nvim-app.db.app :as app]
   [nvim-app.utils :as u]
   [com.stuartsierra.component :as component]
   [clojure.tools.logging :as log])
  (:import
   [java.util.concurrent Executors TimeUnit ScheduledThreadPoolExecutor]
   [java.time Instant Duration]
   [java.util Date]))

(defn schedule-task [scheduler task interval]
  (.scheduleAtFixedRate scheduler task 1 interval TimeUnit/HOURS)
  (log/info "Scheduled task" (str task) "to run every" interval "hr"))

(defn update-repos! []
  (log/info "Scheduler: Updating Github repositories")
  (github/update-all!))

(defn update-previews! []
  (when-not (some->
             (:last-preview-update (app/get-data))
             (Instant/parse)
             (Duration/between (Instant/now))
             (.toDays)
             (< 7))

    (log/info "Scheduler: Updating previews")
    (u/update-previews!)
    (app/save-data! (assoc (app/get-data)
                           :last-preview-update (Date.)))))

(defn start-tasks [config scheduler]
  (schedule-task scheduler update-repos! (:update-repos-interval-hr config))
  (schedule-task scheduler update-previews! (:update-previews-interval-hr config)))

(defn completed-tasks
  ([]
   (completed-tasks (:scheduler (:sched @state/app-system-atom))))
  ([^ScheduledThreadPoolExecutor scheduler]
   (when scheduler
     (.getCompletedTaskCount  scheduler))))

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
