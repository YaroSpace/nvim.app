(ns nvim-app.views.about
  (:require [nvim-app.views.layout :refer [base-layout]]))

(defn index []
  (base-layout
   [:div {:class "max-w-4xl mx-auto p-6"}
    [:div {:class ""}
     [:h2 {:class "text-xl font-semibold text-green-900"} "About"]
     [:br] [:br]
     [:div {:class "text-green-900 text-lg"}
      [:p "This is a catalog of Neovim plugins and Neovim related projects and my way to express gratitude and appreciation to the Neovim community."]
      [:br]
      [:p "The data is collected from "
       [:a {:class "font-semibold" :href "https://github.com/rockerBOO/awesome-neovim"} "Awesome Neovim"]
       " and searched in " [:span {:class "font-semibold"} "GitHub"]
       " for topics \"neovim, nvim, plugin\", so some users' configs may also end up in the catalog."]
      [:br]
      [:p "If you have ideas or suggestions, please feel free to to open an issue or a feature request on GitHub."]]]]))
