(ns nvim-app.integration.editing-test
  (:require
   [nvim-app.utils :refer [fetch-request] :as u]
   [nvim-app.helpers :refer [setup-sut! silent-logging with-driver] :as h]
   [nvim-app.integration.core :refer :all]
   [lazytest.core :refer [defdescribe describe before expect it]]
   [clojure.core.async :as a]
   [matcher-combinators.standalone :as m]))

(def driver (atom nil))
(def sut (atom nil))
(def page (atom nil))

(def sleep 0.7)

(defn go-sut [route & [params]]
  (with-driver driver
    (go (h/get-sut-url-for @sut route params))))

(defn login! []
  (let [responses (a/to-chan! [{:status 200 :body {:access_token "valid-token"}}
                               {:status 200 :body {:id 10 :login "username" :html_url "url"}}])]
    (with-driver driver
      (with-redefs [fetch-request (fn [& _] (a/<!! responses))]
        (go-sut :github-callback {:code "valid-code"})))))

(defdescribe editing-test
  {:context [silent-logging (setup-sut! sut driver :headless? true)]}
  (with-driver driver
    (describe "Logged in user can edit plugins"
      (before
       (login!)
       (reset! page (get-repos-page driver)))

      (it "shows edit edit and watch buttons"
        (expect (m/match? {:repo-edit "" :repo-watch ""}
                          (-> @page :repos first))))

      (it "shows edit fields"
        (click {:fn/has-class "repo-edit"})
        (wait sleep)
        (reset! page (get-repos-page driver))

        (expect (m/match? {:repo-category-edit "Plugin Manager"
                           :repo-description-edit "Web dictionaries on the vim with async."
                           :repo-hidden-toggle "true"}
                          (-> @page :repos first)))))))

    ; (it "updates data")
    ; (it "toggles watch")))

(comment
  (with-driver driver
    (get-element-value-el (query {:id "repo-11"} {:fn/has-class "repo-hidden-toggle"}))))
    ; (get-element-value-el (query {:id "repo-11"} {:id "description-edit"}))))
    ; (get-element-value-el (query {:id "repo-11"} {:id "category-edit"}))))
    ; (e/js-execute @driver "return window.location.href")))
