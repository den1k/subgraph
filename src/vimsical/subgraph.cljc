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

(ns vimsical.subgraph
  "An in-memory graph data store consisting of maps with links.

  ## Introduction

  This is a very basic database that stores entities. An *entity* is a
  Clojure map from *attributes* to values.

  An *attribute* is a keyword, usually namespace-qualified.

  An entity has exactly one *unique identity* attribute whose value
  acts as a globally-unique identifier for that entity. An entity may
  not have more than one unique identity attribute; if it does, the
  behavior is undefined.

  A *lookup ref* is a vector pair of a unique identity attribute and a
  value, such as `[:user/id 1234]`. The combination of a unique
  identity attribute and its value uniquely identifies a single entity
  in the database.

  ## Usage

  Create a new database with `new-db`.

  The schema of the database is the set of all unique identity
  attributes. `add-id-attr` extends the schema with a new unique
  identity attribute.

  `add` inserts entities in the database. Entities already in the
  database are updated as by `clojure.core/merge`. (To replace an
  entity without merging, `dissoc` it first.)

  All entites added to a database are automatically *normalized*: all
  nested entities are replaced with lookup refs to other entities in
  the database.

  Entity normalization recognizes the following forms:

    - any non-entity value, including collections
    - a single entity
    - a collection (list, vector, set) of entities
    - a map where keys are any type, vals are single entities

  Collection values may not mix entities and non-entities. Collections
  are searched only one layer deep: `add` will not recursively walk
  arbitrary data structures to search for entities to normalize.

  Databases can be manipulated with primitive map operations such as
  `get` and `dissoc`. Keep in mind that there are no indexes: If you
  `dissoc` an entity from the database you may leave behind broken
  links to that entity.

  To get nested maps back out, use `pull`, which follows a pull
  pattern to recursively expand entity lookup refs.")

(defn- seek [pred s]
  (some #(when (pred %) %) s))

(defn- possible-entity-map?
  "True if x is a non-sorted map. This check prevents errors from
  trying to compare keywords with incompatible keys in sorted maps."
  [x]
  (and (map? x)
       (not (sorted? x))))

(defn- find-id-key
  "Returns the first identifier key found in map, or nil if it is not
  a valid entity map."
  [map id-attrs]
  (when (possible-entity-map? map)
    (seek #(contains? map %) id-attrs)))

(defn- get-ref
  "Returns a lookup ref for the map, given a collection of identifier
  keys, or nil if the map does not have an identifier key."
  [map id-attrs]
  (when-let [k (find-id-key map id-attrs)]
    [k (get map k)]))

(defn- keept
  "Like clojure.core/keep but preserves the types of vectors and sets,
  including sorted sets. If coll is a map, applies f to each value in
  the map and returns a map of the same (sorted) type."
  [f coll]
  (cond
    (vector? coll) (into [] (keep f) coll)
    (set? coll) (into (empty coll) (keep f) coll)
    (map? coll) (reduce-kv (fn [m k v]
                             (if-let [vv (f v)]
                               (assoc m k vv)
                               m))
                           (empty coll)
                           coll)
    :else (keep f coll)))

(defn- like
  "Returns a collection of the same type as type-coll (vector, set,
  sequence) containing elements of sequence s."
  [type-coll s]
  (cond
    (vector? type-coll) (vec s)
    (set? type-coll) (into (empty type-coll) s)  ; handles sorted-set
    :else s))

(defn- into!
  "Transient version of clojure.core/into"
  [to from]
  (reduce conj! to from))

(defn- update!
  "Transient version of clojure.core/update"
  [m k f x]
  (assoc! m k (f (get m k) x)))

(defn- normalize-entities
  "Returns a sequence of normalized entities starting with map m."
  [m id-attrs]
  (lazy-seq
   (loop [sub-entities (transient [])
          normalized (transient {})
          kvs (seq m)]
     (if-let [[k v] (first kvs)]
       (if (map? v)
         (if-let [r (get-ref v id-attrs)]
           ;; v is a single entity
           (recur (conj! sub-entities v)
                  (assoc! normalized k r)
                  (rest kvs))
           ;; v is a map, not an entity
           (let [values (vals v)]
             (if-let [refs (seq (keep #(get-ref % id-attrs) values))]
               ;; v is a map whose values are entities
               (do (when-not (= (count refs) (count v))
                     (throw (ex-info "Map values may not mix entities and non-entities"
                                     {:reason ::mixed-map-vals
                                      ::attribute k
                                      ::value v})))
                   (recur (into! sub-entities values)
                          (assoc! normalized k (into (empty v)  ; preserve type
                                                     (map vector (keys v) refs)))
                          (rest kvs)))
               ;; v is a plain map
               (recur sub-entities
                      (assoc! normalized k v)
                      (rest kvs)))))
         ;; v is not a map
         (if (coll? v)
           (if-let [refs (seq (keep #(get-ref % id-attrs) v))]
             ;; v is a collection of entities
             (do (when-not (= (count refs) (count v))
                   (throw (ex-info "Collection values may not mix entities and non-entities"
                                   {:reason ::mixed-collection
                                    ::attribute k
                                    ::value v})))
                 (recur (into! sub-entities v)
                        (assoc! normalized k (like v refs))
                        (rest kvs)))
             ;; v is a collection of non-entities
             (recur sub-entities
                    (assoc! normalized k v)
                    (rest kvs)))
           ;; v is a single non-entity
           (recur sub-entities
                  (assoc! normalized k v)
                  (rest kvs))))
       (cons (persistent! normalized)
             (mapcat #(normalize-entities % id-attrs)
                     (persistent! sub-entities)))))))

(defn new-db
  "Returns a new, empty database value."
  []
  {::id-attrs #{}})

(defn db?
  "Returns true if x is a mapgraph database."
  [x]
  (and (map? x)
       (set? (::id-attrs x))
       (every? keyword? (::id-attrs x))))

(defn add-id-attr
  "Adds unique identity attributes to the db schema. Returns updated
  db."
  [db & id-keys]
  (update db ::id-attrs into id-keys))

(defn add
  "Returns updated db with normalized entities merged in."
  [db & entities]
  (let [id-attrs (::id-attrs db)]
    (persistent!
     (reduce (fn [db e]
               (let [ref (get-ref e id-attrs)]
                 (update! db ref merge e)))
             (transient db)
             (mapcat #(normalize-entities % id-attrs) entities)))))

(defn entity?
  "Returns true if map is an entity according to the db schema. An
  entity is a map from keywords to values with exactly one identifier
  key."
  [db map]
  (and (map? map)
       (every? keyword? (keys map))
       (= 1 (count (filter #(contains? map %) (::id-attrs db))))))

(defn ref-to
  "Returns a lookup ref for the entity using the schema in db, or nil
  if not found. The db does not need to contain the entity."
  [db entity]
  (get-ref entity (::id-attrs db)))

(defn ref?
  "Returns true if ref is a lookup ref according to the db schema."
  [db ref]
  (and (vector? ref)
       (= 2 (count ref))
       (contains? (::id-attrs db) (first ref))))

(defn link?
  [expr]
  (when (map? expr)
    (when (vector? (key (first expr)))
      (= '_ (second (key (first expr)))))))

(defn expr-type
  "Returns an expression type."
  [expr]
  (cond
    (= '* expr)         ::*
    (vector? expr)      ::pattern
    (keyword? expr)     ::attr
    (link? expr)        ::link
    (or (map? expr)
        (= '... expr)
        (number? expr)) ::join
    :else               nil))

(defmulti parse-expr
  (fn [_ _ expr]
    (expr-type expr)))

(defmethod parse-expr :default
  [{:keys [pattern lookup-ref]} _ expr]
  (throw
   (ex-info
    "Invalid form in pull pattern"
    {:reason ::invalid-pull-form
     ::form expr
     ::pattern pattern
     ::lookup-ref lookup-ref})))

(defmethod parse-expr ::pattern
  [{:keys [parser db db-get-ref lookup-ref] :as context} result pattern]
  ;; We have two conflicting implementation details here: 1. links require that
  ;; we parse the pattern even when no lookup-ref is providied (that is their
  ;; whole purpose) 2. We should return nil in the case where the pattern
  ;; doesn't contain any links and the lookup-ref provided is not-found. We
  ;; could have made this logical distinction more visible in the code by adding
  ;; a branching condition such as (some link? pattern) but this degrades
  ;; performance more than just proceeding with the degenerate case and making
  ;; sure we don't return empty colls after the fact.
  (not-empty
   (let [entity (db-get-ref context pattern lookup-ref)
         ;; rec joins need a reference to the outer pattern
         context' (assoc context :entity entity :pattern pattern)]
     (reduce
      (fn [result expr]
        (parser context' result expr))
      result pattern))))

(defmethod parse-expr ::attr
  [{:keys [entity]} result k]
  (if-let [[_ val] (find entity k)]
    (assoc result k val)
    result))

(defmethod parse-expr ::join
  [{:keys [parser db db-ref? entity pattern] :as context} result pull-map]
  (letfn [(dec-rec-pull-map [k m]
            (assert (== 1 (count m)) "Join maps should have a single key")
            ;; No-op for a map with a different key
            (let [[[k' cnt]] (seq m)]
              (if (= k k') {k (dec ^long cnt)} m)))
          (dec-rec-join-pattern [k pattern]
            (mapv
             (fn [expr]
               (if (map? expr) (dec-rec-pull-map k expr) expr))
             pattern))
          (rec-join-expr [k join-expr]
            ;; No-op for a simple pattern, recursive exprs will need to get
            ;; their pattern from the context
            (cond
              (vector? join-expr) join-expr
              (= '... join-expr) pattern
              (and (number? join-expr)
                   (pos? ^long join-expr))
              (dec-rec-join-pattern k pattern)
              :else nil))
          (parse-one [pull-expr ref]
            (parser
             (assoc context :lookup-ref ref)
             {} pull-expr))
          (parse-many [join-expr refs]
            (keept #(parse-one join-expr %) refs))]
    (reduce-kv
     (fn [result k join-expr]
       (if-let [val (get entity k)]
         (if-let [join-expr' (rec-join-expr k join-expr)]
           (cond
             (db-ref? db val)
             (assoc result k (parse-one join-expr' val))

             (coll? val)
             (assoc result k (parse-many join-expr' val))

             :else
             (throw
              (ex-info
               "pull map pattern must be to a lookup ref or a collection of lookup refs."
               {:reason ::pull-join-not-ref
                ::pull-map-pattern pull-map
                ::entity entity
                ::attribute k
                ::value val})))
           ;; remove key at end of recursion
           (dissoc result k))
         ;; no value for key
         result))
     result pull-map)))

(defmethod parse-expr ::*
  [{:keys [entity]} result _]
  (merge result (apply dissoc entity (keys result))))

(defmethod parse-expr ::link
  [{:keys [parser db db-get-ref] :as context} result link-map]
  (let [[[[k] pattern]] (seq link-map)]
    (when-some [lookup-ref (db-get-ref context pattern k)]
      ;; Recursively parse with a join-like context
      (parser
       (assoc-in context [:entity k] lookup-ref)
       result
       {k pattern}))))

(defn default-get-ref
  [{:keys [db]} _ lookup-ref]
  (get db lookup-ref))

(defn pull
  "Returns a map representation of the entity found at lookup ref in
  db. Builds nested maps following a pull pattern.

  A pull pattern is a vector containing any of the following forms:

     :key  If the entity contains :key, includes it in the result.

     '*    (literal symbol asterisk) Includes all keys from the entity
           in the result.

     { :key sub-pattern }
           The entity's value for key is a lookup ref or collection of
           lookup refs. Expands each lookup ref to the entity it refers
           to, then applies pull to each of those entities using the
           sub-pattern."
  ([db pattern]
   (pull db pattern nil nil))
  ([db pattern lookup-ref]
   (pull db pattern lookup-ref nil))
  ([db pattern lookup-ref {:as   options
                           :keys [parser
                                  db-ref?
                                  db-get-ref]
                           :or   {parser     parse-expr
                                  db-ref?    ref?
                                  db-get-ref default-get-ref}}]
   (let [context {:parser     parser
                  :db         db
                  :db-ref?    db-ref?
                  :db-get-ref db-get-ref
                  :lookup-ref lookup-ref}
         result  {}]
     (parser context result pattern))))
