(ns com.stuartsierra.subgraph
  (:require
   [com.stuartsierra.mapgraph :as mg]
   [re-frame.core :as re-frame]
   [re-frame.interop :as interop]))

(defn- <sub
  "Subscribe to and deref event"
  [event]
  (deref (re-frame/subscribe event)))

;; * Subscriptions

(re-frame/reg-sub
 ::id-attrs
 (fn sub-id-attrs
   [db [_]]
   {:pre [(not (interop/deref? db))]}
   (select-keys db [::mg/id-attrs])))

(re-frame/reg-sub-raw
 ::pull
 (fn pull-sub
   [db [_ pattern lookup-ref]]
   {:pre [(interop/deref? db)]}
   (interop/make-reaction
    #(mg/pull
      db pattern lookup-ref
      {:parser     parse-expr
       :db-ref?    ref?
       :db-get-ref get-ref}))))


;; * Derefable db helpers

(defn ref?
  [_ expr]
  (mg/ref? (<sub [::id-attrs]) expr))

(defn skip?
  [expr]
  (-> expr meta ::skip boolean))

(defn get-ref
  [{:keys [db] :as context} pattern lookup-ref]
  {:pre [(interop/deref? db)]}
  (deref (interop/make-reaction #(get @db lookup-ref))))


;; * Subscription db parser

(defn parse-expr
  [{:keys [parser db db-ref? pattern] :as context} result expr]
  {:pre [(interop/deref? db)]}
  (letfn [(parse [] (mg/parse-expr context result expr))
          (join? [expr] (= ::mg/join (mg/expr-type expr)))]
    (if (and (not (skip? expr))
             (or (db-ref? db expr)
                 (join? expr)))
      (deref (interop/make-reaction parse))
      (parse))))

