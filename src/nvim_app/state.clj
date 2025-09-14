(ns nvim-app.state)

(defonce app-system-atom (atom nil))
(defonce app-config {})
(defonce dev? false)

(defn alter-in-app-config! [path value]
  (alter-var-root #'app-config #(assoc-in % path value)))
