(ns nvim-app.unit.github-test
  (:require
   [nvim-app.github :refer :all :as github]
   [nvim-app.utils :refer [json-parse]]
   [nvim-app.db.core :as db]
   [nvim-app.helpers :refer [with-database-system silent-logging]]
   [clojure.core.async :as a]
   [lazytest.core :refer [defdescribe describe expect it] :as lt]))

(defn drain-channel [ch]
  (loop [results []]
    (if-let [v (a/<!! ch)]
      (recur (conj results v))
      results)))

(def error-search-response
  {:errors
   [{:type "undefinedField"
     :path ["query" "search" "sedges"],
     :locations [{:line 3, :column 5}],
     :message
     "Field 'sedges' doesn't exist on type 'SearchResultItemConnection'"}

    {:type "undefinedField"
     :path ["query" "search" "sedges"]
     :message "Field 'sedges' doesn't exist"}

    {:path ["query" "search" "edges" "node" "... on Repository" "names"],
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

(def successful-data-response
  {:body
   {:data
    {:ID6456
     {:description
      " 🚧 A Neovim plugin that facilitates the identification of a HEX, RGB, HSL, or LCH color name and its conversion.",
      :defaultBranchRef
      {:target {:committedDate "2024-09-30T23:15:05Z"}},
      :name "CSSColorConverter",
      :stargazerCount 0,
      :isArchived false,
      :createdAt "2023-12-05T16:50:17Z",
      :repositoryTopics
      {:nodes [{:topic {:name "lua"}} {:topic {:name "nvim-plugin"}}]},
      :url "https://github.com/farias-hecdin/CSSColorConverter",
      :owner {:login "farias-hecdin"}},
     :ID6516
     {:description
      "When you don't need a debugger. Quickly evaluate code and view output as virtual lines.",
      :defaultBranchRef
      {:target {:committedDate "2025-08-12T14:01:52Z"}},
      :name "itchy.nvim",
      :stargazerCount 3,
      :isArchived false,
      :createdAt "2025-05-06T00:41:54Z",
      :repositoryTopics
      {:nodes
       [{:topic {:name "debugging-tools"}}
        {:topic {:name "neovim-plugin"}}
        {:topic {:name "neovim"}}
        {:topic {:name "nvim"}}
        {:topic {:name "nvim-plugin"}}
        {:topic {:name "code-runner"}}
        {:topic {:name "repl"}}]},
      :url "https://github.com/joncrangle/itchy.nvim",
      :owner {:login "joncrangle"}},
     :rateLimit {:cost 1, :remaining 4999}}}})

(defdescribe process-github-search-response-test
  {:context [silent-logging]}
  (it "Successful response"
    (let [process-result (atom [])
          process-fn (fn [resp] (swap! process-result into (map :owner resp)))
          result (process-search-response successful-search-response process-fn)]

      (expect (=
               {:page-info {:endCursor "Y3Vyc29yOjY=", :hasNextPage true},
                :rate-limit {:cost 1, :remaining 4683}}
               (select-keys result [:page-info :rate-limit])))

      (expect (= 2 (count (:results result))))
      (expect (= {:archived true,
                  :created #inst "2013-05-01T15:31:38.000000000-00:00",
                  :description "A console-based mail-client with integrated Lua scripting support.",
                  :name "lumail.obsolete",
                  :owner "lumail",
                  :stars 102,
                  :topics ["rust" "python" "lua" "fantasy-console" "game-engine" "rhai" "wasm"],
                  :updated #inst "2015-11-06T18:12:00.000000000-00:00",
                  :url "https://github.com/lumail/lumail.obsolete"}
                 (first (:results result))))

      (expect (= ["lumail" "LukeZGD"] @process-result))))

  (it "Error response"
    (let [result (process-search-response error-search-response)]
      (expect (= {:results nil
                  :errors
                  [{:type "undefinedField"
                    :path ["query" "search" "sedges"],
                    :locations [{:line 3, :column 5}],
                    :message "Field 'sedges' doesn't exist on type 'SearchResultItemConnection'"}

                   {:type "undefinedField"
                    :path ["query" "search" "sedges"]
                    :message "Field 'sedges' doesn't exist"}

                   {:path ["query" "search" "edges" "node" "... on Repository" "names"],
                    :locations [{:line 6, :column 11}],
                    :message "Field 'names' doesn't exist on type 'Repository'"}]
                  :page-info nil
                  :rate-limit nil}

                 result)))))

(defdescribe search-and-add-repos-test
  {:context [silent-logging]}
  (it "Success, merges results from multiple queries"
    (with-redefs [search-github-and-process-async
                  (fn [query & _] (a/go {:results [{:query query}]
                                         :errors [{:message query}]
                                         :rate-limit {:remaining 500}}))]

      (let [result (search-and-add-repos! ["query1" "query2"])]
        (expect (= 22 (count (:results result)))) ; 2 queries * split by 11 (<16, 16-26)
        (expect (= 22 (count (:errors result))))
        (expect (= {:remaining 500} (:rate-limit result)))))))

(defdescribe errors-summary-test
  (it "Testing error summary formatting"
    (expect (= "Type: undefinedField\n[\"query\" \"search\" \"sedges\"]:sedges\n[\"query\" \"search\" \"sedges\"]:sedges\n\nType: none\n[\"query\" \"search\" \"edges\" \"node\" \"... on Repository\" \"names\"]:names"
               (errors-summary (:errors error-search-response))))))

(defdescribe fetch-search-cursors-async-test
  {:context [silent-logging]}
  (it "Returns channel with cursors"
    (let [responses
          (a/to-chan! (for [[cursor has-next] [["cursor1" true] ["cursor2" false]]]
                        {:body {:data {:search {:pageInfo {:endCursor cursor
                                                           :hasNextPage has-next}}}}}))]

      (with-redefs [make-github-request (fn [_] (a/<!! responses))]
        (let [result (drain-channel (fetch-search-cursors-async "lua-console"))]
          (expect (= ["" "cursor1" "cursor2"] result))))))

  (describe "Failed request"))

(defdescribe process-github-response-async-test
  {:context [silent-logging]}
  (it "Returns channel with processed responses"
    (let [cursors-ch (doto (a/to-chan! ["" "cursor1"]))
          responses (a/to-chan! [successful-search-response
                                 successful-search-response])]

      (with-redefs [make-github-request (fn [_] (a/<!! responses))]
        (let [result (drain-channel (process-search-response-async "lua-console" cursors-ch))]
          (expect (= 2 (count result)))
          (expect (= 4 (count (:results (apply merge-with into result)))))
          (expect (= {:archived true,
                      :created #inst "2018-04-17T16:27:05.000-00:00",
                      :description "An unofficial Doki Doki Literature Club port to Lua for the PS Vita and other game consoles",
                      :name "DDLC-LOVE",
                      :owner "LukeZGD",
                      :stars 281,
                      :topics '(),
                      :updated #inst "2021-09-16T07:03:09.000-00:00",
                      :url "https://github.com/LukeZGD/DDLC-LOVE"}

                     (-> result last :results last)))))))

  (describe "Failed request")

  (it "Failed processing"
    (let [cursors-ch (doto (a/to-chan! [""]))
          responses (a/to-chan! [successful-search-response])
          process-fn (fn [_] (throw (ex-info "Processing failed" {})))]

      (with-redefs [make-github-request (fn [_] (a/<!! responses))]
        (let [result (drain-channel (process-search-response-async "lua-console" cursors-ch
                                                                   :update-fn process-fn))]
          (expect (= "Failed to process GitHub response"
                     (-> result first :errors first :message))))))))

(defdescribe get-data-queries-for-test
  (let [repos [{:id 1 :url "https://github.com/owner1/repo1"}
               {:id 2 :url "https://github.com/owner2/repo2"}]
        result (-> repos get-data-queries-for drain-channel first json-parse :query)]

    (it "Generates correct query"
      (expect (= "query {\n  ID1: repository(owner: \"owner1\", name: \"repo1\") { \n  stargazerCount\n  description\n  owner { login }\n  name\n  createdAt\n  isArchived\n  url\n  repositoryTopics(first: 20) {\n    nodes {\n      topic {\n        name\n      }\n    }\n  }\n  defaultBranchRef {\n    target {\n      ... on Commit {\n        committedDate\n      }\n    }\n  }\n}\n\nID2: repository(owner: \"owner2\", name: \"repo2\") { \n  stargazerCount\n  description\n  owner { login }\n  name\n  createdAt\n  isArchived\n  url\n  repositoryTopics(first: 20) {\n    nodes {\n      topic {\n        name\n      }\n    }\n  }\n  defaultBranchRef {\n    target {\n      ... on Commit {\n        committedDate\n      }\n    }\n  }\n}\n\n \n  rateLimit { cost remaining }\n}\n"
                 result)))))

(defdescribe process-data-queries-async-test
  {:context [silent-logging]}
  (let [queries-ch (a/to-chan! [{:query "query1"} {:query "query2"}])
        responses (a/to-chan! [{:body {:data {:ID1 {:stargazerCount 10}
                                              :ID2 {:stargazerCount 20}}}}
                               {:body {:data {:ID3 {:stargazerCount 30}
                                              :ID4 {:stargazerCount 40}}}}])]

    (with-redefs [make-github-request (fn [_] (a/<!! responses))
                  process-github-data-async (fn [resp ch]
                                              (a/put! ch resp)
                                              (a/close! ch))]
      (it "Processes all queries"
        (let [result (drain-channel (process-data-queries-async queries-ch))]
          (expect (= 2 (count result))))))))

(defdescribe normalize-data-response-test
  {:context [silent-logging]}
  (let [result (normalize-data-response successful-data-response)]
    (it "Normalizes data correctly"
      (expect (=
               {:errors nil,
                :rate-limit {:cost 1, :remaining 4999},
                :results [{:archived false,
                           :created #inst "2023-12-05T16:50:17.000-00:00",
                           :description " 🚧 A Neovim plugin that facilitates the identification of a HEX, RGB, HSL, or LCH color name and its conversion.",
                           :name "CSSColorConverter",
                           :owner "farias-hecdin",
                           :stars 0,
                           :topics '("lua" "nvim-plugin"),
                           :updated #inst "2024-09-30T23:15:05.000-00:00",
                           :url "https://github.com/farias-hecdin/CSSColorConverter"}
                          {:archived false,
                           :created #inst "2025-05-06T00:41:54.000-00:00",
                           :description "When you don't need a debugger. Quickly evaluate code and view output as virtual lines.",
                           :name "itchy.nvim",
                           :owner "joncrangle",
                           :stars 3,
                           :topics '("debugging-tools" "neovim-plugin" "neovim" "nvim" "nvim-plugin" "code-runner" "repl"),
                           :updated #inst "2025-08-12T14:01:52.000-00:00",
                           :url "https://github.com/joncrangle/itchy.nvim"}]}

               result)))))
(defdescribe process-github-data-async-test
  {:context [silent-logging]}
  (it "Processes GitHub data asynchronously"
    (let [out-ch (a/chan)
          response successful-data-response
          result (a/<!! (process-github-data-async response out-ch :update-fn list))]
      (expect (= 2 (count (:results result)))))))

(defdescribe update-repos-data!-test
  {:context [silent-logging]}
  (it "Merges results from multiple updates"
    (let [queries-ch (a/to-chan! [{:query "query1"} {:query "query2"}])
          results-ch (a/to-chan! [{:errors [{:message "error1"}]
                                   :rate-limit {:cost 1, :remaining 4999},
                                   :results [{:archived false,
                                              :name "CSSColorConverter",
                                              :owner "farias-hecdin"}]}
                                  {:errors [{:message "error2"}],
                                   :rate-limit {:cost 1, :remaining 4999},
                                   :results [{:archived false}
                                             :name "itchy.nvim",
                                             :owner "joncrangle"]}])]

      (with-redefs [db/select list
                    get-data-queries-for (fn [_] queries-ch)
                    process-data-queries-async (fn [_] results-ch)]
        (let [result (update-repos-data!)]
          (expect (= {:errors [{:message "error1"} {:message "error2"}],
                      :rate-limit {:cost 1, :remaining 4999},
                      :results [{:archived false, :name "CSSColorConverter", :owner "farias-hecdin"}
                                {:archived false}
                                :name
                                "itchy.nvim"
                                :owner
                                "joncrangle"]}
                     result)))))))

(defdescribe update-stars-test
  (it "Updates stars correctly"
    (let [repos [{:id 1 :stars_week 10 :stars 50}
                 {:id 2 :stars_month 10 :stars 50}]]

      (expect (= 50 (:stars_week (update-stars (first repos) :today (java.time.LocalDate/of 2025 9 15)))))
      (expect (= 50 (:stars_month (update-stars (second repos) :today (java.time.LocalDate/of 2025 9 1))))))))

(defdescribe retry-on-errors-async-test
  {:context [silent-logging]}
  (it "Retry"
    (let [responses (a/to-chan! [{:status 503 :errors "Service Unavailable"
                                  :body {:message "Try later"}}
                                 {:status 200}])
          calls (atom 0)
          result (retry-on-errors-async #(do (swap! calls inc) (a/<!! responses)))]

      (expect (= 200 (:status (a/<!! result))))
      (expect (= 2 @calls))))

  (it "Failure"
    (let [result (retry-on-errors-async #(throw (ex-info "Failed" {})))]
      (expect (= {:errors [{:message "Request failed after retries: 1"}]}
                 (a/<!! result))))))

(defdescribe update-repos-from-awesome!-test)

(defdescribe create-from-awesome!-test
  {:context [silent-logging]}
  (it "pending"
    (with-database-system sut
      (let [ds (:database-component sut)
            result (count (db/query! ds {:select :* :from [:repos]}))]
        (expect (= 0 result))))))
