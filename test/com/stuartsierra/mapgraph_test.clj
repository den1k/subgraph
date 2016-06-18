(ns com.stuartsierra.mapgraph-test
  (:require [clojure.spec :as s]
            [clojure.spec.gen :as gen]
            [clojure.test :refer [deftest is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [com.stuartsierra.mapgraph :as mg]
            [com.stuartsierra.mapgraph.spec]
            [com.stuartsierra.mapgraph.examples :as examples]))

(s/instrument-ns 'com.stuartsierra.mapgraph)

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

;; Local Variables:
;; eval: (put-clojure-indent 'for-all :defn)
;; End:
