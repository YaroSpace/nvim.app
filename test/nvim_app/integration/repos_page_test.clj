(ns nvim-app.integration.repos-page-test
  (:require
   [nvim-app.utils :refer [with-rpcall] :as u]
   [nvim-app.helpers :refer [setup-sut! with-driver] :as h]
   [etaoin.api :as e]
   [lazytest.core :refer [defdescribe describe before expect it]]
   [matcher-combinators.standalone :as m]))

(def driver (atom nil))
(def sut (atom nil))

(defn go [route & [params]]
  (e/go @driver (h/get-sut-url-for @sut route params)))

(defn get-repo-cards []
  (with-driver driver
    (query-all {:fn/has-class "repo-card"})))

(defn get-repos-on-page []
  (let [els ["repo-url" "repo-category" "repo-description" "repo-topics"
             "repo-stars" "repo-stars-week" "repo-stars-month"
             "repo-created" "repo-updated"
             "repo-category-edit" "repo-description-edit"
             "repo-archived" "repo-hidden" "repo-watch" "repo-edit"]]

    (with-driver driver
      (doall
       (for [repo (get-repo-cards)]
         (merge
          {:repo-hidden-toggle (with-rpcall :silent
                                 (get-element-value-el
                                  (query-from repo {:fn/has-class "repo-hidden-toggle"})))}
          (into {} (for [el els]
                     [(keyword el)
                      (with-rpcall :silent
                        (get-element-text-el
                         (query-from repo {:fn/has-class el})))]))))))))

