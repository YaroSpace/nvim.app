(ns nvim-app.components.pedestal.handlers.core
  (:require
   [nvim-app.components.pedestal.views.news :as news]
   [nvim-app.components.pedestal.views.about :as about]
   [nvim-app.components.pedestal.views.not-found :as not-found]))

(defn response
  ([status]
   (response status nil))

  ([status body & response]
   (cond-> {:status status}
     body    (assoc :body body)
     response (merge response))))

(def ok (partial response 200))

(defn redirect [location & [response]]
  (cond-> {:status 302
           :headers {"Location" location}}
    response (merge response)))

(defn not-found [context]
  (assoc context :response
         {:status 404
          :headers {"Content-Type" "text/html"}
          :body (not-found/index)}))

(defn invalid-params [context message]
  (assoc context
         :request (assoc-in (:request context)
                            [:accept :field] "application/json")
         :response {:status 400
                    :body {:errors {:message message}}}))

(def news-index
  {:name :news-index
   :enter
   (fn [{:keys [request] :as context}]
     (assoc context :response
            (ok (news/index request))))})

(def about-index
  {:name :news-index
   :enter
   (fn [{:keys [request] :as context}]
     (assoc context :response
            (ok (about/index request))))})

(comment
  "
```http
http://localhost:6080/repos-page
Accept: application/json

```
")
