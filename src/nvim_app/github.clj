(ns nvim-app.github
  (:require
   [nvim-app.config :as config]
   [nvim-app.db.repo :as repo]
   [nvim-app.utils :refer [fetch-request]]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.core.async :as a]
   [clojure.tools.logging :as log])

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
  [{{:keys [owner createdAt stargazerCount
            repositoryTopics defaultBranchRef] :as node} :node}]

  (assoc (select-keys node [:name :url :description])
         :stars stargazerCount
         :owner (:login owner)
         :topics (map #(get-in % [:topic :name]) (:nodes repositoryTopics))
         :created (-> createdAt (Instant/parse) (Timestamp/from))
         :updated (or (some-> (get-in defaultBranchRef [:target :committedDate])
                              (Instant/parse)
                              (Timestamp/from))
                      (Timestamp/from (Instant/parse "1970-01-01T00:00:00Z")))))

(defn process-github-response [resp update-fn]
  (let [data (get-in resp [:body :data])
        errors (:errors resp)
        rate-limit (:rateLimit data)
        search (:search data)
        pageInfo (:pageInfo search)
        search-normalized (when-let [edges (:edges search)]
                            (map normalize-github-data edges))]
    (when search-normalized
      (log/info "Github: Downloaded" (count search-normalized) "repositories")
      (update-fn search-normalized))

    (when errors
      (log/warn "Github: Errors downloading repo data" errors))

    {:results search-normalized :errors errors
     :page-info pageInfo :rate-limit rate-limit}))

(defn should-retry-after?
  [{status :status headers :headers delay-ms :delay-ms
    {message :message} :errors :or {delay-ms 1000}}]

  (let [remaining (get headers "x-ratelimit-remaining")
        reset (get headers "x-ratelimit-reset")
        retry-after (get headers "retry-after")]

    (cond ;; TODO: add more conditions
      (or (= 503 status) (= 504 status)) delay-ms ; Service Unavailable or Timeout
      (or (and (= 200 status) (= "Rate limit exceeded" message))
          (and (= 403 status) (= "Rate limit exceeded" message))) delay-ms

      :else false)))

(defn retry-on-errors
  [fn & {:keys [retries] :or {retries 2}}]

  (loop [attempt 1]
    (let [response (fn)
          {:keys [status errors]} response
          delay (should-retry-after? response)]

      (if (and delay (< attempt retries))
        (do
          (log/warn "Retrying request due to errors:" status errors)
          (a/<! (a/timeout (* attempt delay)))
          (recur (inc attempt)))
        response))))

(defn process-github-response-async
  [query cursors-ch update-fn & {:keys [max-conccurency delay-ms]
                                 :or {max-conccurency 10 delay-ms 1000}}]

  (let [responses-ch (a/chan) results-ch (a/chan)]
    (a/pipeline-async
     max-conccurency responses-ch
     (fn [page-token out-ch]
       (a/go
         (a/<! (a/timeout (rand-int delay-ms)))
         (a/>! out-ch (retry-on-errors #(make-github-request (query page-token))))
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
        query-for #(partial build-query (get github-config %) search-str page-size)]

    (a/go (a/>! cursors-ch ""))
    (a/go-loop [page-token "" pages 1]
      (let [resp (make-github-request ((query-for :cursors-query) page-token))
            errors (:errors resp)
            pageInfo (get-in resp [:body :data :search :pageInfo])
            {:keys [hasNextPage endCursor]} pageInfo]

        (log/info "Github: fetching repositories with:" search-str page-token)

        (when endCursor (a/>! cursors-ch endCursor))
        (when errors (log/error "Github: Errors acquiring next cursor" errors))

        (if (and hasNextPage (< pages page-limit))
          (recur endCursor (inc pages))
          (a/close! cursors-ch))))

    (process-github-response-async (query-for :main-query) cursors-ch update-fn)))

(defn log-update-results [result start-time]
  (log/info "Github: Updated TOTAL:" (count (:results result)) "repositories in"
            (/ (- (System/currentTimeMillis) start-time) 1000.0) "s")

  (when (:errors result)
    (log/warn "Github: Errors TOTAL during update" (count (:errors result))))

  (log/info "Github: Rate limit remaining:" (get-in result [:rate-limit :remaining])))

(defn update-github-repos!
  "Updates repositories in the database based on the search terms.
   Splits search by year, to avoid hitting the max search results of 1000"
  ([]
   (let [search-terms ["topic:neovim topic:plugin" "topic:nvim topic:plugin"
                       "topic:neovim-plugin" "topic:nvim-plugin"]

         end-year (+ 1 (mod (.getYear (java.time.LocalDate/now)) 100))
         start-time (System/currentTimeMillis)
         result-chs (mapcat (fn [term]
                              (map #(update-github-repos!
                                     (format "%s created:20%s-01-01..20%s-01-01"
                                             term % (inc %)))
                                   (range 20 end-year)))
                            search-terms)]

     (doto (a/<!! (a/reduce #(merge-with into %1 %2) {} (a/merge result-chs)))
       (log-update-results start-time))))

  ([search-str]
   (search-github-async search-str
                        :page-size 100 :page-limit 1000
                        :update-fn repo/upsert-repos!)))

;; TODO: handle exceptions and close channels

(comment
  (update-github-repos!)
  (search-github-async "topic:neovim topic:plugin")
  (count (:results *1)))
   ; (user/ppn "\\\\n" (fetch-github-data)))
