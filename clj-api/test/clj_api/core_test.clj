(ns clj-api.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [ring.mock.request :as mock]
            [jsonista.core :as json]
            [clj-api.core :refer [app]]))

(def ^:private json-mapper
  (json/object-mapper {:decode-key-fn keyword}))

(defn- parse-body [response]
  (some-> response :body slurp (json/read-value json-mapper)))

(defn- json-post [path body]
  (app (-> (mock/request :post path)
           (mock/content-type "application/json")
           (mock/body (json/write-value-as-string body)))))

(deftest root-returns-200
  (let [response (app (mock/request :get "/"))]
    (is (= 200 (:status response)))))

(deftest not-found-returns-404
  (let [response (app (mock/request :get "/no-such-route"))]
    (is (= 404 (:status response)))))

(deftest health-returns-expected-shape
  (let [response (app (mock/request :get "/health"))
        body     (parse-body response)]
    (is (= 200 (:status response)))
    (is (= "up" (:status body)))
    (is (string? (:time body)))
    (is (string? (:up-since body)))
    (is (string? (:message body)))))

(deftest greet-with-name
  (let [response (json-post "/greet" {:name "Alice"})
        body     (parse-body response)]
    (is (= 200 (:status response)))
    (is (= "Hello, Alice!" (:greeting body)))))

(deftest greet-defaults-to-world
  (let [response (json-post "/greet" {})
        body     (parse-body response)]
    (is (= 200 (:status response)))
    (is (= "Hello, World!" (:greeting body)))))

(deftest greet-rejects-non-string-name
  (testing "name must be a string — coercion returns 400"
    (let [response (json-post "/greet" {:name 42})]
      (is (= 400 (:status response))))))

(deftest echo-returns-request-info
  (let [response (app (mock/request :get "/echo"))
        body     (parse-body response)]
    (is (= 200 (:status response)))
    (is (= "/echo" (:uri body)))
    (is (= "get" (:request-method body)))))

(deftest response-includes-generated-request-id
  (let [response (app (mock/request :get "/health"))]
    (is (some? (get-in response [:headers "x-request-id"])))))

(deftest response-echoes-provided-request-id
  (let [response (app (-> (mock/request :get "/health")
                          (mock/header "x-request-id" "my-trace-id")))]
    (is (= "my-trace-id" (get-in response [:headers "x-request-id"])))))
