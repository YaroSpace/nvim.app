(ns spy.reader
  (:require [clojure.pprint :as pp]
            [clojure.string :as str]))

(defn print-log
  "Reader function to pprint a form's value."
  [form]
  `(doto ~form
     clojure.pprint/pprint))

(defn print-log-detailed [form]
  (let [meta-form (meta form)
        file (some-> (:file meta-form) (str/split #"/") last)
        line (:line meta-form)]

    `(let [form# ~form]
       (when ~file (println (str "=> " ~file ":" ~line)))
       (when ~meta-form (pp/pprint ~meta-form))
       (pp/pprint form#)
       form#)))
