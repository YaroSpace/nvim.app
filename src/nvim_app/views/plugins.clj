(ns nvim-app.views.plugins
  (:require
   [nvim-app.views.layout :refer [base-layout]]
   [hiccup.util :as u]
   [hiccup2.core :as h]
   [clojure.string :as str]))

(defn search-form [url]
  [:form {:class "flex items-center gap-2"
          :hx-get (u/url url)
          :hx-trigger "submit, keyup changed delay:300ms from:input[name='q']"
          :hx-target "#plugins-list"
          :hx-include "#query-input, #limit-input, #sort"}

   [:input {:id "query-input"
            :class "px-2 py-2 mr-1"
            :type "text" :name "q" :placeholder "Search plugins"}]

   [:button {:type "submit"
             :class "bg-blue-500 text-white px-2 py-2 rounded"} "Search"]])

(defn sort-toolbar [url]
  [:div {:class "flex space-x-2"}
   [:input#sort {:type "hidden" :name "sort" :value ""}]

   [:button {:class "px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 focus:outline-none whitespace-nowrap"
             :hx-get (u/url url {:sort "stars"})
             :hx-include "#query-input, #limit-input, #page"
             :hx-on:click "document.getElementById('sort').value = 'stars';"
             :hx-target "#plugins-list"} "Stars ↓"]

   [:button {:class "px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 focus:outline-none whitespace-nowrap"
             :hx-get (u/url url {:sort "lastChange"})
             :hx-include "#query-input, #limit-input, #page"
             :hx-on:click "document.getElementById('sort').value = 'lastChange';"
             :hx-target "#plugins-list"} "Last Change ↓"]])

(defn plugins []
  (base-layout
   [:div {:class "p-6 max-w-2xl mx-auto"}
    [:div {:class "flex items-center space-x-4"}
     [:img {:src "/images/favicon.ico"}]
     [:h1 {:class "text-3xl font-bold"} "Neovim Plugins Catalog"]
     [:img {:id "indicator" :class "htmx-indicator" :src "/images/loader.svg"}]]

    [:div {:class "flex items-center justify-between bg-gray-100 p-4 rounded mb-4"}
     (search-form "/plugins-page")
     (sort-toolbar "/plugins-page")]

    [:div {:id "plugins-list"
           :hx-get (u/url "/plugins-page" {:page 1 :limit 10})
           :hx-trigger "load" :hx-target "#plugins-list"
           :hx-indicator "#indicator"}
     "Loading plugins..."]]))

(defn navigation [url page limit total]
  (h/html
   [:div {:class "flex space-x-2 items-center justify-between bg-gray-100 p-4 rounded mb-4 "}
    [:span {:class "px-2 py-2 text-gray-700 font-semibold"
            :style "min-width: 8rem; display: inline-block;"}
     (str "Page " page " of " total)]

    [:input#page {:type "hidden" :name "page" :value page}]

    [:input {:id "limit-input"
             :class "border rounded px-2 py-2" :style "width: 4rem;"
             :type "number" :name "limit" :value limit :min 1
             :placeholder "Results per page"
             :hx-get (u/url url {:page  page})
             :hx-include "#query-input, #sort"
             :hx-trigger "change, keyup changed delay:300ms"
             :hx-target "#plugins-list"
             :hx-indicator "#indicator"}]

    [:button {:class "px-2 py-2 bg-blue-500 text-white rounded disabled:opacity-50"
              :hx-get (u/url url {:page (max 1 (dec page))})
              :hx-include "#query-input, #limit-input, #sort"
              :hx-target "#plugins-list"
              :hx-indicator "#indicator"
              :disabled (<= page 1)} "<<"]

    [:button {:class "px-2 py-2 bg-blue-500 text-white rounded disabled:opacity-50"
              :hx-get (u/url url {:page (min total (inc page))})
              :hx-include "#query-input, #limit-input, #sort"
              :hx-target "#plugins-list"
              :hx-indicator "#indicator"
              :disabled (>= page total)} ">>"]]))

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

             (for [{:keys [url description stargazers topics updated_at]} plugins]
               [:div {:class "mb-5"}
                [:h3 {:class "text-xl font-semibold text-blue-600"}
                 [:span {:class "flex items-center space-x-1"}
                  [:a {:href url} url]
                  [:img {:src "/images/star.jpeg" :alt "Star" :class "w-4 h-4"}]
                  [:span {:class "text-sm text-gray-600"} stargazers]]]
                [:p {:class "text-gray-700"} description
                 (when topics
                   [:div {:class "flex flex-wrap gap-2 mt-2"}
                    (for [topic (str/split topics #" ")]
                      [:span {:class "px-2 py-1 bg-gray-200 text-gray-800 text-sm rounded"} topic])])]
                (when-let [updated_at (when updated_at (first (str/split (str updated_at) #" ")))]
                  [:div {:class "flex items-center justify-between mt-2"}
                   [:p {:class "text-sm text-gray-500 mt-2"} (str "Last updated: " updated_at)]])])]]))]))))
