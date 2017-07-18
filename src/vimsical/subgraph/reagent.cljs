(ns vimsical.subgraph.reagent
  (:require
   [vimsical.subgraph :as sg]
   [reagent.core :as reagent]))

;; * Db helpers

(defn- ratom?
  [db]
  (satisfies? IDeref db))

(defn- ref?
  [db expr]
  (sg/ref? (::sg/id-attrs @db) expr))

(defn- get-ref
  [{:keys [db]} pattern lookup-ref]
  (deref (reagent/make-reaction #(get @db lookup-ref))))

(defn- skip?
  [expr]
  (-> expr meta ::sg/skip boolean))

;; * Parser

(defn- parser
  [{:keys [db db-ref?] :as context} result expr]
  {:pre [(ratom? db)]}
  (letfn [(parse [] (sg/parse-expr context result expr))
          (join? [expr] (= ::sg/join (sg/expr-type expr)))]
    (if (and (not (skip? expr))
             (or (db-ref? db expr)
                 (join? expr)))
      (deref (reagent/make-reaction parse))
      (parse))))

;; * API

(defn pull
  [db pattern lookup-ref]
  {:pre [(ratom? db)]}
  (reagent/make-reaction
   #(sg/pull db pattern lookup-ref
             {:parser     parser
              :db-ref?    ref?
              :db-get-ref get-ref})))
