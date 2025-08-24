(ns nvim-app.views.repos-compact
  (:require
   [nvim-app.views.assets :refer :all]
   [hiccup.util :as u]
   [clojure.string :as str]))

(defn date->str [date]
  (first (str/split (str date) #" ")))

(defn number->str [n]
  (str/replace (format "%,d" n) "," " "))

(def hx-include "#search-input, #limit-input, #category, #sort, #group")

(defn plugin-topic-compact [topic]
  [:span {:class (str "inline-block px-1 py-0.5 rounded text-xs
                  font-medium" (topic-color topic) "text-gray-600")} topic])

(defn compact-plugin-card [user {:keys [repo url name description topics created updated
                                        stars stars_week stars_month watched]}]
  [:div {:class "flex items-center py-1 px-2 border-b border-green-200 hover:bg-green-100 transition-colors text-sm"
         :style "min-height: 2.5rem;"}

   ;; Plugin name - fixed width column
   [:div {:class "w-64 flex-shrink-0"}
    [:a {:href url
         :class "text-green-900 hover:text-green-700 font-medium truncate block"
         :style "word-break: break-word; overflow-wrap: anywhere;"} name]]

   ;; Stars - narrow column
   [:div {:class "w-16 flex-shrink-0 text-center"}
    (when (pos? stars)
      [:div {:class "flex items-center justify-center space-x-1 text-yellow-600"}
       (star-icon)
       [:span {:class "text-xs"} (number->str stars)]])]

   ;; Description - flexible column
   [:div {:class "flex-1 px-3 min-w-0"}
    [:p {:class "text-gray-600 text-xs truncate"}
     (if (> (count description) 80)
       (str (subs description 0 80) "...")
       description)]]

   ;; Topics - fixed width column
   [:div {:class "w-48 flex-shrink-0"}
    (when (seq topics)
      (let [topic-list (str/split topics #" ")
            visible-topics (take 3 topic-list)
            remaining-count (- (count topic-list) 3)]
        [:div {:class "flex items-center flex-wrap gap-1"}
         (map plugin-topic-compact visible-topics)
         (when (pos? remaining-count)
           [:span {:class "text-xs text-gray-400"}
            (str "+" remaining-count)])]))]

   ;; Date - narrow column
   [:div {:class "w-20 flex-shrink-0 text-xs text-gray-500 text-center"}
    (date->str updated)]

   ;; Actions - narrow column
   [:div {:class "w-8 flex-shrink-0 text-center"}
    (when user
      [:button {:title (if watched "Remove from watchlist" "Add to watchlist")
                :class (str "text-xs cursor-pointer hover:text-green-600 "
                            (if watched "text-blue-600" "text-green-600"))
                :hx-get (u/url "/user/watch" {:repo repo})
                :hx-target "#plugins-list"
                :hx-include hx-include}
       (watch-icon)])]])

;; Updated table header colors to match page green scheme
(defn compact-table-header []
  [:div {:class "flex items-center py-2 px-2 border-b-2 text-xs font-semibold text-green-800"
         :style (str bg-color "border-bottom-color: " border-color)}
   [:div {:class "w-64 flex-shrink-0"} "Plugin"]
   [:div {:class "w-16 flex-shrink-0 text-center"} "Stars"]
   [:div {:class "flex-1 px-3"} "Description"]
   [:div {:class "w-48 flex-shrink-0"} "Topics"]
   [:div {:class "w-20 flex-shrink-0 text-center"} "Updated"]
   [:div {:class "w-8 flex-shrink-0 text-center"} ""]])

(defn compact-category-section [user category-name plugins]
  [:div {:class "mb-8"}
   [:div {:class "flex items-center justify-between mb-3"}
    [:h2
     [:span {:class "text-lg font-semibold text-green-600"} "Category: "]
     [:span {:class "text-lg font-semibold text-blue-600"} category-name]]

    [:div {:class "flex items-center space-x-2"}
     [:button {:class "p-1.5 text-green-700 hover:text-green-900 hover:bg-green-50 rounded-lg transition-colors 
                       focus:outline-none focus:ring-2 focus:ring-green-500"
               :title "Full view"}
      (full-view-icon)]
     [:button {:class "p-1.5 text-green-700 hover:text-green-900 hover:bg-green-50 rounded-lg transition-colors 
                       focus:outline-none focus:ring-2 focus:ring-green-500 bg-green-50"
               :title "Compact view"}
      (compact-view-icon)]]]

   ;; Updated container colors to match page green scheme
   [:div {:class "border rounded-lg overflow-hidden"
          :style (str bg-color-compact "  border-color: " border-color)}
    (compact-table-header)
    [:div {:class "divide-y"
           :style (str "--tw-divide-opacity: 1; border-color: " border-color)}
     (map (fn [plugin] (compact-plugin-card user plugin)) plugins)]]])
