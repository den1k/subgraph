(ns vimsical.subgraph.bench.re-frame
  "A recursive query benchmark meant to assess query caching performance. The
  pulled data is highly redundant and caching should show orders of magnitude
  improvements."
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
   {:entity/children '...}
   {:entity/parent 1}])

;;
;; * Db
;;

(defn gen-data [depth]
  (reduce
   (fn [db i]
     {:db/id           i
      :entity/parent   (when (pos? i) {:db/id (dec i)})
      :entity/children (when db [db])})
   nil (reverse (range depth))))

(defn new-db
  [depth]
  (-> (sg/new-db)
      (sg/add-id-attr :db/id)
      (sg/addr (gen-data (inc depth)))))


(def depth 150)

(def db (new-db depth))

(re-frame.core/reg-event-db
 ::init
 (constantly db))

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

(re-frame.core/reg-sub-raw ::pull sg.re-frame/raw-sub-handler)
(re-frame.core/reg-sub ::counter (fn [db _] (:counter db)))

;;
;; * Updates
;;

(re-frame.core/reg-event-db
 ::addr
 (fn [db [_ entity]] (sg/addr db entity)))

(re-frame.core/reg-event-db
 ::inc
 (fn [{:keys [counter] :as db :or {counter -1}} [_ refs]]
   (let [n        (inc counter)]
     (reduce
      (fn [db ref]
        (assoc-in db [ref :foo] n))
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
  (do (println "\n\n# Re-frame\n\n")
      (println "\n## Baseline\n")
      (time
       (doall
        (for [ref refs]
          (time
           (sg/pull db query ref)))))

      (clear-subscription-cache!)
      (re-frame/dispatch-sync [::init])
      (let [counter (re-frame/subscribe [::counter])]
        (letfn [(pull-refs! []
                  (time
                   (doall
                    (for [ref refs]
                      (time (deref (re-frame/subscribe [::pull query ref])))))))
                (check-data! []
                  (doall
                   (for [ref refs]
                     (assert (= (sg/pull db query ref)
                                @(re-frame/subscribe [::pull query ref]))))))
                (check-counters! []
                  (doall
                   (for [ref refs]
                     (assert (= @counter (:foo (deref (re-frame/subscribe [::pull [:foo] ref]))))))))]
          (println "\n## First pass\n")
          (pull-refs!)
          (check-data!)
          (check-counters!)
          ;; Trigger recomputation
          (re-frame/dispatch-sync [::inc refs])
          (println "\n## Second pass\n")
          (pull-refs!)
          (check-data!)
          (check-counters!))
        nil)))
