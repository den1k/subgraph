# SubGraph

> Reactive graph database for re-frame



## Releases and Dependency Information

* Latest stable release is [0.1.0](https://github.com/vimsical/subgraph/tree/0.1.0)

* [All releases](https://clojars.org/vimsical/subgraph)

[Leiningen] dependency information:

    [vimsical/subgraph "0.1.0"]

[Maven] dependency information:

    <dependency>
      <groupId>vimsical</groupId>
      <artifactId>subgraph</artifactId>
      <version>0.1.0</version>
    </dependency>

[Gradle] dependency information:

    compile "vimsical:subgraph:1.0.0"

[Clojars]: http://clojars.org/
[Leiningen]: http://leiningen.org/
[Maven]: http://maven.apache.org/
[Gradle]: http://www.gradle.org/



## Dependencies and Compatibility

SubGraph is written in `.cljc` and depends on Clojure or ClojureScript
version 1.7.0 or higher.

To run the tests you will need clojure.spec.alpha, available in Clojure
1.9.0-alpha17 or higher.



## Discussion

Please post questions and issues to the Github issues system.



## Usage

```clojure
(ns examples
  (:require [com.stuartsierra.mapgraph :as mg]))
```

Create a new Subgraph database with `new-db`. You will probably want
to store it in a mutable reference such as an Atom.

```clojure
(def db (atom (mg/new-db)))
```

Add the unique identity attributes that define your schema.

```clojure
(swap! db mg/add-id-attr :user/id :color/hex)
```

Add entities to your database with `add`. You can add multiple
entities at once, and they may be nested.

```clojure
(swap! db mg/add
       {:user/id 1
        :user/name "Pat"
        :user/favorite-color {:color/hex "9C27B0"
                              :color/name "Purple"}}
                          ;  ^-- nested entity

       {:user/id 2
        :user/name "Reese"
        :user/favorite-color {:color/hex "D50000"
                              :color/name "Red"}})
```

Entities in the database are stored *normalized*: all nested entities
are replaced with lookup refs. You can see this if you `get` an entity
by its lookup ref.

```clojure
(get @db [:user/id 2])
;;=> {:user/id 2,
;;    :user/name "Reese",
;;    :user/favorite-color [:color/hex "D50000"]}
                        ;  ^-- lookup ref
```

To get back nested entities, use `pull`, which takes a pattern
describing which attributes and entities you want to get back.
It is similar to [Datomic Pull].

[Datomic Pull]: http://docs.datomic.com/pull.html

```clojure
(mg/pull @db
         [:user/name {:user/favorite-color [:color/name]}]
         [:user/id 2])
;;=> {:user/name "Reese",
;;    :user/favorite-color {:color/name "Red"}}
```

Entities with the same unique identity are merged.

```clojure
(swap! db
       mg/add
       {:user/id 1  ; "Pat"
        :user/profession "Programmer"})

(mg/pull @db
         [:user/id :user/name :user/profession]
         [:user/id 1])
;; {:user/id 1,
;;  :user/name "Pat",
;;  :user/profession "Programmer"}
```

Entities can refer to other entities, forming a graph. The graph may
have cycles.

```clojure
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
```

To remove an entity, `dissoc` its lookup ref. Dangling lookup refs
will be ignored on subsequent `pull`.

```clojure
(swap! db dissoc [:user/id 2])  ; Reese

(mg/pull @db '[*] [:user/id 2])
;;=> nil

(mg/pull @db
         [:user/name
          {:user/friends [:user/name]}]
         [:user/id 1])
;;=> {:user/name "Pat",
;;    :user/friends #{}}
                  ;  ^-- Reese is gone
```


### Collections

Attribute values can be any Clojure collection type.

```clojure
(swap! db mg/add
       {:user/id 1
        :user/favorite-sports '(hockey tennis golf)})

(mg/pull @db
         [:user/name :user/favorite-sports]
         [:user/id 1])
;;=> {:user/name "Pat", :user/favorite-sports (hockey tennis golf)}
```

Merging a new collection value completely replaces the previous value.

```clojure
(swap! db mg/add
       {:user/id 1
        :user/favorite-sports '(tennis polo)})

(mg/pull @db
         [:user/name :user/favorite-sports]
         [:user/id 1])
;;=> {:user/name "Pat", :user/favorite-sports (tennis polo)}
```

A collection of nested entities may be a list, vector, set, or map in
which the vals are entities.

```clojure
(def sample-host
  {;; identifier
   :host/ip "10.10.1.1"

   ;; non-entity value
   :host/name "web1"

   ;; collections (list, vector, set, map) of non-entity values
   :host/aliases ["host1" "www"]
   :host/rules {"input" {"block" "*", "allow" 80}
                "output" {"allow" 80}}

   ;; single entity value
   :host/gateway {:host/ip "10.10.10.1"}

   ;; collection of entities (list, vector, set)
   :host/peers #{{:host/ip "10.10.1.2", :host/name "web2"}
                 {:host/ip "10.10.1.3"}}

   ;; map of non-entity keys to entity vals
   :host/connections {"database"         {:host/ip "10.10.1.4", :host/name "db"}
                      ["cache" "level2"] {:host/ip "10.10.1.5", :host/name "cache"}}})
```

`pull` works the same way on single entities and collections of entities.

```clojure
(def hosts
  (atom (-> (mg/new-db)
            (mg/add-id-attr :host/ip)
            (mg/add sample-host))))

(mg/pull @hosts
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
```

Collections may not mix entities and non-entities.

```clojure
(try (swap! db mg/add {:user/id 3 :user/friends [{:user/id 1} "Bob"]})
     (catch Throwable t t))
;; #error {:data {:reason ::mg/mixed-collection,
;;                ::mg/attribute :user/friends,
;;                ::mg/value [{:user/id 1} "Bob"]}}
```



## Comparison with Datomic/Datascript

Subgraph is designed to be used as a temporary store for data kept in
[Datomic] or [Datascript].

[Datomic]: http://www.datomic.com/
[Datascript]: https://github.com/tonsky/datascript

Subgraph is different from Datomic/Datascript in the following ways:

* Schema only specifies unique identity attributes

* Non-identity attributes do not need to be declared before they are
  used

* An entity must not have more than one unique identity attribute

* Values may include collections of any type

* Empty collections will be stored rather than ignored

* Updating the value of an attribute with a collection always replaces
  the entire previous value

* No reverse attribute references (like `:user/_friends`)

* No component attributes

* `pull` does not support recursion, default values, limits, or
  reverse lookup

* No indexes

* No queries, only lookup by unique identity attribute

* No database entity IDs, only lookup refs



## Bug reports

Please file issues on GitHub with minimal sample code that
demonstrates the problem.



## Contributing

Please do not send pull requests without prior discussion.
Please contact me via email first. Thank you.



## Special thanks to 

[Jeb Beich](https://github.com/jebberjeb) for discussion, early
testing, and contributions.

[Cognitect](http://www.cognitect.com/) for providing me with time to
work on open-source projects. This library is my personal work and is
not officially supported by Cognitect, Inc.



## Copyright and License

The MIT License (MIT)

Copyright (c) 2016 Stuart Sierra as part of the MapGraph project
(https://github.com/stuartsierra/mapgraph)

Copyright (c) 2017 Vimsical

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
