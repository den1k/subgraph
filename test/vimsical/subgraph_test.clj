(ns vimsical.subgraph-test
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.test.alpha :as st]
            [clojure.test :refer [deftest is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [com.stuartsierra.mapgraph :as mg]
            [vimsical.subgraph.examples :as examples]
            vimsical.subgraph.spec))

(st/instrument 'com.stuartsierra.mapgraph)

;;; Example tests

(deftest t-pull-friend-names-depth-3
  (is (= (mg/pull
          examples/friends
          '[:user/name
            {:user/friends [:user/name
                            {:user/friends [:user/name]}]}]
          [:user/id 1])
         {:user/name "Alice"
          :user/friends
          #{{:user/name "Claire"
             :user/friends #{{:user/name "Alice"}}}
            {:user/name "Bob"
             :user/friends #{{:user/name "Emily"}
                             {:user/name "Frank"}}}}})))

(deftest t-pull-entities-in-map
  (is (= (mg/pull examples/addresses
                  [:user/name
                   {:user/addresses [:address/street]}]
                  [:user/id 1])
         {:user/name "Alice"
          :user/addresses
          {"home" {:address/street "123 Home Lane"}
           "work" {:address/street "456 Corporate Street"}}})))

(deftest t-pull-map-with-complex-keys
  (is (= (mg/pull examples/hosts
                  [:host/ip
                   :host/rules
                   {:host/connections [:host/name]}]
                  [:host/ip "10.10.1.1"])
         {:host/ip "10.10.1.1"
          :host/rules
          {"input" {"block" "*", "allow" 80}
           "output" {"allow" 80}}
          :host/connections
          {"database" {:host/name "db"}
           ["cache" "level2"] {:host/name "cache"}}})))

