(ns vimsical.subgraph.compare
  "Tests and benchmarks comparing MapGraph with Datomic and
  Datascript."
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.test.check.clojure-test :refer [defspec]]
   [clojure.test.check.generators :as tgen]
   [clojure.test.check.properties :as prop]
   [clojure.walk :as walk]
   [vimsical.subgraph :as sg]
   [vimsical.subgraph.spec :as sg.spec]
   [criterium.core :as crit]
   [datascript.core :as datascript]
   [datomic.api :as datomic]))

;;; Specs for generating entity data

(s/def ::Person
  (s/keys :req [::person-id ::person-name ::person-age ::home-town]
          :opt [::friends]))

(s/def ::Friend
  (s/keys :req [::person-id ::person-name ::person-age ::home-town]))

(s/def ::Town
  (s/keys :req [::town-id ::town-name]))

(s/def ::person-id uuid?)
(s/def ::person-name string?)
(s/def ::person-age (s/int-in 0 125))
(s/def ::home-town ::Town)

;; Spec for the ::friends attribute of a ::Person. References ::Friend
;; instead of ::Person to prevent infinite recursion in a generator.
;; Must be non-empty for the pull tests because MapGraph will store an
;; empty collection but Datomic and Datascript will not.
(s/def ::friends (s/and (s/coll-of ::Friend :into #{})
                        not-empty))

;; ::town-id spec is limited to a small range to increase the
;; likelihood of generating clashes leading to upserts.
(s/def ::town-id (s/int-in 0 1000))
(s/def ::town-name string?)

;;; Setup

(def datomic-schema
  [{:db/ident ::person-id
    :db/valueType :db.type/uuid
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity
    :db/id (datomic/tempid :db.part/db)
    :db.install/_attribute :db.part/db}
   {:db/ident ::person-name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/id (datomic/tempid :db.part/db)
    :db.install/_attribute :db.part/db}
   {:db/ident ::person-age
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/id (datomic/tempid :db.part/db)
    :db.install/_attribute :db.part/db}
   {:db/ident ::home-town
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/id (datomic/tempid :db.part/db)
    :db.install/_attribute :db.part/db}
   {:db/ident ::friends
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/id (datomic/tempid :db.part/db)
    :db.install/_attribute :db.part/db}
   {:db/ident ::town-id
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/id (datomic/tempid :db.part/db)
    :db.install/_attribute :db.part/db}
   {:db/ident ::town-name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/id (datomic/tempid :db.part/db)
    :db.install/_attribute :db.part/db}])

(def datascript-schema
  {::person-id {:db/unique :db.unique/identity}
   ::home-town {:db/valueType :db.type/ref}
   ::friends {:db/valueType :db.type/ref
              :db/cardinality :db.cardinality/many}
   ::town-id {:db/unique :db.unique/identity}})

(defn new-mapgraph-db
  "Returns a new map graph database with the test schema."
  []
  (sg/add-id-attr (sg/new-db) ::person-id ::town-id))

(defn new-datomic-db
  "Returns a new Datomic database with the test schema."
  []
  (let [uri (str "datomic:mem://" (datomic/squuid))]
    (datomic/create-database uri)
    (let [conn (datomic/connect uri)]
      @(datomic/transact conn datomic-schema)
      (datomic/db conn))))

(defn new-datascript-db
  "Returns a new Datascript database with the test schema."
  []
  (datascript/db (datascript/create-conn datascript-schema)))

(defn remove-db-ids
  "Recursively walks entity and removes :db/id from maps. Needed for
  the pull tests because MapGraph does not use :db/id."
  [entity]
  (walk/postwalk (fn [x] (if (map? x) (dissoc x :db/id) x)) entity))

(defn vecs->sets
  " recursively walks entity and transforms vectors of maps into sets.
  Needed for the pull tests because MapGraph preserves collection
  types but Datomic and Datascript always return vectors."
  [entity]
  (walk/postwalk (fn [x]
                   (if (and (vector? x) (map? (first x)))
                     (set x)
                     x))
                 entity))

(defn add-tempids
  "Recursively walks entities adding temp IDs to maps. tempid-fn is
  either datomic.api/tempid or datascript.core/tempid."
  [entities tempid-fn]
  (walk/postwalk (fn [x] (if (map? x)
                           (assoc x :db/id (tempid-fn :db.part/user))
                           x))
                 entities))

(defn new-dbs
  "Returns a map containing each type of database, initialized with
  the test schema."
  []
  {:mapgraph (new-mapgraph-db)
   :datomic (new-datomic-db)
   :datascript (new-datascript-db)})

(defn insert-all
  "Inserts a collection of entities in each database returned by
  new-dbs."
  [dbs entities]
  (let [{:keys [mapgraph datomic datascript]} dbs
        mapgraph (apply sg/add mapgraph entities)
        datomic (:db-after
                 (datomic/with
                  datomic
                  (add-tempids entities datomic/tempid)))
        datascript (:db-after
                    (datascript/with
                     datascript
                     (add-tempids entities datascript/tempid)))]
    {:mapgraph mapgraph
     :datomic datomic
     :datascript datascript}))

(defn pull-all
  "Pulls the same entity from each database in the dbs map."
  [dbs pull-expr lookup-ref]
  (-> dbs
      (update :mapgraph sg/pull pull-expr lookup-ref)
      (update :datomic (fn [db]
                         (-> (datomic/pull db pull-expr lookup-ref)
                             vecs->sets
                             remove-db-ids)))
      (update :datascript (fn [db]
                            (-> (datascript/pull db pull-expr lookup-ref)
                                vecs->sets
                                remove-db-ids)))))

(def pull-friends
  "Pull expression for use in the pull benchmarks and tests."
  '[*
    {::friends [::person-id ::person-name]
     ::home-town [::town-id]}])

