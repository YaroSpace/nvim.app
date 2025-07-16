(ns dev
  (:require [com.stuartsierra.component.repl :as component-repl]
            [nvim-app.core :as core]))

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
  (component-repl/reset))
