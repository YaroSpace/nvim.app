(ns nvim-app.awesome
  (:require
   [nvim-app.db :as db]

   [clj-http.client :as http]
   [clojure.string :as str]
   [clojure.tools.logging :as log]))

(def url "https://raw.githubusercontent.com/rockerBOO/awesome-neovim/refs/heads/main/README.md")
(def repo-re #"^- \[(.*?)\]\((.*?)\) - (.*)")

(defn parse-readme [content]
  (let [lines (str/split-lines content)]
    (loop [lines lines
           category nil
           result []]
      (if (empty? lines)
        result
        (let [line (first lines)
              next-lines (rest lines)]
          (cond
            (and (str/starts-with? line "## ")
                 (not (re-matches #"## Contents" line)))
            (recur next-lines (str/trim (subs line 3)) result)

            (re-matches repo-re line)
            (let [[_ repo-name repo-url description] (re-matches repo-re line)]
              (recur next-lines category
                     (conj result {:category category
                                   :repo repo-name
                                   :url repo-url
                                   :description description})))
            :else
            (recur next-lines category result)))))))

(defn fetch-readme []
  (let [resp (http/get url {:as :text})]
    (log/info "Downloading awesome-neovim README...")

    (if (= 200 (:status resp))
      (:body resp [])
      (log/error (str "Failed to download awesome-neovim README."
                      {:status (:status resp)
                       :error (:error resp)})))))

(defn get-or-create-category-id [category]
  (let [result (db/query-one! {:select :id
                               :from :categories
                               :where [:= :name category]})]
    (if (seq result)
      (:id result)
      (let [res (db/query-one! {:insert-into :categories
                                :columns [:name]
                                :values [[category]]
                                :returning :id})]
        (:id res)))))

(defn upsert-plugin! [{:keys [category repo url description]}]
  (let [category-id (get-or-create-category-id category)
        existing (db/query-one! {:select [:id]
                                 :from [:plugins]
                                 :where [:and
                                         [:= :repo repo]
                                         [:= :url url]]})]
    (if (seq existing)
      (db/query! {:update :plugins
                  :set {:description description
                        :category_id category-id}
                  :where [:= :id (:id existing)]
                  :returning :*})
      (db/query! {:insert-into :plugins
                  :columns [:category_id :repo :url :description]
                  :values [[category-id repo url description]]
                  :returning :*}))))

(defn upsert-plugins! [plugins]
  (doseq [plugin plugins]
    (upsert-plugin!  plugin)))

(comment
  (upsert-plugins! (parse-readme (fetch-readme))))
