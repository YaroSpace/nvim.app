(ns nvim-app.utils
  (:require
   [clj-http.client :as http]
   [cheshire.core :as json]
   [clojure.tools.logging :as log]))

(defn json-parse
  ([data]
   (json-parse data {:verbose false}))

  ([data opts]
   (try
     (json/parse-string data true)
     (catch Exception e
       (when (:verbose opts)
         (log/error (str "Failed to parse JSON: " data " - " (ex-message e))))))))

(defn fetch-request [request]
  (try
    (let [{:keys [status body] :as resp} (http/request request)
          json (json-parse body)]
      {:status status
       :body (or json body)
       :errors (:errors json)
       :response resp})

    (catch Exception e
      (let [{:keys [status reason-phrase body]} (ex-data e)
            json (json-parse body)
            resp {:status status
                  :errors {:message (or (:message json) (ex-message e))
                           :reason reason-phrase}
                  :body (or json body)
                  :request request}]
        (log/error (str "Failed to fetch request: " resp))
        resp))))
