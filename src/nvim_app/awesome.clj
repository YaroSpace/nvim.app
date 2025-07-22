(ns nvim-app.awesome
  (:require [nvim-app.db.plugin :as plugin]
            [nvim-app.utils :as utils]
            [clojure.string :as str]
            [clojure.tools.logging :as log]))

(def url "https://raw.githubusercontent.com/rockerBOO/awesome-neovim/refs/heads/main/README.md")
(def repo-re #"^- \[(.*?)\]\((.*?)\) - (.*)")

(defn parse-readme [content]
  (let [lines (str/split-lines content)]
    (loop [lines lines
           category nil
           result []]
      (if (empty? lines)
        (do (log/info "Downloaded " (count result) " plugins from awesome-neovim README.")
            result)
        (let [line (first lines)
              next-lines (rest lines)]
          (cond
            (and (str/starts-with? line "## ")
                 (not (re-matches #"## Contents" line)))
            (recur next-lines (str/trim (subs line 3)) result)

            (re-matches repo-re line)
            (let [[_ repo-name repo-url description] (re-matches repo-re line)]
              (recur next-lines category
                     (conj result {:category category
                                   :repo repo-name
                                   :url repo-url
                                   :description description})))
            :else
            (recur next-lines category result)))))))

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
  (plugin/upsert-plugins! (parse-readme (fetch-readme)))

  (def repo-re #"^- \[(.*?)\]\((.*?)\) - (.*)")
  (re-matches repo-re "https://github.com/you-n-g/simplegpt.nvim"))
