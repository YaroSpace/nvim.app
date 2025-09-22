(ns nvim-app.github
  (:require
   [nvim-app.state :refer [app-config]]
   [nvim-app.db.core :as db]
   [nvim-app.db.repo :as repo]
   [nvim-app.specs :as specs]
   [nvim-app.awesome :as awesome]
   [nvim-app.utils :refer [fetch-request with-rpcall pretty-format]]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.core.async :as a]
   [clojure.tools.logging :as log]
   [clojure.string :as str]
   [clojure.instant :as inst])
  (:import
   [java.time LocalDate]))

(def github-config
  {:api-uri "https://api.github.com/graphql"
   :batch-size 100 ; max number of nodes per query
   :main-query (slurp (io/resource "github/main.gql")) ; main search query
   :cursors-query (slurp (io/resource "github/cursors.gql")) ; get endCursor for pagination
   :data-template (slurp (io/resource "github/data-template.gql")) ; template for data updated query
   :data-partial (slurp (io/resource "github/data-partial.gql"))}) ; partial for data updated query

(def processing-config
  {:request-retries 2   ; number of retries on errors
   :max-concurrency 10  ; max concurrent requests to GitHub
   :delay-ms 1000       ; max random delay before each request
   :async-buffer 10     ; async channels buffer size
   :async-workers 10})   ; number of async workers for processing responses

(defn build-graphql-query [query-type & params]
  (let [query (query-type github-config)]
    (json/encode {:query (apply format query params)})))

(defn make-github-request [body]
  (fetch-request
   {:method :post
    :url (:api-uri github-config)
    :content-type "application/json"
    :headers {"Authorization" (str "Bearer " (-> app-config :github :token))}
    :body body}))

(defn normalize-search-node
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
         :created (some-> createdAt inst/read-instant-timestamp)
         :updated (some-> (get-in defaultBranchRef [:target :committedDate])
                          inst/read-instant-timestamp)))

(defn normalize-data-response
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
               (map (fn [[_ repo-data]] (normalize-search-node {:node repo-data}))))}))

