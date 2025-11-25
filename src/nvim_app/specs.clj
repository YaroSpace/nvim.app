(ns nvim-app.specs
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.instant :as inst]))

(s/def ::->int
  (s/conformer
   (fn [s] (or (parse-long s) ::s/invalid))))

(s/def ::nil->string
  (s/conformer (fn [s] (str (or s "")))))

(s/def ::ne-string?
  (s/and string? (complement str/blank?)))

(defn format-problems [spec x]
  (let [problem (->> (s/explain-data spec x) ::s/problems last)]
    (format "value: '%s' in %s is invalid (%s)"
            (:val problem) (:in problem) (name (peek (:via problem))))))

(defn conform!
  "Like s/conform but throws if invalid"
  [spec x]
  (let [conformed (s/conform spec x)]
    (if (s/invalid? conformed)
      (throw (ex-info (format "Spec %s invalid: %s"
                              (pr-str spec)
                              (format-problems spec x))
                      {:spec spec :value x}))
      conformed)))

(s/def :user/id integer?)
(s/def :user/github_id integer?)
(s/def :user/username ::ne-string?)
(s/def :user/url ::ne-string?)
(s/def :user/email ::nil->string)
(s/def :user/name ::nil->string)
(s/def :user/avatar_url ::nil->string)
(s/def :user/role ::ne-string?)

(def user-defaults
  {:email "" :name "" :avatar_url ""})

(s/def ::user
  (s/and
   (s/conformer
    (fn [user] (merge user-defaults user)))

   (s/keys :req-un [:user/github_id :user/username :user/url]
           :opt-un [:user/email :user/name :user/avatar_url :user/role])))

(s/def :repo-page/q ::nil->string)
(s/def :repo-page/group ::nil->string)
(s/def :repo-page/sort (s/and ::nil->string #{"" "name" "stars" "stars_week" "stars_month" "updated" "created"}))
(s/def :repo-page/category ::nil->string)
(s/def :repo-page/page (s/and ::nil->string ::->int pos?))
(s/def :repo-page/limit (s/and ::nil->string ::->int pos?))

(s/def ::repos-page-params
  (s/nilable
   (s/keys :opt-un [:repo-page/q :repo-page/group :repo-page/category :repo-page/sort
                    :repo-page/page :repo-page/limit])))

(s/def :repo/id ::->int)
(s/def :repo/name ::ne-string?)
(s/def :repo/owner ::ne-string?)
(s/def :repo/repo ::ne-string?)
(s/def :repo/url ::ne-string?)
(s/def :repo/description ::nil->string)
(s/def :repo/category_id (s/nilable integer?))
(s/def :repo/stars int?)
(s/def :repo/stars_week int?)
(s/def :repo/stars_month int?)
(s/def :repo/topics (s/and
                     (s/coll-of string?)
                     (s/conformer (fn [s] (str/join " " s)))))
(s/def :repo/updated inst?)
(s/def :repo/created inst?)
(s/def :repo/archived boolean?)
(s/def :repo/hidden boolean?)
(s/def :repo/dirty boolean?)

(def default-timestamp
  (inst/read-instant-timestamp "1970-01-01"))

(def repo-defaults
  {:description ""
   :topics [""]
   :stars 0
   :updated default-timestamp
   :created default-timestamp
   :archived false
   :hidden false
   :dirty false})

(s/def ::repo
  (s/and
   (s/conformer
    (fn [repo] (as-> repo repo
                 (reduce-kv (fn [acc k v] (if (nil? v) (dissoc acc k) acc)) repo repo)
                 (merge repo-defaults repo)
                 (assoc repo :repo (str (:owner repo) "/" (:name repo))))))

   (s/keys :req-un [:repo/name :repo/owner :repo/repo :repo/url]
           :opt-un [:repo/description :repo/category_id :repo/topics
                    :repo/stars :repo/stars_week :repo/stars_month
                    :repo/updated :repo/created :repo/archived :repo/hidden :repo/dirty])))

(s/def :category/id integer?)
(s/def :category/name ::ne-string?)
