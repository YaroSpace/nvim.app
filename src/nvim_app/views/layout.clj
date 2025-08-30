(ns nvim-app.views.layout
  (:require
   [nvim-app.state :refer [dev? app-config]]
   [nvim-app.views.assets :refer :all]
   [hiccup.page :refer [html5 include-js include-css]]
   [hiccup2.core :refer [raw]]))

(defn google-analytics []
  [:div
   [:script {:async "async" :src "https://www.googletagmanager.com/gtag/js?id=G-ZL4M33MPVW"}]
   (include-js "/js/google-analytics.js")])

(defn head [head-include]
  [:head
   [:meta {:charset "UTF-8"}]
   [:meta {:content "width=device-width, initial-scale=1.0" :name "viewport"}]
   [:link {:href "/images/favicon.ico" :rel "icon" :type "image/x-icon"}]

   (when dev?
     [:meta {:name "htmx-config" :content "{\"responseHandling\": [{\"code\":\".*\", \"swap\": true}]}"}])

   [:title "Neovim Plugins Catalog"]

   (include-js "/js/htmx.min.js")
   (include-js "/js/layout.js")
   (include-css "/css/out.css")

   (when head-include head-include)
   (when-not dev? (google-analytics))])

(defn menu-item [href text]
  [:a {:href href :onclick "toggleMenu(this)"
       :class "block px-4 py-2 text-sm text-secondary
               hover-brand hover-brand-text transition-colors"} text])

(defn menu []
  [:div {:class "relative"}
   [:button {:id "menu-btn"
             :class "text-brand hover-brand-text focus-brand rounded-lg p-1"
             :type "button"
             :onclick "toggleMenu(this)"}
    (menu-icon)]

   [:div {:id "menu" :class "hidden absolute top-full left-0 mt-2 w-48
                 bg-surface-card rounded-lg shadow-lg border border-subtle z-50"}
    [:div {:class "py-1"}
     (menu-item "/" "Home")
     (menu-item "/news" "News")
     (menu-item "/about" "About")]]])

(defn user-login [user]
  (if-not user
    [:a {:title "Login with GitHub" :href "/auth/github"}
     (github-icon)]

    [:div {:class "w-8 h-8 rounded-full overflow-hidden"}
     [:a {:href (:url user) :class "block w-full h-full"}
      [:img {:src (:avatar_url user)}]]]))

(defn header [{:keys [user]}]
  [:header {:class "bg-surface-card border-b border-subtle"}
   [:div {:class "max-w-4xl mx-auto px-4 py-4"}
    [:div {:class "flex items-center justify-between relative"}
     (menu)

     [:div {:class "flex items-center space-x-2 sm:space-x-6"}
      [:img {:alt "Neovim" :class "w-7 h-8" :crossorigin "anonymous"
             :onerror "this.style.display='none'; this.nextElementSibling.style.display='flex'"
             :src "/images/neovim.png"}]

      [:div {:class "w-8 h-8 bg-brand rounded-full flex items-center justify-center"
             :style "display: none"}
       [:span {:class "text-white text-sm font-bold"} "N"]]

      [:h1 {:class "text-2xl font-bold text-brand-strong"} "Neovim Plugins Catalog"]]

     [:div {:class "flex items-center space-x-4"}
      (if (-> app-config :app :features :auth)
        (user-login user)

        [:a {:title "Nvim.app on GitHub"
             :class "text-muted hover:text-primary"
             :href "https://github.com/yarospace/nvim.app"}
         (github-icon)])]]]])

(def footer
  [:footer {:class "bg-surface-card mt-0 border-t border-subtle"}
   [:div {:class "max-w-4xl mx-auto px-4 py-8"}
    [:div {:class "text-center"}
     [:h2 {:class "text-2xl font-bold text-primary tracking-wider"}]]]])

(defn base-layout
  ([body]
   (base-layout {} nil body))
  ([request body]
   (base-layout {} request body))
  ([{:keys [head_include body_include]} request body]
   (html5
    (head head_include)
    [:body {:class "dark:scheme-dark min-h-screen bg-surface text-primary"}
     (let [mode (:mode request)]
       (if (or (nil? mode) (= "light" mode))
         [:script (raw "document.documentElement.classList.remove('dark');")]
         [:script (raw "document.documentElement.classList.add('dark');")]))

     (when body_include body_include)
     [:div {:id "alert-container-main"}
      (alert (:flash request))]
     (header request)
     body]

    footer)))
