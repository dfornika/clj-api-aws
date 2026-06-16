(ns clj-api.middleware
  (:require [taoensso.telemere :as tel]))

(defn wrap-correlation-id
  [handler]
  (fn [request]
    (let [request-id (or (get-in request [:headers "x-request-id"])
                         (str (java.util.UUID/randomUUID)))]
      (tel/with-ctx+ {:request-id request-id}
        (-> (handler (assoc request :request-id request-id))
            (assoc-in [:headers "x-request-id"] request-id))))))

(defn wrap-request-logging
  [handler]
  (fn [request]
    (let [start    (System/currentTimeMillis)
          method   (-> request :request-method name .toUpperCase)
          uri      (:uri request)
          response (handler request)
          elapsed  (- (System/currentTimeMillis) start)]
      (tel/log! :info (str method " " uri " " (:status response) " " elapsed "ms"))
      response)))
