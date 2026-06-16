(ns clj-api.db
  (:require [cognitect.aws.client.api :as aws]
            [clojure.string :as str]))

(defonce client (atom nil))
(defonce table  (atom nil))

(defn- parse-endpoint [url]
  (let [uri (java.net.URI. url)]
    {:protocol (keyword (.getScheme uri))
     :hostname (.getHost uri)
     :port     (.getPort uri)}))

(defn init-db! [{:keys [table-name endpoint region]}]
  (when (str/blank? table-name)
    (throw (IllegalArgumentException. "table-name must not be blank")))
  (let [cfg (cond-> {:api :dynamodb}
              region   (assoc :region region)
              endpoint (assoc :endpoint-override (parse-endpoint endpoint)))]
    (reset! client (aws/client cfg))
    (reset! table table-name)))

(defn ->attr [v]
  (cond (string? v) {:S v}
        (number? v) {:N (str v)}
        :else       {:S (str v)}))

(defn <-attr [{:keys [S N]}]
  (or S (some-> N parse-long)))

(defn ->item [m] (update-vals m ->attr))
(defn <-item [m] (update-vals m <-attr))
