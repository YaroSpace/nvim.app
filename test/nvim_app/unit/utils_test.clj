(ns nvim-app.unit.utils-test
  (:require
   [nvim-app.utils :refer :all]
   [nvim-app.helpers :refer [to-json] :as h]
   [clj-http.fake :refer [with-fake-routes-in-isolation]]
   [lazytest.core :refer [defdescribe describe expect it]]))

(defdescribe fetch-request-test
  (it "success, json response"
    (with-fake-routes-in-isolation
      {#".*success/json"
       (fn [_] {:status 200
                :headers {"Content-Type" "application/json"}
                :body (to-json {:payload "data"})})}

      (expect (= {:status 200
                  :headers {"Content-Type" "application/json"}
                  :body {:payload "data"}}
                 (->
                  (fetch-request {:url "https://example.com/success/json"})
                  (select-keys [:status :headers :body]))))))

  (it "success, text response"
    (with-fake-routes-in-isolation
      {#".*success/text"
       (fn [_] {:status 200 :body "data"})}

      (expect (= {:body "data"}
                 (->
                  (fetch-request {:method :get :url "https://example.com/success/text"})
                  (select-keys [:body]))))))

  (it "success, json response with errors"
    (with-fake-routes-in-isolation
      {#".*success/text"
       (fn [_] {:status 200 :body (to-json {:errors {:message "Error message"}})})}

      (expect (= {:errors {:message "Error message"}}
                 (->
                  (fetch-request {:method :get :url "https://example.com/success/text"})
                  (select-keys [:errors]))))))

  (it "failed request, text response"
    (with-fake-routes-in-isolation
      {#".*fail/text"
       (fn [_] (throw (ex-info "Bad request"
                               {:status 400
                                :headers {"Content-Type" "text/plain"}
                                :reason-phrase "Service Unavailable"
                                :body "none"})))}

      (expect (= {:status 400
                  :body "none"
                  :headers {"Content-Type" "text/plain"}
                  :errors {:message "Bad request", :reason "Service Unavailable"}}
                 (->
                  (fetch-request {:method :get :url "https://example.com/fail/text"})
                  (select-keys [:status :headers :body :errors]))))))

  (it "failed request, json response"
    (with-fake-routes-in-isolation
      {#".*fail/text"
       (fn [_] {:status 500 :body (to-json {:errors {:message "Error message"}})})}

      (expect (= {:status 500
                  :errors {:message "Error message", :reason nil}}
                 (->
                  (fetch-request {:method :get :url "https://example.com/fail/text"})
                  (select-keys [:status :errors])))))))
