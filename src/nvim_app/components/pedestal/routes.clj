(ns nvim-app.components.pedestal.routes
  (:require
   [nvim-app.components.pedestal.handlers :as h]
   [io.pedestal.http.route :as route]))

(def routes
  (route/expand-routes
   #{["/" :get h/repos-index :route-name :home]
     ["/news" :get h/news-index :route-name :news]
     ["/about" :get h/about-index :route-name :about]
     ["/repos" :get h/repos-index :route-name :repos]
     ["/repo" :put h/repo-update :route-name :repo-update]
     ["/repos-page" :get h/repos-page :route-name :repos-page]
     ["/auth/github" :get h/github-login :route-name :github-login]
     ["/auth/github/callback" :get h/github-callback :route-name :github-callback]
     ["/user/watch" :put h/user-watch-toggle :route-name :watch-toggle]}))
