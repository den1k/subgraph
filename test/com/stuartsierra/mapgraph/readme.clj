(ns com.stuartsierra.mapgraph.readme
  "Examples used in documentation"
  (:require [com.stuartsierra.mapgraph :as mg]
            [com.stuartsierra.mapgraph.examples :as examples]))

(def db (atom (mg/new-db)))

(swap! db mg/add-id-attr :user/id :color/hex)

(swap! db mg/add
       {:user/id 1
        :user/name "Pat"
        :user/favorite-color {:color/hex "9C27B0"
                              :color/name "Purple"}}

       {:user/id 2
        :user/name "Reese"
        :user/favorite-color {:color/hex "D50000"
                              :color/name "Red"}})

(get @db [:user/id 2])
;;=> {:user/id 2,
;;    :user/name "Reese",
;;    :user/favorite-color [:color/hex "D50000"]}

(mg/pull @db
         [:user/name {:user/favorite-color [:color/name]}]
         [:user/id 2])
;;=> {:user/name "Reese",
;;    :user/favorite-color {:color/name "Red"}}

(swap! db
       mg/add
       {:user/id 1
        :user/profession "Programmer"})

(mg/pull @db
         [:user/id :user/name :user/profession]
         [:user/id 1])
;; {:user/id 1,
;;  :user/name "Pat",
;;  :user/profession "Programmer"}

(swap! db
       mg/add
       {:user/id 1
        :user/friends #{{:user/id 2}}}
       {:user/id 2
        :user/friends #{{:user/id 1}}})

(mg/pull @db
         [:user/name
          {:user/friends [:user/name
                          {:user/friends [:user/name]}]}]
         [:user/id 1])
;;=> {:user/name "Pat",
;;    :user/friends #{{:user/name "Reese",
;;                     :user/friends #{{:user/name "Pat"}}}}}

(swap! db dissoc [:user/id 2])

(mg/pull @db '[*] [:user/id 2])
;;=> nil

(mg/pull @db
         [:user/name
          {:user/friends [:user/name]}]
         [:user/id 1])
;;=> {:user/name "Pat",
;;    :user/friends #{}}

(swap! db mg/add
       {:user/id 1
        :user/favorite-sports '(hockey tennis golf)})

(mg/pull @db
         [:user/name :user/favorite-sports]
         [:user/id 1])
;;=> {:user/name "Pat", :user/favorite-sports (hockey tennis golf)}

(swap! db mg/add
       {:user/id 1
        :user/favorite-sports '(tennis polo)})

(mg/pull @db
         [:user/name :user/favorite-sports]
         [:user/id 1])
;;=> {:user/name "Pat", :user/favorite-sports (tennis polo)}

(mg/pull examples/hosts
         [:host/ip
          :host/rules
          {:host/gateway [:host/ip]
           :host/peers [:host/ip]
           :host/connections [:host/name]}]
         [:host/ip "10.10.1.1"])
;;=> {:host/ip "10.10.1.1",
;;    :host/rules {"input" {"block" "*", "allow" 80},
;;                 "output" {"allow" 80}},
;;    :host/gateway {:host/ip "10.10.10.1"},
;;    :host/peers #{{:host/ip "10.10.1.3"}
;;                  {:host/ip "10.10.1.2"}},
;;    :host/connections {"database" {:host/name "db"},
;;                       ["cache" "level2"] {:host/name "cache"}}}

(try (swap! db mg/add {:user/id 3 :user/friends [{:user/id 1} "Bob"]})
     (catch Throwable t t))
;; #error
;; {:reason ::mg/mixed-collection,
;;  ::mg/attribute :user/friends,
;;  ::mg/value [{:user/id 1} "Bob"]}
