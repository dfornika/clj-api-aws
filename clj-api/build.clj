(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'clj-api) ; Group-id/artifact-id
(def version "0.1.0-SNAPSHOT")
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def uber-file (format "target/app.jar" (name lib) version))

(defn clean [_]
  (println "Cleaning target...")
  (b/delete {:path "target"}))

(defn uber [_]
  (clean nil)
  (println (str "Copying sources to " class-dir "..."))
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (println (str "Compiling " (str lib) "..."))
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir
                  :ns-compile ['clj-api.core]})
  (println (str "Building uberjar: " uber-file "..."))
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis basis
           :main 'clj-api.core}))

(defn -main [task & _args]
  (case task
    "uber" (uber nil)
    (println "Unknown task:" task)))
