(ns nvim-app.awesome
  (:require [nvim-app.utils :as utils]
            [clojure.string :as str]
            [clojure.tools.logging :as log]))

(def url "https://raw.githubusercontent.com/rockerBOO/awesome-neovim/refs/heads/main/README.md")
(def repo-re #"^- \[(.*?)\]\((.*?)\) - (.*)")

(defn parse-readme [content]
  (let [lines (str/split-lines content)
        result
        (->> lines
             (reduce (fn [{:keys [category result]} line]
                       (cond
                         (and (str/starts-with? line "## ")
                              (not (re-matches #"## Contents" line)))
                         {:category (str/trim (subs line 3)) :result result}

                         (re-matches repo-re line)
                         (let [[_ repo-name repo-url description] (re-matches repo-re line)]
                           {:category category
                            :result (conj result {:category category
                                                  :repo repo-name
                                                  :url repo-url
                                                  :description description})})
                         :else
                         {:category category :result result}))

                     {:category nil :result []})
             :result)]

    (log/info "Downloaded" (count result) "plugins from awesome-neovim README.")
    result))

(defn fetch-readme []
  (let [resp (utils/fetch-request {:method :get :url url})]
    (log/info "Downloading awesome-neovim README...")

    (if (= 200 (:status resp))
      (:body resp)
      (log/error "Failed to download awesome-neovim README." resp))))

(defn get-plugins []
  (parse-readme (fetch-readme)))

(comment
  (re-matches #"https://[^/].+/(.+/.+)$" "https://git.sr.ht/~whynothugo/lsp_lines.nvim")
  (last (str/split "https://github.com/zaldih/themery.nvim" #"/"))
  (parse-readme (fetch-readme))

  (def repo-re #"^- \[(.*?)\]\((.*?)\) - (.*)")
  (re-matches repo-re "https://github.com/you-n-g/simplegpt.nvim"))
