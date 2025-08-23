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

(defn log-artifacts [user-imports]
  (println "\nERROR: Found artifacts in:")
  (doseq [{:keys [file matches]} user-imports]
    (println " -" file)
    (doseq [match matches]
      (println "   " (str/trim match)))))

(defn check-for-artifacts []
  (println "Checking for 'user' namespace references...")
  (println "Checking for #p")

  (let [src-dir "src"
        found (for [file (file-seq (io/file src-dir))
                    :when (.isFile file)
                    :let [content (slurp file)
                          matches (concat
                                   (re-seq #".*\[user :as.*" content)
                                   (re-seq #".*#p .*" content))]
                    :when (seq matches)]
                {:file (.getPath file)
                 :matches matches})]

    (if (seq found)
      (log-artifacts found)
      true)))

(defn uber [_]
  (clean nil)
  (if-not (check-for-artifacts)
    (System/exit 1)
    (do
      (b/copy-dir {:src-dirs ["src" "resources"]
                   :target-dir class-dir})

      (b/compile-clj {:basis @basis
                      :ns-compile '[nvim-app.core]
                      :class-dir class-dir})

      (b/uber {:class-dir class-dir
               :uber-file uber-file
               :basis @basis
               :main 'nvim-app.core}))))

;; To build: clj -T:build uber
