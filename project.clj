(defproject dmohs/react "1.3.0" ;; Also update README.md on version change.
  :description "A ClojureScript wrapper for React."
  :license "http://opensource.org/licenses/MIT"
  :url "https://github.com/dmohs/react-cljs"
  :source-paths ["src/main/cljs"]
  :profiles {:unbundled
             {:source-paths ["src/unbundled/cljs"]
              :cljsbuild
              {:builds
               {:client
                {:source-paths ["src/unbundled/cljs"]}}}}
             :bundled
             {:source-paths ["src/bundled/cljs"]
              :cljsbuild
              {:builds
               {:client
                {:source-paths ["src/bundled/cljs"]
                 :dependencies [[cljsjs/create-react-class "15.5.3-0"]
                                [cljsjs/react-dom "15.5.4-1"] ; react-dom depends on react
                                ]}}}}
             :ui-base
             {:plugins [[lein-cljsbuild "1.1.6"] [lein-figwheel "0.5.10"]]
              :dependencies [[binaryage/devtools "0.9.4"]
                             [org.clojure/clojure "1.8.0"]
                             [org.clojure/clojurescript "1.9.562"]]
              :target-path "resources/public/target"
              :clean-targets ^{:protect false} ["resources/public/target"]
              :cljsbuild
              {:builds
               {:client
                {:source-paths ["src/test/cljs"]
                 :compiler
                 {:main "webui.main"
                  :output-dir "resources/public/target/build"
                  :output-to "resources/public/target/compiled.js"
                  :asset-path "target/build"}
                 :figwheel true}}}}
             :ui
             [:ui-base
              {:cljsbuild
               {:builds
                {:client
                 {:compiler
                  {:optimizations :none
                   :source-map true
                   :source-map-timestamp true
                   :preloads [devtools.preload]
                   :external-config {:devtools/config
                                     {:features-to-install [:formatters :hints]}}}}}}}]
             :docs
             [:ui-base
              {:cljsbuild {:builds {:client {:compiler {:optimizations :simple}}}}}]})
