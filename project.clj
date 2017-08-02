(defproject vimsical/subgraph "0.1.0-SNAPSHOT"
  :description "Reactive graph database for re-frame"
  :url "https://github.com/vimsical/subgraph"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :aliases {"bench-jvm" ["run" "-m" "vimsical.subgraph.compare/bench"]
            "bench-node" ["doo" "node" "bench" "once"]
            "bench-browser" ["doo" "chrome-canary" "bench-browser" "once"]
            "bench-all" ["do" "clean," ["bench-jvm"] ["bench-node"] ["bench-browser"]]
            "test-jvm" ["test"]
            "test-node" ["doo" "node" "test" "once"]
            "test-all" ["do" "clean," ["test-jvm"] ["test-node"]]}

  :profiles
  {:provided {:dependencies [[re-frame "0.9.4"]
                             [reagent "0.6.0"]]}
   :dev {:source-paths ["bench"]
         :dependencies
         [[com.datomic/datomic-free "0.9.5561"]
          [criterium "0.4.4"]
          [datascript "0.16.1"]
          [org.clojure/clojure "1.9.0-alpha17"]
          [org.clojure/clojurescript "1.9.562"]
          [org.clojure/spec.alpha "0.1.123"]
          [org.clojure/test.check "0.9.0"]
          [org.clojure/tools.namespace "0.3.0-alpha1"]
          [com.google.guava/guava "21.0"]
          [re-frame "0.9.4"]
          [day8.re-frame/test "0.1.5"]
          [reagent "0.6.0"]
          [com.cemerick/piggieback "0.2.2-SNAPSHOT"]]}}
  :plugins [[lein-doo "0.1.7"]
            [lein-cljsbuild "1.1.6"]]
  :clean-targets ^{:protect false} ["resources/out" "target/"]
  :cljsbuild
  {:builds
   [{:id           "test"
     :source-paths ["src" "test"]
     :compiler     {:output-to "resources/out/test/main.js"
                    :output-dir "resources/out/test"
                    :main vimsical.test-runner
                    :target :nodejs
                    :optimizations  :none
                    :parallel-build true}}
    {:id           "bench"
     :source-paths ["src" "test" "bench"]
     :compiler     {:output-to "resources/out/bench/main.js"
                    :output-dir "resources/out/bench"
                    :main vimsical.bench-runner
                    :target :nodejs
                    :optimizations :none
                    :parallel-build true}}
    {:id           "bench-browser"
     :source-paths ["src" "test" "bench"]
     :compiler     {:output-to "resources/out/bench-browser/main.js"
                    :output-dir "resources/out/bench-browser"
                    :main vimsical.bench-runner
                    :optimizations :advanced
                    :parallel-build true}}]}
  :repositories [["releases" "https://clojars.org/repo/"]])
