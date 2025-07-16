(ns dev
  (:require [com.stuartsierra.component.repl :as component-repl]
            [nvim-app.core :as core]
            [nvim-app.db :as db]))

(def dev-db-spec
  {:jdbc-url "jdbc:postgresql://localhost:5432/nvim.app"
   :username "nvim"
   :password "nvim"})

(component-repl/set-init
 (fn [_]
   (core/nvim-app-system
    {:server {:port 8080}
     :db-spec dev-db-spec})))

(defn reset []
  (let [res (component-repl/reset)]
    (reset! core/nvim-app-system-atom component-repl/system)
    (db/run-migrations!)
    res))
