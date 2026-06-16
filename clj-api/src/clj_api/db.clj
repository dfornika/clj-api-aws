(ns clj-api.db
  (:require [cognitect.aws.client.api :as aws]
            [clojure.string :as str]))

(defonce client (atom nil))
(defonce table  (atom nil))

(defn- parse-endpoint [url]
  (let [[protocol remainder] (str/split url #"://")
        [host port]          (str/split remainder #":")]
    {:protocol (keyword protocol)
     :hostname host
     :port     (Integer/parseInt port)}))

(defn init-db! [{:keys [table-name endpoint region]}]
  (let [cfg (cond-> {:api :dynamodb :region region}
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
