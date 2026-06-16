(ns clj-api.db
  (:require [cognitect.aws.client.api :as aws]
            [clojure.string :as str]))

(defonce ^:private client (atom nil))
(defonce ^:private table  (atom nil))

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

(defn- ->attr [v]
  (cond (string? v) {:S v}
        (number? v) {:N (str v)}
        :else       {:S (str v)}))

(defn- <-attr [{:keys [S N]}]
  (or S (some-> N parse-long)))

(defn- ->item [m] (update-vals m ->attr))
(defn- <-item [m] (update-vals m <-attr))

(defn create-item! [item]
  (aws/invoke @client {:op      :PutItem
                       :request {:TableName @table
                                 :Item      (->item item)}}))

(defn get-item [id]
  (let [res (aws/invoke @client {:op      :GetItem
                                 :request {:TableName @table
                                           :Key       {:id {:S id}}}})]
    (when-not (:cognitect.anomalies/category res)
      (some-> res :Item <-item))))

(defn list-items []
  (let [res (aws/invoke @client {:op      :Scan
                                 :request {:TableName @table}})]
    (when-not (:cognitect.anomalies/category res)
      (map <-item (:Items res)))))

(defn delete-item! [id]
  (aws/invoke @client {:op      :DeleteItem
                       :request {:TableName @table
                                 :Key       {:id {:S id}}}}))

(defn create-table! []
  (aws/invoke @client
              {:op      :CreateTable
               :request {:TableName            @table
                         :AttributeDefinitions [{:AttributeName "id" :AttributeType "S"}]
                         :KeySchema            [{:AttributeName "id" :KeyType "HASH"}]
                         :BillingMode          "PAY_PER_REQUEST"}}))
