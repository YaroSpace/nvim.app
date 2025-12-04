(ns nvim-app.utils
  (:require
   [nvim-app.db.core :as db]
   [nvim-app.state :refer [app-config alter-in-app-config! dev?]]
   [hiccup2.core :refer [raw]]
   [markdown.core :as md]
   [clj-http.client :as http]
   [etaoin.api :as e]
   [etaoin.impl.client]
   [cheshire.core :as json]
   [clojure.tools.logging :as log]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.pprint :as pprint]
   [clj-commons.slingshot :refer [try+]])
  (:import [java.time Instant Duration]))

(defn older-than?
  "Checks if given instant is older than specified number of hours from now"
  [^Instant instant hours]
  (-> instant
      (Duration/between (Instant/now))
      .toHours
      (> hours)))

(defn strip-trace [m]
  (if-not (map? m)
    m
    (update-vals (dissoc m :trace)
                 #(cond
                    (instance? Throwable %) (strip-trace (Throwable->map %))
                    (map? %) (strip-trace %)
                    (vector? %) (mapv strip-trace %)
                    :else %))))

(defn ex-short [ex]
  (let [ex-map (if (instance? Throwable ex) (Throwable->map ex) ex)
        ex-trace (:trace ex-map)]
    (cond-> (strip-trace ex-map)
      ex-trace (assoc :trace ex-trace))))

(defn pretty-format [x]
  (binding [*print-length* false]
    (with-out-str
      (pprint/pprint x))))

(defn ex-format [ex]
  (pretty-format
   (ex-short ex)))

(defmacro with-pcall
  "Protected call, returns [success?, result of body or formatted exception]

   Arguments:
     - `:silent` - if true, does not log errors (default false)
     - `body` - body to execute
  "
  [& body]
  (let [silent?# (= :silent (first body))
        body (if silent?# (rest body) body)]
    `(try
       (let [result# (do ~@body)]
         [true result#])
       (catch Exception e#
         (when-not ~silent?# (log/errorf "Error during pcall: %s" (ex-format e#)))
         [false (ex-short e#)]))))

(defmacro with-rpcall
  "Protected call, returns result of body or `nil`.

   Arguments:
     - `:silent` - if true, does not log errors (default false)
     - `body` - body to execute
     "
  [& body]
  `(let [[success?# result#] (with-pcall ~@body)]
     (when success?# result#)))

(defn json-parse
  ([data]
   (json-parse data {:verbose false}))

  ([data opts]
   (try
     (json/parse-string data true)
     (catch Exception e
       (when (:verbose opts)
         (log/error (str "Failed to parse JSON: " data " - " (ex-format e))))))))

(defn markdown->html [text]
  (raw (md/md-to-html-string text)))

(defn truncate
  [data & {:keys [lines] :or {lines 2}}]
  (str (->> (str/split data #"\n|\\n")
            (take lines)
            (str/join "\n"))
       "..."))

(def default-request-params
  {:method :get
   :url ""
   :headers {"User-Agent" "nvim-app"}
   :throw-exceptions true})

(defn fetch-request
  "
  Makes request using clj-http.client.
  Body and errors are parsed as json if possible. 
  Errors are :errors from response body or exception message
  Logs error on failure.

  Arguments:
    - `request` - request map, 
    - `:verbose` - log errors (default false)
    - `:throw-exceptions` - throw on errors (default false)

  Returns: `{:status, :body, :errors}`.
  "
  [request & {:keys [verbose throw-exceptions]
              :or {verbose false throw-exceptions false}}]
  (try
    (let [request* (merge default-request-params request)
          {:keys [status headers body] :as resp} (http/request request*)
          json (or (json-parse body) body)
          response {:status status
                    :headers headers
                    :body json
                    :errors (or (:errors json)
                                (when-let [error (:error json)]
                                  {:message error}))
                    :response resp}]

      (if (and (:errors response) throw-exceptions)
        (throw (ex-info "HTTP request errors" response))
        response))

    (catch Exception e
      (let [{:keys [status reason-phrase headers body errors]} (ex-data e)
            json (or (json-parse body) body)
            response {:status status
                      :headers headers
                      :body json
                      :errors (or errors {:reason reason-phrase
                                          :message (or (get-in json [:errors :message])
                                                       (:message json)
                                                       (ex-message e))})}]

        (let [errors (-> response
                         (assoc :url (:url request))
                         (dissoc :headers))]
          (when verbose
            (log/errorf "Failed to fetch request: \n%s" (pretty-format errors))
            (tap> response)
            (tap> e))

          (when throw-exceptions
            (throw (ex-info "HTTP request errors" errors e))))

        response))))

(def driver nil)
(defn driver-opts []
  (let [config (:chrome app-config)]
    {:host (:host config)
     :port (:port config)
     :args ["--no-sandbox"]
     :size [680 1000]}))

(def preview-dir "/app/web/public/images/")
(def preview-filename (str preview-dir "preview-%s.png"))

(defn preview-stale? [id]
  (->
   (format preview-filename id)
   io/file
   .lastModified
   Instant/ofEpochMilli
   (older-than? (-> app-config :sched :update-previews-interval-hr))))

(defn make-preview
  ([id]
   (make-preview id false))
  ([id force-update?]
   (let [filename (format preview-filename id)
         file (io/file filename)]
     (or
      (and (-> file .exists)
           (< 40000 (-> file .length)) ; file is too small if preview is incomplete
           (not (preview-stale? id))
           (not force-update?))

      (when-let [url (:url (db/select-one :repos :id id))]
        (let [selector (if (str/includes? url "github.com")
                         {:tag :article :fn/has-class "entry-content"}
                         {:tag :body})]
          (try+
           (e/with-chrome-headless (driver-opts) driver
             (e/go driver url)

             (when (e/exists? driver selector)
               (e/screenshot-element driver selector filename)
               (log/info "Made preview for repo" id)
               true))

           (catch [:type :etaoin/http-error] {:keys [response]}
             (if (str/includes? "tab crashed" (-> response :value :error))
               (log/error "Preview crashed for repo" id)
               (log/error "HTTP errror during preview for repo" id response)))

           (catch [:type :etaoin/http-ex] {:keys [response]}
             (if (str/includes? "tab crashed" (-> response :value :error))
               (log/error "Preview timed out for repo" id)
               (log/error "HTTP errror during preview for repo" id response)))

           (catch [:type :etaoin/timeout] _
             (alter-in-app-config! [:app :features :preview] false)
             (log/error "Timeout making preview for repo" id))

           (catch Object e
             (if dev?
               (do (tap> e) false)
               (log/error "Failed to make preview for repo "
                          (ex-format (:wrapper &throw-context))))))))))))

(defn update-previews! [& {:keys [force-update] :or {force-update false}}]
  (when (-> app-config :app :features :preview)
    (->>
     (for [{:keys [id]} (db/select :repos)]
       (make-preview id force-update))
     (keep some?)
     count
     (log/info "Updated previews:"))))

(comment
  (update-previews!)
  (make-preview 6 true)
  (def driver (e/chrome (driver-opts)))
  (e/go driver "https://github.com/mistweaverco/kulala.nvim")
  (e/get-element-tag driver {:tag :article :fn/has-class "entry-content"}))
