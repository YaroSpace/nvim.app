(ns nvim-app.views.repos-compact
  (:require
   [nvim-app.views.assets :refer :all]
   [nvim-app.views.repos-shared :refer :all]
   [clojure.string :as str]))

(defn compact-table-header []
  [:div {:class "flex items-center py-2 px-2 border-b-2 text-xs font-semibold text-brand-emphasis bg-surface-card border-subtle"}

   [:div {:class "pl-2 w-24 sm:w-48 flex-shrink-0"} "Plugin"]
   [:div {:class "min-w-16 sm:w-26 flex-shrink-0 text-center"} "Stars"]
   [:div {:class "flex-1 px-1 sm:px-3"} "Description"]])

(defn plugin-topic [topic]
  [:span {:class (str "inline-block px-1 py-0.5 rounded text-xs
                  font-medium" (topic-color topic) "text-muted")} topic])

(defn topics [topics]
  [:div {:class "w-48 flex-shrink-0"}
   (when (seq topics)
     (let [topic-list (str/split topics #" ")
           visible-topics (take 3 topic-list)
           remaining-count (- (count topic-list) 3)]

       [:div {:class "flex items-center flex-wrap gap-1"}
        (map plugin-topic visible-topics)
        (when (pos? remaining-count)
          [:span {:class "text-xs text-gray-400"}
           (str "+" remaining-count)])]))])

(defn plugin-card [{:keys [user]} _
                   {:keys [id url repo name description
                           updated watched archived
                           stars stars_month] :as plugin}]

  [:div {:id (str "repo-" (:id plugin))
         :class "flex items-center py-1 px-2 border-b border-brand-light hover-brand transition-colors text-sm"
         :style "min-height: 2.5rem;"}

   [:div {:class "w-24 sm:w-48 flex"}
    [:a {:href url
         :class "pl-2 text-brand-strong hover:text-brand font-medium truncate block"
         :style "word-break: break-word; overflow-wrap: anywhere;"} (truncate-text name 40)]
    (when archived
      [:span {:title "Archived" :class "px-2"} (archived-icon)])]

   [:div {:class "min-w-16 sm:w-26 flex-shrink-0"}
    (when (pos? stars)
      [:div {:class "flex items-center space-x-0 text-yellow-600"}
       (star stars star-icon "space-x-1 text-xs text-yellow-500" "Total")
       (star (- stars stars_month) growth-icon-m "space-x-0 text-xs text-red-500 hidden sm:flex" "Stars since beginning of the month")])]

   [:div {:class "flex-1 px-1 sm:px-3 min-w-0"}
    [:p {:class "text-secondary text-xs truncate"}
     (truncate-text description 80)]]

   (when-let [repo-id (and user (str "#repo-" id))]
     [:div {:class "w-8 flex-shrink-0 text-center"}
      (watch-button repo-id repo watched)])])

(defn category-section [request params n show-group? group-name plugins]
  [:div {:class "mb-6 -mt-2"}
   [:div {:class "flex items-center justify-between pb-2"}
    [:h2 {:class (when-not show-group? "invisible")}
     [:span {:class "font-semibold text-brand-emphasis"} "Category: "]
     [:span {:class "font-semibold text-category"} group-name]]

    (when (= 0 n)
      [:div {:class "flex items-center space-x-2"}
       (mode-toggle (:mode request))
       (view-toggle (:view params))])]

   [:div {:class "border rounded-sm overflow-hidden bg-surface-card-compact border-subtle"}
    (compact-table-header)

    [:div {:class "divide-y divide-gray-200"}
     (map (fn [plugin] (plugin-card request params plugin)) plugins)]]])
