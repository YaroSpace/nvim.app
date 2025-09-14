(ns nvim-app.components.pedestal.specs
  (:require
   [clojure.spec.alpha :as s]))

(s/def ::q string?)
(s/def ::category string?)
(s/def ::sort (s/and string? #{"stars" "stars_week" "stars_month" "updated" "created"}))
(s/def ::page (s/and string? ::->int))
(s/def ::limit (s/and string? ::->int))

(s/def ::repos-page-params
  (s/nilable
   (s/keys :opt-un [::q ::category ::sort ::page ::limit])))

(s/def ::->int
  (s/conformer
   (fn [s] (or (parse-long s) ::s/invalid))))

(s/explain-data ::repos-page-params {:page "s"})

(defn format-problems [spec x]
  (let [problem (->> (s/explain-data spec x) ::s/problems last)]
    (format "value: '%s' in %s is invalid (%s)"
            (:val problem) (:in problem) (name (peek (:via problem))))))
