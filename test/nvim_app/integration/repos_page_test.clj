(ns nvim-app.integration.repos-page-test
  (:require
   [nvim-app.helpers :refer [setup-sut! silent-logging with-driver] :as h]
   [nvim-app.integration.core :refer :all]
   [lazytest.core :refer [defdescribe describe before expect it]]
   [matcher-combinators.standalone :as m]))

(def driver (atom nil))
(def sut (atom nil))
(def page (atom nil))

(def sleep 0.7)

(defn go-sut [route & [params]]
  (with-driver driver
    (go (h/get-sut-url-for @sut route params))))

(defdescribe repo-page-test
  {:context [silent-logging (setup-sut! sut driver :headless? true)]}

  (describe "Compact view")
  (describe "Dark mode")

  (describe "Repos Page"
    (with-driver driver

      (describe "show page with default params"
        (before
         (go-sut :repos)
         (reset! page (get-repos-page driver)))

        (it "has the correct title"
          (let [title "Neovim Plugins Catalog"]
            (expect (= title (get-title)))
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
          (go-sut :repos {:params {:q "search" :group "category"
                                   :sort "created" :category "Plugin Manager"
                                   :page 2 :limit 5}})
          (reset! page (get-repos-page driver))

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
         (go-sut :repos {:params {:sort "name"}}))

        (it "sorts by Name"
          (click [{:id :sort} {:value "name"}])
          (wait sleep)
          (reset! page (get-repos-page driver))

          (expect (= "Name" (-> @page :sort)))
          (expect (= "aref-web.vim" (-> @page :repos first :repo-url)))
          (expect (= "cmdzero.nvim" (-> @page :repos second :repo-url)))
          (expect (= "vim-project-search" (-> @page :repos last :repo-url))))

        (it "sorts by Stars Total"
          (click [{:id :sort} {:value "stars"}])
          (wait sleep)
          (reset! page (get-repos-page driver))

          (expect (= "Stars Total" (-> @page :sort)))
          (expect (= "neoformat" (-> @page :repos first :repo-url)))
          (expect (= "vim-gina" (-> @page :repos second :repo-url)))
          (expect (= "nvim-vim-runner" (-> @page :repos last :repo-url))))

        (it "sorts by Stars Weekly"
          (click [{:id :sort} {:value "stars_week"}])
          (wait sleep)
          (reset! page (get-repos-page driver))

          (expect (= "Stars Weekly" (-> @page :sort)))
          (expect (= "vim-gina" (-> @page :repos first :repo-url)))
          (expect (= "trailblazer.nvim" (-> @page :repos second :repo-url)))
          (expect (= "CSSColorConverter" (-> @page :repos last :repo-url))))

        (it "sorts by Stars Monthly"
          (click [{:id :sort} {:value "stars_month"}])
          (wait sleep)
          (reset! page (get-repos-page driver))

          (expect (= "Stars Monthly" (-> @page :sort)))
          (expect (= "neoformat" (-> @page :repos first :repo-url)))
          (expect (= "vim-gina" (-> @page :repos second :repo-url)))
          (expect (= "CSSColorConverter" (-> @page :repos last :repo-url))))

        (it "sorts by Created"
          (click [{:id :sort} {:value "created"}])
          (wait sleep)
          (reset! page (get-repos-page driver))

          (expect (= "Created" (-> @page :sort)))
          (expect (= "cmdzero.nvim" (-> @page :repos first :repo-url)))
          (expect (= "CSSColorConverter" (-> @page :repos second :repo-url)))
          (expect (= "neoformat" (-> @page :repos last :repo-url))))

        (it "sorts by Last Updated"
          (click [{:id :sort} {:value "updated"}])
          (wait sleep)
          (reset! page (get-repos-page driver))

          (expect (= "Last Update" (-> @page :sort)))
          (expect (= "neoformat" (-> @page :repos first :repo-url)))
          (expect (= "trailblazer.nvim" (-> @page :repos second :repo-url)))
          (expect (= "nvim-vim-runner" (-> @page :repos last :repo-url)))))

      (describe "Grouping"
        (before
         (go-sut :repos {:params {:sort "name" :group ""}}))

        (it "groups by Category"
          (click [{:id :group} {:value "category"}])
          (wait sleep)
          (reset! page (get-repos-page driver))

          (expect (= "Category" (-> @page :group)))
          (expect (= "-" (-> @page :repos-groups first)))
          (expect (= "Formatting" (-> @page :repos-groups second)))
          (expect (= "Plugin Manager" (-> @page :repos-groups last)))

          (expect (= "cmdzero.nvim" (-> @page :repos first :repo-url)))
          (expect (= "comfortable-motion.vim" (-> @page :repos second :repo-url))))

        (it "groups by Last Updated"
          (click [{:id :group} {:value "updated"}])
          (wait sleep)
          (reset! page (get-repos-page driver))

          (expect (= "Last Update" (-> @page :group)))
          (expect (= "2024-01-25" (-> @page :repos-groups first)))
          (expect (= "2023-03-16" (-> @page :repos-groups second)))
          (expect (= "2022-03-24" (-> @page :repos-groups last)))

          (expect (= "cmdzero.nvim" (-> @page :repos first :repo-url)))
          (expect (= "vim-laravel" (-> @page :repos second :repo-url)))))

      (describe "Searhing"
        (it "searches by description"
          (fill :search-input "project")
          (wait sleep)
          (reset! page (get-repos-page driver))

          (expect (= "trailblazer.nvim" (-> @page :repos first :repo-url)))
          (expect (= "vim-laravel" (-> @page :repos second :repo-url)))))

      (describe "Filtering"))))
