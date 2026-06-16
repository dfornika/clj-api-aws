(ns clj-api.items
  (:require [cognitect.aws.client.api :as aws]
            [clj-api.db :as db]))

(defn create-item! [item]
  (aws/invoke @db/client {:op      :PutItem
                          :request {:TableName @db/table
                                    :Item      (db/->item item)}}))

(defn get-item [id]
  (let [res (aws/invoke @db/client {:op      :GetItem
                                    :request {:TableName @db/table
                                              :Key       {:id {:S id}}}})]
    (when-not (:cognitect.anomalies/category res)
      (some-> res :Item db/<-item))))

(defn list-items []
  (let [res (aws/invoke @db/client {:op      :Scan
                                    :request {:TableName @db/table}})]
    (when-not (:cognitect.anomalies/category res)
      (map db/<-item (:Items res)))))

(defn delete-item! [id]
  (aws/invoke @db/client {:op      :DeleteItem
                          :request {:TableName @db/table
                                    :Key       {:id {:S id}}}}))

(defn create-table! []
  (aws/invoke @db/client
              {:op      :CreateTable
               :request {:TableName            @db/table
                         :AttributeDefinitions [{:AttributeName "id" :AttributeType "S"}]
                         :KeySchema            [{:AttributeName "id" :KeyType "HASH"}]
                         :BillingMode          "PAY_PER_REQUEST"}}))