(deftest t-pull-star
  (is (= (mg/pull examples/friends
                  '[*]
                  [:user/id 3])
         {:user/id 3
          :user/name "Claire"
          :user/friends #{[:user/id 1]}})))

(deftest t-pull-star-after-join
  ;; star in pull expression should not replace nested entities joined
  ;; elsewhere in the pull expression.
  (is (= (mg/pull examples/friends
                  '[{:user/friends [:user/name]}
                    *]
                  [:user/id 3])
         {:user/id 3
          :user/name "Claire"
          :user/friends #{{:user/name "Alice"}}})))

(deftest t-pull-broken-ref
  (is (= (mg/pull (dissoc examples/friends [:user/id 1])
                  '[{:user/friends [:user/name]}
                    *]
                  [:user/id 3])
         {:user/id 3
          :user/name "Claire"
          :user/friends #{}})))

(deftest t-do-not-pull-simple-keys-not-in-entity
  (is (= (mg/pull examples/friends
                  [:user/id :user/name :foo :bar]
                  [:user/id 3])
         {:user/id 3
          :user/name "Claire"})))

(deftest t-do-not-pull-ref-keys-not-in-entity
  (is (= (mg/pull examples/friends
                  [:user/id :user/name {:foo '[*], :bar '[*]}]
                  [:user/id 3])
         {:user/id 3
          :user/name "Claire"})))

(deftest t-pull-query-link
  (is (= (mg/pull examples/friends
                  [{[:link/user '_]
                    [:user/id :user/name {:foo '[*], :bar '[*]}]}])
         {:link/user
          {:user/id 3 :user/name "Claire"}}))
  (is (= (mg/pull examples/friends
                  [{[:link/users '_]
                    [:user/id :user/name {:foo '[*], :bar '[*]}]}])
         {:link/users
          [{:user/id 2 :user/name "Bob"}
           {:user/id 3 :user/name "Claire"}]})))

(deftest t-pull-rec-friend-names-depth-3
  (is (= (mg/pull
          examples/friends
          '[:user/name {:user/friends 2}]
          [:user/id 1])
         {:user/name "Alice"
          :user/friends
          #{{:user/name "Claire"
             :user/friends #{{:user/name "Alice"}}}
            {:user/name "Bob"
             :user/friends #{{:user/name "Emily"}
                             {:user/name "Frank"}}}}})))

(deftest t-pull-rec
  (is (= (mg/pull
          examples/friends-no-cycles
          [:user/id :user/name {:user/friends 100}]
          [:user/id 1])
         (mg/pull
          examples/friends-no-cycles
          [:user/id :user/name {:user/friends '...}]
          [:user/id 1]))))


;;; Generative tests

(defspec t-roundtrip-pull-star 10
  ;; An entity map with no nested entities, pulled with [*], should
  ;; equal the original map.
  (let [db (-> (mg/new-db)
               (mg/add-id-attr :thing/id))]
    (prop/for-all [e (s/gen ::mg/entity)]
      (let [entity (assoc e :thing/id 1)
            new-db (mg/add db entity)]
        (= entity
           (mg/pull new-db '[*] [:thing/id 1]))))))

(defspec t-merge-same-id 10
  ;; Entity maps with the same ID should be merged in order.
  (let [db (-> (mg/new-db)
               (mg/add-id-attr :thing/id))]
    (prop/for-all [es (gen/vector (s/gen ::mg/entity) 2 10)]
      (let [entities (map #(assoc % :thing/id 1) es)
            new-db (apply mg/add db entities)]
        (= (apply merge entities)
           (mg/pull new-db '[*] [:thing/id 1]))))))

;; entity specs used for the following tests
(s/def ::A (s/keys :req [::a-id] :opt [::b-ref]))
(s/def ::B (s/keys :req [::b-id] :opt [::a-ref]))
(s/def ::a-id #{"a1" "a2" "a3"})
(s/def ::b-id #{"b1" "b2" "b3"})
(s/def ::a-ref ::A)
(s/def ::b-ref ::B)

(defspec t-replace-lookup-refs 100
  ;; Nested entity maps are replaced with lookup refs.
  (let [db (-> (mg/new-db)
               (mg/add-id-attr ::a-id ::b-id))]
    (prop/for-all [as (gen/vector (s/gen ::A) 1 10)
                   a-id (s/gen ::a-id)]
      (let [new-db (apply mg/add db as)]
        (s/valid? (s/nilable ::mg/reference)
                  (::b-ref (get new-db [::a-id a-id])))))))

(defspec t-removed-entity-pull-is-nil 100
  ;; An entity dissoc'd from top level should return nil from a pull.
  (let [db (-> (mg/new-db)
               (mg/add-id-attr ::a-id ::b-id))]
    (prop/for-all [as (gen/vector (s/gen ::A) 1 10)
                   a-id (s/gen ::a-id)
                   to-remove (s/gen ::a-id)]
      (let [new-db (dissoc (apply mg/add db as) [::a-id to-remove])]
        (nil? (mg/pull new-db '[*] [::a-id to-remove]))))))

(defspec t-do-not-pull-removed-references 100
  ;; An entity dissoc'd from top level should not be returned from a
  ;; nested reference in a pull.
  (let [db (-> (mg/new-db)
               (mg/add-id-attr ::a-id ::b-id))]
    (prop/for-all [as (gen/vector (s/gen ::A) 1 10)
                   a-id (s/gen ::a-id)
                   to-remove (s/gen ::b-id)]
      (let [new-db (dissoc (apply mg/add db as) [::b-id to-remove])]
        (not= to-remove
              (get-in (mg/pull new-db
                               [{::b-ref [::b-id]}]
                               [::a-id a-id])
                      [::b-ref ::b-id]))))))

;; entity specs used for the following tests
(s/def ::C (s/keys :req [::c-id ::ds]))
(s/def ::D (s/keys :req [::d-id]))
(s/def ::c-id integer?)
(s/def ::d-id integer?)
(s/def ::ds (s/and (s/coll-of ::D :into [] :kind? vector?)
                   not-empty))

(def ^:private coll-pred
  "Mapping from a collection constructor (given another non-empty
  collection) to a predicate function that checks for the same type.
  Used to construct various types of collections and verify that they
  round-trip as the 'same' type, according to the predicate."
  {seq seq?
   vec vector?
   set set?
   #(apply sorted-set %&) #(and (set? %) (sorted? %))})

(defspec t-roundtrip-ref-coll 100
  ;; Preserve the type (seq, vector, set, sorted set) of collections
  ;; of lookup refs.
  (let [db (-> (mg/new-db)
               (mg/add-id-attr ::c-id ::d-id))]
    (prop/for-all [c (s/gen ::C)
                   [coll-fn pred] (gen/elements coll-pred)]
      (let [c (update c ::ds coll-fn)
            new-db (mg/add db c)]
        (pred (::ds (mg/pull new-db '[::ds] [::c-id (::c-id c)])))))))

;; entity specs used for the following tests
(s/def ::E (s/keys :req [::e-id ::fs]))
(s/def ::F (s/keys :req [::f-id]))
(s/def ::e-id integer?)
(s/def ::f-id integer?)
(s/def ::fs (s/and (s/map-of any? ::F)
                   not-empty))

(defspec t-roundtrip-map 10
  ;; Round trip a map of lookup refs, any key type.
  (let [db (-> (mg/new-db)
               (mg/add-id-attr ::e-id ::f-id))]
    (prop/for-all [e (s/gen ::E)]
      (let [new-db (mg/add db e)]
        (= e (mg/pull new-db
                      [::e-id {::fs [::f-id]}]
                      [::e-id (::e-id e)]))))))

;; entity specs used for the following tests (sorted map)
(s/def ::G (s/keys :req [::g-id ::hs]))
(s/def ::H (s/keys :req [::h-id]))
(s/def ::g-id integer?)
(s/def ::h-id integer?)
(s/def ::hs (s/and (s/coll-of (s/tuple string? ::H)
                              :kind (every-pred map? sorted?)
                              :into (sorted-map))
                   not-empty))

(defspec t-roundtrip-sorted-map 10
  ;; Sorted map of lookup refs preserves its sorted-ness.
  (let [db (-> (mg/new-db)
               (mg/add-id-attr ::g-id ::h-id))]
    (prop/for-all [g (s/gen ::G)]
      (let [new-db (mg/add db g)]
        (sorted? (::hs (mg/pull new-db
                                [::g-id {::hs [::h-id]}]
                                [::g-id (::g-id g)])))))))

;; Local Variables:
;; eval: (put-clojure-indent 'for-all :defn)
;; End:
