(ns nvim-app.integration.github-login-test
  (:require
   [nvim-app.utils :refer [fetch-request] :as u]
   [nvim-app.helpers :refer [setup-sut! silent-logging with-driver] :as h]
   [etaoin.api :as e]
   [lazytest.core :refer [defdescribe describe expect it]]
   [clojure.core.async :as a]
   [matcher-combinators.standalone :as m]))

(def driver (atom nil))
(def sut (atom nil))

(defn go [route & [params]]
  (e/go @driver (h/get-sut-url-for @sut route params)))

(defdescribe github-login-test
  {:context [silent-logging (setup-sut! sut driver :headless? true)]}
  (let [responses (a/to-chan! [{:status 200 :body {:access_token "valid-token"}}
                               {:status 200 :body {:id 1
                                                   :login "login"
                                                   :email "email"
                                                   :name "username"
                                                   :html_url "url"
                                                   :avatar_url "avatar_url"}}])]

    (with-driver driver
      (describe "Logs in with GitHub"
        (it "Successful login"
          (go :home)
          (click {:fn/has-class "github-login"})

          (let [url (e/js-execute @driver "return window.location.href")]
            (expect (m/match? #"login" url))

            (with-redefs [fetch-request (fn [& _] (a/<!! responses))]
              (go :github-callback {:code "valid-code"})

              (let [alert-title (get-element-text ".alert-title")
                    alert-message (get-element-text ".alert-message")]
                (expect (m/match? #"Login Successful" alert-title))
                (expect (m/match? #"Welcome, username!" alert-message))))))))))

(comment
  (with-driver driver
    (click :asda)))
    ; (e/js-execute @driver "return window.location.href")))
