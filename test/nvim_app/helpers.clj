(ns nvim-app.helpers
  (:require
   [nvim-app.state :refer [app-system-atom]]
   [nvim-app.core :refer [nvim-app-system nvim-database-system]]
   [nvim-app.components.app :refer [reset-state!]]
   [nvim-app.components.pedestal.routes :as r]
   [nvim-app.integration.fixtures :as fixtures]
   [nvim-app.db.core :as db]
   [io.pedestal.http.route :as route]
   [com.stuartsierra.component :as component]
   [cheshire.core :as json]
   [clojure.tools.logging :as log]
   [clojure.string :as str])

  (:import
   [org.testcontainers.containers PostgreSQLContainer]))

(defn to-json [data]
  (json/generate-string data))

(defn sut->url
  [sut path]
  (str/join ["http://localhost:"
             (-> sut :app :pedestal-component :config :port)
             path]))

(def get-url-for
  (route/url-for-routes r/routes))

(defn get-sut-url-for
  ([sut route]
   (get-sut-url-for sut route {}))

  ([sut route opts]
   (sut->url sut (get-url-for route opts))))

(defn get-free-port []
  (with-open [socket (java.net.ServerSocket. 0)]
    (.getLocalPort socket)))

(defn get-database-container
  ([] (get-database-container "postgres:latest"))

  ([container-image]
   (PostgreSQLContainer. container-image))

  ([container-image db-spec]
   (doto (PostgreSQLContainer. container-image)
     (.withDatabaseName (:name db-spec))
     (.withUsername (:user db-spec))
     (.withPassword (:password db-spec)))))

(defn get-container-db-spec
  [^PostgreSQLContainer container]
  {:jdbc-url (.getJdbcUrl container)
   :username (.getUsername container)
   :password (.getPassword container)})

(defn get-config [database-container]
  {:server {:port (get-free-port)}
   :db-spec (get-container-db-spec database-container)})

(defn get-system [system database-container]
  (system (get-config database-container)))

(defmacro with-system
  [[bound-var system] & body]
  `(let [current-system# @app-system-atom
         database-container# (get-database-container)]
     (try
       (.start database-container#)
       (~`log/info "Starting test system:" ~(name system))
       (let [~bound-var (component/start (get-system ~system database-container#))]
         (try
           ~@body
           (finally
             (component/stop ~bound-var))))
       (finally
         (.stop database-container#)
         (when current-system#
           (reset-state! current-system#))))))

(defmacro with-test-system
  [bound-var & body]
  `(with-system [~bound-var nvim-app-system]
     ~@body))

(defmacro with-database-system
  [bound-var & body]
  `(with-system [~bound-var nvim-database-system]
     ~@body))

(defn test-handler
  ([handler params]
   (test-handler handler params "application/json"))

  ([handler params content-type]
   (let [context {:request {:accept {:field content-type}
                            :query-params params}}]
     (-> context
         ((:enter handler))
         :response
         :body))))

(defn setup-fixtures [sut]
  (let [ds (:database-component sut)]
    (db/query-one! ds
                   {:insert-into :categories
                    :values fixtures/categories})
    (db/query-one! ds
                   {:insert-into :repos
                    :values fixtures/plugins})))
