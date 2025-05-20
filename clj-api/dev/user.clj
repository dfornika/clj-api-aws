(ns user
  (:require [clj-api.core :as core]
            [portal.api :as p]
            [clj-api.middleware :as app-middleware]
            [dev-middleware]
            [reitit.middleware :as reitit-middleware]))


(defn add-middleware
  ""
  [m name]
  (swap! core/route-data update :middleware conj (reitit-middleware/map->Middleware {:name name
                                                                                     :wrap m})))

(defn remove-middleware
  ""
  [name]
  (swap! core/route-data update :middleware
         (fn [ms] (vec (filter #(not= name (:name %)) ms)))))

(comment
  (add-middleware dev-middleware/tap-middleware :dev-middleware/tap-middleware)
  (remove-middleware :dev-middleware/tap-middleware)

  (add-middleware dev-middleware/pprint-middleware :dev-middleware/pprint-middleware)
  (remove-middleware :dev-middleware/pprint-middleware)
  
  (tap> (:middleware @core/route-data))
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
  @core/server
  (core/start-server!)
  (core/restart-server!)
  )


