(ns nvim-app.utils
  (:require
   [nvim-app.db.core :as db]
   [nvim-app.state :refer [app-config dev?]]
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
   [slingshot.slingshot :refer [try+]])
  (:import [java.time Instant Duration]))

(defn strip-trace [m]
  (if-not (map? m)
    m
    (update-vals (dissoc m :trace)
                 #(cond
                    (instance? Throwable %) (strip-trace (Throwable->map %))
                    (map? %) (strip-trace %)
                    (vector? %) (mapv strip-trace %)
                    :else %))))

(defn ex-format [ex]
  (with-out-str
    (pprint/pprint
     (-> ex
         (Throwable->map)
         (strip-trace)
         (assoc :trace (.getStackTrace ex))))))

(defn pcall
  "Protected call, returns [success?, result or error message]"
  [fn & args]
  (try
    [true (apply fn args)]
    (catch Exception e
      (log/errorf "Error during pcall: %s" (ex-format e))
      [false (ex-format e)])))

(defn rpcall
  "Protected call, returns result or nil"
  [fn & args]
  (let [[success? result] (apply pcall fn args)]
    (when success? result)))

(defn json-parse
  ([data]
   (json-parse data {:verbose false}))

  ([data opts]
   (try
     (json/parse-string data true)
     (catch Exception e
       (when (:verbose opts)
         (log/error (str "Failed to parse JSON: " data " - " (ex-message e))))))))

(defn markdown->html [text]
  (raw (md/md-to-html-string text)))

(defn truncate
  [data & {:keys [lines] :or {lines 2}}]
  (str (->> (str/split data #"\n|\\n")
            (take lines)
            (str/join "\n"))
       "..."))

(defn fetch-request
  "
  Makes request using clj-http.client.
  Body and errors are parsed as json if possible. 
  Errors are :errors from response body or exception message
  Logs error on failure.

  Arguments:
    - `request` - request map, 
    - `:verbose` - log errors (default false)

  Returns: `{:status, :body, :errors}`.
  "
  [request & {:keys [verbose] :or {verbose false}}]

  (try
    (let [{:keys [status headers body] :as resp} (http/request request)
          json (or (json-parse body) body)]
      {:status status
       :headers headers
       :body json
       :errors (:errors json)
       :response resp})

    (catch Exception e
      (let [{:keys [status reason-phrase headers body]} (ex-data e)
            json (or (json-parse body) body)
            resp {:status status
                  :errors {:message (or (get-in json [:errors :message])
                                        (ex-message e))
                           :reason reason-phrase}
                  :headers headers
                  :body (or json body)}]

        (when verbose
          (log/error (str "Failed to fetch request: "
                          status " " (select-keys resp [:status :errors :body])))
          (tap> resp)
          (tap> e))
        resp))))

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
   (io/file)
   (.lastModified)
   (Instant/ofEpochMilli)
   (Duration/between (Instant/now))
   (.toDays)
   (> 7)))

(defn make-preview
  ([id]
   (make-preview id false))
  ([id force-update]
   (let [filename (format preview-filename id)]
     (or
      (and (-> (io/file filename) .exists)
           (not (preview-stale? id))
           (not force-update))

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
             (log/error "HTTP errror during preview for repo" id response))

           (catch [:type :etaoin/timeout] _
             (alter-var-root #'app-config
                             #(assoc-in % [:app :features :preview] false))
             (log/error "Timeout making preview for repo" id))

           (catch Object e
             (if dev?
               (do (tap> e) false)
               (log/error "Failed to make preview for repo " (ex-format e)))))))))))

(defn update-previews! []
  (when (-> app-config :app :features :preview)
    (->>
     (for [{:keys [id]} (db/select :repos)]
       (make-preview id))
     (keep some?)
     (count)
     (log/info "Updated previews:"))))

(comment
  (update-previews!)
  (make-preview 754 true)
  (def driver (e/chrome (driver-opts)))
  (e/go driver "https://github.com/mistweaverco/kulala.nvim")
  (e/get-element-tag driver {:tag :article :fn/has-class "entry-content"}))
