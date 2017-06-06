(defproject com.stuartsierra/mapgraph "0.2.2-SNAPSHOT"
  :description "Basic in-memory graph database of maps with links"
  :url "https://github.com/stuartsierra/mapgraph"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [re-frame "0.9.4"]]
  :profiles
  {:dev {:dependencies
         [[com.datomic/datomic-free "0.9.5561"]
          [criterium "0.4.4"]
          [datascript "0.16.1"]
          [org.clojure/clojure "1.9.0-alpha17"]
          [org.clojure/clojurescript "1.9.562"]
          [org.clojure/spec.alpha "0.1.123"]
          [org.clojure/test.check "0.9.0"]
          [org.clojure/tools.namespace "0.3.0-alpha1"]]}}
  :repositories [["releases" "https://clojars.org/repo/"]])
