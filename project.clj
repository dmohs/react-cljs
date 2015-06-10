(def build-dir (or (System/getenv "BUILD_DIR") (throw (Exception. "BUILD_DIR is not defined"))))


(defproject dmohs/react "0.2.1"
  :description "A ClojureScript wrapper for React."
  :license "http://opensource.org/licenses/MIT"
  :url "https://github.com/dmohs/react-cljs"
  :dependencies [[cljsjs/react "0.13.3-0"]]
  :source-paths ["src"]
  :target-path ~(str build-dir "/target/%s/")
  :clean-targets ^{:protect false} [:target-path]
  )
