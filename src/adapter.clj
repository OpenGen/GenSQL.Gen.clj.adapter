(ns adapter
  (:require
    [gen.dynamic :as dynamic]
    [gen.dynamic.choice-map :as choice-map]
    [gen.generative-function :as gf]
    [gen.inference.importance :as importance]
    [gen.trace :as trace]
    [inferenceql.inference.gpm.conditioned :as conditioned]
    [inferenceql.inference.gpm.proto :as gpm.proto])
  (:import [gen.dynamic DynamicDSLFunction]))

(def ^:dynamic *default-n-samples* 1E4)

(defn gen->iql
  ([]
   (gen->iql *default-n-samples*))
  ([n-samples]
   (extend-type DynamicDSLFunction
     gpm.proto/GPM
     (simulate [gf targets constraints]
       (let [targets (map name targets)
             constraints (update-keys constraints name)
             ]
         (-> (importance/resampling gf [] (choice-map/choice-map constraints) n-samples)
             (:trace)
             (trace/choices)
             (choice-map/unwrap)
             (select-keys targets))))

     (logpdf [gf targets constraints]
       (let [targets (update-keys targets name)
             constraints (update-keys constraints name)
             log-marginal-likelihood #(-> (importance/resampling gf [] (choice-map/choice-map %) n-samples)
                                          (:weight))]
         (if (empty? constraints)
           (log-marginal-likelihood targets)
           (- (log-marginal-likelihood (merge targets constraints))
              (log-marginal-likelihood constraints)))))

     gpm.proto/Condition
     (condition [gf conditions]
       (conditioned/condition gf conditions))

     gpm.proto/Variables
     (variables [gf]
       (:variables (meta gf))))))
