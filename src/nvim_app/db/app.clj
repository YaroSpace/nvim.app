(ns nvim-app.db.app
  (:require
   [nvim-app.db.core :as db]
   [cheshire.core :as json]))

(defn get-data []
  (let [data (db/select-one :app :id 1)]
    (json/parse-string (:data data) true)))

(defn save-data! [data]
  (db/update! :app {:data [:cast (json/generate-string data) :jsonb]}
              :where [:= :id 1]))
