(ns nvim-app.views.preview
  (:require [hiccup2.core :refer [html]]))

(defn index [id]
  (str
   (html
    [:div {:class "preview-content h-120 w-80 p-2 bg-white"}
     [:img {:src (str "images/preview-" id ".png") :class "w-full h-full object-contain"}]])))
