(ns user.ns-docs
  (:require [clojure.repl :refer :all]))

(defn ns-docs
  "Print the docstring of a namespace, and the docstrings of all public vars it
  contains."
  [ns-symbol]
  (when ns-symbol
    (println ns-symbol)
    (println "-------------------------")
    (println " " (:doc (meta (find-ns ns-symbol))))
    (run! (comp #'clojure.repl/print-doc meta)
          (->> ns-symbol
               ns-publics
               sort
               vals))))

; Example usage:
(comment
  (require '[clojure.string])
  (ns-docs 'clojure.string)
  (clojure.java.javadoc/javadoc java.lang.String))

