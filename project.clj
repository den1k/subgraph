(defproject com.stuartsierra/mapgraph "0.1.0-SNAPSHOT"
  :description "Basic in-memory graph database of maps with links"
  :url "https://github.com/stuartsierra/mapgraph"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.7.0"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.9.0-alpha5"]
                                  [org.clojure/tools.namespace "0.3.0-alpha1"]
                                  [org.clojure/test.check "0.9.0"]]}})
