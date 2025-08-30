(ns nvim-app.views.repos
  (:require
   [nvim-app.views.assets :refer :all]
   [nvim-app.views.layout :refer [base-layout]]
   [nvim-app.views.repos-compact :as compact]
   [nvim-app.views.repos-shared :refer :all]
   [nvim-app.state :refer [dev?]]
   [io.pedestal.http.route :refer [url-for]]
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

(defn group-by-date [repos]
  (->> repos
       (group-by #(date->week (:updated %)))
       (into (sorted-map-by >))
       (reduce-kv (fn [acc week repos] (assoc acc (week->date week) repos)) {})))

(defn search-input [url query]
  [:div {:class "relative"}
   [:form {:id "search-form" :class "flex px-4 items-center gap-2 w-full"
           :hx-get url :hx-include hx-include :hx-target "#plugins-list"
           :hx-trigger "submit, keyup changed delay:300ms from:input[name='q']"}

    [:input {:class "w-full px-4 py-3 border border-muted rounded-lg focus-brand focus-brand-border bg-surface-card"
             :id "search-input" :name "q" :value query
             :placeholder "category, name, repo, description, topics ..."
             :type "text"}]

    [:div {:class "absolute right-6 top-4 text-brand"}
     (search-icon)]]])

(defn dropdown-select [url]
  [:select {:class "dark:scheme-dark appearance-none bg-transparent border border-brand rounded-lg
                      px-3 py-2 pr-8 text-sm text-secondary 
                      focus-brand focus-brand-border"
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
             [:option {:value "archived" :selected (= "archived" category)} "*Archived*"]

             (for [name categories]
               [:option {:value name :selected (= name category)} name]))

    [:div {:class "absolute inset-y-0 right-0 flex items-center px-2 pointer-events-none"}
     (chevron-down-icon)]]])

(defn pagination-btn []
  [:button {:class "px-3 py-2 text-sm font-medium text-brand
                      bg-transparent border border-brand rounded-lg hover-brand active:bg-brand-active
                      focus-brand focus-brand-border focus:bg-brand-hover 
                      disabled:opacity-50 disabled:cursor-not-allowed"
            :hx-include hx-include :hx-target "#plugins-list"
            :hx-on:htmx:before-request "document.documentElement.scrollIntoView({ behavior: 'smooth'});"
            :hx-indicator "#indicator"}])

(defn pagination-btn-previous [url page]
  (el-with (pagination-btn)
           {:id "btn-previous"
            :hx-get (u/url url {:page (max 1 (dec page))})
            :disabled (<= page 1)}
           (chevron-left-icon)))

(defn pagination-btn-next [url page total]
  (el-with (pagination-btn)
           {:id "btn-next"
            :hx-get (u/url url {:page (min total (inc page))})
            :disabled (>= page total)}
           (chevron-right-icon)))

(defn controls-and-pagination [user url {:keys [group sort category categories
                                                page total limit]}]
  [:div {:class "flex flex-col md:flex-row items-center justify-between 
                 mt-4 mb-4 py-3 px-4 gap-4 max-w-4xl mx-auto bg-surface-card rounded-lg"}

   [:div {:class "flex flex-wrap gap-y-4 justify-center items-center space-x-2 text-brand"}
    (group-icon) (group-dropdown url group)
    (sort-icon) (sort-dropdown url sort)
    (category-icon) (category-dropdown user url category categories)]

   [:div {:class "flex items-center space-x-2"}
    [:div {:class "text-sm font-light text-brand font-medium mr-2"}
     (str "Page " page " / " total)]

    (pagination-btn-previous url page)

    [:input {:id "limit-input" :name "limit" :title "Results per page"
             :class "w-16 px-2 py-1.5 bg-transparent border border-brand rounded-lg 
                     text-sm text-center text-secondary focus-brand focus-brand-border
                     dark:scheme-dark"
             :type "number" :min "1" :max "100" :value (or limit "10")
             :hx-get url :hx-include hx-include :hx-target "#plugins-list"
             :hx-trigger "submit, input delay:500ms, keyup changed delay:500ms"}]

    [:input {:id "current-page" :href "#" :class "hidden"
             :hx-get (u/url url {:page page}) :hx-include hx-include :hx-target "#plugins-list"}]

    (pagination-btn-next url page total)]])

(defn pagination-btm [url page total]
  [:div {:class "flex items-center justify-center space-x-2"}
   (pagination-btn-previous url page)

   [:div {:class "text-sm font-light text-brand font-medium px-2"}
    (str "Page " page " out of " total)]

   (pagination-btn-next url page total)])

(defn plugin-topic [topic]
  [:span {:class (str "inline-flex items-center px-2 py-1 rounded-full text-xs
                  font-medium" (topic-color topic) "green-100 text-topic")} topic])

(defn user-section [{:keys [user query-params]}
                    {:keys [id repo watched] :as plugin}]
  (let [repo-id (str "#repo-" id)]
    [:div {:class "flex pt-2"}
     (watch-button repo-id repo watched)

     (when (can-edit? user plugin)
       (if (= repo (:edit query-params))
         (save-button repo-id repo)
         (edit-button repo-id repo)))]))

(defn edit-category-dropdown [{:keys [category]} categories]
  [:div {:class "flex items-center"}
   [:div {:class "relative w-full"}
    [:select {:id "category-edit" :name "category-edit" :title "Edit category"
              :class "appearance-none bg-transparent border border-brand rounded-lg
                      px-3 py-2 pr-8 text-sm text-secondary 
                      focus-brand focus-brand-border w-full sm:w-60"}

     [:option {:value "" :selected (nil? category)} "-"]
     (for [name categories]
       [:option {:value name :selected (= name category)} name])]

    [:div {:class "absolute inset-y-0 right-0 flex items-center px-2 pointer-events-none"}
     (chevron-down-icon)]]])

(defn hide-toggle [hidden]
  [:div {:class "flex items-center text-sm text-nvim-text-muted space-x-2 mb-2"}
   [:span "Show"]
   [:label {:for "hidden-toggle" :title "Toggle plugin visiblity"
            :class (str "relative block h-7 w-14 rounded-full transition-colors bg-nvim-surface border border-brand rounded-xl")}
    [:input {:id "hidden-toggle" :name "hidden-edit" :type "checkbox" :value "true"
             :class "peer sr-only"
             :checked (when hidden "checked")}]
    [:span {:class "absolute inset-y-0 start-0 ml-1 size-5 bg-nvim-text rounded-full transition-[inset-inline-start] 
                    top-1/2 -translate-y-1/2 peer-checked:start-6 peer-checked:bg-nvim-text-muted"}]]
   [:span "Hide"]])

(defn plugin-card [{:keys [user] :as request}
                   {:keys [edit categories]}
                   {:keys [url repo name description topics created updated
                           stars stars_week stars_month archived hidden] :as plugin}]
  (when (or (not hidden) (can-edit? user plugin))
    (let [edit? (= edit repo)]
      [:div {:id (str "repo-" (:id plugin))
             :class "rounded-lg border border-subtle p-6 hover:shadow-md transition-shadow bg-surface-card"}

       [:div {:class "flex items-start justify-between"}
        [:div {:class "flex-1"}
         [:div {:class "flex sm:flex-row flex-col items-start justify-between"}
          [:a {:class "flex items-center text-xl font-semibold text-brand-strong mb-2 overflow-hidden
                   break-words break-all whitespace-normal max-w-full hyphens-auto"
               :href url} name
           (when archived
             [:div {:class "pl-2" :title "Archived"} (archived-icon)])]
          (when edit?
            (hide-toggle hidden))]

         (when edit?
           [:div {:class "sm:flex space-y-2 sm:space-x-2 sm:space-y-0 mb-2 gap-2"}
            (edit-category-dropdown plugin categories)])

         (if edit?
           [:textarea {:id "description-edit" :name "description-edit" :type "text"
                       :class "appearance-none bg-transparent border border-brand rounded-lg
                      px-3 py-2 mb-2 text-sm text-secondary 
                      focus-brand focus-brand-border w-full field-sizing-content"} description]
           [:div {:class "flex text-muted mb-3 max-w-64 sm:max-w-full whitespace-pre-wrap"} description])

         (when (seq topics)
           [:div {:class "flex items-center flex-wrap gap-2 mb-3"}
            (map plugin-topic (str/split topics #" "))])

         [:div {:class "text-sm text-subtle flex flex-col sm:flex-row space-x-4 mt-4"}
          (let [created (date->str created)]
            (when (not= "1970-01-01" created)
              [:span "Created: " created]))

          (let [updated (date->str updated)]
            (when (not= "1970-01-01" updated)
              [:span "Last updated: " updated]))]]

        [:div {:class "flex flex-col items-end pt-2 pl-2 self-stretch"}
         (when (pos? stars)
           [:div {:class "flex-col h-full"}
            (star stars star-icon "justify-end space-x-1 py-2 text-sm text-yellow-500" "Total")
            (star (- stars stars_week) growth-icon-w "justify-end space-x-1 py-2 text-sm text-orange-500" "Stars since beginning of the week")
            (star (- stars stars_month) growth-icon-m "justify-end space-x-1 py-2 text-sm text-red-500" "Stars since beginning of the month")])

         (when user
           (user-section request plugin))]]])))

(defn category-section [request params n show-group? group-name plugins]
  (if (= "compact" (:view request))
    (compact/category-section request params n show-group? group-name plugins)

    [:div {:class "mb-8"}
     [:div {:class "flex items-center justify-between mb-4"}
      [:h2 {:class (when-not show-group? "invisible")}
       [:span {:class "text-xl font-semibold text-brand-emphasis"} "Category: "]
       [:span {:class "text-xl font-semibold text-category"} group-name]]

      (when (= 0 n)
        [:div {:class "flex items-center space-x-2"}
         (mode-toggle (:mode request))
         (view-toggle (:view request))])]

     [:div {:class "space-y-6"}
      (map (fn [plugin] (plugin-card request params plugin)) plugins)]]))

(defn plugins-list [request plugins {:keys [group category page total] :as params}]
  (str
   (html
    (let [url (url-for :repos-page) user (:user request)]
      [:div {:class "space-y-6"}
       [:div {:id "alert-container-repos"}
        (alert (:flash request))]
       (controls-and-pagination user url params)

       (let [show-group? (some seq [group category])
             grouped (cond
                       (= "updated" group) (group-by-date plugins)
                       show-group? (into (sorted-map) (group-by :category plugins))
                       :else {"-" plugins})]

         (for [[n [group-name plugins]] (map-indexed vector grouped)]
           (category-section request params n show-group? (or group-name "-") plugins)))

       (pagination-btm url page total)]))))

(defn main [request params]
  (let [url (url-for :repos-page)]
    (base-layout
     request
     [:div {:class "max-w-4xl mx-auto px-4 py-6"}
      (search-input url (:q params))
      [:img {:id "indicator" :class "htmx-indicator hidden" :src "/images/loader.svg"}]

      [:div {:id "plugins-list" :class "max-w-4xl mx-auto px-4 pb-6"
             :hx-get (u/url url params) :hx-target "#plugins-list"
             :hx-trigger "load"}]])))
