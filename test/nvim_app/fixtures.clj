(ns nvim-app.fixtures
  (:require [clojure.instant :as inst]))

(defn str->date [s]
  (inst/read-instant-timestamp s))

(def users
  [{:github_id 10,
    :username "username",
    :email "email",
    :name "name",
    :url "profile_url",
    :avatar_url "avatar_url"
    :role "admin"}])

(def categories
  [{:id 1, :name "Plugin Manager"}
   {:id 2, :name "LSP"}
   {:id 3, :name "Completion"}
   {:id 4, :name "AI"}
   {:id 5, :name "Programming Languages Support"}
   {:id 6, :name "Language"}
   {:id 7, :name "Syntax"}
   {:id 8, :name "Snippet"}
   {:id 9, :name "Register"}
   {:id 10, :name "Marks"}
   {:id 11, :name "Search"}
   {:id 12, :name "Fuzzy Finder"}
   {:id 13, :name "File Explorer"}
   {:id 14, :name "Project"}
   {:id 15, :name "Color"}
   {:id 16, :name "Colorscheme"}
   {:id 17, :name "Bars and Lines"}
   {:id 18, :name "Startup"}
   {:id 19, :name "Icon"}
   {:id 20, :name "Media"}
   {:id 21, :name "Note Taking"}
   {:id 22, :name "Utility"}
   {:id 23, :name "Animation"}
   {:id 24, :name "Terminal Integration"}
   {:id 25, :name "Debugging"}
   {:id 26, :name "Deployment"}
   {:id 27, :name "Test"}
   {:id 28, :name "Code Runner"}
   {:id 29, :name "Neovim Lua Development"}
   {:id 30, :name "Fennel"}
   {:id 31, :name "Dependency Management"}
   {:id 32, :name "Git"}
   {:id 33, :name "Motion"}
   {:id 34, :name "Keybinding"}
   {:id 35, :name "Mouse"}
   {:id 36, :name "Scrolling"}
   {:id 37, :name "Editing Support"}
   {:id 38, :name "Formatting"}
   {:id 39, :name "Command Line"}
   {:id 40, :name "Session"}
   {:id 41, :name "Remote Development"}
   {:id 42, :name "Split and Window"}
   {:id 43, :name "Game"}
   {:id 44, :name "Workflow"}
   {:id 45, :name "Preconfigured Configuration"}
   {:id 46, :name "External"}
   {:id 47, :name "Starter Templates"}
   {:id 48, :name "Vim"}
   {:id 49, :name "Resource"}])

