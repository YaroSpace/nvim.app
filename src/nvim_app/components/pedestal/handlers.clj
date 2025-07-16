(ns nvim-app.components.pedestal.handlers
  (:require
   ; [real-world-clojure-api.components.pedestal.schemas :refer [Todo]]

   [io.pedestal.interceptor :as interceptor]
   [next.jdbc :as jdbc]
   [honey.sql :as sql]
   [cheshire.core :as json]
   [schema.core :as s]

   [clojure.tools.logging :as log]
   [clojure.string :as str]))

(defn response
  ([status]
   (response status nil))

  ([status body]
   (merge
    {:status status
     :headers {"Content-Type" "application/json"}}
    (when body {:body (json/encode body)}))))

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

(def exception-handler
  (interceptor/interceptor
   {:name ::exception-handler
    :error (fn [context exception]
             (let [exception-type (-> exception class .getName)
                   exception-message (.getMessage exception)]

               (log/error :msg (str "Exception occurred: " exception-message))

               (assoc context :response
                      {:status 500
                       :headers {"Content-Type" "application/json"}
                       :body {:error true
                              :exception-type exception-type
                              :message exception-message}})))}))
