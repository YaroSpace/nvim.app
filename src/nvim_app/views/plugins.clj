(ns nvim-app.views.plugins
  (:require
   [nvim-app.views.layout :refer [base-layout]]
   [hiccup.util :as u]
   [hiccup2.core :as h]
   [clojure.string :as str]))

(defn search-form [url]
  [:div {:class "flex items-center bg-gray-100 p-4 rounded mb-2"}
   [:form {:class "flex items-center gap-2 w-full"
           :hx-get (u/url url)
           :hx-trigger "submit, keyup changed delay:300ms from:input[name='q']"
           :hx-target "#plugins-list"
           :hx-include "#query-input, #limit-input, #sort"}

    [:input {:id "query-input"
             :class "px-2 py-2 mr-1 w-full"
             :type "text" :name "q" :placeholder "Category, Name, Repo, Description, Topics"}]

    [:button {:type "submit"
              :class "bg-blue-500 text-white px-2 py-2 rounded"} "Search"]]])

(defn sort-toolbar [url sort]
  [:div {:class "flex space-x-2 items-center justify-between bg-gray-100 p-4 rounded mb-2"}
   [:span {:class "text-gray-700 font-semibold whitespace-nowrap"} "Sort by"]

   [:input#sort {:type "hidden" :name "sort" :value ""}]

   [:span
    [:select {:class "px-2 py-2 text-center bg-blue-500 text-white rounded focus:outline-none"
              :name "sort"
              :hx-get (u/url url)
              :hx-include "#query-input, #limit-input, #page"
              :hx-target "#plugins-list"
              :onchange "document.getElementById('sort').value = this.value;"}
     [:option {:value "" :selected (= "" sort)} "Name ↓"]
     [:option {:value "stars" :selected (= "stars" sort)} "Stars ↓"]
     [:option {:value "updated" :selected (= "updated" sort)} "Last Update ↓"]
     [:option {:value "created" :selected (= "created" sort)} "Added ↓"]]]])

(defn navigation [url page limit total]
  (h/html
   [:div {:class "flex space-x-2 items-center justify-between bg-gray-100 p-4 rounded mb-2 "}
    [:span {:class "px-2 py-2 text-gray-700 font-semibold"}
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

(defn plugins-page [plugins sort page limit total]
  (str
   (h/html
    (let [url "/plugins-page"]
      [:div
       [:div {:class "flex items-center gap-4 mb-4"}
        (navigation url page limit total)
        (sort-toolbar "/plugins-page" sort)]

       (let [grouped (into (sorted-map) (group-by :category plugins))]
         (for [[category plugins] grouped]
           [:div {:class "mb-8"}
            [:h2
             [:span {:class "text-xl font-semibold text-black-600"} "Category: "]
             [:span {:class "text-xl font-semibold text-blue-600"} category]
             [:br] [:br]

             (for [{:keys [url description stargazers topics created_at updated_at]} plugins]
               (let [created (first (str/split (str created_at) #" "))
                     updated (when updated_at (first (str/split (str updated_at) #" ")))]
                 [:div {:class "mb-5"}
                  [:h3 {:class "text-xl font-semibold text-blue-600"}
                   [:span {:class "flex items-center space-x-1"}
                    [:a {:href url} url]
                    [:img {:src "/images/star.jpeg" :alt "Star" :class "w-4 h-4"}]
                    [:span {:class "text-sm text-gray-600"} stargazers]]]
                  [:p {:class "text-gray-700"} description
                   (when-not (empty? topics)
                     [:div {:class "flex flex-wrap gap-2 mt-2"}
                      (for [topic (str/split topics #" ")]
                        [:span {:class "px-2 py-1 bg-gray-200 text-gray-800 text-sm rounded"} topic])])]
                  (when updated
                    [:div {:class "flex items-center justify-between mt-2"}
                     [:p {:class "text-sm text-gray-500 mt-2"}
                      [:span (str "Added: " created)]
                      [:span {:class "ml-2"} (str "Last updated: " updated)]]])]))]]))]))))

(defn plugins []
  (base-layout
   [:div {:class "p-6 max-w-2xl mx-auto"}
    [:div {:class "flex items-center space-x-4"}
     [:img {:src "/images/favicon.ico"}]
     [:h1 {:class "text-3xl font-bold"} "Neovim Plugins Catalog"]
     [:img {:id "indicator" :class "htmx-indicator" :src "/images/loader.svg"}]]

    (search-form "/plugins-page")

    [:div {:id "plugins-list"
           :hx-get (u/url "/plugins-page" {:page 1 :limit 10})
           :hx-trigger "load" :hx-target "#plugins-list"
           :hx-indicator "#indicator"}
     "Loading plugins..."]]))
