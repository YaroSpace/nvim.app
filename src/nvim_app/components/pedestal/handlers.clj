(ns nvim-app.components.pedestal.handlers
  (:require
   [nvim-app.db.plugin :as plugin]
   [nvim-app.db.repo :as repo]
   [nvim-app.views.plugins :as view-plugin]
   [nvim-app.views.repos :as view-repo]

   [next.jdbc :as jdbc]))
   ; [schema.core :as s]

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

(def plugins-page-handler
  {:name :plugins-page-handler
   :enter
   (fn [context]
     (let [type (get-in context [:request :accept :field])
           params (-> context :request :query-params)
           query  (:q params)
           sort  (:sort params)
           page   (Integer/parseInt (get-in params [:page] "1"))
           limit  (Integer/parseInt (get-in params [:limit] "10"))
           offset (* (dec page) limit)

           matched (plugin/search-trm-plugins query sort offset limit)
           total  (int (Math/ceil (/ (or (:total (first matched)) 0) limit)))]

       (assoc context :response
              (ok (if (= type "application/json")
                    matched
                    (view-plugin/plugins-page matched sort page limit total))))))})

(def repos-page-handler
  {:name :repos-page-handler
   :enter
   (fn [context]
     (let [type (get-in context [:request :accept :field])
           params (-> context :request :query-params)
           query  (:q params)
           sort  (:sort params)
           page   (Integer/parseInt (get-in params [:page] "1"))
           limit  (Integer/parseInt (get-in params [:limit] "10"))
           offset (* (dec page) limit)

           matched (repo/search-repos query sort offset limit)
           total  (int (Math/ceil (/ (or (:total (first matched)) 0) limit)))]

       (assoc context :response
              (ok (if (= type "application/json")
                    matched
                    (view-repo/repos-page matched sort page limit total))))))})

#_(def plugins-page-handler
    "In memory filtering"
    {:name :plugins-page-handler
     :enter
     (fn [context]
       (let [type (get-in context [:request :accept :field])
             params (-> context :request :query-params)
             query  (:q params)
             page   (Integer/parseInt (get-in params [:page] "1"))
             limit  (Integer/parseInt (get-in params [:limit] "10"))
             offset (* (dec page) limit)
             matched (plugin/search-plugins query)
             total  (int (Math/ceil (/ (count matched) limit)))
             plugins (->> matched (drop offset) (take limit))]

         (assoc context :response
                (ok (if (= type "application/json")
                      plugins
                      (views/plugins-page plugins page limit total))))))})

(comment
  (require '[helpers :as h])
  (h/test-handler plugins-page-handler {:page "1" :limit "2"}))

(def plugins-handler
  {:name :plugins-handler
   :enter
   (fn [context]
     (assoc context :response (ok (view-plugin/plugins))))})

(def repos-handler
  {:name :repos-handler
   :enter
   (fn [context]
     (assoc context :response (ok (view-repo/repos))))})

(comment)
  ; (views/plugins-page (take 5 (plugin/get-plugins)) 1 2 3))
