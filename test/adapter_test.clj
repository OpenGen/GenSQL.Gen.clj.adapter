(ns adapter-test
  (:require [adapter :refer [gen->iql]]
            [clojure.test :refer [deftest is]]
            [gen.distribution.commons-math :as dist]
            [inferenceql.inference.gpm :as gpm]
            [inferenceql.inference.gpm.proto :as gpm.proto]
            [gen.dynamic :as dynamic :refer [gen]]))

(gen->iql)

(def test-model-almost-deterministic
  (with-meta
    (gen
      []
      (if (dynamic/trace! "foo" dist/bernoulli 0.5)
        (dynamic/trace! "bar" dist/bernoulli 0.01)
        (dynamic/trace! "bar" dist/bernoulli 0.99)))
    {:variables #{"foo" "bar"}}))

(deftest variables
  (is (= #{"foo" "bar"} (gpm/variables test-model-almost-deterministic))))

(deftest simulate-smoke
  (is (map? (gpm/simulate test-model-almost-deterministic ["foo" "bar"] {})))
  (is (= #{"foo" "bar"} (set (keys (gpm/simulate test-model-almost-deterministic ["foo" "bar"] {})))))
  (is (= #{"foo" "bar"} (set (keys (gpm/simulate test-model-almost-deterministic ["foo" "bar"] {"baz" 42}))))))

(deftest simulate-identity
  (is (= true
         (get (gpm/simulate test-model-almost-deterministic ["foo" "bar"] {"foo" true}) "foo")))
  (is (= false
         (get (gpm/simulate test-model-almost-deterministic ["foo" "bar"] {"foo" false}) "foo")))
  (is (= true
         (get (gpm/simulate test-model-almost-deterministic ["foo" "bar"] {"bar" true}) "bar"))))

(def num-sims 50)

(deftest simulate-conditional
  ;; very rough test for conditional sampling
  (is (< (* num-sims 0.8)
         (get (frequencies (repeatedly num-sims #(get (gpm/simulate test-model-almost-deterministic
                                                                    ["foo"]
                                                                    {"bar" true}) "foo")))
              false)))
  (is (< (* num-sims 0.8)
         (get (frequencies (repeatedly num-sims #(get (gpm/simulate test-model-almost-deterministic
                                                                    ["foo"]
                                                                    {"bar" false}) "foo")))
              true))))

(def test-model
  (with-meta
    (gen
      []
      (if (dynamic/trace! "foo" dist/bernoulli 0.2)
        (dynamic/trace! "bar" dist/bernoulli 0.3)
        (dynamic/trace! "bar" dist/bernoulli 0.7)))
    {:variables #{"foo" "bar"}}))

;; ideally, someone should do the math to actual hints about how big this error
;; should be in light of n-samples used for importance sampling and do repeated
;; experiments.
(def error 0.1)

(defn almost-equal
  [x y]
  (> error (clojure.core/abs (- x y))))

(deftest logpdf
  (let [x-and-y (gpm/logpdf test-model {"foo" true "bar" false} {})
        x-given-y (gpm/logpdf test-model {"foo" true} {"bar" false})
        y (gpm/logpdf test-model {"bar" false} {}) ]
    (is (number? x-and-y))
    (is (number? x-given-y))
    (is (number? y))
    ;; Test Bayes' Rule approximation.
    (is (almost-equal (+ x-given-y y) x-and-y))))

(deftest condition
  (let [x-and-y (gpm/logpdf test-model {"foo" true "bar" false} {})
        x-given-y (gpm/logpdf (gpm/condition test-model {"bar" false}) {"foo" true} {})
        y (gpm/logpdf test-model {"bar" false} {}) ]
    (is (number? x-and-y))
    (is (number? x-given-y))
    (is (number? y))
    ;; Test Bayes' Rule approximation.
    (is (almost-equal (+ x-given-y y) x-and-y))))
