(ns vimsical.subgraph.re-frame-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is use-fixtures]]
            [vimsical.subgraph :as sg]
            [vimsical.subgraph.re-frame :as sg.re-frame]
            [re-frame.core :as re-frame]
            [re-frame.interop :as interop]
            [vimsical.subgraph.examples :as examples]
            [vimsical.subgraph.spec]))

(defn re-frame-db-fixture
  [f]
  (re-frame/reg-event-db ::init-db (fn [_ _] examples/friends))
  (re-frame/dispatch-sync [::init-db])
  (re-frame/reg-sub-raw ::pull sg.re-frame/raw-sub-handler)
  (f))

(use-fixtures :each re-frame-db-fixture)


(deftest t-id-attrs-subscription
  (is (= {::sg/id-attrs #{:user/id}} (deref (re-frame/subscribe [::sg/id-attrs])))))

(deftest t-pull-sub
  (is (interop/deref? (re-frame/subscribe [::pull '[:user/name] [:user/id 1]]))))

(deftest t-sub-friend-names-depth-3
  (is (= (deref
          (re-frame/subscribe
           [::pull
            [:user/name
             {:user/friends [:user/name
                             {:user/friends [:user/name]}]}]
            [:user/id 1]]))
         {:user/name "Alice"
          :user/friends
          #{{:user/name    "Claire"
             :user/friends #{{:user/name "Alice"}}}
            {:user/name    "Bob"
             :user/friends #{{:user/name "Emily"}
                             {:user/name "Frank"}}}}})))
(deftest t-pull-query-link
  (is (= (deref (re-frame/subscribe
                 [::pull
                  [{[:link/user '_]
                    [:user/id :user/name {:foo '[*], :bar '[*]}]}]]))
         {:link/user
          {:user/id   3
           :user/name "Claire"}})))

;; TODO Updates

(re-frame/reg-event-db
 ::uppercase-user-name
 (fn [db [_ user-ref]]
   (update-in db user-ref :user/name str/upper-case)))
