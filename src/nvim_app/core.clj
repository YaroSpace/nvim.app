(ns nvim-app.core
  (:require
   [nvim-app.state :refer [nvim-app-system-atom]]
   [nvim-app.config :as config]
   [nvim-app.components.pedestal.core :as pedestal-component]
   [nvim-app.components.database :as database-component]

   [com.stuartsierra.component :as component]
   [clojure.tools.logging :as log]))

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
    (log/info "Starting nvim-app...")

    (.addShutdownHook
     (Runtime/getRuntime)
     (new Thread #(component/stop-system system)))))

(comment
  (require 'dev)
  (require '[com.stuartsierra.component.repl :as repl])
  repl/system

  nvim-app-system-atom

  (-main)

  "
```http

http://localhost:8080/plugins
Accept: text/html
Accept: application/json

```
")
