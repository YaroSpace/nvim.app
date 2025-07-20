(ns nvim-app.components.pedestal.routes
  (:require
   [nvim-app.components.pedestal.handlers :as h]

   [io.pedestal.http.route :as route]))
   ; [io.pedestal.http.body-params :as body-params]))

(def routes
  (route/expand-routes
   #{["/" :get h/plugins-handler :route-name :home]
     ["/greet" :get h/respond-hello :route-name :greet]
     ["/info" :get h/info-handler :route-name :info]
     ["/plugins-page" :get h/plugins-page-handler :route-name :plugins-page]}))
