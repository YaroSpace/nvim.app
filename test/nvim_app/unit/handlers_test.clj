(ns nvim-app.unit.handlers-test
  (:require
   [nvim-app.db.user :as users]
   [nvim-app.components.pedestal.handlers.github :refer :all]
   [nvim-app.utils :refer [fetch-request]]
   [nvim-app.helpers :refer [with-silenced-logging]]
   [io.pedestal.http.route :as route]
   [lazytest.core :refer [defdescribe describe expect it]]
   [clojure.core.async :as a]))

; TODO: use pedestal response-for

(defdescribe github-login-test
  (with-silenced-logging
    (with-redefs [route/url-for (fn [_] "/redirect")]

      (describe "Successful login"
        (let [context {:request {:params {"code" "valid-code"}}}
              responses (a/to-chan!
                         [{:status 200 :body {:access_token "valid-token"}}
                          {:status 200 :body {:id 1
                                              :login "login"
                                              :email "email"
                                              :name "username"
                                              :html_url "url"
                                              :avatar_url "avatar_url"}}])]

          (with-redefs [fetch-request (fn [& _] (a/<!! responses))
                        users/get-or-create! (fn [_] {:id 1 :name "username"})]

            (let [result (:response ((:enter github-callback) context))]
              (it "returns redirect response"
                (expect (= {:status 302,
                            :headers {"Location" "/redirect"},
                            :session {:user 1},
                            :flash {:success {:title "Login Successful",
                                              :message "Welcome, username!"}}}
                           result)))))))

      (describe "Failed token exchange"
        (with-redefs [get-access-token (fn [_]
                                         (throw
                                          (ex-info "HTTP request errors"
                                                   {:errors {:message "bad_verification_code"}})))]

          (let [context {:request {:params {"code" "valid-code"}}}
                result (:response ((:enter github-callback) context))]

            (it "returns redirect response"
              (expect (= {:status 302,
                          :headers {"Location" "/redirect"},
                          :session {:user nil},
                          :flash {:error {:title "Errors logging in"
                                          :message "bad_verification_code"}}}
                         result))))))

      (describe "Failed to get user info"
        (with-redefs [get-access-token (fn [_] {:body {:access_token "valid-token"}})
                      get-user-info (fn [_] (throw
                                             (ex-info "HTTP request errors"
                                                      {:status 401,
                                                       :errors {:reason "Unauthorized",
                                                                :message "Bad credentials"}})))]

          (let [context {:request {:params {"code" "valid-code"}}}
                result (:response ((:enter github-callback) context))]

            (it "retuns redirect response"
              (expect (= {:status 302,
                          :headers {"Location" "/redirect"},
                          :session {:user nil},
                          :flash {:error {:title "Errors logging in"
                                          :message "Unauthorized:Bad credentials"}}}
                         result))))))

      (describe "Failed to save user"
        (with-redefs [get-access-token (fn [_] {:body {:access_token "valid-token"}})
                      get-user-info (fn [_] {:body {:id "invalid-id"}})]

          (let [context {:request {:params {"code" "valid-code"}}}
                result (:response ((:enter github-callback) context))]

            (it "retuns redirect response"
              (expect (= {:status 302,
                          :headers {"Location" "/redirect"},
                          :session {:user nil},
                          :flash {:error
                                  {:title "Errors logging in"
                                   :message "Spec :nvim-app.specs/user invalid: value: 'null' in [:url] is invalid (ne-string?)"}}}

                         result)))))))))
