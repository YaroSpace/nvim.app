(ns dev
  (:require
   [nvim-app.core :refer [nvim-app-system]]
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
    {:server {:port 6080}
     :db-spec dev-db-spec})))

(defn reset []
  (let [res (component-repl/reset)]
    res))

(comment
  (component-repl/stop))
