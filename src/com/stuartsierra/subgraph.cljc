(ns com.stuartsierra.subgraph
  (:require
   [com.stuartsierra.mapgraph :as mg]
   [re-frame.core :as re-frame]
   [re-frame.interop :as interop]))

;; * Debug

(defn now
  []
  #?(:cljs (.toFixed (.now js/performance) 3)
     :clj (java.util.Date.)))

(defn debug-computed-at
  "Assoc ::computed-at in the entity's metadata. Meant to be used inside a
  reaction in order for downstream consumers to check how often the reaction is
  recomputed."
  [entity]
  (cond
    (map? entity)        (with-meta entity {::computed-at (now)})
    (sequential? entity) (into (empty entity) (map debug-computed-at) entity)
    :else entity))


;; * Derefable db helpers

(defn- <sub
  "Subscribe to and deref event"
  [event]
  (deref (re-frame/subscribe event)))

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
      (deref
       (interop/make-reaction
        (comp debug-computed-at parse)))
      (parse))))


;; * Subscriptions

(re-frame/reg-sub
 ::id-attrs
 (fn sub-id-attrs
   [db [_]]
   {:pre [(not (interop/deref? db))]}
   (select-keys db [::mg/id-attrs])))

(defn pull
  [db [_ pattern lookup-ref]]
  {:pre [(interop/deref? db)]}
  (interop/make-reaction
   #(mg/pull
     db pattern lookup-ref
     {:parser     parse-expr
      :db-ref?    ref?
      :db-get-ref get-ref})))

(re-frame/reg-sub-raw ::pull pull)