(def repos
  (mapv #(-> %
             (update :created str->date)
             (update :updated str->date))
        [{:description
          "TrailBlazer enables you to seemlessly move through important project marks as quickly and efficiently as possible to make your workflow blazingly fast ™.",
          :archived false,
          :dirty false,
          :updated "2025-02-04T10:07:04.000000000-00:00",
          :name "trailblazer.nvim",
          :stars_week 200,
          :created "2023-01-11T18:38:53.000000000-00:00",
          :stars_month 100,
          :topics
          "neovim neovim-lua neovim-plugin vim-motions motion nvim vim lua nvim-plugin",
          :hidden false,
          :id 1606,
          :stars 275,
          :url "https://github.com/LeonHeidelbach/trailblazer.nvim",
          :repo "LeonHeidelbach/trailblazer.nvim",
          :owner "LeonHeidelbach",
          :category_id 10}
         {:description ":sparkles: A (Neo)vim plugin for formatting code.",
          :archived false,
          :dirty false,
          :updated "2025-07-29T23:19:23.000000000-00:00",
          :name "neoformat",
          :stars_week 2000,
          :created "2016-03-15T03:50:00.000000000-00:00",
          :stars_month 1000,
          :topics "vim neovim formatter plugin",
          :hidden false,
          :id 4,
          :stars 2041,
          :url "https://github.com/sbdchd/neoformat",
          :repo "sbdchd/neoformat",
          :owner "sbdchd",
          :category_id 38}
         {:description
          "Experimental Neovim plugin that replaces cmdline with a floating equivalent",
          :archived false,
          :dirty false,
          :updated "2024-01-31T19:54:59.000000000-00:00",
          :name "cmdzero.nvim",
          :stars_week 400,
          :created "2024-01-26T03:24:37.000000000-00:00",
          :stars_month 300,
          :topics "neovim neovim-plugin plugin",
          :hidden false,
          :id 442,
          :stars 1,
          :url "https://github.com/ivanjermakov/cmdzero.nvim",
          :repo "ivanjermakov/cmdzero.nvim",
          :owner "ivanjermakov",
          :category_id nil}
         {:description "Web dictionaries on the vim with async.",
          :archived true,
          :dirty false,
          :updated "2019-08-13T03:58:34.000000000-00:00",
          :name "aref-web.vim",
          :stars_week 5,
          :created "2016-05-04T14:06:36.000000000-00:00",
          :stars_month 3,
          :topics "vim neovim plugin curl web",
          :hidden false,
          :id 11,
          :stars 9,
          :url "https://github.com/aiya000/aref-web.vim",
          :repo "aiya000/aref-web.vim",
          :owner "aiya000",
          :category_id 1}
         {:description
          "Brings physics-based smooth scrolling to the Vim world!",
          :archived false,
          :dirty false,
          :updated "2018-02-22T20:08:57.000000000-00:00",
          :name "comfortable-motion.vim",
          :stars_week 600,
          :created "2016-10-02T12:01:05.000000000-00:00",
          :stars_month 300,
          :topics "vim neovim smooth-scrolling physical-based plugin",
          :hidden false,
          :id 6,
          :stars 641,
          :url "https://github.com/yuttie/comfortable-motion.vim",
          :repo "yuttie/comfortable-motion.vim",
          :owner "yuttie",
          :category_id nil}
         {:description
          "👣  Asynchronously control git repositories in Neovim/Vim 8",
          :archived true,
          :dirty false,
          :updated "2022-03-30T04:28:47.000000000-00:00",
          :name "vim-gina",
          :stars_week 500,
          :created "2016-12-14T09:55:30.000000000-00:00",
          :stars_month 300,
          :topics "vim neovim git plugin jobs asynchronously vim-gita gina",
          :hidden false,
          :id 5,
          :stars 689,
          :url "https://github.com/lambdalisue/vim-gina",
          :repo "lambdalisue/vim-gina",
          :owner "lambdalisue",
          :category_id nil}
         {:description
          " 🚧 A Neovim plugin that facilitates the identification of a HEX, RGB, HSL, or LCH color name and its conversion.",
          :archived false,
          :dirty false,
          :updated "2024-09-30T23:15:05.000000000-00:00",
          :name "CSSColorConverter",
          :stars_week 6000,
          :created "2023-12-05T16:50:17.000000000-00:00",
          :stars_month 3000,
          :topics "lua nvim-plugin",
          :hidden false,
          :id 6456,
          :stars 0,
          :url "https://github.com/farias-hecdin/CSSColorConverter",
          :repo "farias-hecdin/CSSColorConverter",
          :owner "farias-hecdin",
          :category_id nil}
         {:description "Vim support for Laravel/Lumen projects",
          :archived false,
          :dirty false,
          :updated "2023-03-21T15:04:36.000000000-00:00",
          :name "vim-laravel",
          :stars_week 100,
          :created "2016-09-13T01:47:50.000000000-00:00",
          :stars_month 50,
          :topics
          "vim laravel lumen neovim plugin php-framework php-development",
          :hidden false,
          :id 8,
          :stars 159,
          :url "https://github.com/noahfrederick/vim-laravel",
          :repo "noahfrederick/vim-laravel",
          :owner "noahfrederick",
          :category_id nil}
         {:description "Optimized search and edit workflow",
          :archived false,
          :dirty false,
          :updated "2020-08-30T21:45:51.000000000-00:00",
          :name "vim-project-search",
          :stars_week 5,
          :created "2016-06-21T21:29:36.000000000-00:00",
          :stars_month 3,
          :topics "vim neovim plugin",
          :hidden false,
          :id 12,
          :stars 8,
          :url "https://github.com/still-dreaming-1/vim-project-search",
          :repo "still-dreaming-1/vim-project-search",
          :owner "still-dreaming-1",
          :category_id nil}
         {:description "The utility for running vim on neovim",
          :archived false,
          :dirty false,
          :updated "2017-01-08T09:19:56.000000000-00:00",
          :name "nvim-vim-runner",
          :stars_week 0,
          :created "2016-12-22T19:42:14.000000000-00:00",
          :stars_month 0,
          :topics "neovim vim plugin",
          :hidden false,
          :id 13,
          :stars 0,
          :url "https://github.com/aiya000/nvim-vim-runner",
          :repo "aiya000/nvim-vim-runner",
          :owner "aiya000",
          :category_id nil}]))

