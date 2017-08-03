(ns vimsical.subgraph.bench.re-frame.depth
  "Benchmark recursive joins performance.

  We have a single entity with a deep recusive query that back references the
  previous level as its parent, so the denormalized data is highly redundant.

  The updates target the top, middle and bottom of the tree. We expect them to
  be slower at the top of the tree since the back-references propagates updates
  all the way down.

  However we should watch out for opportunities in caching unique [pattern ref]
  combinations since the same work is repeated many times over in the simplest
  case.
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
  [:db/id
   :entity/counter
   {:entity/children '...}
   {:entity/parent 1}])

;;
;; * Entities
;;

(defn gen-entity [depth]
  (reduce
   (fn [db i]
     {:db/id           i
      :entity/parent   (when (pos? i) {:db/id (dec i)})
      :entity/children (when db [db])})
   nil (reverse (range depth))))

;;
;; * Db
;;

(def depth 150)

(defn new-db
  [entity]
  (-> (sg/new-db)
      (sg/add-id-attr :db/id)
      (sg/addr entity)))

(re-frame.core/reg-event-db
 ::init
 (fn [_ _]
   (println "\n## Add")
   (let [entity (gen-entity depth)]
     (time (new-db entity)))))

;;
;; * Refs
;;

(def ref-top [:db/id 0])
(def ref-mid [:db/id (int (/ depth 2))])
(def ref-bot [:db/id depth])
(def refs [ref-top ref-mid ref-bot])

;;
;; * Subs
;;

(re-frame.core/reg-sub ::db (fn [db _] db))
(re-frame.core/reg-sub-raw ::pull sg.re-frame/raw-sub-handler)
(re-frame.core/reg-sub ::counter (fn [db _] (:counter db)))

;;
;; * Updates
;;

;; Update an attr that's not part of the query
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
    (println "\n\n# Re-frame depth\n")

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
