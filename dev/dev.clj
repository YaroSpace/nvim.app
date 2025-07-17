(ns dev
  (:require
   [nvim-app.state :refer [nvim-app-system-atom]]
   [nvim-app.core :refer [nvim-app-system]]
   [nvim-app.db :as db]

   [com.stuartsierra.component.repl :as component-repl]
   [clojure.tools.namespace.repl :as repl]))

(repl/disable-reload! (the-ns 'nvim-app.state))

(def dev-db-spec
  {:jdbc-url "jdbc:postgresql://localhost:5432/nvim.app"
   :username "nvim"
   :password "nvim"})

(component-repl/set-init
 (fn [_]
   (nvim-app-system
    {:server {:port 8080}
     :db-spec dev-db-spec})))

(defn reset []
  (let [res (component-repl/reset)
        system component-repl/system]
    (reset! nvim-app-system-atom system)
    (db/run-migrations!)
    res))

(comment
  (reset)
  (reset! nvim-app-system-atom component-repl/system)
  (println (System/identityHashCode nvim-app-system-atom)))

