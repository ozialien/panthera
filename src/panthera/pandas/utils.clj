(ns panthera.pandas.utils
  (:require
    [libpython-clj.python :as py]
    [camel-snake-kebab.core :as csk]
    [camel-snake-kebab.extras :as cske]
    [clojure.core.memoize :as m]))

(py/initialize!)

(defonce builtins (py/import-module "builtins"))
(defonce pd (py/import-module "pandas"))

(defn slice
  ([]
   (py/call-attr builtins "slice" nil))
  ([start]
   (py/call-attr builtins "slice" start))
  ([start stop]
   (py/call-attr builtins "slice" start stop))
  ([start stop incr]
   (py/call-attr builtins "slice" start stop incr)))

(defn pytype
  [obj]
  (py/python-type obj))

(def memo-key-converter
  (m/fifo csk/->snake_case_string {} :fifo/threshold 512))

(def memo-columns-converter
  (m/fifo #(if (number? %)
             %
             (csk/->kebab-case-keyword %)) {} :fifo/threshold 512))

(defn vec->pylist
  [v]
  (py/->py-list v))

(defn vals->pylist
  [obj]
  (if (vector? obj)
    (py/->py-list obj)
    obj))

(defn keys->pyargs
  [m]
  (let [nm (reduce-kv 
            (fn [m k v] 
              (assoc m k (vals->pylist v))) 
            {} m)]
    (cske/transform-keys memo-key-converter nm)))

(defn ->clj
  [df-or-srs]
  (let [v  (py/get-attr df-or-srs "values")
        tp ({:data-frame "columns"
             :series     "name"} (pytype df-or-srs))
        ks (py/get-attr df-or-srs tp)]
    (if (= tp "columns")
      (map #(zipmap
              (map memo-columns-converter (vec ks)) %) v)
      (map #(hash-map
              (memo-columns-converter (or ks :unnamed)) %) v))))

(defn simple-kw-call
  [df kw & [attrs]]
  (py/call-attr-kw df kw []
                   (keys->pyargs attrs)))

(defn kw-call
  [df kw pos & [attrs]]
  (py/call-attr-kw df kw [pos]
                   (keys->pyargs attrs)))

(defn series?
  [obj]
  (identical? :series (pytype obj)))

(defn data-frame?
  [obj]
  (identical? :data-frame (pytype obj)))