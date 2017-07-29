# SubGraph

> Reactive graph database for re-frame

[![CircleCI](https://circleci.com/gh/vimsical/subgraph.svg?style=shield&circle-token=60839af806151dc02bcd591d9ec9a26875d0820b)](https://circleci.com/gh/vimsical/subgraph)

[![Clojars Project](https://img.shields.io/clojars/v/vimsical/subgraph.svg)](https://clojars.org/vimsical/subgraph)



## Releases and Dependency Information

* SNAPSHOT only, stable release coming soon

* [All releases](https://clojars.org/vimsical/subgraph)

[Leiningen] dependency information:

    [vimsical/subgraph "0.1.0-SNAPSHOT"]

[Maven] dependency information:

    <dependency>
      <groupId>vimsical</groupId>
      <artifactId>subgraph</artifactId>
      <version>0.1.0-SNAPSHOT</version>
    </dependency>

[Gradle] dependency information:

    compile "vimsical:subgraph:0.1.0-SNAPSHOT"

[Clojars]: http://clojars.org/
[Leiningen]: http://leiningen.org/
[Maven]: http://maven.apache.org/
[Gradle]: http://www.gradle.org/



## Dependencies and Compatibility

SubGraph is written in `.cljc` and depends on Clojure or ClojureScript version 1.8.0 or higher.

`re-frame` and `reagent` are provided dependencies and will need to be included in your own project's dependencies.



## Terminology



### Entity

SubGraph entities are regular maps that can be uniquely identified by one (and currently only one) of their entries.



### Lookup ref

A lookup-ref is a 2-element vector representing an entity's identifying key and its associated value.



### Normalization

Normalization is the process of eliminating redundancy in entities by replacing every the other entities that it references, aka joins, with lookup refs.



### Database

The SubGraph database is a regular map associating lookup refs to their corresponding entities.



## Usage

This section shows how to initialize a SubGraph database, add, query and update entities using `re-frame` event handlers and subscriptions.

For more usage examples of interacting directly with the database, refer to the [usage section in the MapGraph README](https://github.com/stuartsierra/mapgraph#usage)

In the following examples we'll setup a reactive database dealing with users and their favorite colors. When denormalized the data looks like this:

```clojure
  {:user/id             1
   :user/name           "Pat"
   :user/favorite-color {:color/hex  "9C27B0"
                         :color/name "Purple"}
   :user/friends        [{:user/id             2
                          :user/name           "Reese"
                          :user/favorite-color {:color/hex  "D50000"
                                                :color/name "Red"}}]}
```


To get started we'll need re-frame, subgraph and the interop namespaces.

```clojure
(ns example
  (:require
   [re-frame.core :as re-frame]
   [vimsical.subgraph :as sg]
   [vimsical.subgraph.re-frame :as sg.re-frame]))
```



### Database initialization

In order to avoid duplication we want to normalize not only users, but colors as well, looking at our denormalized data we see that our identifying attributes are `:user/id` and `:color/hex`.

We can create a new empty database with `sg/new-db` but for normalization to work we'll have to configure it by adding our identifying attributes using `add-id-attr`.

```clojure
(defn new-db
  []
  (-> (sg/new-db)
      (sg/add-id-attr :user/id :color/hex)))
```

We're now able to produce a new empty db value, so we register a `re-frame` handler to initialize our app db, and dispatch it right away.

```clojure
(re-frame/reg-event-db ::new-db (constantly (new-db)))
```

```clojure
(re-frame/dispatch [::new-db])
```



### Adding entities

We can populate our database using `add` which accepts a variable number of entities. In order to be able to `add` data in reaction to user input in a component we'll need to invoke that function inside a `re-frame` event handler. The most generic of such handlers simply wraps `add`.

```clojure
(re-frame/reg-event-db
 ::add
 (fn [db [_ & entities]]
   (apply sg/add db entities)))
```

We can now dispatch `::add` events to populate our database. Note that nested entities are valid and will normalize recursively according to the attributes added with `add-id-attr`.

```clojure
(re-frame/dispatch
 [::add
  {:user/id             1
   :user/name           "Pat"
   :user/favorite-color {:color/hex  "9C27B0"
                         :color/name "Purple"}}
  {:user/id             2
   :user/name           "Reese"
   :user/favorite-color {:color/hex  "D50000"
                         :color/name "Red"}}])
```



### Subscriptions and pull queries

One of the design goals of SubGraph was to enable fully reactive pull queries against a (r)atom. Reactive pull queries return reactions that not only update with changes in that entity's pattern, but recursively for any joined entity.

`vimsical.subgraph.re-frame/pull` is an api-compatible version of `vimsical.subgraph/pull` designed to work with (r)atoms. For convenience we also provide a re-frame raw subscription handler `vimsical.subgraph.re-frame/raw-sub-handler`.

We register a generic subscription handler that we'll call `::q`.

```clojure
(re-frame/reg-sub-raw ::q sg.re-frame/raw-sub-handler)
```

This subscription accepts any pattern and lookup-ref, and will return a fully reactive reaction graph. 

```clojure
(deref
 (re-frame/subscribe
  [::q 
   ;; Pattern
   [:user/name {:user/favorite-color [:color/name]}]
   ;; Lookup ref
   [:user/id 2]]))

;; => #:user{:name "Reese", :favorite-color #:color{:name "Red"}}
```



### Updates

To update an entity we simply `add` it again to the database, the semantics are equivalent to that of `merge`. 

Let's create a cycle by updating Pat and Reese, making them friends by referencing each other.

```clojure
(re-frame/dispatch
 [::add
  {:user/id 1 :user/friends #{{:user/id 2}}}
  {:user/id 2 :user/friends #{{:user/id 1}}}])
```

Since queries can be arbitrarily nested, we can ask for Pat's friends' favorite colors.

```clojure
(deref
 (re-frame/subscribe
  [::q
   [:user/name
    {:user/friends
     [:user/name {:user/favorite-color [:color/name]}]}]
   [:user/id 1]]))

;; => #:user{:name "Pat", :friends #{#:user{:name "Reese", :favorite-color #:color{:name "Red"}}}}
```

And then update Reese's favorite color.

```clojure
(re-frame/dispatch
 [::add
  {:user/id             2
   :user/favorite-color {:color/hex  "1789d6"
                         :color/name "DodgerBlue3"}}])
```

Thanks to normalization and our reactive graph, our subscription for Pat's friends' favorite colors updates, showing Reese's new favorite color.

```clojure
(deref
 (re-frame/subscribe
  [::q
   [:user/name
    {:user/friends
     [:user/name {:user/favorite-color [:color/name]}]}]
   [:user/id 1]]))

;; => #:user{:name "Pat", :friends #{#:user{:name "Reese", :favorite-color #:color{:name "DodgerBlue3"}}}}
```



## Comparison with MapGraph

SubGraph builds on a fork of Stuart Sierra's [MapGraph](https://github.com/stuartsierra/mapgraph), the implementation diverged in order to add support for (r)atoms in the `pull` api.

The `vimsical.subgraph` namespace is api-compatible with `com.stuartsierra.mapgraph`, however SubGraph extends the pull query syntax with support for:

- Recursive join queries

Unbounded recursion should be used with caution since there is no mechanism to detect cycles. In our example of mutual friends, the following query would run infinitely .

```clojure
[:user/name
 {:user/favorite-color [:color/name]}
 {:user/friends '...}]
```

A number can be provided to limit the level of nesting.

```clojure
[:user/name
 {:user/favorite-color [:color/name]}
 {:user/friends 3}]
```



- Link references

Applications commonly need to keep track of global references, such as the current user or a selection. This is easily achieved by storing a lookup-ref as a value in the database, for example `{:app/user [:user/id 1]}`. 

SubGraph supports this pattern with a special query syntax identical to that of `om.next`.

```clojure
[{[:app/user '_] 
  [:user/name
    {:user/favorite-color [:color/name]}
    {:user/friends 3}]}]
```



## Comparison with om.next 


### Limitations

- SubGraph currently doesn't support union queries
- SubGraph's query parser is not extensible, as such there is no support for parametrized joins and mutations


### Differences

- SubGraph doesn't have an indexer and relies on reagent reactions to update components when data changes.
- Normalization is driven by the database's `id-attrs`, when adding entities no query, or component tree, is required.



## Comparison with Datomic/Datascript

Refer to the comparison in the [MapGraph Repo](https://github.com/stuartsierra/mapgraph#comparison-with-datomicdatascript)



## Bug reports

Please file issues on GitHub with minimal sample code that demonstrates the problem.



## Contributing

Pull requests are welcome!



## Special thanks to 

[Stuart Sierra](https://github.com/stuartsierra/mapgraph) for MapGraph

[David Nolen](https://github.com/swannodette) for ClojureScript and om.next