(defn process-search-response
  "
  Normalizes GitHub search response and processes results with update-fn.

  Arguments:
  - `resp`: response map `{:body {...} :errors [...]}`.
  - `update-fn`: function to process normalized results (default identity)

  Returns:
  `{:results [...], :errors [...], :page-info {...}, :rate-limit {...}}`,
  "
  ([resp]
   (process-search-response resp identity))
  ([resp update-fn]
   (let [data (get-in resp [:body :data])
         errors (:errors resp)
         rate-limit (:rateLimit data)
         search (:search data)
         pageInfo (:pageInfo search)
         search-normalized (when-let [edges (:edges search)]
                             (map normalize-search-node edges))]
     (when search-normalized
       (log/info "Github: Downloaded" (count search-normalized) "repositories")
       (update-fn search-normalized))

     (when errors
       (log/warn "Github: Errors downloading repo data\n" (pretty-format errors)))

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
   & {:keys [delay-ms] :or {delay-ms (:delay-ms processing-config)}}]

  (let [{:strs [retry-after]} headers ; x-ratelimit-remaining x-ratelimit-reset  
        delay (* delay-ms (parse-long (or retry-after "1")))]

    (cond
      (and (= 403 status) ; Rate limit exceeded
           (str/starts-with? (:message body) "You have exceeded a secondary rate limit"))
      delay

      (contains? #{502 503 504} status) ; Bad Gateway, Service Unavailable, Timeout
      delay

      :else false)))

(defn retry-on-errors-async
  "Retries request-fn on errors up to `retries` times with exponential backoff.

  Arguments:
  - `request-fn`: function that makes request and returns response map.
  - `retries`: max number of retries (default 2).

  Returns: response map from request-fn."

  [request-fn & {:keys [retries] :or {retries (:request-retries processing-config)}}]

  (a/go-loop [attempt 1]
    (let [response (with-rpcall (request-fn))
          {:keys [body errors]} response
          delay (should-retry-after? response)]

      (if (and delay (< attempt retries))
        (do
          (log/warnf "Retrying request #%s in %ss due to errors: %s\n%s"
                     attempt (/ delay 1000) (:message body) (pretty-format errors))
          (a/<! (a/timeout (* attempt delay)))
          (recur (inc attempt)))

        (or response
            {:errors [{:message (str "Request failed after retries: " attempt)}]})))))

(defn process-search-response-async
  "
  Makes request for each cursor from cursors-ch,
  normalizes and processes response with update-fn (e.g. updates in DB).

  Arguments:
  - `search-str`: GitHub search string.
  - `cursors-ch`: channel with GH page cursors (strings).
  - `:page-size` (Number): Number of results per page.
  - `:update-fn`: function that processes response.
  - `:delay-ms`: max random delay before each request (default 1000ms).

  Returns: a channel with normalized responses
  `{:results [...], :errors [...], :rate-limit {...}}`
  "
  [search-str cursors-ch
   & {:keys [page-size update-fn delay-ms]
      :or {page-size 2 update-fn identity
           delay-ms (:delay-ms processing-config)}}]

  (let [ab (:async-buffer processing-config)
        aw (:async-workers processing-config)
        responses-ch (a/chan ab) results-ch (a/chan ab)]

    (a/pipeline-async
     aw responses-ch
     (fn [page-cursor out-ch]
       (let [params [:main-query search-str page-size page-cursor]
             query (apply build-graphql-query params)]
         (a/go
           (a/<! (a/timeout (rand-int delay-ms)))
           (a/>! out-ch (a/<! (retry-on-errors-async #(make-github-request query))))
           (a/close! out-ch))))
     cursors-ch)

    (a/pipeline-async
     aw results-ch
     (fn [resp out-ch]
       (a/go
         (a/>! out-ch (or (with-rpcall (process-search-response resp update-fn))
                          {:errors [{:message "Failed to process GitHub response"}]}))
         (a/close! out-ch)))
     responses-ch)

    results-ch))

(defn fetch-search-cursors-async
  "
  Fetches GitHub search cursors asynchronously.

  Arguments:
  - `search-str`: GitHub search string.
  - `:page-size` (Number): Number of results per page.
  - `:page-limit` (Number): Max number of pages to fetch.

  Returns: a channel with GH page cursors (strings).
  "
  [search-str & {:keys [page-size page-limit]
                 :or {page-size 2 page-limit 2}}]

  (let [cursors-ch (a/chan (:async-buffer processing-config))]
    (a/go (a/>! cursors-ch "")) ; start with empty cursor

    (a/go-loop [page-cursor "" pages 1]
      (let [params [:cursors-query search-str page-size page-cursor]
            query (apply build-graphql-query params)
            resp (with-rpcall (make-github-request query))
            errors (:errors resp)
            pageInfo (get-in resp [:body :data :search :pageInfo])
            {:keys [hasNextPage endCursor]} pageInfo]

        (log/info "Github: fetching repositories with query:" search-str page-cursor)

        (when endCursor (a/>! cursors-ch endCursor))
        (when errors (log/error "Github: Errors acquiring next cursor\n" (pretty-format errors)))

        (if (and hasNextPage (< pages page-limit))
          (recur endCursor (inc pages))
          (a/close! cursors-ch))))

    cursors-ch))

(defn search-github-and-process-async
  "
  Searches GitHub repositories with search string,
  optionally processes results with update-fn (e.g. update in db).

  Arguments:
  - `search-str`: GitHub search string.
  - `:page-size` (Number): Number of results per page.
  - `:page-limit` (Number): Max number of pages to fetch.
  - `:update-fn` (Function): Function to process each page of results.

  Returns: a channel with normalized results
  - `{:results [...], :errors [...], :rate-limit {...}}`
  "
  [search-str & {:keys [] :as args}]
  (let [cursors-ch (fetch-search-cursors-async search-str args)]
    (process-search-response-async search-str cursors-ch args)))

(defn errors-summary
  "Formats errors summary by type and paths."
  [errors]
  (->> errors
       (keep (fn [e]
               (when-let [msg (:message e)]
                 (assoc e :message
                        (str (:path e) ":" (or (second (re-find #"'([^']+)'" msg)) msg))))))
       (group-by :type)
       (map (fn [[type errors]] (format "Type: %s\n%s" (or type "none")
                                        (str/join "\n" (map :message errors)))))
       (str/join "\n\n")))

(defn log-update-results
  [{:keys [time-elapsed total-results total-errors
           errors-summary rate-limit-remaining]}]

  (log/infof "Github: Updated TOTAL: %s repositories in %s s"
             total-results time-elapsed)

  (when (pos? total-errors)
    (log/error "Github: Errors TOTAL during update" total-errors)
    (when (seq errors-summary)
      (log/error "Github: Errors Summary\n" errors-summary)))

  (log/info "Github: Rate limit remaining:" rate-limit-remaining))

(defn processed-results-stats [result start-time]
  (let [{:keys [results errors rate-limit]} result
        errors (remove empty? errors)]

    {:time-elapsed (/ (- (System/currentTimeMillis) start-time) 1000.0)
     :total-results (count results)
     :total-errors (count errors)
     :errors-summary (when (seq errors) (errors-summary errors))
     :rate-limit-remaining (:remaining rate-limit)}))

(defn partition-search-by-year
  "Generate search strings split by year ranges to avoid 
   hitting GitHub search limit of 1000 results.

   Arguments:
    - `search-str`: GitHub search string.

   Returns: sequence of search strings split by year ranges, <16, 16 - next year."
  [search-str]

  (let [end-year (+ 1 (mod (.getYear (LocalDate/now)) 100))]
    (conj
     (map #(format "%s created:20%s-01-01..20%s-01-01" search-str % (inc %))
          (range 16 end-year))
     (format "%s created:<2016-01-01" search-str))))

(defn search-and-add-repos!
  "Searches for GitHub search string and adds GH repos to db.

   Arguments:
    - `search-strs`: list of GitHub search strings (default searches for Neovim plugins).

   Returns: `{:results [...], :errors [...], :rate-limit {...}}`
   "
  ([]
   (search-and-add-repos! ["topic:neovim topic:plugin" "topic:nvim topic:plugin"
                           "topic:neovim-plugin" "topic:nvim-plugin"]))
  ([search-strs]
   (let [start-time (System/currentTimeMillis)
         search-qrys (mapcat partition-search-by-year search-strs)
         search-params {:page-size 100 :page-limit 10 :update-fn repo/upsert-repos!}
         result-chs (map #(search-github-and-process-async % search-params) search-qrys)]

     (doto (a/<!! (a/reduce #(merge-with into %1 %2) {} (a/merge result-chs)))
       (-> (processed-results-stats start-time)
           log-update-results)))))

(defn create-from-awesome!
  "Creates a new repo from Awesome plugin data.
    Arguments:
      - `{:keys [repo url description category-id]}`: repo data map.
  
    Returns: update result map.
  "
  [plugin]
  (let [[owner name] (str/split (:repo plugin) #"/")]
    (-> plugin
        (merge {:owner owner
                :name (or name owner)
                :topics ["awesome"]})
        (dissoc :category)
        repo/upsert-repo!)))

(defn update-repos-from-awesome!
  "Updates GitHub repos from the Awesome list.
   Creates new repos and categories if they don't exist."
  []
  (->> (awesome/get-plugins)
       (map (fn [{:keys [url category] :as plugin}]
              (let [[_ owner name] (re-matches #".+github.com/([^/]+)/([^/#/?]+).*" url)
                    repo-id (:id (db/select-one :repos :where [:ilike :url (str "%github.com/" owner "/" name "%")]))
                    category-id (:id (or (db/select-one :categories :name category)
                                         (and (specs/conform! :category/name category)
                                              (db/insert! :categories [{:name category}]))))]
                (if repo-id
                  (db/update! :repos {:category_id category-id}
                              :where [:and [:= :id repo-id] [:= :dirty false]])
                  (create-from-awesome! (assoc plugin :category-id category-id))))))
       count
       (log/info "Github: Updated repositories from Awesome:")))

(defn update-stars
  "Updates :stars_week every Sunday, :stars_month - on the 1st of each month."
  [{:keys [stars] :as repo} & {:keys [today]}]

  (let [now (or today (LocalDate/now))
        dow (.getDayOfWeek now)
        dom (.getDayOfMonth now)]
    (cond-> repo
      (= "MONDAY" (.name dow)) (assoc :stars_week stars)
      (= 1 dom) (assoc :stars_month stars))))

(defn process-github-data-async
  "Processes GitHub data response, updates repos in DB
   and puts normalized response to `out-ch`.

   Arguments:
    - `resp`: response map `{:body {...} :errors [...]}`.
    - `out-ch`: output channel.
    - `:update-fn`: function to process normalized results, (default updates repos in DB).

   Returns: `out-ch` with normalized response
   "
  [resp out-ch & {:keys [update-fn]}]
  (a/go
    (when-let [resp-norm (with-rpcall (normalize-data-response resp))]
      (let [{:keys [results errors]} resp-norm]
        (doseq [repo results]
          (with-rpcall
            (if update-fn
              (update-fn repo)
              (-> repo update-stars repo/upsert-repo!))))

        (log/info "Github: Updated data for" (count results) "repos")
        (when errors
          (log/warn "Github: Errors updating repos data\n" (pretty-format errors))))

      (a/>! out-ch resp-norm))
    (a/close! out-ch))

  out-ch)

(defn process-data-queries-async
  "Executes data queries from queries-ch,
   normalizes responses and updates repos in DB.

   Arguments:
    - `queries-ch`: channel with data queries.

   Returns: a channel with normalized responses
   `{:results [...], :errors [...], :rate-limit {...}}`
   "
  [queries-ch]

  (let [ab (:async-buffer processing-config)
        aw (:async-workers processing-config)
        responses-ch (a/chan ab) results-ch (a/chan ab)]

    (a/pipeline-async
     aw responses-ch
     (fn [query out-ch]
       (a/go
         (a/<! (a/timeout (rand-int 1000)))
         (a/>! out-ch (a/<! (retry-on-errors-async #(make-github-request query))))
         (a/close! out-ch)))
     queries-ch)

    (a/pipeline-async
     aw results-ch
     process-github-data-async
     responses-ch)

    results-ch))

(defn get-data-queries-for
  "Generates data queries for given repos and puts them to queries-ch.

   Arguments:
    - `repos`: sequence of repo maps with `:id` and `:url`.

   Returns: channel with generated queries.
  "
  [repos]
  (->> repos
       (keep (fn [{:keys [id url]}]
               (when-let [[_ owner name] (re-matches #".+github\.com/([^/]+)/([^/#/?]+).*" url)]
                 (format (:data-partial github-config) id owner name))))
       (partition-all 100) ; Github allows up to 100 nodes per query
       (map #(->> % (str/join "") (build-graphql-query :data-template)))
       a/to-chan!))

(defn update-repos-data!
  "Updates data for GitHub repos in the database.

   Arguments:
    - `where`: update condition (default: non-hidden repos with GitHub URLs).

    Returns: `{:results [...], :errors [...], :rate-limit {...}}`
   "
  [& {:keys [where] :or {where [:and [:not= :hidden true]
                                [:ilike :url "%github.com%"]]}}]

  (when-let [queries-ch (get-data-queries-for (db/select :repos :where where))]

    (let [start-time (System/currentTimeMillis)
          results-ch (process-data-queries-async queries-ch)]

      (doto (a/<!! (a/reduce #(merge-with into %1 %2) {} results-ch))
        (-> (processed-results-stats start-time)
            log-update-results)))))

(defn delete-duplicate-repos! []
  (let [deleted (->> (concat
                      (repo/with-duplicate-urls)
                      (repo/with-duplicate-names)
                      (repo/renamed-repos))
                     (keep #(db/query-one! {:delete-from :repos
                                            :where [:and [:= :id %] [:= :topics "awesome"]]
                                            :returning [:id]})))]
    (log/info "Github: Deleted duplicate repositories:" (count deleted))))

(defn update-all!
  "Entry point to update all repos in DB."
  []
  (with-rpcall
    (search-and-add-repos!)
    (update-repos-from-awesome!)
    (delete-duplicate-repos!)
    (update-repos-data!)))

(comment
  (update-all!)
  (update-repos-data! :where [:in :id [10259 8306]])
  (take 2 (db/select :repos))
  (map completing)
  (a/<!! (search-github-and-process-async "lua-console"))
  (->> (db/select :repos)
       (mapcat #(str/split (:topics %) #" "))
       frequencies
       (sort-by val >)
       (take 40)))
