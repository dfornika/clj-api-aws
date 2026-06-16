(ns clj-api.core
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [aero.core :as aero]
   [ring.adapter.jetty :as jetty]
   [reitit.ring :as reitit-ring]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.exception :as exception]
   [reitit.ring.coercion :as ring-coercion]
   [reitit.coercion.malli]
   [muuntaja.core :as m]
   [reitit.openapi :as openapi]
   [taoensso.telemere :as tel]
   [taoensso.telemere.tools-logging :as tel-tl]
   [jsonista.core :as json]
   [clj-api.cli :as cli]
   [clj-api.db :as db]
   [clj-api.items :as items]
   [clj-api.middleware :as middleware])
  (:import
   [java.time Instant]
   [java.lang.management ManagementFactory])
  (:gen-class))

;; Can we compile with GraalVM?
(set! *warn-on-reflection* true)

;; log uncaught exceptions in threads
;; lifted from kit template
(Thread/setDefaultUncaughtExceptionHandler
 (fn [^Thread thread ex]
   (tel/log! :error {:what :uncaught-exception
                     :exception ex
                     :where (str "Uncaught exception on " (.getName thread))})))

;; Minimal component-like hot-reloading of
;; web server. Consider replacing with
;; component, integrant or other
(defonce config (atom {}))
(defonce server (atom nil))

(defn setup-logging! []
  (tel-tl/tools-logging->telemere!)
  (tel/remove-handler! :default/console)
  (if (= (System/getenv "APP_ENV") "dev")
    (tel/add-handler! :console (tel/handler:console {}))
    (tel/add-handler! :console
                      (tel/handler:console
                       {:output-fn (fn [{:keys [level ctx inst ns] :as signal}]
                                     (json/write-value-as-string
                                      {:level (name level)
                                       :ts    (str inst)
                                       :ns    ns
                                       :msg   (force (:msg_ signal))
                                       :ctx   ctx}))}))))

(setup-logging!)

(defn load-config!
  ([]
   (load-config! (io/resource "config.edn")))
  ([config-source]
   (let [profile (keyword (or (System/getenv "APP_ENV") "default"))
         new-config (aero/read-config config-source {:profile profile})]
     (reset! config new-config))))

(defn root-handler
  "Send an empty response"
  [_request]
  {:status 200
   :headers {}
   :body ""})

(defn echo-handler
  "Just echo the request map, excluding some difficult-to-serialize values"
  [request]
  (let [response-body (-> request
                          (dissoc :reitit.core/router)
                          (dissoc :reitit.core/match))]
    {:status 200
     :body response-body}))

(defn health-check-handler
  "Send a summary of system status."
  [_request]
  (let [now        (Instant/now)
        start-time (Instant/ofEpochMilli (.getStartTime (ManagementFactory/getRuntimeMXBean)))]
    {:status 200
     :body {:time     (str now)
            :up-since (str start-time)
            :status   "up"
            :message  "Hello from Clojure!"}}))

(defn greet-handler
  "Respond with a greeting, taking the name from a json-formatted request body."
  [request]
  (let [name (get-in request [:body-params :name] "World")]
    {:status 200
     :body {:greeting (str "Hello, " name "!")}}))

(defn list-items-handler [_request]
  {:status 200
   :body {:items (items/list-items)}})

(defn create-item-handler [request]
  (let [{:keys [title content]} (:body-params request)
        id   (str (java.util.UUID/randomUUID))
        now  (str (Instant/now))
        item (cond-> {:id id :title title :created-at now}
               content (assoc :content content))]
    (items/create-item! item)
    {:status 201 :body item}))

(defn get-item-handler [request]
  (let [id   (get-in request [:path-params :id])
        item (items/get-item id)]
    (if item
      {:status 200 :body item}
      {:status 404 :body {:error "not found"}})))

(defn delete-item-handler [request]
  (let [id (get-in request [:path-params :id])]
    (items/delete-item! id)
    {:status 204 :body nil}))

