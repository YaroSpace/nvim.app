(ns nvim-app.config
  (:require
   [aero.core :as aero]
   [malli.core :as m]
   [malli.error :as me]
   [clojure.java.io :as io]))

(def config-schema
  (m/schema
   [:map
    [:server
     [:map
      [:port [:int {:min 1 :max 10000}]]]]]))

(defn assert-valid-config!
  [config]
  (if-let [errors (me/humanize (m/explain config-schema config))]
    (throw (ex-info "Invalid configuration" {:errors errors}))
    config))

(defn read-config
  ([] (read-config {:profile :default}))
  ([opts]
   (-> "config.edn"
       (io/resource)
       (aero/read-config opts))))

(comment
  (System/getenv "DATABASE_URL")
  (read-config))

