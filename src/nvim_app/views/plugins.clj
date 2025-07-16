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
      (for [{:keys [id repo url description]} plugins]
        [:li {:key id}
         [:a {:href url} repo]
         [:p description]])]]]))
