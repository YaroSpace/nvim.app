(ns nvim-app.github
  (:require
   [nvim-app.config :as config]
   [nvim-app.db.plugin :as plugin]
   [nvim-app.db.core :as db]
   [nvim-app.utils :refer [fetch-request]]
   [cheshire.core :as json]
   [clojure.tools.logging :as log]
   [clojure.string :as str])
  (:import [java.time Instant]
           [java.sql Timestamp]))

(def github-api-uri "https://api.github.com/graphql")

(defn github-token []
  (:token (:github (config/read-config))))

(def github-query-batch 50)

(def query-template
  "query { %s rateLimit { cost remaining } }")

(def query-line
  "%s: repository(owner: \"%s\", name: \"%s\") { 
    stargazerCount
    repositoryTopics(first: 10) {
      nodes {
        topic {
          name
        }
      }
    }
    defaultBranchRef {
      target {
        ... on Commit {
          committedDate
        }
      }
    }
  }")

(defn build-query [plugins]
  (let [query-str
        (format query-template
                (reduce (fn [query {:keys [id repo]}]
                          (let [[owner name] (str/split repo #"/")]
                            (if-not (and owner name)
                              query
                              (str query (format query-line (str "id" id)
                                                 owner
                                                 (first (str/split name #"#")))))))
                        "" plugins))]
    (json/encode {:query query-str})))

(defn fetch-github-data [plugins]
  (for [batch (partition-all github-query-batch plugins)]
    (let [query (build-query batch)
          resp (fetch-request  {:method :post
                                :url github-api-uri
                                :content-type "application/json"
                                :headers {"Authorization" (str "Bearer "  (github-token))}
                                :body query})
          result {:data (get-in resp [:body :data])
                  :errors (:errors resp)}]

      (log/info "Fetched Github data batch" {:success (- (count (:data result)) 1)
                                             :errors (count (:errors result))})
      (when (:errors result)
        (log/error "Github fetch errors: " (reduce #(str %1 "\n" %2) "" (:errors result))))

      result)))

(defn normalize-github-data [resp]
  (reduce-kv
   (fn [m id data]
     (if (= :rateLimit id) (assoc m :rateLimit data)
         (merge m
                {(keyword (subs (name id) 2))
                 (assoc data
                        :repositoryTopics
                        (reduce (fn [acc {:keys [topic]}] (str (:name topic) " " acc))
                                "" (:nodes (:repositoryTopics data)))
                        :updatedAt (when-let [date (get-in data [:defaultBranchRef :target :committedDate])]
                                     (Timestamp/from (Instant/parse date))))})))
   {} resp))

(defn get-github-data [plugins]
  (let [result (reduce (fn [acc {:keys [data errors]}]
                         (if-not data acc
                                 (-> acc
                                     (merge (normalize-github-data data))
                                     (update :errors #(if errors
                                                        (conj (flatten %) errors)
                                                        %)))))
                       {:errors []}
                       (fetch-github-data plugins))

        stats {:success (- (count (filter seq result)) 1)
               :errors (count (:errors result))
               :rateLimit (:rateLimit result)}]

    (log/info "Fetched Github stats: " stats)
    result))

(defn update-github-data! [plugins]
  (let [data (dissoc (get-github-data plugins) :errors :rateLimit)
        update-count
        (count (for [[id {:keys [updatedAt stargazerCount repositoryTopics]}] data]
                 (do
                   (db/query-one!
                    {:insert-into :github
                     :values [{:id id
                               :stargazers (or stargazerCount 0)
                               :updated_at updatedAt
                               :topics repositoryTopics}]
                     :on-conflict :id
                     :do-update-set {:stargazers (or stargazerCount 0)
                                     :updated_at  updatedAt
                                     :topics repositoryTopics}})
                   (db/query-one!
                    {:update :plugins
                     :set {:github_id id}
                     :where [:= :id id]}))))]
    (log/info "Updated Github data for" update-count "plugins")))

(comment
  (require 'user)
  (ex-message *e)
  (def json (build-query (take 1 (plugin/get-plugins))))
  (filter #(str/includes? (:repo %) "dev-tools") (plugin/get-plugins))
  (let [plugins (take 3 (plugin/get-plugins))]
    (update-github-data! plugins))
  (user/ppn json))

