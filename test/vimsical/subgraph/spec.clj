;; The MIT License (MIT)

;; Copyright (c) 2016 Stuart Sierra

;; Permission is hereby granted, free of charge, to any person
;; obtaining a copy of this software and associated documentation
;; files (the "Software"), to deal in the Software without
;; restriction, including without limitation the rights to use, copy,
;; modify, merge, publish, distribute, sublicense, and/or sell copies
;; of the Software, and to permit persons to whom the Software is
;; furnished to do so, subject to the following conditions:

;; The above copyright notice and this permission notice shall be
;; included in all copies or substantial portions of the Software.

;; THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
;; EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
;; MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
;; NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
;; BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
;; ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
;; CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
;; SOFTWARE.

(ns vimsical.subgraph.spec
  "clojure.spec.alpha definitions for mapgraph functions.
  Requires Clojure 1.9.0."
  (:require [clojure.spec.alpha :as s]
            [re-frame.interop :as interop]
            [vimsical.subgraph :as sg]))

(s/def ::map-db sg/db?)

(s/def ::reaction-db
  (fn [db]
    (and
     (interop/deref? db)
     (some-> db deref sg/db?))))

(defn get-conformed-db [{[k val] :db :as conformed}]
  (case k
    :map      val
    :reaction (some-> val deref)) )

(s/def ::sg/db (s/or :map ::map-db :reaction ::reaction-db))

(s/def ::sg/entity (s/map-of keyword? any?))

(s/def ::sg/reference
  (s/and vector? (s/tuple keyword? any?)))

(s/def ::sg/lookup-ref ::sg/reference)

(s/def ::sg/link (s/tuple keyword? #{'_}))

(s/def ::sg/join-pattern
  (s/or :pattern ::sg/pattern
        :rec-unbounded #{'...}
        :rec-limit number?))

(s/def ::sg/pattern
  (s/* (s/or :attr keyword?
             :star #{'*}
             :join (s/map-of keyword? ::sg/join-pattern)
             :link (s/map-of ::sg/link ::sg/pattern))))

(s/def ::sg/result (s/nilable map?))

(s/def ::sg/parser-context
  (s/keys :req-un [::sg/parser ::sg/db ::sg/lookup-ref]
          :opt-un [::sg/pattern ::sg/entity]))

(s/fdef sg/add-id-attr
  :args (s/cat :db ::sg/db
               :idents (s/* keyword?))
  :ret ::sg/db)

(s/fdef sg/add
  :args (s/& (s/cat :db ::sg/db
                    :entities (s/* ::sg/entity))
             (fn [{:keys [entities] :as conformed}]
               (let [db (get-conformed-db conformed)]
                 (every? #(sg/entity? db %) entities))))
  :ret ::sg/db
  :fn (fn [{:keys [ret args]}]
        (let [{:keys [entities] :as conformed} args]
          (let [db (get-conformed-db conformed)]
            (every? #(contains? ret (sg/ref-to db %)) entities)))))

(s/fdef sg/pull
  :args (s/or
         :link (s/cat :db ::sg/db
                      :pattern (s/spec ::sg/pattern))
         :default (s/&
                   (s/cat :db ::sg/db
                          :pattern (s/spec ::sg/pattern)
                          :ref ::sg/reference)
                   (fn [{:keys [ref] :as conformed}]
                     (sg/ref? (get-conformed-db conformed) ref)))
         :pull (s/cat :db ::sg/db
                      :pattern (s/spec ::sg/pattern)
                      :ref (s/nilable ::sg/reference)
                      :context (s/nilable ::sg/parser-context)))
  :ret (s/nilable map?))

;; Local Variables:
;; eval: (put-clojure-indent 'fdef :defn)
;; End:
