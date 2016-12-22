(defproject com.stuartsierra/mapgraph "0.2.1"
  :description "Basic in-memory graph database of maps with links"
  :url "https://github.com/stuartsierra/mapgraph"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.7.0"]]
  :profiles
  {:dev {:dependencies
         [[com.datomic/datomic-free "0.9.5372"]
          [criterium "0.4.4"]
          [datascript "0.15.0"]
          [org.clojure/clojure "1.9.0-alpha7"]
          [org.clojure/clojurescript "1.9.93"]
          [org.clojure/test.check "0.9.0"]
          [org.clojure/tools.namespace "0.3.0-alpha1"]]}}
  :repositories [["releases" "https://clojars.org/repo/"]])
