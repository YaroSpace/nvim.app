(ns nvim-app.core
  (:gen-class)
  (:require
   [nvim-app.config :as config]
   [nvim-app.components.app :as app]
   [nvim-app.components.pedestal.core :as pedestal-component]
   [nvim-app.components.database :as database-component]
   [nvim-app.components.xtdb :as xtdb-component]
   [nvim-app.components.sched :as sched-component]
   [nvim-app.components.repl :as repl-component]
   [nvim-app.utils :refer [ex-format]]
   [com.stuartsierra.component :as component]
   [clojure.tools.logging :as log]))

(defn nvim-database-system [config]
  (component/system-map
   :database-component (database-component/new config)))

(defn nvim-app-system [config]
  (->
   (component/system-map
    :repl (repl-component/new config)
    :database-component (database-component/new config)
    :xtdb-component (xtdb-component/new config)
    :pedestal-component (component/using (pedestal-component/new config)
                                         [:database-component])
    :sched (sched-component/new config)
    :app  (app/new config))
   (component/system-using {:app [:database-component
                                  :xtdb-component
                                  :pedestal-component
                                  :sched :repl]})))

(defn -main []
  (try
    (let [system (-> (config/read-config)
                     config/assert-valid-config!
                     nvim-app-system
                     component/start-system)]

      (.addShutdownHook
       (Runtime/getRuntime)
       (new Thread #(component/stop-system system))))

    (catch Exception e
      (log/error "Failed to start system component\n" (ex-format e))
      (component/stop (:system (ex-data e))))))
