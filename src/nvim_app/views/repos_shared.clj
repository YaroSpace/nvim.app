(ns nvim-app.views.repos-shared
  (:require [nvim-app.views.assets :refer :all]
            [clojure.string :as str]))

(defn topic-color [topic]
  (let [colors [["bg-yellow-100" "lsp" "telescope"]
                ["bg-orange-100" "ai" "llm" "lua"]
                ["bg-red-100" "colorscheme" "theme"]
                ["bg-cyan-100" "markdown" "treesitter"]
                ["bg-indigo-100" "config" "dotfiles"]
                ["bg-lime-100" "python" "rust"]
                ["bg-blue-100" "neovim" "nvim"]
                ["bg-purple-100" "vim" "plugin" "terminal"]]]

    (or (some (fn [[color & topics]]
                (when (some #(str/includes? topic %) topics)
                  (format " %s " color)))
              colors)
        " bg-green-100 ")))

(def hx-include "#search-input, #limit-input, #category, #sort, #group, #view, #mode")

(defn date->str [date]
  (first (str/split (str date) #" ")))

(defn number->str [n]
  (str/replace (format "%,d" n) "," " "))

(defn truncate-text [text max-length]
  (if (> (count text) max-length)
    (str (subs text 0 max-length) "...")
    text))

(defn el-with [el opts & body]
  (vec (concat (update el 1 #(merge-with (fn [a b] (str/join " " [a b])) % opts))
               (when body body))))

(defn view-toggle [view]
  [:button {:title (if (= "full" view) "Compact view" "Full view")
            :name "view" :value (if (= "full" view) "compact" "full")
            :class "p-2 text-green-700 hover:text-green-900 hover:bg-green-50 rounded-lg transition-colors 
                        focus:outline-none focus:ring-2 focus:ring-green-500
                        disabled:opacity-50 disabled:cursor-not-allowed"
            :hx-get "/repos-page" :hx-target "#plugins-list" :hx-include hx-include :hx-params "not #view"}
   [:input {:id "view" :class "hidden" :name "view" :value (if (= "full" view) "full" "compact")}]
   (if (= "full" view) (compact-view-icon) (full-view-icon))])

(defn mode-toggle [mode]
  [:button {:title (if (= "light" mode) "Dark mode" "Light mode")
            :name "mode" :value (if (= "light" mode) "dark" "light")
            :class "p-2 text-green-700 hover:text-green-900 hover:bg-green-50 rounded-lg transition-colors 
                        focus:outline-none focus:ring-2 focus:ring-green-500
                        disabled:opacity-50 disabled:cursor-not-allowed"
            :hx-get "/repos-page" :hx-target "#plugins-list" :hx-include hx-include :hx-params "not #mode"}
   [:input {:id "mode" :class "hidden" :name "mode" :value (if (= "light" mode) "light" "dark")}]
   (if (= "light" mode) (light-mode-icon) (dark-mode-icon))])

(defn watch-button [repo-id repo watched]
  [:button {:title (if watched "Remove from watchlist" "Add to watchlist")
            :name "repo" :value repo
            :class (str "flex items-center pl-2 space-x-1 cursor-pointer "
                        (if watched "text-blue-700" "text-green-700"))
            :hx-put "/user/watch" :hx-target repo-id :hx-include hx-include
            :hx-select repo-id :hx-swap "outerHTML"}
   (watch-icon)])

(defn save-button [repo-id repo]
  [:button {:title "Save" :name "repo" :value repo
            :class "flex items-center pl-2 space-x-1 cursor-pointer text-blue-700"
            :hx-put "/repo" :hx-include (str "#category-edit, " hx-include)
            :hx-select repo-id :hx-target repo-id :hx-swap "outerHTML"}
   (save-icon)])

(defn edit-button [repo-id repo]
  [:button {:title "Edit" :name "edit" :value repo
            :class "flex items-center pl-2 space-x-1 cursor-pointer text-green-700"
            :hx-get "/repos-page" :hx-include  hx-include
            :hx-select repo-id :hx-target repo-id :hx-swap "outerHTML"}
   (edit-icon)])

(defn star [number icon color title]
  (when (pos? number)
    [:div {:title title :class (str "flex items-center " color)}
     (icon)
     [:span {:class "text-green-900"} (number->str number)]]))
