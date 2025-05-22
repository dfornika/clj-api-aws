(ns user
  (:require [clj-reload.core :as reload]
            [portal.api :as p]
            [reitit.middleware :as reitit-middleware]
            [clj-api.middleware :as app-middleware]
            [dev-middleware]
            [clj-api.core :as core]))

(reload/init
  {:dirs ["src" "dev"]})

(comment
  (reload/reload)
  )

 
(defn add-middleware
  ""
  [m name]
  (swap! core/route-data update :middleware
         (fn [ms]
           (conj ms (reitit-middleware/map->Middleware {:name name :wrap m})))))

(defn remove-middleware
  ""
  [name]
  (swap! core/route-data update :middleware
         (fn [ms] (vec (filter #(not= name (:name %)) ms)))))


(defn reset-default-middleware
  ""
  []
  (swap! core/route-data assoc :middleware core/matched-route-middleware-stack))


(comment
  ;; Add/remove dev middleware
  (add-middleware dev-middleware/tap-request :dev-middleware/tap-request)
  (remove-middleware :dev-middleware/tap-request)
  
  (add-middleware dev-middleware/tap-response :dev-middleware/tap-response)
  (remove-middleware :dev-middleware/tap-response)

  
  (tap> (:middleware @core/route-data))
  (reset-default-middleware)
  )


(comment
  ;; Portal setup
  (def p (p/open))
  (add-tap #'p/submit)

  (p/clear)
  (remove-tap #'p/submit)
  (p/close)
  )

(comment
  ;; Starting/restarting the web server
  @core/server
  (core/start-server!)
  (core/restart-server!)
  )


