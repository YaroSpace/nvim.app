(ns nvim-app.core
  (:require
   [nvim-app.config :as config]
   [nvim-app.components.pedestal.core :as pedestal-component]
   [nvim-app.components.database :as database-component]

   [com.stuartsierra.component :as component]
   [clojure.tools.logging :as log]))

(defonce nvim-app-system-atom (atom nil))

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
  (-main)

  "
```http

http://localhost:8080/info
http://localhost:8080/todo/:todo-id

```
")
