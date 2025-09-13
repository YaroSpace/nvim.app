(ns nvim-app.github
  (:require
   [nvim-app.config :as config]
   [nvim-app.db.core :as db]
   [nvim-app.db.repo :as repo]
   [nvim-app.awesome :as awesome]
   [nvim-app.utils :refer [fetch-request rpcall]]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.core.async :as a]
   [clojure.tools.logging :as log]
   [clojure.string :as str])

  (:import
   [java.time Instant LocalDate]
   [java.sql Timestamp]))

(def github-config
  {:api-uri "https://api.github.com/graphql"
   :token (-> (config/read-config) :github :token)
   :batch-size 100
   :main-query (slurp (io/resource "github/main.gql")) ; main search query
   :cursors-query (slurp (io/resource "github/cursors.gql")) ; get endCursor for pagination
   :data-template (slurp (io/resource "github/data-template.gql")) ; template for data updated query
   :data-partial (slurp (io/resource "github/data-partial.gql"))}) ; partial for data updated query

(defn build-query [query & params]
  (json/encode {:query (apply format query params)}))

(defn make-github-request [body]
  ; (user/tap>> {:body body}
  (fetch-request
   {:method :post
    :url (:api-uri github-config)
    :content-type "application/json"
    :headers {"Authorization" (str "Bearer " (:token github-config))}
    :body body}))

(defn normalize-github-node
  "Normalizes GitHub node response.

  Arguments:
   - `node`: `{:node ...}` map with GitHub node data.

   Returns:
   `{:name ... :url ... :description ... :stars ... :owner ... :archived ...
    :topics [...] :created ... :updated ...}`"
  [{{:keys [owner createdAt stargazerCount isArchived
            repositoryTopics defaultBranchRef] :as node} :node}]

  (assoc (select-keys node [:name :url :description])
         :stars stargazerCount
         :owner (:login owner)
         :archived isArchived
         :topics (map #(get-in % [:topic :name]) (:nodes repositoryTopics))
         :created (some-> createdAt (Instant/parse) (Timestamp/from))
         :updated (some-> (get-in defaultBranchRef [:target :committedDate])
                          (Instant/parse)
                          (Timestamp/from))))

(defn normalize-github-data-response
  "Normalizes GitHub response.

   Arguments:
   - `resp`: response map `{:body {...} :errors [...]}`.

   Returns: `{:results [...], :errors [...], :rate-limit {...}}`."
  [resp]
  (let [data (get-in resp [:body :data])]
    {:errors (:errors resp)
     :rate-limit (:rateLimit data)
     :results (some->>
               data
               (remove (fn [[repo repo-data]] (or (= :rateLimit repo)
                                                  (nil? repo-data))))
               (map (fn [[_ repo-data]] (normalize-github-node {:node repo-data}))))}))

(defn process-github-search-response
  "
  Normalizes GitHub search response and processes results with update-fn.

  Arguments:
  - `resp`: response map `{:body {...} :errors [...]}`.
  - `update-fn`: function that processes normalized results (default identity)

  Returns:
  `{:results [...], :errors [...], :page-info {...}, :rate-limit {...}}`,
  "
  ([resp]
   (process-github-search-response resp identity))
  ([resp update-fn]
   (let [data (get-in resp [:body :data])
         errors (:errors resp)
         rate-limit (:rateLimit data)
         search (:search data)
         pageInfo (:pageInfo search)
         search-normalized (when-let [edges (:edges search)]
                             (map normalize-github-node edges))]
     (when search-normalized
       (log/info "Github: Downloaded" (count search-normalized) "repositories")
       (update-fn search-normalized))

     (when errors
       (log/warn "Github: Errors downloading repo data" errors))

     {:results search-normalized :errors errors
      :page-info pageInfo :rate-limit rate-limit})))

(defn should-retry-after?
  "Determines if a request should be retried based on the response.

   Arguments:
   - `response`: map `{:status ... :headers {...} :body {...} :errors {...}}`
   - `delay-ms`: base delay in ms (default 1000ms)

   Returns: delay in ms if should retry, otherwise false.
   "

  [{:keys [status headers body]}
   & {:keys [delay-ms] :or {delay-ms 1000}}]

  (let [{:strs [retry-after]} headers ; x-ratelimit-remaining x-ratelimit-reset  
        message-body (:message body)
        delay (* delay-ms (parse-long (or retry-after "1")))]

    (cond
      ; Rate limit exceeded
      (and (= 403 status)
           (str/starts-with? message-body "You have exceeded a secondary rate limit"))
      delay

      ; Service Unavailable or Timeout
      (contains? #{503 504} status)
      delay

      :else false)))

(defn retry-on-errors-async
  "Retries request-fn on errors up to `retries` times with exponential backoff.

  Arguments:
  - `request-fn`: function that makes request and returns response map.
  - `retries`: max number of retries (default 2).

  Returns: response map from request-fn."

  [request-fn & {:keys [retries] :or {retries 2}}]

  (a/go-loop [attempt 1]
    (let [response (rpcall request-fn)
          {:keys [body errors]} response
          delay (should-retry-after? response)]

      (if (and delay (< attempt retries))
        (do
          (log/warn "Retrying request #" attempt "in" (/ delay 1000) "s due to errors:"
                    (:message body) errors)
          (a/<! (a/timeout (* attempt delay)))
          (recur (inc attempt)))

        response))))

(defn process-github-response-async
  "
  Makes request for each cursor from cursors-ch using query function,
  normalizes and processes response with update-fn (e.g. updates in DB).

  Arguments:
  - `query`: function that takes GH page cursor and returns json payload with GraphQL query.
  - `cursors-ch`: channel with GH page cursors (strings).
  - `update-fn`: function that processes response.
  - `max-conccurency`: max number of concurrent requests (default 10).
  - `delay-ms`: max random delay before each request (default 1000ms).

  Returns: channel with combined results
  `{:results [...], :errors [...], :page-info {...}, :rate-limit {...}}`
  "
  [query cursors-ch update-fn & {:keys [max-conccurency delay-ms]
                                 :or {max-conccurency 10 delay-ms 1000}}]

  (let [responses-ch (a/chan) results-ch (a/chan)]
    (a/pipeline-async
     max-conccurency responses-ch
     (fn [page-token out-ch]
       (a/go
         (a/<! (a/timeout (rand-int delay-ms)))
         (a/>! out-ch (a/<! (retry-on-errors-async
                             #(make-github-request (query page-token)))))
         (a/close! out-ch)))
     cursors-ch)

    (a/pipeline-async
     5 results-ch
     (fn [resp out-ch]
       (a/go
         (a/>! out-ch (rpcall process-github-search-response resp update-fn))
         (a/close! out-ch)))
     responses-ch)

    results-ch))

(defn search-github-async
  "
  Searches GitHub repositories using provided search string,
  optionally processes results with update-fn (e.g. update in DB).

  Arguments:
  - `search-str`: GitHub search string.
  - `page-size` (Number): Number of results per page.
  - `page-limit` (Number): Max number of pages to fetch.
  - `update-fn` (Function): Function to process each page of results.

  Returns: a channel with combined results
  - `{:results [...], :errors [...], :page-info {...}, :rate-limit {...}}`
  "
  [search-str & {:keys [page-size page-limit update-fn]
                 :or {page-size 2 page-limit 2 update-fn identity}}]

  (let [cursors-ch (a/chan)
        query-for #(partial build-query (get github-config %) search-str page-size)]

    (a/go (a/>! cursors-ch ""))
    (a/go-loop [page-token "" pages 1]
      (let [resp (rpcall make-github-request ((query-for :cursors-query) page-token))
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

(defn errors-summary
  "Formats errors summary by type and paths."
  [errors]

  (->> errors
       (group-by :type)
       (map (fn [[type errors]]
              [type (str/join "\n" (map #(str (:path %) ":" (second (re-find #"'(.+)'" (:message % ""))))
                                        errors))]))
       (map (fn [[type msgs]]
              (str "Type: " type "\n" msgs)))
       (str/join "\n\n")))

(defn log-update-results [result start-time]
  (let [{:keys [results errors rate-limit]} result
        errors (remove empty? errors)]
    (log/info "Github: Updated TOTAL:" (count results) "repositories in"
              (/ (- (System/currentTimeMillis) start-time) 1000.0) "s")

    (when (seq errors)
      (log/error "Github: Errors TOTAL during update" (count errors))
      (log/error "Github: Errors Summary\n" (errors-summary errors)))

    (log/info "Github: Rate limit remaining:" (:remaining rate-limit))))

(defn update-github-repos!
  "Updates repositories in the database.
   Splits search by year, to avoid hitting the max search results of 1000.

   Arguments:
    - `search-str`: GitHub search string (default searches for Neovim plugins).

   Returns: `{:results [...], :errors [...], :page-info {...}, :rate-limit {...}}`
   "
  ([]
   (let [search-terms ["topic:neovim topic:plugin" "topic:nvim topic:plugin"
                       "topic:neovim-plugin" "topic:nvim-plugin"]

         end-year (+ 1 (mod (.getYear (java.time.LocalDate/now)) 100))
         start-time (System/currentTimeMillis)
         result-chs (mapcat (fn [term]
                              (conj
                               (map #(update-github-repos!
                                      (format "%s created:20%s-01-01..20%s-01-01"
                                              term % (inc %)))
                                    (range 16 end-year))
                               (update-github-repos! (format "%s created:<2016-01-01" term))))
                            search-terms)]

     (doto (a/<!! (a/reduce #(merge-with into %1 %2) {} (a/merge result-chs)))
       (log-update-results start-time))))

  ([search-str]
   (search-github-async search-str
                        :page-size 100 :page-limit 1000
                        :update-fn repo/upsert-repos!)))

(defn create-from-awesome!
  "Creates a new repo from Awesome plugin data.
    Arguments:
      - `{:keys [repo url description category-id]}`: repo data map.
  
    Returns: update result map.
  "

  [{:keys [repo url description category-id]}]

  (let [[owner name] (str/split repo #"/")]
    (repo/upsert-repo!
     {:owner owner
      :name (or name owner)
      :url (or url "")
      :description (or description "")
      :topics ["awesome"]
      :category_id category-id})))

(defn update-repos-from-awesome!
  "Updates GitHub repos from the Awesome list.
   Creates new repos and categories if they don't exist."
  []
  (->> (awesome/get-plugins)
       (map (fn [{:keys [url category] :as plugin}]
              (let [[_ owner name] (re-matches #".+github.com/([^/]+)/([^/#/?]+).*" url)
                    repo-id (:id (db/select-one :repos :where [:ilike :url (str "%github.com/" owner "/" name "%")]))
                    category-id (:id (or (db/select-one :categories :name category)
                                         (db/insert! :categories :values [{:name category}])))]
                (if repo-id
                  (db/update! :repos
                              :where [:and [:= :id repo-id] [:= :dirty false]]
                              :values {:category_id category-id})
                  (create-from-awesome! (assoc plugin :category-id category-id))))))
       (count)
       (log/info "Github: Updated repositories from Awesome:")))

(defn update-stars
  "Updates :stars_week every Sunday, :stars_month - on the 1st of each month."
  [{:keys [stars] :as repo}]

  (let [now (LocalDate/now)
        dow (.getDayOfWeek now)
        dom (.getDayOfMonth now)]
    (cond-> repo
      (= "SUNDAY" (.name dow)) (assoc :stars_week stars)
      (= 1 dom) (assoc :stars_month stars))))

(defn process-github-data-async
  "Processes GitHub data, updates repos in DB
   and puts normalized response to `out-ch`.

   Arguments:
    - `resp`: response map `{:body {...} :errors [...]}`.
    - `out-ch`: output channel.
   "
  [resp out-ch]
  (a/go
    (let [resp-norm (normalize-github-data-response resp)
          {:keys [results errors]} resp-norm]
      (rpcall
       (fn []
         (doseq [repo results]
           (-> repo
               (update-stars)
               (repo/upsert-repo!)))))

      (log/info "Github: Updated data for" (count results) "repos")
      (when errors
        (log/warn "Github: Errors updating repos data" errors))

      (a/>! out-ch resp-norm))
    (a/close! out-ch)))

(defn update-github-data!
  "Updates GitHub data for repos in the database.

   Arguments:
    - `where`: update condition (default: non-hidden repos with GitHub URLs).

    Returns: `{:results [...], :errors [...], :page-info {...}, :rate-limit {...}}`
   "

  [& {:keys [where] :or {where [:and [:not= :hidden true]
                                [:ilike :url "%github.com%"]]}}]
  (let [queries-ch (a/chan) responses-ch (a/chan) results-ch (a/chan)
        start-time (System/currentTimeMillis)]
    (->>
     (db/select :repos :where where)
     (partition-all 100)
     (map #(->> %
                (reduce (fn [acc {:keys [id url]}]
                          (let [[_ owner name] (re-matches #".+github\.com/([^/]+)/([^/#/?]+).*" url)]
                            (str acc (format (:data-partial github-config) id owner name))))
                        "")
                (build-query (:data-template github-config))))
     (a/onto-chan! queries-ch))

    (a/pipeline-async
     5 responses-ch
     (fn [query out-ch]
       (a/go
         (a/<! (a/timeout (rand-int 1000)))
         (a/>! out-ch (a/<! (retry-on-errors-async #(make-github-request query))))
         (a/close! out-ch)))
     queries-ch)

    (a/pipeline-async 3 results-ch process-github-data-async responses-ch)

    (doto (a/<!! (a/reduce #(merge-with into %1 %2) {} results-ch))
      (log-update-results start-time))))

(defn delete-duplicate-repos! []
  (->> (concat
        (repo/with-duplicate-urls)
        (repo/with-duplicate-names)
        (repo/renamed-repos))
       (keep #(db/query-one! {:delete-from :repos
                              :where [:and [:= :id %] [:= :topics "awesome"]]
                              :returning [:id]}))
       (count)
       (log/info "Github: Deleted duplicate repositories:")))

(defn update-all!
  "Entry point to update all repos in DB."
  []
  (rpcall
   (fn []
     (update-github-repos!)
     (update-repos-from-awesome!)
     (delete-duplicate-repos!)
     (update-github-data!))))

(comment
  (update-all!)
  (map completing)
  (a/<!! (search-github-async "lua-console"))
  (->> (db/select :repos)
       (mapcat #(str/split (:topics %) #" "))
       (frequencies)
       (sort-by val >)
       (take 40)))

