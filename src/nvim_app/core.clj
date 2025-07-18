(ns nvim-app.core
  (:gen-class)
  (:require
   [nvim-app.state :refer [nvim-app-system-atom repl-server-atom]]
   [nvim-app.config :as config]
   [nvim-app.db :as db]
   [nvim-app.awesome :as awesome]

   [nvim-app.components.pedestal.core :as pedestal-component]
   [nvim-app.components.database :as database-component]
   [com.stuartsierra.component :as component]

   [clojure.tools.logging :as log]
   [nrepl.server :as nrepl]))

(defn start-repl! []
  (when-not @repl-server-atom
    (let [port (:port (:repl (config/read-config)))
          repl (nrepl/start-server :bind "0.0.0.0" :port port)]

      (reset! repl-server-atom repl)
      (log/info (str "Starting REPL server on port: " (:server-socket repl))))))

(defn nvim-database-system [config]
  (component/system-map
   :database-component (database-component/new config)))

(defn nvim-app-system [config]
  (component/system-map
   :database-component (database-component/new config)

   :pedestal-component
   (component/using
    (pedestal-component/new-pedestal-component config)
    [:database-component])))

(defn -main []
  (let [system (-> (config/read-config)
                   (config/assert-valid-config!)
                   (nvim-app-system)
                   (component/start-system))]

    (reset! nvim-app-system-atom system)

    (log/info (str "Starting nvim-app on port: "
                   (-> system :pedestal-component :config :server :port)))
    (log/info (str "Starting database on: "
                   (-> system :database-component :db-spec :jdbc-url)))

    (when (db/db-empty?)
      (db/run-migrations!)
      (awesome/update-plugins!))

    (start-repl!)

    (.addShutdownHook
     (Runtime/getRuntime)
     (new Thread #(component/stop-system system)))))

(comment
  (-main)
  (component/stop-system @nvim-app-system-atom)
  (start-repl!)
  (nrepl/stop-server @repl-server-atom)
  (reset! repl-server-atom nil)

  "
```http

http://localhost:8080/plugins
Accept: text/html
Accept: application/json

```
")
