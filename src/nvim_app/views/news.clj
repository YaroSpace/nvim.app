(ns nvim-app.views.news
  (:require [nvim-app.views.layout :refer [base-layout]]))

(defn index []
  (base-layout
   [:div {:class "max-w-4xl mx-auto p-6"}
    [:div {:class ""}
     [:h2 {:class "text-xl font-semibold text-green-900"} "News"]
     [:br] [:br]
     [:div {:class "text-green-900 text-lg"}
      [:p "The place for news, updates, and announcements related to Neovim plugins and the Neovim community."]
      [:br]]]]))
