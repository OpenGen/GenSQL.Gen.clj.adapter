# GenSQL.Gen.clj.adapter

A simple adapter to query any Gen.clj model from GenSQL.query. It works by
ensuring that gen model implements the gpm protocol functions that GenSQL.query
is expecting (so in a sense, it's an adapter of Gen.clj to the GPM protocol).

## How can we ensure conditioning works as expected?

We resort to Gen.clj's importance sampling capabilities to approximate
`gpm/simulate` and `gpm/logpdf`.

# Usage example

Below is a usage example from Clojure.

```clojure
(ns foo
  ;; Import our adapter for gen programs in IQL.
  (:require [adapter :refer [gen->iql]]
    ;; Import gen dependencies to write your gen program.
    [gen.distribution.commons-math :as dist]
    [gen.dynamic :as dynamic :refer [gen]]
    ;; The next two requirements are not a dependency of this library.
    ;; You'll have to manually add them to run this example.
    [inferenceql.query.permissive :as permissive]
    [inferenceql.query.db :as db]))

;; initialize default inference with 1,000 importance samples (default)
(gen->iql)

;; initialize default inference with 10,000 importance samples.
(gen->iql 1E5)

(def gen-model
  (with-meta ;; users have to specify all variables in their gen model that will be queried.
    (gen
      []
      (if (dynamic/trace! "foo" dist/bernoulli 0.3)
        (dynamic/trace! "bar" dist/bernoulli 0.1)
        (dynamic/trace! "bar" dist/bernoulli 0.9)))
    {:variables #{"foo" "bar"}}))

(def database (-> (db/empty)
                  (db/with-model :model gen-model)
                  atom))

(prn (permissive/query "SELECT * FROM GENERATE * UNDER model LIMIT 3" database))
```

# Testing

To test, run
```shell
clojure -X:test
```
