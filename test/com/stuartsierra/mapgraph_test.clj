(ns com.stuartsierra.mapgraph-test
  (:require [clojure.spec :as s]
            [clojure.test :refer [deftest is]]
            [com.stuartsierra.mapgraph :as mg]
            [com.stuartsierra.mapgraph.spec]
            [com.stuartsierra.mapgraph.examples :as examples]))

(s/instrument-ns 'com.stuartsierra.mapgraph)

(deftest t-pull-friend-names-depth-3
  (is (= (mg/pull
          examples/friends
          '[:user/name
            {:user/friends [:user/name
                            {:user/friends [:user/name]}]}]
          [:user/id 1])
         {:user/name "Alice"
          :user/friends
          #{{:user/name "Claire"
             :user/friends #{{:user/name "Alice"}}}
            {:user/name "Bob"
             :user/friends #{{:user/name "Emily"}
                             {:user/name "Frank"}}}}})))

(deftest t-pull-entities-in-map
  (is (= (mg/pull examples/addresses
                  [:user/name
                   {:user/addresses [:address/street]}]
                  [:user/id 1])
         {:user/name "Alice"
          :user/addresses
          {"home" {:address/street "123 Home Lane"}
           "work" {:address/street "456 Corporate Street"}}})))

(deftest t-pull-map-with-complex-keys
  (is (= (mg/pull examples/hosts
                  [:host/ip
                   :host/rules
                   {:host/connections [:host/name]}]
                  [:host/ip "10.10.1.1"])
         {:host/ip "10.10.1.1"
          :host/rules
          {"input" {"block" "*", "allow" 80}
           "output" {"allow" 80}}
          :host/connections
          {"database" {:host/name "db"}
           ["cache" "level2"] {:host/name "cache"}}})))

(deftest t-pull-star
  (is (= (mg/pull examples/friends
                  '[*]
                  [:user/id 3])
         {:user/id 3
          :user/name "Claire"
          :user/friends #{[:user/id 1]}})))

(deftest t-pull-star-after-join
  ;; star in pull expression should not replace nested entities joined
  ;; elsewhere in the pull expression.
  (is (= (mg/pull examples/friends
                  '[{:user/friends [:user/name]}
                    *]
                  [:user/id 3])
         {:user/id 3
          :user/name "Claire"
          :user/friends #{{:user/name "Alice"}}})))

(deftest t-pull-broken-ref
  (is (= (mg/pull (dissoc examples/friends [:user/id 1])
                  '[{:user/friends [:user/name]}
                    *]
                  [:user/id 3])
         {:user/id 3
          :user/name "Claire"
          :user/friends #{}})))
