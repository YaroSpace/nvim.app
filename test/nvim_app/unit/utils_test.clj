(ns nvim-app.unit.utils-test
  (:require
   [nvim-app.utils :refer :all]
   [nvim-app.helpers :refer [to-json] :as h]
   [clj-http.fake :refer [with-fake-routes-in-isolation]]
   [clojure.test :refer [deftest testing is]]))

(deftest fetch-request-test
  (testing "success, json response"
    (with-fake-routes-in-isolation
      {#".*success/json"
       (fn [_] {:status 200
                :headers {"Content-Type" "application/json"}
                :body (to-json {:payload "data"})})}

      (is (= {:status 200
              :headers {"Content-Type" "application/json"}
              :body {:payload "data"}}
             (->
              (fetch-request {:url "https://example.com/success/json"})
              (select-keys [:status :headers :body]))))))

  (testing "success, text response"
    (with-fake-routes-in-isolation
      {#".*success/text"
       (fn [_] {:status 200 :body "data"})}

      (is (= {:body "data"}
             (->
              (fetch-request {:method :get :url "https://example.com/success/text"})
              (select-keys [:body]))))))

  (testing "success, json response with errors"
    (with-fake-routes-in-isolation
      {#".*success/text"
       (fn [_] {:status 200 :body (to-json {:errors {:message "Error message"}})})}

      (is (= {:errors {:message "Error message"}}
             (->
              (fetch-request {:method :get :url "https://example.com/success/text"})
              (select-keys [:errors]))))))

  (testing "failed request, text response"
    (with-fake-routes-in-isolation
      {#".*fail/text"
       (fn [_] (throw (ex-info "Bad request"
                               {:status 400
                                :headers {"Content-Type" "text/plain"}
                                :reason-phrase "Service Unavailable"
                                :body "none"})))}

      (is (= {:status 400
              :body "none"
              :headers {"Content-Type" "text/plain"}
              :errors {:message "Bad request", :reason "Service Unavailable"}}
             (->
              (fetch-request {:method :get :url "https://example.com/fail/text"})
              (select-keys [:status :headers :body :errors]))))))

  (testing "failed request, json response"
    (with-fake-routes-in-isolation
      {#".*fail/text"
       (fn [_] {:status 500 :body (to-json {:errors {:message "Error message"}})})}

      (is (= {:status 500
              :errors {:message "Error message", :reason nil}}
             (->
              (fetch-request {:method :get :url "https://example.com/fail/text"})
              (select-keys [:status :errors])))))))
