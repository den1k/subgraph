(ns vimsical.subgraph.readme
  "Examples used in documentation"
  (:require
   [re-frame.core :as re-frame]
   [vimsical.subgraph :as sg]
   [vimsical.subgraph.re-frame :as sg.re-frame]
   [vimsical.subgraph.examples :as examples]))


(defn new-db
  []
  (-> (sg/new-db)
      (sg/add-id-attr :user/id :color/hex)))

(re-frame/reg-event-db ::new-db (constantly (new-db)))

(re-frame/dispatch [::new-db])

(re-frame/reg-sub-raw ::q sg.re-frame/raw-sub-handler)

(deref
 (re-frame/subscribe
  [::q
   ;; Pattern
   [:user/name {:user/favorite-color [:color/name]}]
   ;; Lookup ref
   [:user/id 2]]))

(re-frame/reg-event-db
 ::add
 (fn [db [_ & entities]]
   (apply sg/add db entities)))


(re-frame/dispatch
 [::add
  {:user/id             1
   :user/name           "Pat"
   :user/favorite-color {:color/hex  "9C27B0"
                         :color/name "Purple"}}
  {:user/id             2
   :user/name           "Reese"
   :user/favorite-color {:color/hex  "D50000"
                         :color/name "Red"}}])

(deref
 (re-frame/subscribe
  [::q
   ;; Pattern
   [:user/name {:user/favorite-color [:color/name]}]
   ;; Lookup ref
   [:user/id 2]]))


(re-frame/dispatch
 [::add
  {:user/id 1 :user/friends #{{:user/id 2}}}
  {:user/id 2 :user/friends #{{:user/id 1}}}])

(deref
 (re-frame/subscribe
  [::q
   [:user/name
    {:user/friends
     [:user/name :user/favorite-color]}]
   [:user/id 1]]))

(deref
 (re-frame/subscribe
  [::q
   [:user/name
    {:user/friends
     [:user/name {:user/favorite-color [:color/name]}]}]
   [:user/id 1]]))

(re-frame/dispatch
 [::add
  {:user/id             2
   :user/favorite-color {:color/hex  "1789d6"
                         :color/name "DodgerBlue3"}}])

(deref
 (re-frame/subscribe
  [::q
   [:user/name
    {:user/friends
     [:user/name {:user/favorite-color [:color/name]}]}]
   [:user/id 1]]))
