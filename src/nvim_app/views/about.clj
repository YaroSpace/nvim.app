(ns nvim-app.views.about
  (:require [nvim-app.views.layout :refer [base-layout]]))

(defn index [request]
  (base-layout
   request
   [:div {:class "max-w-4xl mx-auto p-6 text-justify"}
    [:span {:class "flex items-center"}
     [:span {:class "shrink-0 pe-4 text-xl font-semibold text-green-900"} "About"]
     [:span {:class "h-px flex-1 bg-gray-300"}]]
    [:br]

    [:div {:class "text-green-900 text-lg"}
     [:p "This catalog of Neovim plugins and Neovim related projects is my way to express gratitude and appreciation to the Neovim community."]
     [:br]
     [:p "The data is collected from "
      [:a {:class "font-semibold" :href "https://github.com/rockerBOO/awesome-neovim"} "Awesome Neovim"]
      " and searched in " [:span {:class "font-semibold"} "GitHub"] " for topics "
      [:span {:class "bg-gray-100 text-green-800 font-mono text-sm px-1.5 py-0.5 rounded"} "neovim, nvim, plugin"]
      ", so some users' configs may also end up in the catalog."]
     [:br]
     [:p "If you have ideas or suggestions, please feel free to to open an issue or a feature request on "
      [:a {:class "font-semibold" :href "https://github.com/yarospace/nvim.app"} "GitHub"] "."]]]))
