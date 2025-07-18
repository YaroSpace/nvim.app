(ns nvim-app.components.pedestal.handlers
  (:require
   [nvim-app.db :as db]
   [nvim-app.views.plugins :as views]

   [next.jdbc :as jdbc]
   [cheshire.core :as json]))
   ; [schema.core :as s]
   ; [clojure.tools.logging :as log]))

(defn response
  ([status]
   (response status nil))

  ([status body]
   (merge
    {:status status}
    (when body {:body body}))))

(def ok (partial response 200))
(def created (partial response 201))
(def not-found (partial response 404))

(defn respond-hello [_]
  {:status 200 :body "Hello, world!"})

(def info-handler
  {:name :info-handler
   :enter
   (fn [{:keys [dependencies] :as context}]
     (let [{:keys [database-component]} dependencies
           db-response (first (jdbc/execute! database-component ["SHOW SERVER_VERSION"]))]

       (assoc context :response (ok db-response))))})

(def plugins-handler
  {:name :plugins-handler
   :enter
   (fn [context]
     (let [plugins (take 50 (db/get-plugins))
           type (get-in context [:request :accept :field])]

       (assoc context :response
              (ok (if (= type "application/json")
                    plugins
                    (str (views/plugins-page plugins)))))))})

(comment
  (views/plugins-page (take 5 (db/get-plugins))))
