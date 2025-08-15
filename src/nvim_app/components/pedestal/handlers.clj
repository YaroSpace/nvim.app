(ns nvim-app.components.pedestal.handlers
  (:require
   [nvim-app.db.repo :as repo]
   [nvim-app.views.repos :as view-repo]
   [next.jdbc :as jdbc]))
   ; [schema.core :as s]

(defn response
  ([status]
   (response status nil))

  ([status body & {:keys [session]}]
   (cond-> {:status status}
     body    (assoc :body body)
     session (assoc :session session))))

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

(def repos-page-handler
  {:name :repos-page-handler
   :enter
   (fn [{:keys [request] :as context}]
     (let [{:keys [session accept query-params]} request
           {:keys [q group sort page limit] :or {page "1" limit "10"}} query-params

           page   (parse-long page)
           limit  (parse-long limit)
           offset (* (dec page) limit)

           matched (repo/search-repos q sort offset limit)
           total  (int (Math/ceil (/ (or (:total (first matched)) 0) limit)))]

       (assoc context :response
              (response 200
                        (cond-> matched
                          (not= (:type accept) "application/json")
                          (view-repo/plugins-list (assoc query-params
                                                         :page page
                                                         :limit limit
                                                         :total total)))
                        :session {:params query-params}))))})

(def repos-handler
  {:name :repos-handler
   :enter
   (fn [{:keys [request] :as context}]
     (assoc context :response
            (ok (view-repo/main
                 (get-in request [:session :params])))))})

