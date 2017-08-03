(ns vimsical.subgraph.bench.re-frame.breadth
  "Benchmark wide joins performance.

  We have a small number of entities each joining an equally large number of
  uniquer children entities.

  The updates target the top-level entities, so we expect the second pass to be
  _much_ faster than the first pass since we're changing a single attribute at
  the top of the tree.
  "
  #?@(:clj
      [(:require
        [re-frame.core :as re-frame]
        [re-frame.subs :as re-frame.subs]
        [vimsical.subgraph :as sg]
        [vimsical.subgraph.re-frame :as sg.re-frame])]
      :cljs
      [(:require
        [re-frame.core :as re-frame]
        [vimsical.subgraph :as sg]
        [vimsical.subgraph.re-frame :as sg.re-frame])]))

;;
;; * Query
;;

(def query
  ['* {:entity/children ['*]}])


;;
;; * Entities
;;

(def entities-count 4e4)
(def kvs-count 30)

(defn entity
  [id]
  (assoc
   (zipmap
    (map (comp keyword str) (range kvs-count))
    (range kvs-count))
   :db/id id))

;;
;; * Refs
;;

(def ref-top [:db/id :top])
(def ref-mid [:db/id :mid])
(def ref-bot [:db/id :bot])
(def refs [ref-top ref-mid ref-bot])

;;
;; * Entities
;;

(defn gen-entities
  [entities-count]
  (let [entities (mapv entity (range entities-count))]
    (doall
     (for [[i ref] (map-indexed vector refs)]
       (let [from (int (* i (/ entities-count 3)))
             to   (int (+ from (/ entities-count 3)))
             entities (subvec entities from to)]
         (-> (conj {} ref)
             (assoc :entity/children entities)))))))

;;
;; * Db
;;

(defn new-db
  [entities]
  (let [db (-> (sg/new-db) (sg/add-id-attr :db/id))]
    (apply sg/add db entities)))

(re-frame.core/reg-event-db
 ::init
 (fn [_ _]
   (println "\n## Add")
   (let [entities (gen-entities entities-count)]
     (time (new-db entities)))))

;;
;; * Subs
;;

(re-frame.core/reg-sub ::db (fn [db _] db))
(re-frame.core/reg-sub-raw ::pull sg.re-frame/raw-sub-handler)
(re-frame.core/reg-sub ::counter (fn [db _] (:counter db)))

;;
;; * Updates
;;

(re-frame.core/reg-event-db
 ::update-entity-counters
 (fn [{:keys [counter] :as db :or {counter -1}} [_ refs]]
   (let [n (inc counter)]
     (reduce
      (fn [db [k id]]
        (sg/add db {k id :entity/counter n}))
      (assoc db :counter n) refs))))

;;
;; * Re-frame jvm cache fix https://github.com/Day8/re-frame/issues/385
;;

(defn clear-subscription-cache!
  []
  #?(:clj  (reset! re-frame.subs/query->reaction {})
     :cljs (re-frame/clear-subscription-cache!)))

;;
;; * Benchmark
;;

(defn bench []
  (do
    (println "\n\n# Re-frame breadth\n")

    (clear-subscription-cache!)
    (re-frame/dispatch-sync [::init])

    (println "\n## Baseline")
    (let [db @(re-frame/subscribe [::db])]
      (time
       (doseq [ref refs]
         (time (sg/pull db query ref)))))

    (letfn [(pull-refs! []
              (time
               (doseq [ref refs]
                 (time @(re-frame/subscribe [::pull query ref])))))
            (check-pull-results! []
              (doseq [ref refs]
                (assert
                 (= (sg/pull @(re-frame/subscribe [::db]) query ref)
                    @(re-frame/subscribe [::pull query ref])))))
            (check-refs-udpated! []
              (doseq [ref refs]
                (assert
                 (= @(re-frame/subscribe [::counter])
                    (:entity/counter @(re-frame/subscribe [::pull [:entity/counter] ref]))))))]
      (println "\n## First pass")
      (pull-refs!)
      (check-pull-results!)
      (check-refs-udpated!)
      (re-frame/dispatch-sync [::update-entity-counters refs])
      (println "\n## Second pass")
      (pull-refs!)
      (check-pull-results!)
      (check-refs-udpated!))
    nil))
