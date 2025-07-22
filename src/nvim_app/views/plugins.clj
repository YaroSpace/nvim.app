(ns nvim-app.views.plugins
  (:require
   [nvim-app.views.layout :refer [base-layout]]
   [hiccup.util :as u]
   [hiccup2.core :as h]))

(defn search-form [url]
  [:form {:class "flex items-center gap-2 mb-2"
          :hx-get (u/url url)
          :hx-trigger "submit, keyup changed delay:300ms from:input[name='q']"
          :hx-target "#plugins-list" :hx-include "#query-input, #limit-input"}

   [:input {:id "query-input"
            :class "px-2 py-2 mr-1"
            :type "text" :name "q" :placeholder "Search plugins"}]

   [:button {:type "submit"
             :class "bg-blue-500 text-white px-2 py-2 rounded"} "Search"]])

(defn plugins []
  (base-layout
   [:div {:class "p-6 max-w-2xl mx-auto"}
    [:div {:class "flex items-center space-x-4"}
     [:h1 {:class "text-3xl font-bold"} "The Neovim Plugins Catalog"]
     [:img {:id "indicator" :class "htmx-indicator" :src "/images/loader.svg"}]]

    (search-form "/plugins-page")

    [:div {:id "plugins-list"
           :hx-get (u/url "/plugins-page" {:page 1 :limit 10})
           :hx-trigger "load" :hx-target "#plugins-list"
           :hx-indicator "#indicator"}
     "Loading plugins..."]]))

(defn navigation [url page limit total]
  (h/html
   [:span {:class "px-2 py-2 text-gray-700 font-semibold"
           :style "min-width: 8rem; display: inline-block;"}
    (str "Page " page " of " total)]

   [:input {:id "limit-input"
            :class "border rounded px-2 py-2 mr-1" :style "width: 5rem;"
            :type "number" :name "limit" :value limit :min 1
            :placeholder "Results per page"
            :hx-get (u/url url {:page  page}) :hx-include "#query-input"
            :hx-trigger "keyup changed delay:300ms" :hx-target "#plugins-list"
            :hx-indicator "#indicator"}]

   [:button {:class "px-2 py-2 bg-blue-500 text-white rounded disabled:opacity-50"
             :hx-get (u/url url {:page (max 1 (dec page))})
             :hx-include "#query-input, #limit-input"
             :hx-target "#plugins-list"
             :hx-indicator "#indicator"
             :disabled (<= page 1)} "previous"]

   [:button {:class "px-2 py-2 bg-blue-500 text-white rounded disabled:opacity-50"
             :hx-get (u/url url {:page (min total (inc page))})
             :hx-include "#query-input, #limit-input"
             :hx-target "#plugins-list"
             :hx-indicator "#indicator"
             :disabled (>= page total)} "next"]))

(defn plugins-page [plugins page limit total]
  (str
   (h/html
    (let [url "/plugins-page"]
      [:div
       [:div {:class "flex items-center gap-4 mb-4"}
        (navigation url page limit total)]

       (let [grouped (into (sorted-map) (group-by :category plugins))]
         (for [[category plugins] grouped]
           [:div {:class "mb-8"}
            [:h2
             [:span {:class "text-xl font-semibold text-black-600"} "Category: "]
             [:span {:class "text-xl font-semibold text-blue-600"} category]
             [:br] [:br]

             (for [{:keys [repo url description]} plugins]
               [:div {:class "mb-5"}
                [:h3 {:class "text-xl font-semibold text-blue-600"}
                 [:a {:href url} (str "https://gitbub.com/" repo)]]
                [:p {:class "text-gray-700"} description]])]]))]))))
