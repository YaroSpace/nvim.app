(ns nvim-app.unit.persistance.awesome-test
  (:require
   [nvim-app.db.core :as db]
   [clojure.test :refer :all]
   [nvim-app.helpers :as h]))

(def test-categories
  [{:name "Editing"}
   {:name "Navigation"}
   {:name "Programming"}
   {:name "UI"}
   {:name "Tools"}])

(def test-plugins
  [{:category "Editing" :repo "foo/bar" :url "https://github.com/foo/bar" :description "A plugin"}
   {:category "Navigation" :repo "baz/qux" :url "https://github.com/baz/qux" :description "Another plugin"}
   {:category "Programming" :repo "quux/corge" :url "https://github.com/quux/corge" :description "Yet another plugin"}
   {:category "UI" :repo "grault/garply" :url "https://github.com/grault/garply" :description "A different plugin"}
   {:category "Tools" :repo "waldo/fred" :url "https://github.com/waldo/fred" :description "A cool plugin"}])

(defn insert-test-data []
  (doseq [{:keys [name]} test-categories]
    (db/query-one! {:insert-into :categories
                    :columns [:name]
                    :values [[name]]}))

  (doseq [{:keys [category repo url description]} test-plugins]
    (let [category-id (db/query-one! {:select :id
                                      :from :categories
                                      :where [:= :name category]})]
      (db/query-one! {:insert-into :plugins
                      :columns [:category_id :repo :url :description]
                      :values [[(:id category-id) repo url description]]}))))
