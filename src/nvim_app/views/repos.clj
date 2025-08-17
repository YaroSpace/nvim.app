(ns nvim-app.views.repos
  (:require
   [nvim-app.views.assets :refer :all]
   [nvim-app.views.layout :refer [base-layout]]
   [hiccup2.core :refer [html]]
   [hiccup.util :as u]
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

(defn date->str [date]
  (first (str/split (str date) #" ")))

(defn number->str [n]
  (str/replace (format "%,d" n) "," " "))

(defn group-by-date [repos]
  (->> repos
       (group-by #(date->week (:updated %)))
       (into (sorted-map-by >))
       (reduce-kv (fn [acc week repos] (assoc acc (week->date week) repos)) {})))

(def bg-color "background-color:#d3e4db; ")
(def hx-include "#query-input, #limit-input, #category, #sort, #group")

(defn topic-color [topic]
  (let [has? (fn [topics] (some #(str/includes? topic %) topics))]
    (format " %s "
            (cond
              (has? ["lsp" "telescope"]) "bg-yellow-100"
              (has? ["ai" "llm" "lua"]) "bg-orange-100"
              (has? ["colorscheme" "theme"]) "bg-red-100"
              (has? ["markdown" "treesitter"]) "bg-cyan-100"
              (has? ["config" "dotfiles"]) "bg-indigo-100"
              (has? ["python" "rust"]) "bg-lime-100"
              (has? ["neovim" "nvim"]) "bg-blue-100"
              (has? ["vim" "plugin" "terminal"]) "bg-purple-100"
              :else " bg-green-100 "))))

(defn search-input [url query]
  [:div {:class "relative"}
   [:form {:class "flex px-4 items-center gap-2 w-full"
           :hx-get url
           :hx-include hx-include
           :hx-target "#plugins-list"
           :hx-trigger "submit, keyup changed delay:300ms from:input[name='q']"}

    [:input {:class "w-full px-4 py-3 border border-gray-300 rounded-lg focus:outline-none 
                     focus:ring-2 focus:ring-green-500 focus:border-transparent"
             :id "query-input" :name "q" :value query
             :placeholder "category, name, repo, description, topics ..."
             :style bg-color :type "text"}]

    [:div {:class "absolute right-6 top-4 text-green-600"}
     (search-icon)]]])

(defn sort-dropdown [url sort]
  [:div {:class "relative"}
   [:div {:class "flex items-center space-x-1"}
    [:select {:class "appearance-none bg-transparent border border-green-500 rounded-lg
                      px-3 py-2 pr-8 text-sm text-gray-700 
                      focus:outline-none focus:ring-2 focus:ring-green-600
                      focus:border-transparent w-32"
              :hx-get url
              :hx-include hx-include
              :hx-target "#plugins-list"
              :id "sort" :name "sort"}

     [:option {:value "" :selected (= "" sort)} "Name"]
     [:option {:value "stars" :selected (= "stars" sort)} "Stars"]
     [:option {:value "updated" :selected (= "updated" sort)} "Last Update"]
     [:option {:value "created" :selected (= "created" sort)} "Created"]]

    [:div {:class "absolute inset-y-0 right-0 flex items-center px-2 pointer-events-none"}
     (chevron-down-icon)]]])

(defn category-dropdown [url category categories]
  [:div {:class "relative"}
   [:div {:class "flex items-center space-x-1"}
    [:select {:class "appearance-none bg-transparent border border-green-500 rounded-lg
                      px-3 py-2 pr-8 text-sm text-gray-700 
                      focus:outline-none focus:ring-2 focus:ring-green-600
                      focus:border-transparent w-auto sm:w-34"
              :hx-get url
              :hx-include hx-include
              :hx-target "#plugins-list"
              :id "category" :name "category"}

     [:option {:value "" :selected (= "" category)} "-"]
     (for [name categories]
       [:option {:value name :selected (= name category)} name])]

    [:div {:class "absolute inset-y-0 right-0 flex items-center px-2 pointer-events-none"}
     (chevron-down-icon)]]])

(defn group-dropdown [url group]
  [:div {:class "relative"}
   [:select {:class "appearance-none bg-transparent border border-green-500
                     rounded-lg px-3 py-2 pr-8 text-sm text-gray-700 
                     focus:outline-none focus:ring-2 focus:ring-green-600 
                     focus:border-transparent w-32"
             :hx-get url
             :hx-include hx-include
             :hx-target "#plugins-list"
             :id "group" :name "group"}

    [:option {:value "category" :selected (= "category" group)} "Category"]
    [:option {:value "updated" :selected (= "updated" group)} "Last Update"]]

   [:div {:class "absolute inset-y-0 right-0 flex items-center px-2 pointer-events-none"}
    (chevron-down-icon)]])

(defn pagination-btn-previous [url page]
  [:button {:class "px-3 py-2 text-sm font-medium text-green-700
                      bg-transparent border border-green-500 rounded-lg hover:bg-green-50
                      focus:outline-none focus:ring-2 focus:ring-green-600 focus:border-transparent 
                      disabled:opacity-50 disabled:cursor-not-allowed"

            :hx-get (u/url url {:page (max 1 (dec page))})
            :hx-include hx-include
            :hx-target "#plugins-list"
            :hx-on:htmx:before-request "document.documentElement.scrollIntoView({ behavior: 'smooth'});"
            :hx-indicator "#indicator"
            :disabled (<= page 1)}
   (chevron-left-icon)])

(defn pagination-btn-next [url page total]
  [:button {:class "px-3 py-2 text-sm font-medium text-green-700 bg-transparent
                      border border-green-500 rounded-lg hover:bg-green-50
                      focus:outline-none focus:ring-2 focus:ring-green-600 focus:border-transparent 
                      disabled:opacity-50 disabled:cursor-not-allowed"

            :hx-get (u/url url {:page (min total (inc page))})
            :hx-include hx-include
            :hx-target "#plugins-list"
            :hx-on:htmx:before-request "document.documentElement.scrollIntoView({ behavior: 'smooth'});"
            :hx-indicator "#indicator"
            :disabled (>= page total)}
   (chevron-right-icon)])

(defn controls-and-pagination [url {:keys [group sort category categories
                                           page total limit]}]
  [:div {:class "flex flex-col md:flex-row items-center justify-between 
                 mt-4 mb-4 py-3 px-4 gap-4 max-w-4xl mx-auto"
         :style (str bg-color "border-radius: 8px;")}

   [:div {:class "flex flex-wrap gap-y-4 justify-center items-center space-x-2 text-green-700"}
    (group-icon)
    (group-dropdown url group)

    (sort-icon)
    (sort-dropdown url sort)

    (category-icon)
    (category-dropdown url category categories)]

   [:div {:class "flex items-center space-x-2"}
    [:div {:class "text-sm text-green-700 font-medium mr-2"}
     (str "Page " page " out of " total)]

    (pagination-btn-previous url page)

    [:input {:class "w-16 px-2 py-1.5 bg-transparent border border-green-500 rounded-lg text-sm text-center
                     focus:outline-none focus:ring-2 focus:ring-green-600 focus:border-transparent"
             :type "number" :min "1" :max "100" :value (or limit "10")
             :id "limit-input" :name "limit"
             :hx-get url
             :hx-include hx-include
             :hx-target "#plugins-list"}]

    (pagination-btn-next url page total)]])

(defn pagination-btm [url page total]
  [:div {:class "flex items-center justify-center space-x-2"}
   (pagination-btn-previous url page)

   [:div {:class "text-sm text-green-700 font-medium px-2"}
    (str "Page " page " out of " total)]

   (pagination-btn-next url page total)])

(defn plugin-topic [topic]
  [:span {:class (str "inline-flex items-center px-2 py-1 rounded-full text-xs
                  font-medium" (topic-color topic) "green-100 text-gray-700")} topic])

(defn plugin-card [{:keys [url name description topics created updated
                           stars stars_week stars_month]}]
  [:div {:class "rounded-lg border border-gray-200 p-6 hover:shadow-md transition-shadow"
         :style bg-color}

   [:div {:class "flex items-start justify-between"}
    [:div {:class "flex-1"}

     [:a {:href url
          :class "text-xl font-semibold text-green-900 mb-2 break-word overflow-hidden"
          :style "word-break: break-word; overflow-wrap: anywhere; word-wrap: break-word; 
                   white-space: normal; max-width: 100%; hyphens: auto;"} name]

     [:p {:class "text-gray-600 mb-3"} description]

     (when (seq topics)
       [:div {:class "flex items-center flex-wrap gap-2 mb-3"}
        (map plugin-topic (str/split topics #" "))])

     [:div {:class "mt-4"}
      [:div {:class "text-sm text-gray-500 flex flex-col sm:flex-row sm:space-x-4"}
       [:span "Created: " (date->str created)]
       [:span "Last updated: " (date->str updated)]]]]

    (when (> stars 0)
      [:div {:class "flex flex-col items-end pt-4 pl-2"}
       [:div {:class "flex items-center space-x-1 py-2 text-yellow-500"}
        (star-icon)
        [:span {:class "text-sm text-green-900"} (number->str stars)]]

       (let [stars-week (- stars stars_week)]
         (when (> stars-week 0)
           [:div {:class "flex items-center space-x-1 py-2 text-yellow-500"}
            (growth-icon)
            [:span {:class "text-sm"} "w "]
            [:span {:class "text-sm text-green-900"} (number->str stars-week)]]))

       (let [stars-month (- stars stars_month)]
         (when (> stars-month 0)
           [:div {:class "flex items-center space-x-1 py-2 text-yellow-500"}
            (growth-icon)
            [:span {:class "text-sm"} "m "]
            [:span {:class "text-sm text-green-900"} (number->str stars-month)]]))])]])

(defn category-section [category-name plugins]
  [:div {:class "mb-8"}
   [:h2
    [:span {:class "text-xl font-semibold text-green-600"} "Category: "]
    [:span {:class "text-xl font-semibold text-blue-600"} category-name]
    [:br] [:br]]

   [:div {:class "space-y-6"}
    (map (fn [plugin] (plugin-card plugin)) plugins)]])

(defn plugins-list [plugins {:keys [group page total] :as params}]
  (str
   (html
    (let [url "/repos-page"]
      [:div {:class "space-y-6"}
       (controls-and-pagination url params)

       (let [default (into (sorted-map) (group-by :category plugins))
             grouped (case group
                       "category" default
                       "updated" (group-by-date plugins)
                       default)]

         (for [[group plugins] grouped]
           (category-section group plugins)))

       (pagination-btm url page total)]))))

(defn main [params]
  (let [{:keys [query]} params url "/repos-page"]
    (base-layout
     [:div {:class "max-w-4xl mx-auto px-4 py-6"}
      (search-input url query)
      [:img {:id "indicator" :class "htmx-indicator" :src "/images/loader.svg"
             :style "display: none;"}]

      [:div {:class "max-w-4xl mx-auto px-4 pb-6"
             :hx-get (u/url url params)
             :hx-target "#plugins-list"
             :hx-trigger "load"
             :id "plugins-list"}]])))
