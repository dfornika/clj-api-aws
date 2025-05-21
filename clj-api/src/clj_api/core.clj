(ns clj-api.core
  (:require
   [ring.adapter.jetty :as jetty]
   [reitit.ring :as reitit-ring]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.exception :as exception]
   [muuntaja.core :as m]
   [muuntaja.middleware :refer [wrap-format-response]]
   [reitit.openapi :as openapi]
   [environ.core :refer [env]]
   [clojure.tools.logging :as log]
   [clj-api.middleware :as middleware])
  (:gen-class))


(defonce server (atom nil))


(defn root-handler
  "Send an empty response"
  [request]
  {:status 200
   :headers {}
   :body ""})


(defn echo-handler
  "Just echo the request map, excluding some difficult-to-serialize values"
   [request]
  (let [response-body (-> request
                          #_(dissoc :body)
                          #_(dissoc :muuntaja/request)
                          #_(dissoc :muuntaja/response)
                          (dissoc :reitit.core/router)
                          (dissoc :reitit.core/match))]
      {:status 200
       :body response-body}))


(defn health-check-handler
  ""
  [_request]
  {:status 200
   :body {:status "ok" :message "Hello from Clojure!"}})


(defn greet-handler
  ""
  [request]
  (let [name (get-in request [:body-params :name] "World")]
    {:status 200
     :body {:greeting (str "Hello, " name "!")}}))


(def routes
  [["/" {:get {:handler root-handler}}]
   ["/openapi.json"
        {:get {:no-doc true
               :openapi {:info {:title "clj-api"
                                :description "openapi3 docs with [malli](https://github.com/metosin/malli) and reitit-ring"
                                :version "0.1.0"}}
               :handler (openapi/create-openapi-handler)}}]
   ["/echo" {:get {:handler echo-handler}}]
   ["/health" {:get {:handler health-check-handler}}]
   ["/greet" {:post {:handler greet-handler}}]])


(def matched-route-middleware-stack
  [muuntaja/format-negotiate-middleware
   muuntaja/format-response-middleware
   exception/exception-middleware
   muuntaja/format-request-middleware])


(defonce route-data (atom {:muuntaja m/instance
                           :middleware matched-route-middleware-stack}))


(def router
  (reitit-ring/router routes {:data @route-data}))


(def default-middleware-stack
  [muuntaja/format-negotiate-middleware
   muuntaja/format-response-middleware
   muuntaja/format-request-middleware
   exception/exception-middleware])


(def app
  (reitit-ring/ring-handler
   router
   (reitit-ring/create-default-handler
    {:not-found (constantly {:status 404 :body "" :headers {}})
     :method-not-allowed (constantly {:status 405 :body "" :headers {}})
     :not-acceptable (constantly {:status 406 :body "" :headers {}})})
   {:middleware default-middleware-stack}))


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
