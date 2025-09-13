(ns nvim-app.unit.github-test
  (:require
   [nvim-app.github :refer :all]
   [clojure.core.async :as a]
   [clojure.test :refer :all]
   [clj-http.fake :as fake :refer [with-fake-routes]]))

(def error-search-response
  {:errors
   [{:path ["query" "search" "sedges"],
     :extensions
     {:code "undefinedField",
      :typeName "SearchResultItemConnection",
      :fieldName "sedges"},
     :locations [{:line 3, :column 5}],
     :message
     "Field 'sedges' doesn't exist on type 'SearchResultItemConnection'"}

    {:path ["query" "search" "edges" "node" "... on Repository" "names"],
     :extensions
     {:code "undefinedField",
      :typeName "Repository",
      :fieldName "names"},
     :locations [{:line 6, :column 11}],
     :message "Field 'names' doesn't exist on type 'Repository'"}],
   :page-info nil,
   :rate-limit nil})

(def successful-search-response
  {:errors nil,
   :body
   {:data
    {:search
     {:edges
      [{:node
        {:description
         "A console-based mail-client with integrated Lua scripting support.",
         :defaultBranchRef
         {:target {:committedDate "2015-11-06T18:12:00Z"}},
         :name "lumail.obsolete",
         :stargazerCount 102,
         :isArchived true,
         :createdAt "2013-05-01T15:31:38Z",
         :repositoryTopics
         {:nodes
          [{:topic {:name "rust"}}
           {:topic {:name "python"}}
           {:topic {:name "lua"}}
           {:topic {:name "fantasy-console"}}
           {:topic {:name "game-engine"}}
           {:topic {:name "rhai"}}
           {:topic {:name "wasm"}}]},
         :url "https://github.com/lumail/lumail.obsolete",
         :owner {:login "lumail"}}}
       {:node
        {:description
         "An unofficial Doki Doki Literature Club port to Lua for the PS Vita and other game consoles",
         :defaultBranchRef
         {:target {:committedDate "2021-09-16T07:03:09Z"}},
         :name "DDLC-LOVE",
         :stargazerCount 281,
         :isArchived true,
         :createdAt "2018-04-17T16:27:05Z",
         :repositoryTopics {:nodes []},
         :url "https://github.com/LukeZGD/DDLC-LOVE",
         :owner {:login "LukeZGD"}}}],
      :pageInfo {:hasNextPage true, :endCursor "Y3Vyc29yOjY="}},
     :rateLimit {:cost 1, :remaining 4683}}}})

(deftest github-test
  (testing "Testing GitHub API integration"
    (with-fake-routes
      {#".*"
       (fn [_] (throw (ex-info "Service Unavailable"
                               {:status 503
                                :reason-phrase "Service Unavailable"
                                :body {:errors {:message "Check connection"}}})))}

      (let [result (a/<!! (search-github-async "topic:neovim topic:plugin"))]
        (is (= {:results nil
                :errors {:message "Check connection", :reason "Service Unavailable"},
                :page-info nil,
                :rate-limit nil} result))))))

(deftest search-github-async-test
  (testing "Testing GitHub Search"
    (a/<!! (search-github-async "lua-console.nvim"))))

(deftest process-github-response-async-test
  (testing "Testing GitHub Search"))
    ; (a/<!! (process-github-response-async "lua-console.nvim"))))

(deftest process-github-search-response-test
  (testing "Successful response"
    (let [result (process-github-search-response successful-search-response)]
      (is (=
           {:page-info {:endCursor "Y3Vyc29yOjY=", :hasNextPage true},
            :rate-limit {:cost 1, :remaining 4683}}
           (select-keys result [:page-info :rate-limit])))

      (is (= 2 (count (:results result))))
      (is (= {:archived true,
              :created #inst "2013-05-01T15:31:38.000000000-00:00",
              :description "A console-based mail-client with integrated Lua scripting support.",
              :name "lumail.obsolete",
              :owner "lumail",
              :stars 102,
              :topics ["rust" "python" "lua" "fantasy-console" "game-engine" "rhai" "wasm"],
              :updated #inst "2015-11-06T18:12:00.000000000-00:00",
              :url "https://github.com/lumail/lumail.obsolete"}
             (first (:results result))))))

  (testing "Error response"
    (let [result (process-github-search-response error-search-response)]
      (is (= {:results nil
              :errors [{:message "Field 'sedges' doesn't exist on type 'SearchResultItemConnection'",
                        :locations [{:line 3, :column 5}],
                        :path ["query" "search" "sedges"]
                        :extensions {:code "undefinedField", :fieldName "sedges", :typeName "SearchResultItemConnection"}}
                       {:message "Field 'names' doesn't exist on type 'Repository'",
                        :locations [{:line 6, :column 11}],
                        :extensions {:code "undefinedField", :fieldName "names", :typeName "Repository"}
                        :path ["query" "search" "edges" "node" "... on Repository" "names"]}],
              :page-info nil
              :rate-limit nil}
             result)))))

(deftest normalize-github-data-response-test)

(deftest normalize-github-node-test)
