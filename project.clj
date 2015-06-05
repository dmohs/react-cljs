(def build-dir (or (System/getenv "BUILD_DIR") (throw (Exception. "BUILD_DIR is not defined"))))


(defproject dmohs/react "0.1.1.1"
  :description "A ClojureScript wrapper for React."
  :license "http://opensource.org/licenses/MIT"
  :url "https://github.com/dmohs/react-cljs"
  :dependencies [
                 [cljsjs/react "0.13.3-0"]
                 ;; [org.clojure/clojure "1.6.0"]
                 ;; [org.clojure/clojurescript "0.0-3211"]
                 ]
  ;; :plugins [[lein-cljsbuild "1.0.5"]]
  ;; :hooks [leiningen.cljsbuild]
  :source-paths ["src"]
  :target-path ~(str build-dir "/target/%s/")
  :clean-targets ^{:protect false} [:target-path]
  ;; :cljsbuild
  ;; {:builds []}
  ;;  #_{:main
  ;;   {:source-paths ["src/cljs"]
  ;;    :compiler {:output-dir ~(str build-dir "/build")
  ;;               :output-to ~(str build-dir "/dmohs-react.min.js")}}}
   )
