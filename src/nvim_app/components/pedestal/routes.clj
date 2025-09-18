(ns nvim-app.components.pedestal.routes
  (:require
   [nvim-app.components.pedestal.handlers.core :as h]
   [nvim-app.components.pedestal.handlers.repos :as r]
   [nvim-app.components.pedestal.handlers.github :as g]))

(def routes
  #{["/" :get r/repos-index :route-name :home]
    ["/news" :get h/news-index :route-name :news]
    ["/about" :get h/about-index :route-name :about]
    ["/repo" :put r/repo-update :route-name :repo-update]
    ["/repo/preview" :get r/preview :route-name :preview]
    ["/repos" :get r/repos-index :route-name :repos]
    ["/repos-page" :get r/repos-page :route-name :repos-page]
    ["/user/watch" :put r/user-watch-toggle :route-name :watch-toggle]
    ["/auth/github" :get g/github-login :route-name :github-login]
    ["/auth/github" :delete g/github-logout :route-name :github-logout]
    ["/auth/github/callback" :get g/github-callback :route-name :github-callback]})
