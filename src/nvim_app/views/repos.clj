(ns nvim-app.views.repos
  (:require
   [nvim-app.views.layout :refer [base-layout]]
   [hiccup.util :as u]
   [hiccup2.core :as h]
   [clojure.string :as str])
  (:import [java.time LocalDate]
           [java.time.temporal ChronoUnit]))

(defn week->date [weeks]
  (let [epoch (LocalDate/parse "1970-01-01")]
    (str (.plusWeeks epoch weeks))))

(defn date->week [date]
  (let [epoch (LocalDate/parse "1970-01-01")
        target-date (.toLocalDate (.toLocalDateTime date))]
    (.between ChronoUnit/WEEKS epoch target-date)))

(defn group-by-date [repos]
  (->> repos
       (group-by #(date->week (:updated %)))
       (into (sorted-map-by >))
       (reduce-kv (fn [acc week repos] (assoc acc (week->date week) repos)) {})))

(def hx-include "#query-input, #limit-input, #sort, #group")

(defn search-form [url query]
  [:div {:class "flex items-center bg-gray-100 p-4 rounded mb-2"}
   [:form {:class "flex items-center gap-2 w-full"
           :hx-get (u/url url)
           :hx-trigger "submit, keyup changed delay:300ms from:input[name='q']"
           :hx-target "#repos-list"
           :hx-include hx-include}

    [:input {:id "query-input"
             :class "px-2 py-2 mr-1 w-full"
             :type "text" :name "q" :value query
             :placeholder "Category, Name, Repo, Description, Topics"}]

    [:button {:type "submit"
              :class "bg-blue-500 text-white px-2 py-2 rounded"} "Search"]]])

(defn sort-toolbar [url sort]
  [:div {:class "flex space-x-2 items-center justify-between bg-gray-100 p-4 rounded mb-2"}
   [:span {:class "text-gray-700 font-semibold whitespace-nowrap"} "Sort by"]

   [:span
    [:select {:id "sort"
              :class "px-2 py-2 text-center bg-blue-500 text-white rounded focus:outline-none"
              :name "sort"
              :hx-get (u/url url)
              :hx-include hx-include
              :hx-target "#repos-list"}
     [:option {:value "" :selected (= "" sort)} "Name ↓"]
     [:option {:value "stars" :selected (= "stars" sort)} "Stars ↓"]
     [:option {:value "updated" :selected (= "updated" sort)} "Last Update ↓"]
     [:option {:value "created" :selected (= "created" sort)} "Created ↓"]]]])

(defn group-toolbar [url group]
  [:div {:class "flex space-x-2 items-center justify-between bg-gray-100 p-4 rounded mb-2"}
   [:span {:class "text-gray-700 font-semibold whitespace-nowrap"} "Group by"]

   [:span
    [:select {:id "group"
              :class "px-2 py-2 text-center bg-blue-500 text-white rounded focus:outline-none"
              :name "group"
              :hx-get (u/url url)
              :hx-include hx-include
              :hx-target "#repos-list"}
     [:option {:value "category" :selected (= "category" group)} "Category"]
     [:option {:value "updated" :selected (= "updated" group)} "Last Update"]]]])

(defn navigation [url page limit total]
  (h/html
   [:div {:class "flex space-x-3 items-center justify-between bg-gray-100 p-4 rounded mb-2"}
    [:span {:class "px-2 py-2 text-gray-700 font-semibold"}
     (str "Page " page " of " total)]

    [:input#page {:type "hidden" :name "page" :value page}]

    [:input {:id "limit-input"
             :class "border rounded px-2 py-2" :style "width: 4rem;"
             :type "number" :name "limit" :value limit :min 1
             :placeholder "Results per page"
             :hx-get (u/url url {:page  page})
             :hx-include hx-include
             :hx-trigger "change, keyup changed delay:300ms"
             :hx-target "#repos-list"
             :hx-indicator "#indicator"}]

    [:button {:class "px-2 py-2 bg-blue-500 text-white rounded disabled:opacity-50"
              :hx-get (u/url url {:page (max 1 (dec page))})
              :hx-include hx-include
              :hx-target "#repos-list"
              :hx-indicator "#indicator"
              :disabled (<= page 1)} "<<"]

    [:button {:class "px-2 py-2 bg-blue-500 text-white rounded disabled:opacity-50"
              :hx-get (u/url url {:page (min total (inc page))})
              :hx-include hx-include
              :hx-target "#repos-list"
              :hx-indicator "#indicator"
              :disabled (>= page total)} ">>"]]))

(defn repos-page [repos group sort page limit total]
  (str
   (h/html
    (let [url "/repos-page"]
      [:div
       [:div {:class "flex items-center gap-4 mb-4"}
        (sort-toolbar "/repos-page" sort)
        (group-toolbar "/repos-page" group)
        (navigation url page limit total)]

       (let [default (into (sorted-map) (group-by :category repos))
             grouped (case group
                       "category" default
                       "updated" (group-by-date repos)
                       default)]
         (for [[group repos] grouped]
           [:div {:class "mb-8"}
            [:h2
             [:span {:class "text-xl font-semibold text-black-600"} "Category: "]
             [:span {:class "text-xl font-semibold text-blue-600"} (or group "-")]
             [:br] [:br]

             (for [{:keys [url description stars topics created updated]} repos]
               (let [created (first (str/split (str created) #" "))
                     updated (when updated (first (str/split (str updated) #" ")))]
                 [:div {:class "mb-5"}
                  [:h3 {:class "text-xl font-semibold text-blue-600"}
                   [:span {:class "flex items-center space-x-1"}
                    [:a {:href url} url]
                    [:img {:src "/images/star.jpeg" :alt "Star" :class "w-4 h-4"}]
                    [:span {:class "text-sm text-gray-600"} stars]]]
                  [:p {:class "text-gray-700"} description
                   (when-not (empty? topics)
                     [:div {:class "flex flex-wrap gap-2 mt-2"}
                      (for [topic (str/split topics #" ")]
                        [:span {:class "px-2 py-1 bg-gray-200 text-gray-800 text-sm rounded"} topic])])]
                  (when updated
                    [:div {:class "flex items-center justify-between mt-2"}
                     [:p {:class "text-sm text-gray-500 mt-2"}
                      [:span (str "Created: " created)]
                      [:span {:class "ml-2"} (str "Last updated: " updated)]]])]))]]))]))))

(defn repos []
  (let [defaults {:q "" :sort "created" :group "updated" :page 2 :limit 20}]
    (base-layout
     [:div {:class "p-6 max-w-4xl mx-auto"}
      [:div {:class "flex items-center space-x-4"}
       [:img {:src "/images/favicon.ico"}]
       [:h1 {:class "text-3xl font-bold"} "Neovim Plugins Catalog"]
       [:img {:id "indicator" :class "htmx-indicator" :src "/images/loader.svg"}]]

      (search-form "/repos-page" (:q defaults))

      [:div {:id "repos-list"
             :hx-get (u/url "/repos-page" defaults)
             :hx-trigger "load" :hx-target "#repos-list"
             :hx-indicator "#indicator"}
       "Loading plugins..."]])))
