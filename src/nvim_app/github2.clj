(ns nvim-app.github2
  (:require
   [nvim-app.config :as config]
   [nvim-app.db.core :as db]
   [nvim-app.utils :refer [fetch-request]]
   [clojure.java.io :as io]
   [cheshire.core :as json]
   [clojure.core.async :as a]
   [clojure.tools.logging :as log]
   [clojure.string :as str])
  (:import [java.time Instant]
           [java.sql Timestamp]))

(def github-config
  {:api-uri "https://api.github.com/graphql"
   :token (:token (:github (config/read-config)))
   :batch-size 100
   :main-query (slurp (io/resource "github/main.gql"))
   :cursors-query (slurp (io/resource "github/cursors.gql"))})

(defn build-query [query & params]
  (json/encode {:query (apply format query params)}))

(defn make-github-request [body]
  (fetch-request  {:method :post
                   :url (:api-uri github-config)
                   :content-type "application/json"
                   :headers {"Authorization" (str "Bearer " (:token github-config))}
                   :body body}))

(defn normalize-github-data
  [{{:keys [owner stargazerCount repositoryTopics defaultBranchRef] :as node} :node}]

  (assoc (select-keys node [:name :url :description])
         :stars stargazerCount
         :owner (:login owner)
         :topics (map #(get-in % [:topic :name]) (:nodes repositoryTopics))
         :updated (-> defaultBranchRef
                      (get-in [:target :committedDate])
                      (Instant/parse)
                      (Timestamp/from))))

(defn process-github-response [resp update-fn]
  (let [data (get-in resp [:body :data])
        errors (:errors resp)
        rate-limit (:rateLimit data)
        search (:search data)
        pageInfo (:pageInfo search)
        search-normalized (when-let [edges (:edges search)]
                            (map normalize-github-data edges))]
    (if search-normalized
      (log/info "Github: Downloaded" (count search-normalized) " repositories")
      (log/warn "Github: Errors downloading repo data" errors))

    (some-> search-normalized (update-fn))

    {:results search-normalized :errors errors
     :page-info pageInfo :rate-limit rate-limit}))

(defn should-retry-after?
  [{status :status headers :headers
    {message :message} :errors}]

  (let [remaining (get headers "x-ratelimit-remaining")
        reset (get headers "x-ratelimit-reset")
        retry-after (get headers "retry-after")]

    (cond ;; TODO: add more conditions
      (or (= 503 status) (= 504 status)) 1 ; Service Unavailable or Timeout
      (or (and (= 200 status) (= "Rate limit exceeded" message))
          (and (= 403 status) (= "Rate limit exceeded" message))) 1

      :else false)))

(defn retry-on-errors
  [fn & {:keys [retries delay-ms] :or {retries 2 delay-ms 1000}}]

  (loop [attempt 1]
    (let [response (fn)
          {:keys [status errors]} response
          delay (should-retry-after? response)]
      (if delay
        (do
          (log/warn "Retrying due to errors" status errors)
          (Thread/sleep (* attempt delay))

          (if (< attempt retries)
            (recur (inc attempt))
            response))

        response))))

(defn process-github-response-async
  [query cursors-ch update-fn & {:keys [max-conccurency delay-ms]
                                 :or {max-conccurency 5 delay-ms 1000}}]

  (let [responses-ch (a/chan) results-ch (a/chan)]

    (a/pipeline-async
     max-conccurency responses-ch
     (fn [page-token out-ch]
       (a/go
         (Thread/sleep (rand-int delay-ms))
         (when-let [response (retry-on-errors
                              #(make-github-request
                                (query page-token)))]
           (a/>! out-ch response))
         (a/close! out-ch)))

     cursors-ch
     true)

    (a/pipeline-async
     3 results-ch
     (fn [resp out-ch]
       (a/go
         (a/>! out-ch (process-github-response resp update-fn))
         (a/close! out-ch)))

     responses-ch
     true)

    results-ch))

(defn search-github-async
  [search-str & {:keys [page-size page-limit update-fn]
                 :or {page-size 2 page-limit 2 update-fn identity}}]

  (let [cursors-ch (a/chan)
        cursors-query (partial build-query (:cursors-query github-config)
                               search-str page-size)
        main-query (partial build-query (:main-query github-config)
                            search-str page-size)

        results-ch (process-github-response-async
                    main-query cursors-ch update-fn)]

    (a/go (a/>! cursors-ch ""))

    (loop [page-token "" pages 1]
      (let [resp (make-github-request (cursors-query page-token))
            errors (:errors resp)
            pageInfo (get-in resp [:body :data :search :pageInfo])
            {:keys [hasNextPage endCursor]} pageInfo]

        (if endCursor
          (a/go (a/>! cursors-ch endCursor))
          (log/error "Github: Errors acquiring next cursor" errors))

        (if (and hasNextPage (< pages page-limit))
          (recur endCursor (inc pages))
          (a/close! cursors-ch))))

    (a/<!!
     (a/reduce #(merge-with into %1 %2) {} results-ch))))

(defn update-repos! [repos]
  (let [result
        (for [{:keys [name owner url description
                      stars topics updated]} repos]
          (db/query-one!
           {:insert-into :repositories
            :values [{:name name
                      :owner owner
                      :url url
                      :description description
                      :stars stars
                      :topics (str/join " " topics)
                      :updated updated}]

            :on-conflict [:name :owner]
            :do-update-set {:stars stars
                            :topics (str/join " " topics)
                            :updated updated
                            :description description}}))]

    (log/info "Updated" (count result) "Github repos in DB")))

(defn update-github-repos! []
  (search-github-async "topic:neovim topic:plugin"))
                         ; :page-size 100 :limit 1000
                         ; :update-fn update-repos!)

(comment
  (update-github-repos!)
  (search-github-async "topic:neovim topic:plugin")
  (count (:results *1)))
  ; (user/ppn "\\\\n" (fetch-github-data)))
