(ns nvim-app.components.pedestal.views.repos-compact
  (:require
   [nvim-app.components.pedestal.views.assets :refer :all]
   [nvim-app.components.pedestal.views.repos-shared :refer :all]
   [io.pedestal.http.route :refer [url-for]]))

(defn compact-table-header []
  [:div {:class "flex items-center py-2 px-2 border-b-2 text-xs font-semibold text-brand-emphasis bg-surface-card border-subtle"}

   [:div {:class "pl-2 w-24 sm:w-48 flex-shrink-0"} "Plugin"]
   [:div {:class "min-w-16 sm:w-30 flex-shrink-0"} "Stars"]
   [:div {:class "flex-1 px-1 sm:px-3"} "Description"]])

(defn plugin-card [{:keys [user]} _
                   {:keys [id url repo name description
                           watched archived stars stars_month] :as plugin}]

  [:form {:class "border-b border-brand-light"}
   [:div {:id (str "repo-" id)
          :class "flex relative items-center min-h-2.5 py-1 px-2 border-b border-brand-light hover-brand transition-colors text-sm"}

    [:div {:class "preview-popup absolute left-100 z-50 hidden invisible sm:visible p-2 bg-surface-card
                     border border-brand shadow-lg"
           :hx-get (url-for :preview :params {:id id}) :hx-include hx-include
           :hx-trigger "click" :hx-target "this"}
     "Loading preview..."]

    [:div {:class "w-24 sm:w-48 flex"}
     [:a {:href url
          :class "pl-2 text-brand-strong hover:text-brand font-medium truncate block
                   break-words break-all whitespace-normal max-w-full hyphens-auto"
          :hx-on:mouseover "showPreview(this);" :hx-on:mouseleave "hidePreview(this);"}
      (truncate-text name 40)]

     (when archived
       [:span {:title "Archived" :class "px-2"} (archived-icon)])]

    [:div {:class "min-w-16 sm:w-30 flex-shrink-0"}
     (when (pos? stars)
       [:div {:class "flex items-center space-x-0 text-yellow-600"}
        (star stars star-icon "space-x-1 text-xs text-yellow-500" "Total")
        (star (- stars stars_month) growth-icon-m "space-x-0 text-xs text-red-500 hidden sm:flex" "Stars since beginning of the month")])]

    [:div {:class "flex-1 px-1 sm:px-3 min-w-0"}
     [:p {:class "text-secondary text-xs truncate"}
      (truncate-text description 80)]]

    (when-let [repo-id (and user (str "#repo-" id))]
      [:div {:class "w-8 flex-shrink-0 text-center"}
       (watch-button repo-id repo watched)])]])

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
