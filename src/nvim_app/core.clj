(ns nvim-app.core
  (:gen-class)
  (:require
   [nvim-app.state :refer [nvim-app-system-atom]]
   [nvim-app.config :as config]
   [nvim-app.db.core :as db]
   [nvim-app.awesome :as awesome]

   [nvim-app.components.pedestal.core :as pedestal-component]
   [nvim-app.components.database :as database-component]
   [nvim-app.components.repl :as repl-component]
   [com.stuartsierra.component :as component]))

(defn nvim-database-system [config]
  (component/system-map
   :database-component (database-component/new config)))

(defn nvim-app-system [config]
  (component/system-map
   :repl (repl-component/new-repl-component config)
   :database-component (database-component/new config)

   :pedestal-component
   (component/using
    (pedestal-component/new-pedestal-component config)
    [:database-component :repl])))

(defn -main []
  (let [system (-> (config/read-config)
                   (config/assert-valid-config!)
                   (nvim-app-system)
                   (component/start-system))]

    (reset! nvim-app-system-atom system)

    (when (db/db-empty?)
      (db/run-migrations!)
      (awesome/update-plugins!))

    (.addShutdownHook
     (Runtime/getRuntime)
     (new Thread #(component/stop-system system)))))

(comment
  (-main)
  (component/stop-system @nvim-app-system-atom)
  (require '[portal.api :as inspect])
  (add-tap #'inspect/submit)
  "
```http

http://localhost:6080/plugins-page?page=1&limit=2
Accept: text/html
Accept: application/json

```
")
