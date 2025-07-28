(ns nvim-app.github2
  (:require
   [nvim-app.config :as config]
   [nvim-app.db.core :as db]
   [nvim-app.utils :refer [fetch-request]]
   [clojure.java.io :as io]
   [cheshire.core :as json]
   [clojure.tools.logging :as log]
   [clojure.string :as str])
  (:import [java.time Instant]
           [java.sql Timestamp]))

(def github-config
  {:api-uri "https://api.github.com/graphql"
   :token (:token (:github (config/read-config)))
   :batch-size 100
   :main-query (slurp (io/resource "github/main.graphql"))})

(defn build-query [query & params]
  (json/encode {:query (apply format query params)}))

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

(defn make-github-request [body]
  (fetch-request  {:method :post
                   :url (:api-uri github-config)
                   :content-type "application/json"
                   :headers {"Authorization" (str "Bearer " (:token github-config))}
                   :body body}))

(defn search-github
  [search-str & {:keys [page-size limit update-fn]
                 :or {page-size 2 limit 4 update-fn identity}}]

  (let [query (partial build-query (:main-query github-config) search-str page-size)]
    (loop [page-token ""
           result {:results [] :errors [] :rate-limit nil}]

      (let [resp (make-github-request (query page-token))
            data (get-in resp [:body :data])
            errors (:errors resp)

            search (:search data)
            rate-limit (:rateLimit data)

            page-info (:pageInfo search)
            search-normalized (when-let [edges (:edges search)]
                                (map normalize-github-data edges))
            updated-result
            (-> result
                (update :results #(if search-normalized (into % search-normalized) %))
                (update :errors #(if errors (into % errors) %))
                (assoc :rate-limit rate-limit))]

        (if search-normalized
          (log/info "Downloaded" (count search-normalized) "Github repositories")
          (log/warn "Errors downloading Github data" errors))

        (if (and (:hasNextPage page-info)
                 (< (count (:results updated-result)) limit))
          (do (some-> search-normalized (update-fn))
              (recur (:endCursor page-info) updated-result))

          (let [{:keys [results errors rate-limit]} updated-result]
            (log/info "Downloaded total of" (count results) "Github repositories")
            (log/info "Github rate limit:" rate-limit)

            (when (seq errors)
              (log/warn "Errors total" (count errors) "downloading Github data" errors))

            updated-result))))))

(defn update-repos! [repos]
  (future
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

      (log/info "Updated" (count result) "Github repos in DB"))))

(defn update-github-repos! []
  (search-github "topic:neovim topic:plugin"
                 :page-size 100 :limit 1000
                 :update-fn update-repos!))

(comment
  (update-github-repos!)
  (search-github "topic:neovim topic:plugin" 3)
  (count (:results *1)))
  ; (user/ppn "\\\\n" (fetch-github-data)))
