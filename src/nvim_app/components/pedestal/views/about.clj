(ns nvim-app.components.pedestal.views.about
  (:require
   [nvim-app.components.pedestal.views.layout :refer [base-layout]]
   [nvim-app.utils :as u]
   [clojure.java.io :as io]))

(defn index [request]
  (base-layout
   request
   [:div {:class "max-w-4xl mx-auto p-6 text-justify text-nvim-text text-lg"}
    [:span {:class "flex items-center"}
     [:span {:class "shrink-0 pe-4 text-xl font-semibold"} "About"]
     [:span {:class "h-px flex-1 bg-gray-300"}]]
    [:div {:class "markdown"}
     (u/markdown->html (slurp (io/resource "public/static/about.md")))]]))
