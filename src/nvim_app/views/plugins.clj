(ns nvim-app.views.plugins
  (:require [hiccup2.core :as h]))

(defn plugins-page [plugins]
  (h/html
   [:html
    [:head
     [:title "Plugins"]]
    [:body
     [:h1 "Plugins"]
     [:ul
      (for [{:keys [category repo url description]} plugins]
        [:li {:key category}
         [:a {:href url} repo]
         [:p description]])]]]))
