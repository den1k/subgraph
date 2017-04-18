(ns com.stuartsierra.subgraph-test
  (:require [clojure.spec :as s]
            [clojure.string :as str]
            [clojure.spec.gen :as gen]
            [clojure.test :refer [deftest is use-fixtures]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [com.stuartsierra.mapgraph :as mg]
            [com.stuartsierra.subgraph :as sg]
            [com.stuartsierra.mapgraph.spec]
            [com.stuartsierra.mapgraph.examples :as examples]
            [re-frame.core :as re-frame]
            [re-frame.interop :as interop]))

(defn db-fixture
  [f]
  (re-frame/reg-event-db ::initialize-db (fn  [_ _] examples/friends))
  (re-frame/dispatch-sync [::initialize-db])
  (f))

(use-fixtures :each db-fixture)

(deftest t-id-attrs-subscription
  (is (= {::mg/id-attrs #{:user/id}} (deref (re-frame/subscribe [::sg/id-attrs])))))

(deftest t-pull-sub
  (is (interop/deref? (re-frame/subscribe [::sg/pull '[:user/name] [:user/id 1]]))))

(deftest t-sub-friend-names-depth-3
  (is (= (deref
          (re-frame/subscribe
           [::sg/pull
            '[:user/name
              {:user/friends [:user/name
                              {:user/friends [:user/name]}]}]
            [:user/id 1]]) )
         {:user/name "Alice"
          :user/friends
          #{{:user/name "Claire"
             :user/friends #{{:user/name "Alice"}}}
            {:user/name "Bob"
             :user/friends #{{:user/name "Emily"}
                             {:user/name "Frank"}}}}})))
;; TODO Updates

(re-frame/reg-event-db
 ::uppercase-user-name
 (fn [db [_ user-ref]]
   (update-in db user-ref :user/name str/upper-case)))
