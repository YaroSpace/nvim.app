(ns nvim-app.db.user
  (:require
   [nvim-app.db.core :as db]
   [nvim-app.specs :as specs]))

(defn watched? [user-id repo]
  (boolean (db/select-one :users
                          :where [:and
                                  [:= :id user-id]
                                  [:= repo [:any :watched]]])))

(defn add-to-watched! [user-id repo]
  (db/query-one! {:update :users
                  :set {:watched [:array_append :watched repo]}
                  :where [:and
                          [:= :id user-id]
                          [:is [:= repo [:any :watched]] false]]}))

(defn remove-from-watched! [user-id repo]
  (db/query-one! {:update :users
                  :set {:watched [:array_remove :watched repo]}
                  :where [:and
                          [:= :id user-id]
                          [:= repo [:any :watched]]]}))

(defn toggle-watched! [user-id repo]
  (when (and (specs/conform! :user/id user-id)
             (specs/conform! ::specs/repo repo))
    (if (watched? user-id repo)
      (remove-from-watched! user-id repo)
      (add-to-watched! user-id repo))))

(defn get-or-create! [user]
  (let [user* (specs/conform! ::specs/user user)]
    (or (db/select-one :users :where [:= :github_id (:github_id user*)])
        (db/insert! :users [user*]))))
