(ns vimsical.test-runner
  (:require
   [doo.runner :refer-macros [doo-all-tests doo-tests]]
   [vimsical.subgraph-test]))

(doo-tests 'vimsical.subgraph-test)
