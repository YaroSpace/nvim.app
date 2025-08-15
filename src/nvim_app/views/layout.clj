(ns nvim-app.views.layout
  (:require [nvim-app.views.assets :refer :all]
            [hiccup.page :refer [html5 include-js include-css]]))

(def bg-color "background-color:#d3e4db; ")

(defn head []
  [:head
   [:meta {:charset "UTF-8"}]
   [:meta {:content "width=device-width, initial-scale=1.0" :name "viewport"}]
   [:title "Neovim Plugins Catalog"]

   (include-js "/js/htmx.min.js")
   (include-css "/css/out.css")

   [:link {:href "/images/favicon.ico" :rel "icon" :type "image/x-icon"}]])

(def header
  [:header {:style (str bg-color "border-bottom: 1px solid #c1d5c9")}
   [:div {:class "max-w-4xl mx-auto px-4 py-6"}
    [:div {:class "flex items-center justify-between"}
     [:div {:class "flex items-center space-x-4"}

      [:img {:alt "Neovim" :class "w-7 h-8" :crossorigin "anonymous"
             :onerror "this.style.display='none'; this.nextElementSibling.style.display='flex'"
             :src "https://neovim.io/logos/neovim-mark-flat.png"}]

      [:div {:class "w-8 h-8 bg-green-600 rounded-full flex items-center justify-center"
             :style "display: none"}
       [:span {:class "text-white text-sm font-bold"} "N"]]

      [:h1 {:class "text-2xl font-bold text-green-900"} "Neovim Plugins Catalog"]]

     [:a {:class "text-gray-600 hover:text-gray-900" :href "https://github.com/yarospace/nvim.app"}
      (github-icon)]]]])

(def footer
  [:footer {:style (str bg-color "border-top: 1px solid #c1d5c9; margin-top: 0rem")}
   [:div {:class "max-w-4xl mx-auto px-4 py-8"}
    [:div {:class "text-center"}
     [:h2 {:class "text-2xl font-bold text-gray-900 tracking-wider"}]]]])

(defn base-layout [body]
  (html5
   (head)
   [:body {:style "background-color: #e7eee8; min-height: 100vh"}
    header
    body]
   footer))
