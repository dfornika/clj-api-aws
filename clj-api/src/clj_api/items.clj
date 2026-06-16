(ns clj-api.items
  (:require [cognitect.aws.client.api :as aws]
            [clj-api.db :as db]))

(defn- check-anomaly! [op res]
  (when-let [category (:cognitect.anomalies/category res)]
    (let [msg (or (:cognitect.anomalies/message res) (str category))]
      (throw (ex-info (str "DynamoDB " (name op) " failed: " msg)
                      {:op op :category (name category) :message msg}))))
  res)

(defn create-item! [item]
  (check-anomaly! :PutItem
                  (aws/invoke @db/client {:op      :PutItem
                                          :request {:TableName @db/table
                                                    :Item      (db/->item item)}})))

(defn get-item [id]
  (let [res (check-anomaly! :GetItem
                            (aws/invoke @db/client {:op      :GetItem
                                                    :request {:TableName @db/table
                                                              :Key       {:id {:S id}}}}))]
    (some-> res :Item db/<-item)))

(defn list-items []
  (let [res (check-anomaly! :Scan
                            (aws/invoke @db/client {:op      :Scan
                                                    :request {:TableName @db/table}}))]
    (into [] (map db/<-item) (:Items res))))

(defn delete-item! [id]
  (check-anomaly! :DeleteItem
                  (aws/invoke @db/client {:op      :DeleteItem
                                          :request {:TableName @db/table
                                                    :Key       {:id {:S id}}}})))
