(ns nvim-app.integration.core
  (:require
   [nvim-app.utils :refer [with-rpcall] :as u]
   [nvim-app.helpers :refer [with-driver] :as h]))

(defn get-repo-cards [driver]
  (with-driver driver
    (query-all {:fn/has-class "repo-card"})))

(defn get-repos-on-page [driver]
  (let [text ["repo-url" "repo-category" "repo-description" "repo-topics"
              "repo-stars" "repo-stars-week" "repo-stars-month"
              "repo-created" "repo-updated"
              "repo-archived" "repo-hidden" "repo-watch" "repo-edit"]
        value ["repo-category-edit" "repo-description-edit" "repo-hidden-toggle"]]

    (with-driver driver
      (doall
       (for [repo (get-repo-cards driver)]
         (merge
          (into {} (for [el text]
                     [(keyword el)
                      (with-rpcall :silent
                        (get-element-text-el
                         (query-from repo {:fn/has-class el})))]))
          (into {} (for [el value]
                     [(keyword el)
                      (with-rpcall :silent
                        (get-element-value-el
                         (query-from repo {:fn/has-class el})))]))))))))

(defn get-repos-groups [driver]
  (with-driver driver
    (->> (query-all {:fn/has-class "repos-group"})
         (map #(get-element-text-el %)))))

(defn get-repos-page [driver]
  (with-driver driver
    (merge
     (into {} (mapv (fn [el] [el (get-element-text-el (query el "option:checked"))])
                    [:group :sort :category]))
     {:repos (get-repos-on-page driver)
      :repos-groups (get-repos-groups driver)
      :search-input (get-element-value :search-input)
      :limit-input (get-element-value :limit-input)
      :repos-page-top (get-element-text {:fn/has-class "repos-page-top"})
      :repos-page-btm (get-element-text {:fn/has-class "repos-page-btm"})})))
