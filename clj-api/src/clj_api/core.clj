(ns clj-api.core
  (:require
   [ring.adapter.jetty :as jetty]
   [reitit.ring :as reitit-ring]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [muuntaja.core :as m]
   [environ.core :refer [env]]
   [clojure.tools.logging :as log]
   [clj-api.middleware :as middleware])
  (:gen-class))

(defonce server (atom nil))

(defn health-check-handler [_request]
  ;; Now we can return a Clojure map directly
  {:status 200
   :body {:status "ok" :message "Hello from Clojure!"}})

(defn greet-handler [request]
  (let [name (get-in request [:body-params :name] "World")]
    {:status 200
     :body {:greeting (str "Hello, " name "!")}}))

(def routes
  [["/health" {:get health-check-handler}]
   ["/greet" {:post {:handler greet-handler}}]])

(defonce route-data (atom {:muuntaja m/instance
                           :middleware [muuntaja/format-negotiate-middleware
                                        muuntaja/format-response-middleware
                                        muuntaja/format-request-middleware]}))

(def app-routes
  (reitit-ring/router routes {:data @route-data}))

(def app (reitit-ring/ring-handler app-routes))


(defn start-server!
  ""
  []
  (let [port (Integer/parseInt (env :port "8080"))]
    (log/info (str "Starting server on port " port))
    (reset! server (jetty/run-jetty #'app {:port port :join? false}))))


(defn stop-server!
  "" 
  []
  (when-some [s @server] ;; check if there is an object in the atom
    (.stop s)            ;; call the .stop method
    (reset! server nil)));; overwrite the atom with nil


(defn restart-server!
  ""
  []
  (stop-server!)
  (start-server!))

(defn -main
  "" 
  [& _args]
  (start-server!))
