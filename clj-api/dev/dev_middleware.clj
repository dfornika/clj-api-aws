(ns dev-middleware
  (:require [clojure.pprint :refer [pprint]]
            [ring.util.request]))


(defn tap-request
  "Tap the request map"
  [handler]
  (fn [request]
    (tap> request)
      (handler request)))


(defn tap-response
  "Tap the response map"
  [handler]
  (fn [request]
    (let [response (handler request)]
      (tap> response)
      response)))

(defn pprint-request
  "pprint the request map"
  [handler]
  (fn [request]
    (pprint request)
    (handler request)))

(defn pprint-response
  "pprint the response map"
  [handler]
  (fn [request]
    (let [response (handler request)]
      (pprint response))))
