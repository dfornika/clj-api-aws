(ns clj-api.cli
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defn usage
  ""
  [options-summary]
  (->> [""
        "Usage: java -jar app.jar OPTIONS"
        ""
        "Options:"
        options-summary]
       (str/join \newline)))


(def options
  [["-c" "--config CONFIG_FILE" "Config file"
    :validate [#(.exists (io/as-file %)) #(str "Config file '" % "' does not exist.")]]
   ["-h" "--help"]
   ["-v" "--version"]])

(defn exit
  "Exit the program with status code and message"
  [status msg]
  (println msg)
  (System/exit status))
