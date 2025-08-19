(ns nvim-app.views.news
  (:require [nvim-app.views.layout :refer [base-layout]]))

(defn index [request]
  (base-layout
   request
   [:div {:class "max-w-4xl mx-auto p-6 text-justify"}
    [:span {:class "flex items-center"}
     [:span {:class "shrink-0 pe-4 text-xl font-semibold text-green-900"} "News"]
     [:span {:class "h-px flex-1 bg-gray-300"}]]
    [:br]
    [:div {:class "text-green-900 text-lg"}
     [:p "The place for news, updates, and announcements related to Neovim plugins and the Neovim community."]
     [:br]]]))
