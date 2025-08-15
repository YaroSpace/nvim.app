(ns github-test
  (:require
   [nvim-app.github :refer :all]
   [clojure.test :refer :all]
   [clj-http.client :as http]
   [helpers :as h]))

(def response-error-syntax
  {:results nil,
   :errors
   [{:path ["query" "search" "sedges"],
     :extensions
     {:code "undefinedField",
      :typeName "SearchResultItemConnection",
      :fieldName "sedges"},
     :locations [{:line 3, :column 5}],
     :message
     "Field 'sedges' doesn't exist on type 'SearchResultItemConnection'"}
    {:path ["query" "search" "sedges"],
     :extensions
     {:code "undefinedField",
      :typeName "SearchResultItemConnection",
      :fieldName "sedges"},
     :locations [{:line 3, :column 5}],
     :message
     "Field 'sedges' doesn't exist on type 'SearchResultItemConnection'"}],
   :page-info nil,
   :rate-limit nil})

(deftest github-test
  (testing "Testing GitHub API integration"
    ; (with-redefs [http/request 
    ;                 (fn [_] {:status 200 :body {:errors {:message "Service Unavailable"}}})])

    (with-redefs [http/request
                  (fn [_] (throw (ex-info "Service Unavailable"
                                          {:status 503
                                           :reason-phrase "Service Unavailable"
                                           :body {:errors {:message "Check connection"}}})))]

      (let [result (search-github-async "topic:neovim topic:plugin")]
        (is (= {} result))))))

