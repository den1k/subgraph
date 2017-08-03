(ns vimsical.subgraph.parser.cache)

(def ^:private last-seen-db (volatile! {}))
(def ^:private parser-cache (volatile! {}))

(defn- seen? [db]
  (identical? @last-seen-db db))

(defn- reset-cache! [db]
  (vreset! last-seen-db db)
  (vreset! parser-cache {}))

(defn- cache-key
  [{:keys [lookup-ref]} _ expr]
  ;; Could also add link?
  (when (vector? expr)
    [expr lookup-ref]))

(defn- cache-and-return
  [k v]
  (do (vswap! parser-cache assoc k v) v))

(defn wrap-cache
  [parser db]
  (when-not (seen? db) (reset-cache! db))
  (fn [context result expr]
    (if-some [k (cache-key context result expr)]
      (if-some [[_ v] (find @parser-cache k)]
        v
        (cache-and-return k (parser context result expr)))
      (parser context result expr))))
