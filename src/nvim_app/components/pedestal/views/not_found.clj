(ns nvim-app.components.pedestal.views.not-found
  (:require [nvim-app.components.pedestal.views.layout :refer [base-layout]]))

(defn index []
  (base-layout
   [:h1 {:class "text-green-800 px-4 py-4"} "404 - Page Not Found"
    [:p "The page you are looking for does not exist"]]))
