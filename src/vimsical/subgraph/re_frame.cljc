(ns vimsical.subgraph.re-frame
  (:require
   [vimsical.subgraph :as sg]
   [re-frame.core :as re-frame]
   [re-frame.interop :as interop]))

;; * Db helpers

(defn- ref?
  [_ expr]
  (sg/ref? (deref (re-frame/subscribe [::sg/id-attrs])) expr))

(defn- get-ref
  [{:keys [db]} pattern lookup-ref]
  {:pre [(interop/deref? db)]}
  (deref (interop/make-reaction #(get @db lookup-ref))))

(defn- skip?
  [expr]
  (-> expr meta ::sg/skip boolean))

;; * Subscription db parser

(defn- parser
  [{:keys [parser db db-ref? pattern] :as context} result expr]
  {:pre [(interop/deref? db)]}
  (letfn [(parse [] (sg/parse-expr context result expr))
          (join? [expr] (= ::sg/join (sg/expr-type expr)))]
    (if (and (not (skip? expr))
             (or (db-ref? db expr)
                 (join? expr)))
      (deref (interop/make-reaction parse))
      (parse))))

;; * Internal subscription

(re-frame/reg-sub
 ::sg/id-attrs
 (fn [db _]
   (select-keys db [::sg/id-attrs])))

;; * API

(defn pull
  [db pattern lookup-ref]
  {:pre [(interop/deref? db)]}
  (interop/make-reaction
   #(sg/pull db pattern lookup-ref
             {:parser     parser
              :db-ref?    ref?
              :db-get-ref get-ref})))

(defn raw-sub-handler
  [db [_ pattern lookup-ref]]
  (pull db pattern lookup-ref))
