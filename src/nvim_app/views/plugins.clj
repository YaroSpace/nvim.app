(ns nvim-app.views.plugins
  (:require [hiccup2.core :as h]))

(defn plugins-page [plugins]
  (let [grouped (into (sorted-map) (group-by :category plugins))]
    (h/html
     [:html
      [:head
       [:meta {:charset "UTF-8"}]
       [:title "Neovim Plugins"]
       [:script {:src "https://cdn.tailwindcss.com"}]]
      [:body {:class "bg-gray-50"}
       [:div {:class "p-6 max-w-2xl mx-auto"}
        [:h1 {:class "text-3xl font-bold mb-6"} "Neovim Plugins"]
        (for [[category plugins] grouped]
          [:div {:class "mb-8"}
           [:h2
            [:span {:class "text-xl font-semibold text-black-600"} "Category: "]
            [:span {:class "text-xl font-semibold text-blue-600"} category]
            [:br] [:br]]
           (for [{:keys [repo url description]} plugins]
             [:div {:class "mb-5"}
              [:h3 {:class "text-xl font-semibold text-blue-600"} repo]
              [:p {:class "text-gray-700"} url]
              [:p {:class "text-gray-700"} description]])])]]])))