(defn get-repos-groups []
  (with-driver driver
    (->> (query-all {:fn/has-class "repos-group"})
         (map #(get-element-text-el %)))))

(defn get-repos-page []
  (with-driver driver
    (merge
     (into {} (mapv (fn [el] [el (get-element-text-el (query el "option:checked"))])
                    [:group :sort :category]))
     {:repos (get-repos-on-page)
      :repos-groups (get-repos-groups)
      :search-input (get-element-value :search-input)
      :limit-input (get-element-value :limit-input)
      :repos-page-top (get-element-text {:fn/has-class "repos-page-top"})
      :repos-page-btm (get-element-text {:fn/has-class "repos-page-btm"})})))

(def page (atom nil))

(defdescribe repo-page-test
  {:context [(setup-sut! sut driver :headless? true)]}

  (describe "Compact view")
  (describe "Dark mode")

  (describe "Repos Page"
    (with-driver driver

      (describe "show page with default params"
        (before
         (go :repos)
         (reset! page (get-repos-page)))

        (it "has the correct title"
          (let [title "Neovim Plugins Catalog"]
            (expect (= title (e/get-title @driver)))
            (expect (= title (get-element-text {:tag :h1})))))

        (it "has default params"
          (expect (m/match? {:search-input ""
                             :group "-",
                             :sort "Name"
                             :category "-",
                             :limit-input "10",
                             :repos-page-btm "Page 1 out of 1",
                             :repos-page-top "Page 1 / 1"}
                            @page))
          (expect (= 10 (-> @page :repos count))))

        (it "sorts by Name"
          (expect (= "aref-web.vim" (-> @page :repos first :repo-url)))
          (expect (= "cmdzero.nvim" (-> @page :repos second :repo-url)))
          (expect (= "vim-project-search" (-> @page :repos last :repo-url))))

        (it "shows repo details"
          (expect (= {:repo-archived "",
                      :repo-category "Plugin Manager",
                      :repo-category-edit nil,
                      :repo-created "Created: 2016-05-04",
                      :repo-description "Web dictionaries on the vim with async.",
                      :repo-description-edit nil,
                      :repo-edit nil,
                      :repo-hidden nil,
                      :repo-hidden-toggle nil,
                      :repo-stars "9",
                      :repo-stars-month "6",
                      :repo-stars-week "4",
                      :repo-topics "vim\nneovim\nplugin\ncurl\nweb",
                      :repo-updated "Last updated: 2019-08-13",
                      :repo-url "aref-web.vim",
                      :repo-watch nil}
                     (-> @page :repos first))))

        (it "Sets params from path"
          (go :repos {:params {:q "search" :group "category"
                               :sort "created" :category "Plugin Manager"
                               :page 2 :limit 5}})
          (reset! page (get-repos-page))

          (expect (= {:category "Plugin Manager",
                      :group "Category",
                      :search-input "search",
                      :repos-page-btm "Page 2 out of 1",
                      :repos-page-top "Page 2 / 1",
                      :limit-input "5",
                      :sort "Created"}
                     (dissoc @page :repos :repos-groups)))))

      (describe "Sorting"
        (before
         (go :repos {:params {:sort "name"}}))

        (it "sorts by Name"
          (click [{:id :sort} {:value "name"}])
          (wait 0.7)
          (reset! page (get-repos-page))

          (expect (= "Name" (-> @page :sort)))
          (expect (= "aref-web.vim" (-> @page :repos first :repo-url)))
          (expect (= "cmdzero.nvim" (-> @page :repos second :repo-url)))
          (expect (= "vim-project-search" (-> @page :repos last :repo-url))))

        (it "sorts by Stars Total"
          (click [{:id :sort} {:value "stars"}])
          (wait 0.7)
          (reset! page (get-repos-page))

          (expect (= "Stars Total" (-> @page :sort)))
          (expect (= "neoformat" (-> @page :repos first :repo-url)))
          (expect (= "vim-gina" (-> @page :repos second :repo-url)))
          (expect (= "nvim-vim-runner" (-> @page :repos last :repo-url))))

        (it "sorts by Stars Weekly"
          (click [{:id :sort} {:value "stars_week"}])
          (wait 0.7)
          (reset! page (get-repos-page))

          (expect (= "Stars Weekly" (-> @page :sort)))
          (expect (= "vim-gina" (-> @page :repos first :repo-url)))
          (expect (= "trailblazer.nvim" (-> @page :repos second :repo-url)))
          (expect (= "CSSColorConverter" (-> @page :repos last :repo-url))))

        (it "sorts by Stars Monthly"
          (click [{:id :sort} {:value "stars_month"}])
          (wait 0.7)
          (reset! page (get-repos-page))

          (expect (= "Stars Monthly" (-> @page :sort)))
          (expect (= "neoformat" (-> @page :repos first :repo-url)))
          (expect (= "vim-gina" (-> @page :repos second :repo-url)))
          (expect (= "CSSColorConverter" (-> @page :repos last :repo-url))))

        (it "sorts by Created"
          (click [{:id :sort} {:value "created"}])
          (wait 0.7)
          (reset! page (get-repos-page))

          (expect (= "Created" (-> @page :sort)))
          (expect (= "cmdzero.nvim" (-> @page :repos first :repo-url)))
          (expect (= "CSSColorConverter" (-> @page :repos second :repo-url)))
          (expect (= "neoformat" (-> @page :repos last :repo-url))))

        (it "sorts by Last Updated"
          (click [{:id :sort} {:value "updated"}])
          (wait 0.7)
          (reset! page (get-repos-page))

          (expect (= "Last Update" (-> @page :sort)))
          (expect (= "neoformat" (-> @page :repos first :repo-url)))
          (expect (= "trailblazer.nvim" (-> @page :repos second :repo-url)))
          (expect (= "nvim-vim-runner" (-> @page :repos last :repo-url)))))

      (describe "Grouping"
        (before
         (go :repos {:params {:sort "name" :group ""}}))

        (it "groups by Category"
          (click [{:id :group} {:value "category"}])
          (wait 0.7)
          (reset! page (get-repos-page))

          (expect (= "Category" (-> @page :group)))
          (expect (= "-" (-> @page :repos-groups first)))
          (expect (= "Formatting" (-> @page :repos-groups second)))
          (expect (= "Plugin Manager" (-> @page :repos-groups last)))

          (expect (= "cmdzero.nvim" (-> @page :repos first :repo-url)))
          (expect (= "comfortable-motion.vim" (-> @page :repos second :repo-url))))

        (it "groups by Last Updated"
          (click [{:id :group} {:value "updated"}])
          (wait 0.7)
          (reset! page (get-repos-page))

          (expect (= "Last Update" (-> @page :group)))
          (expect (= "2024-01-25" (-> @page :repos-groups first)))
          (expect (= "2023-03-16" (-> @page :repos-groups second)))
          (expect (= "2022-03-24" (-> @page :repos-groups last)))

          (expect (= "cmdzero.nvim" (-> @page :repos first :repo-url)))
          (expect (= "vim-laravel" (-> @page :repos second :repo-url)))))

      (describe "Searhing"
        (it "searches by description"
          (fill :search-input "project")
          (wait 0.7)
          (reset! page (get-repos-page))

          (expect (= "trailblazer.nvim" (-> @page :repos first :repo-url)))
          (expect (= "vim-laravel" (-> @page :repos second :repo-url)))))

      #_(describe "Editing"
          (it "shows edit fields")
          (it "updates data")
          (it "toggles watch")))))

