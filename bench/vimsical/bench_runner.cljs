(ns vimsical.bench-runner
  (:require
   [vimsical.subgraph.compare :as compare]
   [vimsical.subgraph.bench.re-frame :as re-frame]))

(compare/bench)
(re-frame/bench)
