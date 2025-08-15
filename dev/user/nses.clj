(ns user.nses
  (:require [clojure.java.io              :as io]
            [clojure.tools.deps           :as tdep]
            [clojure.tools.namespace.find :as tnsf]))

(defn dep-nses
  "Returns all namespaces (as a sequence of symbols) in the given dep,
  identified by `lib-sym` and `coords`, as used in lib-map format - see
  https://clojure.github.io/tools.deps/#:clojure.tools.deps.specs/lib-map

  Notes:

  * If not provided, `coords` defaults to `{:mvn/version \"RELEASE\"}`
  * Has the side effect of downloading the dep and all of its transitive
    dependencies, but does NOT add them to the REPL's classpath - use
    `clojure.repl.deps/add-lib` for that"
  ([lib-sym] (dep-nses lib-sym nil))
  ([lib-sym coords]
   (when lib-sym
     (let [coords           (or coords {:mvn/version "RELEASE"})
           deps-map         {:mvn/local-repo (str (System/getenv "HOME") "/.m2/repository")
                             :mvn/repos      {"central" {:url "https://repo1.maven.org/maven2/"}
                                              "clojars" {:url "https://repo.clojars.org/"}}
                             :deps           {lib-sym coords}}
           lib-map          (tdep/resolve-deps deps-map nil)
           relevant-lib-map (get lib-map lib-sym)
           dep-artifacts    (->> (conj (:paths relevant-lib-map) (:deps/root relevant-lib-map))
                                 (filter identity)
                                 (map io/file)
                                 seq)]
       (when dep-artifacts
         (->> (tnsf/find-namespaces dep-artifacts)
              (map str)
              sort
              (map symbol)))))))

; Example usages
(comment
  (dep-nses 'http-kit/http-kit)
  (dep-nses 'http-kit/http-kit {:mvn/version "2.3.0"})  ; Fewer namespaces in v2.3.0

  ; Use it in conjunction with the handy 'find-deps' library (https://github.com/hagmonk/find-deps)
  (require '[find-deps.core :as fd])
  (let [http-kit-deps   (fd/deps "http-kit")  ; Note: this can be quite slow (over a minute) - it also seems to timeout from time to time
        [lib-sym coord] (first (:deps http-kit-deps))]
    (dep-nses lib-sym coord)))

