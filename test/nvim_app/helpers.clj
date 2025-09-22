(ns nvim-app.helpers
  (:require
   [nvim-app.config :as config]
   [nvim-app.state :refer [app-system-atom dev?]]
   [nvim-app.core :refer [nvim-app-system nvim-database-system]]
   [nvim-app.components.app :refer [reset-state!]]
   [nvim-app.components.pedestal.routes :as r]
   [nvim-app.fixtures :as fixtures]
   [nvim-app.db.core :as db]
   [io.pedestal.http.route :as route]
   [com.stuartsierra.component :as component]
   [cheshire.core :as json]
   [clojure.tools.logging :as log]
   [clojure.string :as str]
   [etaoin.api :as e]
   [lazytest.core :refer [around]])

  (:import
   [org.testcontainers.containers PostgreSQLContainer]))

(defn to-json [data]
  (json/generate-string data))

(defn sut->url
  [sut path]
  (str/join ["http://localhost:"
             (-> sut :pedestal-component :config :port)
             path]))

(def get-url-for
  (-> r/routes
      route/expand-routes
      route/url-for-routes))

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
  (let [config (binding [*err* (java.io.StringWriter.)] ; silence not found warnings
                 (config/read-config {:profile :test}))]
    (system (merge config
                   (get-config database-container)))))

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

(defn setup-fixtures! [sut]
  (let [ds (:database-component sut)]
    (doseq [[t v] {:users fixtures/users
                   :categories fixtures/categories
                   :repos fixtures/repos}]
      (db/query-one! ds {:insert-into t :values v}))))

(defn silence-logging!
  "Silences logging in develop"
  []
  (when-let [set-log-level! (resolve 'user/set-log-level!)]
    (let [get-log-level (resolve 'user/get-log-level)]
      (into {} (for [ns ["migratus.core" "migratus.database" "io.pedestal.http"]]
                 (let [current-level (get-log-level ns)]
                   (set-log-level! ns :warn)
                   [ns current-level]))))))

(defn restore-logging!
  "Restores logging in develop"
  [ns-level]
  (when-let [set-log-level! (resolve 'user/set-log-level!)]
    (doseq [[ns level] ns-level]
      (set-log-level! ns level))))

(defmacro with-silenced-logging
  "Macro to silence logging within its body."
  [& body]
  `(let [ns-level# (silence-logging!)
         original-log-fn# log/log*]
     (alter-var-root #'log/log* (constantly (fn [& ~'_])))
     ~@body
     (restore-logging! ns-level#)
     (alter-var-root #'log/log* (constantly original-log-fn#))))

(defn etaoin-redefs [driver]
  (map (fn [[sym var]]
         `(~sym [& ~'args] (apply ~var @~driver ~'args)))
       (ns-publics 'etaoin.api)))

(defmacro with-driver
  "
  Binds etaoin functions to be used without namespace 
  and with the provided driver within the body.

  Arguments:
   `driver` - an atom containing the etaoin driver
   `body` - the body to execute with the bound functions

  Example usage:
  ```clojure
   (with-driver driver
     (click {:fn/has-id \"submit-button\"})
  ```
  "
  [driver & body]
  `(letfn [~@(etaoin-redefs driver)]
     (try
       ~@body
       (catch Exception e#
         (let [msg# (-> e# ex-data :response :value :message)]
           (if dev?
             (println msg#)
             (log/error "Error in with-driver block" msg#)))))))

(defn setup-browser! [driver headless?]
  (let [driver* (e/use-css (e/chrome {:headless headless?}))]
    (reset! driver driver*)
    (e/set-window-size driver* {:width 1280 :height 800})))

(defn setup-sut!
  "
  Sets up the system under test (SUT), seeds fixtures into DB,
  starts browser driver for integration tests, silences logging.

  Arguments:
    `sut` - an atom to hold the system under test
    `driver` - an atom to hold the etaoin driver
    `headless` - boolean indicating whether to run the browser in headless mode (default: true)
  "
  [sut driver & {:keys [headless?] :or {headless? true}}]
  (around [f]
    (with-test-system sut*
      (reset! sut sut*)
      (setup-fixtures! sut*)
      (setup-browser! driver headless?)
      (f)
      (when headless?
        (e/quit @driver)
        (reset! driver nil)))))

(def silent-logging
  (around [f]
    (with-silenced-logging
      (f))))