(def item-schema
  [:map
   [:id :string]
   [:title :string]
   [:created-at :string]
   [:content {:optional true} :string]])

(def routes
  [["/" {:get {:summary "Root"
               :handler #'root-handler}}]
   ["/openapi.json"
    {:get {:no-doc true
           :openapi {:info {:title "clj-api"
                            :description "openapi3 docs with malli and reitit-ring"
                            :version "0.1.0"}}
           :handler (openapi/create-openapi-handler)}}]
   ["/echo" {:get {:summary "Echo request map"
                   :handler #'echo-handler}}]
   ["/health" {:get {:summary "Health check"
                     :responses {200 {:body [:map
                                             [:time :string]
                                             [:up-since :string]
                                             [:status :string]
                                             [:message :string]]}}
                     :handler #'health-check-handler}}]
   ["/greet" {:post {:summary "Greet by name"
                     :parameters {:body [:map [:name {:optional true} :string]]}
                     :responses {200 {:body [:map [:greeting :string]]}}
                     :handler #'greet-handler}}]
   ["/items" {:get  {:summary "List items"
                     :responses {200 {:body [:map [:items [:vector item-schema]]]}}
                     :handler #'list-items-handler}
              :post {:summary "Create item"
                     :parameters {:body [:map [:title :string] [:content {:optional true} :string]]}
                     :responses {201 {:body item-schema}}
                     :handler #'create-item-handler}}]
   ["/items/:id" {:get    {:summary "Get item by id"
                           :responses {200 {:body item-schema}}
                           :handler #'get-item-handler}
                  :delete {:summary "Delete item by id"
                           :handler #'delete-item-handler}}]])

(def matched-route-middleware-stack
  [muuntaja/format-negotiate-middleware
   muuntaja/format-response-middleware
   exception/exception-middleware
   muuntaja/format-request-middleware
   ring-coercion/coerce-request-middleware
   ring-coercion/coerce-response-middleware])

(defonce route-data (atom {:muuntaja m/instance
                           :coercion reitit.coercion.malli/coercion
                           :middleware matched-route-middleware-stack}))

(def router
  (reitit-ring/router routes {:data @route-data}))

(def default-middleware-stack
  [middleware/wrap-correlation-id
   middleware/wrap-request-logging])

(def app
  (reitit-ring/ring-handler
   router
   (reitit-ring/create-default-handler
    {:not-found (constantly {:status 404 :body "" :headers {}})
     :method-not-allowed (constantly {:status 405 :body "" :headers {}})
     :not-acceptable (constantly {:status 406 :body "" :headers {}})})
   {:middleware default-middleware-stack}))

(defn start-server!
  ([]
   (start-server! 8080 "0.0.0.0"))
  ([port]
   (start-server! port "0.0.0.0"))
  ([port host]
   (tel/log! :info (str "Starting server on " host ":" port))
   (reset! server (jetty/run-jetty #'app {:port port :host host :join? false}))))

(defn stop-server!
  []
  (when-some [^org.eclipse.jetty.server.Server s @server]
    (.stop s)
    (reset! server nil)))

(defn restart-server!
  []
  (stop-server!)
  (start-server!))

(defn -main
  [& args]
  (let [opts (cli/parse-args args)]

    (when (seq (:errors opts))
      (cli/exit 1 (str/join \newline (:errors opts))))

    (when (get-in opts [:options :help])
      (let [options-summary (:summary opts)]
        (cli/exit 0 (cli/usage options-summary))))

    (if-let [config-path (get-in opts [:options :config])]
      (load-config! config-path)
      (load-config!))

    (db/init-db! (:dynamodb @config))

    (let [port (get-in @config [:server :port] 8080)
          host (get-in @config [:server :host] "0.0.0.0")]
      (start-server! port host))

    (.addShutdownHook (Runtime/getRuntime) (Thread. (fn [] (stop-server!) (shutdown-agents))))))
