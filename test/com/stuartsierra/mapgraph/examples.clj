;; The MIT License (MIT)

;; Copyright (c) 2016 Stuart Sierra

;; Permission is hereby granted, free of charge, to any person
;; obtaining a copy of this software and associated documentation
;; files (the "Software"), to deal in the Software without
;; restriction, including without limitation the rights to use, copy,
;; modify, merge, publish, distribute, sublicense, and/or sell copies
;; of the Software, and to permit persons to whom the Software is
;; furnished to do so, subject to the following conditions:

;; The above copyright notice and this permission notice shall be
;; included in all copies or substantial portions of the Software.

;; THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
;; EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
;; MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
;; NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
;; BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
;; ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
;; CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
;; SOFTWARE.

(ns com.stuartsierra.mapgraph.examples
  "Sample data for documentation and tests."
  (:require [clojure.pprint :refer [pprint]]
            [com.stuartsierra.mapgraph :as mg]))

(def friends
  (-> (mg/new-db)
      (mg/add-id-attr :user/id)
      (mg/add {:user/id 1
               :user/name "Alice"
               :user/friends #{{:user/id 2
                                :user/name "Bob"
                                :user/friends #{{:user/id 4
                                                 :user/name "David"}
                                                {:user/id 5
                                                 :user/name "Emily"}}}
                               {:user/id 3
                                :user/name "Claire"
                                :user/friends #{{:user/id 1}}}}}
              {:user/id 4
               :user/name "Frank"
               :user/friends #{{:user/id 1}}})
      (assoc :link/user [:user/id 3])
      (assoc :link/users [[:user/id 2] [:user/id 3]])))

(def addresses
  (-> (mg/new-db)
      (mg/add-id-attr :user/id :address/id)
      (mg/add {:user/id 1
               :user/name "Alice"
               :user/addresses {"home" {:address/id 1
                                        :address/street "123 Home Lane"}
                                "work" {:address/id 2
                                        :address/street "456 Corporate Street"}}})))

(def sample-host
  {;; identifier
   :host/ip "10.10.1.1"

   ;; scalar value
   :host/name "web1"

   ;; collections (list, vector, set, map) with no nested entities
   :host/aliases ["host1" "www"]
   :host/rules {"input" {"block" "*", "allow" 80}
                "output" {"allow" 80}}

   ;; single entity value
   :host/gateway {:host/ip "10.10.10.1"}

   ;; collection of entities (list, vector, set)
   :host/peers #{{:host/ip "10.10.1.2", :host/name "web2"}
                 {:host/ip "10.10.1.3"}}

   ;; map of non-entity keys to entities
   :host/connections {"database" {:host/ip "10.10.1.4", :host/name "db"}
                      ["cache" "level2"] {:host/ip "10.10.1.5", :host/name "cache"}}})

(def hosts
  (-> (mg/new-db)
      (mg/add-id-attr :host/ip)
      (mg/add sample-host)))
