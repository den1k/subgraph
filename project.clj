(defproject vimsical/subgraph "0.1.0-SNAPSHOT"
  :description "Reactive graph database for re-frame"
  :url "https://github.com/vimsical/subgraph"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :aliases {"bench" ["run" "-m" "vimsical.subgraph.compare/bench"]}
  :profiles
  {:provided {:dependencies [[re-frame "0.9.4"]]}
   :dev {:dependencies
         [[com.datomic/datomic-free "0.9.5561"]
          [criterium "0.4.4"]
          [datascript "0.16.1"]
          [org.clojure/clojure "1.9.0-alpha17"]
          [org.clojure/clojurescript "1.9.562"]
          [org.clojure/spec.alpha "0.1.123"]
          [org.clojure/test.check "0.9.0"]
          [org.clojure/tools.namespace "0.3.0-alpha1"]]}}
  :repositories [["releases" "https://clojars.org/repo/"]])
