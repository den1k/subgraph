(ns vimsical.bench-runner
  (:require
   [vimsical.subgraph.compare :as compare]
   [vimsical.subgraph.bench.re-frame.breadth :as re-frame.breadth]
   [vimsical.subgraph.bench.re-frame.depth :as re-frame.depth]))

(compare/bench)
(re-frame.depth/bench)
(re-frame.breadth/bench)
