(ns dev-middleware
  (:require [clojure.pprint :refer [pprint]]))

(defn tap-middleware
  ""
  [handler]
  (fn [req]
    (tap> req)
    (handler req)))

(defn pprint-middleware
  ""
  [handler]
  (fn [req]
    (pprint req)
    (handler req)))
