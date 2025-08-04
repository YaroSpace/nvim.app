(ns nvim-app.components.app
  (:require
   [nvim-app.state :refer [app-system-atom]]
   [nvim-app.db.plugin :as plugin]
   [nvim-app.awesome :as awesome]
   [nvim-app.github :as github]
   [com.stuartsierra.component :as component]
   [clojure.tools.logging :as log]))

(defn update-github-data! [])
  ; (github/update-github-data! (plugin/get-plugins)))

(defn update-plugins! []
  (let [plugins (awesome/get-plugins)]
    (plugin/upsert-plugins! plugins)
    (log/info (format "Updated %s plugins from awesome-neovim README..."
                      (count plugins)))))

(defrecord App [config]
  component/Lifecycle

  (start [this]
    (log/info "Starting nvim-app")
    (reset! app-system-atom this)

    (when (:update-plugins? config)
      (update-plugins!))

    ; (when (:update-github-data? config)
    ;   (update-github-data!))

    this)

  (stop [this]
    (log/info "Stopping nvim-app")
    (reset! app-system-atom nil)
    (assoc this :app nil)
    this))
