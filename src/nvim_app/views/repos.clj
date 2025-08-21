(ns nvim-app.views.repos
  (:require
   [nvim-app.views.assets :refer :all]
   [nvim-app.views.layout :refer [base-layout]]
   [nvim-app.components.app :refer [dev?]]
   [hiccup2.core :refer [html]]
   [hiccup.page :refer [include-js]]
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

(defn el-with [el opts & body]
  (vec (concat (update el 1 #(merge-with (fn [a b] (str/join " " [a b])) % opts))
               (when body body))))

(def bg-color " background-color:#d3e4db; ")
(def hx-include "#search-input, #limit-input, #category, #sort, #group")

(defn topic-color [topic]
  (let [colors [["bg-yellow-100" "lsp" "telescope"]
                ["bg-orange-100" "ai" "llm" "lua"]
                ["bg-red-100" "colorscheme" "theme"]
                ["bg-cyan-100" "markdown" "treesitter"]
                ["bg-indigo-100" "config" "dotfiles"]
                ["bg-lime-100" "python" "rust"]
                ["bg-blue-100" "neovim" "nvim"]
                ["bg-purple-100" "vim" "plugin" "terminal"]]]

    (or (some (fn [[color & topics]]
                (when (some #(str/includes? topic %) topics)
                  (format " %s " color)))
              colors)
        " bg-green-100 ")))

(defn search-input [url query]
  [:div {:class "relative"}
   [:form {:id "search-form" :class "flex px-4 items-center gap-2 w-full"
           :hx-get url :hx-include hx-include :hx-target "#plugins-list"
           :hx-trigger "submit, keyup changed delay:300ms from:input[name='q']"}

    [:input {:class "w-full px-4 py-3 border border-gray-300 rounded-lg focus:outline-none 
                     focus:ring-2 focus:ring-green-500 focus:border-transparent"
             :id "search-input" :name "q" :value query
             :placeholder "category, name, repo, description, topics ..."
             :style bg-color :type "text"}]

    [:div {:class "absolute right-6 top-4 text-green-600"}
     (search-icon)]]])

(defn dropdown-select [url]
  [:select {:class "appearance-none bg-transparent border border-green-500 rounded-lg
                      px-3 py-2 pr-8 text-sm text-gray-700 
                      focus:outline-none focus:ring-2 focus:ring-green-600
                      focus:border-transparent"
            :hx-get url :hx-include hx-include :hx-target "#plugins-list"
            :hx-trigger "change delay:500ms"}])

(defn group-dropdown [url group]
  [:div {:class "relative"}
   (el-with (dropdown-select url) {:id "group" :name "group" :title "Group by" :class "w-32"}
            (for [[value text] [["" "-"]
                                ["category" "Category"]
                                ["updated" "Last Update"]]]
              [:option {:value value :selected (= value group)} text]))

   [:div {:class "absolute inset-y-0 right-0 flex items-center px-2 pointer-events-none"}
    (chevron-down-icon)]])

(defn sort-dropdown [url sort]
  [:div {:class "relative"}
   [:div {:class "flex items-center space-x-1"}
    (el-with (dropdown-select url) {:id "sort" :name "sort" :title "Sort by" :class "w-32"}
             (for [[value text] [["" "Name"]
                                 ["stars" "Stars"]
                                 ["stars_week" "Weekly Gain"]
                                 ["stars_month" "Monthly Gain"]
                                 ["updated" "Last Update"]
                                 ["created" "Created"]]]
               [:option {:value value :selected (= value sort)} text]))

    [:div {:class "absolute inset-y-0 right-0 flex items-center px-2 pointer-events-none"}
     (chevron-down-icon)]]])

(defn category-dropdown [user url category categories]
  [:div {:class "relative"}
   [:div {:class "flex items-center space-x-1"}
    (el-with (dropdown-select url) {:id "category" :name "category" :title "Filter by" :class "w-auto sm:w-34"}
             [:option {:value "" :selected (= "" category)} "-"]
             (when user [:option {:value "watched" :selected (= "watched" category)} "*Watched*"])

             (for [name categories]
               [:option {:value name :selected (= name category)} name]))

    [:div {:class "absolute inset-y-0 right-0 flex items-center px-2 pointer-events-none"}
     (chevron-down-icon)]]])

(defn pagination-btn []
  [:button {:class "px-3 py-2 text-sm font-medium text-green-700
                      bg-transparent border border-green-500 rounded-lg hover:bg-green-50 active:bg-green-50
                      focus:outline-none focus:ring-2 focus:ring-green-600 focus:border-transparent focus: bg-green-50 
                      disabled:opacity-50 disabled:cursor-not-allowed"
            :hx-include hx-include :hx-target "#plugins-list"
            :hx-on:htmx:before-request "document.documentElement.scrollIntoView({ behavior: 'smooth'});"
            :hx-indicator "#indicator"}])

(defn pagination-btn-previous [url page]
  (el-with (pagination-btn)
           {:hx-get (u/url url {:page (max 1 (dec page))})
            :disabled (<= page 1)}
           (chevron-left-icon)))

(defn pagination-btn-next [url page total]
  (el-with (pagination-btn)
           {:hx-get (u/url url {:page (min total (inc page))})
            :disabled (>= page total)}
           (chevron-right-icon)))

(defn controls-and-pagination [user url {:keys [group sort category categories
                                                page total limit]}]
  [:div {:class "flex flex-col md:flex-row items-center justify-between 
                 mt-4 mb-4 py-3 px-4 gap-4 max-w-4xl mx-auto"
         :style (str bg-color "border-radius: 8px;")}

   [:div {:class "flex flex-wrap gap-y-4 justify-center items-center space-x-2 text-green-700"}
    (group-icon) (group-dropdown url group)
    (sort-icon) (sort-dropdown url sort)
    (category-icon) (category-dropdown user url category categories)]

   [:div {:class "flex items-center space-x-2"}
    [:div {:class "text-sm font-light text-green-700 font-medium mr-2"}
     (str "Page " page " / " total)]

    (pagination-btn-previous url page)

    [:input {:id "limit-input" :name "limit" :title "Results per page"
             :class "w-16 px-2 py-1.5 bg-transparent border border-green-500 rounded-lg text-sm text-center
                     focus:outline-none focus:ring-2 focus:ring-green-600 focus:border-transparent"
             :type "number" :min "1" :max "100" :value (or limit "10")
             :hx-get url :hx-include hx-include :hx-target "#plugins-list"}]

    [:input {:id "current-page" :href "#" :class "hidden"
             :hx-get (u/url url {:page page}) :hx-include hx-include :hx-target "#plugins-list"}]

    (pagination-btn-next url page total)]])

(defn pagination-btm [url page total]
  [:div {:class "flex items-center justify-center space-x-2"}
   (pagination-btn-previous url page)

   [:div {:class "text-sm font-light text-green-700 font-medium px-2"}
    (str "Page " page " out of " total)]

   (pagination-btn-next url page total)])

(defn plugin-topic [topic]
  [:span {:class (str "inline-flex items-center px-2 py-1 rounded-full text-xs
                  font-medium" (topic-color topic) "green-100 text-gray-700")} topic])

(defn star [number icon color title]
  (when (pos? number)
    [:div {:title title :class (str "flex items-center space-x-1 py-2 " color)}
     (icon)
     [:span {:class "text-sm text-green-900"} (number->str number)]]))

(defn plugin-card [user {:keys [repo url name description topics created updated
                                stars stars_week stars_month watched]}]
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

     [:div {:class "text-sm text-gray-500 flex flex-col sm:flex-row sm:space-x-4 mt-4"}
      [:span "Created: " (date->str created)]
      [:span {:class "flex"} "Last updated: " (date->str updated)
       (when user
         [:button {:title (if watched "Remove from watchlist" "Add to watchlist")
                   :class (str "flex items-center pl-2 space-x-1 cursor-pointer "
                               (if watched "text-blue-700" "text-green-700"))
                   :hx-get (u/url "/user/watch" {:repo repo}) :hx-target "#plugins-list" :hx-include hx-include}
          (watch-icon)])]]]

    (when (pos? stars)
      [:div {:class "flex flex-col items-end pt-2 pl-2"}
       (star stars star-icon "text-yellow-500" "Total")
       (star (- stars stars_week) growth-icon-w "text-orange-500" "Stars since beginning of the week")
       (star (- stars stars_month) growth-icon-m "text-red-500" "Stars since beginning of the month")])]])

(defn category-section [user n show-category? group-name plugins]
  [:div {:class "mb-8"}
   [:div {:class "flex items-center justify-between mb-4"}
    [:h2 {:class (when-not show-category? "invisible")}
     [:span {:class "text-xl font-semibold text-green-600"} "Category: "]
     [:span {:class "text-xl font-semibold text-blue-600"} group-name]]

    (when (= 0 n)
      [:div {:class "flex items-center space-x-2"}
       [:button {:class "p-2 text-green-700 hover:text-green-900 hover:bg-green-50 rounded-lg transition-colors 
                       focus:outline-none focus:ring-2 focus:ring-green-500
                       disabled:opacity-50 disabled:cursor-not-allowed"
                 :title "Compact view"
                 :disabled true}
        (compact-view-icon)]
       [:button {:class "p-2 text-green-700 hover:text-green-900 hover:bg-green-50 rounded-lg transition-colors 
                       focus:outline-none focus:ring-2 focus:ring-green-500
                       disabled:opacity-50 disabled:cursor-not-allowed"
                 :title "Full view"
                 :disabled true}
        (full-view-icon)]])]

   [:div {:class "space-y-6"}
    (map (fn [plugin] (plugin-card user plugin)) plugins)]])

(defn plugins-list [request plugins {:keys [group category page total] :as params}]
  (str
   (html
    (let [url "/repos-page" user (:user request)]
      [:div {:class "space-y-6"}
       (controls-and-pagination user url params)

       (let [show-category? (or (= "category" group) (seq category))
             grouped (cond
                       (= "updated" group) (group-by-date plugins)
                       show-category? (into (sorted-map) (group-by :category plugins))
                       :else {"-" plugins})]

         (for [[n [group-name plugins]] (map-indexed vector grouped)]
           (category-section user n show-category? (or group-name "-") plugins)))

       (pagination-btm url page total)]))))

(defn main [request params]
  (let [{:keys [q]} params url "/repos-page"]
    (base-layout
     {:head_include (when-not (dev?) (include-js "/js/repos.js"))}
     request
     [:div {:class "max-w-4xl mx-auto px-4 py-6"}
      (search-input url q)
      [:img {:id "indicator" :class "htmx-indicator" :src "/images/loader.svg"
             :style "display: none;"}]

      [:div {:id "plugins-list" :class "max-w-4xl mx-auto px-4 pb-6"
             :hx-get (u/url url params) :hx-target "#plugins-list"
             :hx-trigger "load"}]])))
