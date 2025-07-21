(ns nvim-app.views.layout
  (:require [hiccup.page :as p]))

(defn base-layout
  ([content] (base-layout {:head nil :body nil} content))
  ([include content]
   (p/html5
    [:head
     [:title "The Neovim Plugins Catalog"]
     [:meta {:charset "UTF-8"}]
     (p/include-js "https://cdn.tailwindcss.com")
     (p/include-js "/js/htmx.min.js")
     ; [:style {:type "text/tailwindcss"} "@theme { --color-clifford: #da373d};"]
     [:link {:rel "icon" :href "/images/favicon.ico" :type "image/x-icon"}]
   ; (p/include-css "/css/style.css")]
     (when-let [head (:head include)] head)]

    [:body {:class "bg-gray-50"}
     (when-let [body (:body include)] body)
     content])))

(comment
  (base-layout
   [:div {:class "p-6 max-w-2xl mx-auto"}
    [:h1 {:class "text-3xl font-bold mb-6"} "Neovim Plugins"]
    [:p "Welcome to the Neovim Plugins Catalog!"]]))
