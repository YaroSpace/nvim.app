(ns nvim-app.views.about
  (:require [nvim-app.views.layout :refer [base-layout]]))

(defn index [request]
  (base-layout
   request
   [:div {:class "max-w-4xl mx-auto p-6 text-justify text-green-900 text-lg"}
    [:span {:class "flex items-center"}
     [:span {:class "shrink-0 pe-4 text-xl font-semibold"} "About"]
     [:span {:class "h-px flex-1 bg-gray-300"}]]
    [:br]

    [:div
     [:p "This catalog of Neovim plugins and Neovim related projects is my way to express gratitude and appreciation to the Neovim community."]
     [:br]

     [:p "The data is collected from "
      [:a {:class "font-semibold" :href "https://github.com/rockerBOO/awesome-neovim"} "Awesome Neovim"]
      " and searched in " [:span {:class "font-semibold"} "GitHub"] " for topics "
      [:span {:class "bg-gray-100 font-mono text-sm px-1.5 py-0.5 rounded"} "neovim, nvim, plugin"]
      ", so some users' configs may also end up in the catalog."]
     [:br]

     [:h2 {:class "shrink-0 pe-4 text-xl font-semibold"}
      [:a {:href "#api"} "API"]]
     [:br]
     [:p "Plugin data is available as JSON from:"] [:br]
     [:div {:class "italic"}
      [:p] "https://nvim.app/repos-page?" [:br]
      "&nbsp&nbsp sort=sort_name" [:br]
      "&nbsp&nbsp &q=search_string" [:br]
      "&nbsp&nbsp &group=group_name" [:br]
      "&nbsp&nbsp &category=category_name" [:br]
      "&nbsp&nbsp &limit=results_per_page" [:br]
      "&nbsp&nbsp &page=page_no"] [:br]
     [:p "Request headers should contain: " [:span {:class "font-semibold"} "Accept: application/json"]]]
    [:br]

    [:h2 {:class "shrink-0 pe-4 text-xl font-semibold"} "TODO"]
    [:br]
    [:ul {:class "list-disc list-inside"}
     [:li "Allow to watch plugins - done"]
     [:li "Allow plugin owners to edit plugin info - done"]
     [:li "Sort/filter by archived - done"]
     [:li "Add compact view"]
     [:li "Dark mode"]
     [:li "Vim keyboard bindings"]
     [:li "Preview plugins' images"]]
    [:br]

    [:p "If you have ideas or suggestions, please feel free to to open an issue or a feature request on "
     [:a {:class "font-semibold" :href "https://github.com/yarospace/nvim.app"} "GitHub"] "."]]))