;;; Comparison tests

(defspec t-equal-pull 100
  ;; Inserts the same entities into each database, pulls one back, and
  ;; verifies that the results are equal.
  (prop/for-all [entities (tgen/vector (s/gen ::Person) 1 20)]
    (let [lookup-ref [::person-id (::person-id (last entities))]
          pulls (-> (new-dbs)
                    (insert-all entities)
                    (pull-all pull-friends lookup-ref))]
      (apply = (vals pulls)))))

;;; Comparison benchmarks

(defn gen-entities
  "Returns a collection of n generated ::Person entities."
  [n]
  (gen/sample (s/gen ::Person) n))

(defn print-quick-bench
  "Prints db-type and runs Criterium quick-bench."
  [db-type run-fn]
  (printf "%n### %s%n%n" (name db-type))
  (crit/quick-bench (run-fn)))

(s/def ::db-type #{:mapgraph :datomic :datascript})

(s/def ::entities (s/coll-of map? :into []))

(s/def ::run-fn
  (s/fspec :args (s/cat)))

(s/def ::bench-fn
  (s/fspec :args (s/cat :db-type ::db-type
                        :f ::run-fn)))

(s/fdef bench-add
        :args (s/? (s/cat :f ::bench-fn
                          :dbs ::databases-by-type
                          :entities ::entities)))

(defn bench-add
  "Runs a benchmark for adding new entities to a database."
  ([] (bench-add print-quick-bench (new-dbs) (gen-entities 10)))
  ([bench-fn dbs entities]
   (let [{:keys [mapgraph datomic datascript]} (new-dbs)
         datomic-entities (add-tempids entities datomic/tempid)
         datascript-entities (add-tempids entities datascript/tempid)]
     {:mapgraph (bench-fn :mapgraph
                          #(apply sg/add mapgraph entities))
      :datomic (bench-fn :datomic
                         #(datomic/with datomic datomic-entities))
      :datascript (bench-fn :datascript
                            #(datascript/with datascript datascript-entities))})))

(s/fdef bench-pull
        :args (s/? (s/cat :f ::bench-fn
                          :dbs ::databases-by-type
                          :entities ::entities
                          :pull-expr ::sg.spec/pull-pattern
                          :lookup-ref ::sg.spec/reference)))

(defn bench-pull
  "Runs a benchmark for pulling entities from a database."
  ([]
   (let [entities (gen-entities 10)]
     (bench-pull print-quick-bench
                 (new-dbs)
                 entities
                 pull-friends
                 [::person-id (::person-id (last entities))])))
  ([bench-fn dbs entities pull-expr lookup-ref]
   (let [{:keys [mapgraph datomic datascript]} (insert-all dbs entities)]
     {:mapgraph (bench-fn :mapgraph
                          #(sg/pull mapgraph pull-expr lookup-ref))
      :datomic (bench-fn :datomic
                         #(datomic/pull datomic pull-expr lookup-ref))
      :datascript (bench-fn :datascript
                            #(datascript/pull datascript pull-expr lookup-ref))})))
