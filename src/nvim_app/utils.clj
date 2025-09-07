(ns nvim-app.utils
  (:require
   [hiccup2.core :refer [raw]]
   [markdown.core :as md]
   [clj-http.client :as http]
   [cheshire.core :as json]
   [clojure.tools.logging :as log]
   [clojure.string :as str]
   [clojure.pprint :as pprint]))

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

(defn pcall [fn]
  (try
    [true (fn)]
    (catch Exception e
      [false (ex-format e)])))

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
  Makes request using clj-http.client and returns {:status, :body, :errors}.
  Body and errors are parsed as json if possible. 
  Errors are a merge of exception data and errors field from response body.
  Logs error on failure.
  "
  [request & {:keys [verbose] :or {verbose false}}]

  (try
    (let [{:keys [status headers body] :as resp} (http/request request)
          json (json-parse body)]
      {:status status
       :headers headers
       :body (or json body)
       :errors (:errors (or json body))
       :response resp})

    (catch Exception e
      (let [{:keys [status reason-phrase headers body] :as response} (ex-data e)
            json (json-parse body)
            resp {:status status
                  :errors {:message (or (get-in (or json body) [:errors :message])
                                        (ex-message e))
                           :reason reason-phrase}
                  :headers headers
                  :body (or json body)}]
                  ; :request (update request :body truncate)}]
                  ; :response response

        (when verbose
          (log/error (str "Failed to fetch request: "
                          status " " (select-keys resp [:status :errors :body])))
          (tap> resp))
        resp))))
