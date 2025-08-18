(ns nvim-app.core
  (:gen-class)
  (:require
   [nvim-app.config :as config]
   [nvim-app.components.app :as app]
   [nvim-app.components.pedestal.core :as pedestal-component]
   [nvim-app.components.database :as database-component]
   [nvim-app.components.sched :as sched-component]
   [nvim-app.components.repl :as repl-component]
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
    :pedestal-component (component/using (pedestal-component/new config)
                                         [:database-component])
    :sched (sched-component/new config)
    :app  (app/new config))
   (component/system-using {:app [:repl :sched
                                  :pedestal-component
                                  :database-component]})))

(defn -main []
  (try
    (let [system (-> (config/read-config)
                     (config/assert-valid-config!)
                     (nvim-app-system)
                     (component/start-system))]

      (.addShutdownHook
       (Runtime/getRuntime)
       (new Thread #(component/stop-system system))))

    (catch Exception e
      (log/error "Failed to start system component" (ex-message e))
      (component/stop (:system (ex-data e))))))

(comment
  (require 'nvim-app.state)
  (-main)
  ; (require '[portal.api :as inspect])
  ; (add-tap #'inspect/submit)
  (component/stop-system @nvim-app.state/app-system-atom)
  "
```http
http://localhost:6080/plugins-page?page=1&limit=2
Accept: text/html
Accept: application/json

```
")
