(ns persistance.nvim-app.migrations-test
  (:require
   [next.jdbc :as jdbc]
   [honey.sql :as sql]

   [clojure.test :refer :all]
   [helpers :as h]))

(deftest migrations-test
  (testing "Testing database migrations"
    #_{:clj-kondo/ignore [:unresolved-symbol]}
    (h/with-database-system sut
      (let [{:keys [database-component]} sut
            [schema-version :as schema-versions]
            (jdbc/execute! database-component (sql/format {:select :* :from :schema_version}))]

        (is (= {:description "add todo tables"
                :script "V1__add_todo_tables.sql"
                :success true}
               (select-keys schema-version [:description :script :success]))))))

  #_(testing "Testing insert"
      #_{:clj-kondo/ignore [:unresolved-symbol]}
      (h/with-database-system sut
        (let [{:keys [database-component]} sut
              insert-results (jdbc/execute! database-component
                                            (sql/format
                                             {:insert-into :todo
                                              :columns [:title]
                                              :values [["Test Todo"]
                                                       ["Another Todo"]]
                                              :returning [:*]}))
              select-results (jdbc/execute! database-component (sql/format {:select :* :from :todo}))]

          (is (= 2 (count insert-results)
                 (count select-results)))

          (is (= #{"Test Todo" "Another Todo"}
                 (set (map :title insert-results))
                 (set (map :title select-results))))))))
