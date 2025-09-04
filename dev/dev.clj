(ns dev
  (:require
   [nvim-app.state :refer [app-system-atom]]
   [nvim-app.core :refer [nvim-app-system]]
   [nvim-app.config :as config]
   [com.stuartsierra.component.repl :as component-repl]
   [clojure.tools.namespace.repl :as repl]
   [clojure.tools.logging :as log]
   [nvim-app.utils :refer [ex-format]]))

(repl/disable-reload! (the-ns 'nvim-app.state))

(component-repl/set-init
 (fn [_]
   (nvim-app-system
    (config/read-config {:profile :dev}))))

(defn reset []
  (when @app-system-atom
    (component-repl/stop))

  (repl/refresh)

  (try
    (component-repl/start)
    (catch Exception e
      (log/error "Failed to start system component" (ex-format e))
      (tap> e)
      (component-repl/stop))))

(comment
  (:app component-repl/system)
  (component-repl/stop))
