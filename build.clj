(ns build
  (:require
   [clojure.tools.build.api :as b]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def lib 'nvim.app)
(def version (format "1.0.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def uber-file (format "target/nvim-app-%s-%s-standalone.jar" (name lib) version))

;; delay to defer side effects (artifact downloads)
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path "target"}))

(defn log-user-imports [user-imports]
  (println "\nERROR: Found 'user' namespace references in:")
  (doseq [{:keys [file matches]} user-imports]
    (println " -" file)
    (doseq [match matches]
      (println "   " (str/trim match)))))

(defn check-for-user-ns []
  (println "Checking for 'user' namespace references...")
  (let [src-dir "src"
        user-imports (for [file (file-seq (io/file src-dir))
                           :when (.isFile file)
                           :let [content (slurp file)
                                 ns-matches (re-seq #".*\[user.*" content)]
                           :when (seq ns-matches)]
                       {:file (.getPath file)
                        :matches ns-matches})]

    (if (seq user-imports)
      (log-user-imports user-imports)
      true)))

(defn uber [_]
  (clean nil)
  (when (check-for-user-ns)
    (b/copy-dir {:src-dirs ["src" "resources"]
                 :target-dir class-dir})
    (b/compile-clj {:basis @basis
                    :ns-compile '[nvim-app.core]
                    :class-dir class-dir})
    (b/uber {:class-dir class-dir
             :uber-file uber-file
             :basis @basis
             :main 'nvim-app.core})))

;; To build: clj -T:build uber
