(ns github-test
  (:require
   [nvim-app.github :refer :all]
   [clojure.core.async :as a]
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

      (a/go
        (let [result (a/<! (search-github-async "topic:neovim topic:plugin"))]
          (is (= {} result)))))))

(comment
  process-github-response-async
  search-github-async
  process-github-response-async
  update-repos-from-awesome!
  update-github-data!
  update-github-repos!
  delete-duplicate-repos!
  retry-on-errors-async
  should-retry-after?
  process-github-search-response)
