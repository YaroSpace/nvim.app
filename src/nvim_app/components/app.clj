(ns nvim-app.components.app
  (:require
   [nvim-app.state :refer [app-system-atom]]
   [nvim-app.db.core :as db]
   [nvim-app.db.plugin :as plugin]
   [nvim-app.awesome :refer [get-plugins]]
   [com.stuartsierra.component :as component]
   [clojure.tools.logging :as log]))

(defn update-plugins! []
  (let [plugins (get-plugins)]
    (plugin/upsert-plugins! plugins)
    (log/info (format "Updated %s plugins from awesome-neovim README..."
                      (count plugins)))))

(defrecord App []
  component/Lifecycle

  (start [this]
    (log/info "Starting nvim-app")
    (reset! app-system-atom this)
    (db/run-migrations!)

    (when-not (seq (plugin/get-plugins))
      (update-plugins!))
    this)

  (stop [this]
    (log/info "Stopping nvim-app")
    (reset! app-system-atom nil)
    (assoc this :app nil)
    this))
