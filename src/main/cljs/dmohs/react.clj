(ns dmohs.react
  (:require
    cljs.analyzer
    [dmohs.react.common :as common]))


(defmacro defc [name doc-string-or-fn-map & [fn-map]]
  (let [[doc-string fn-map] (if (string? doc-string-or-fn-map)
                              [doc-string-or-fn-map fn-map]
                              [nil doc-string-or-fn-map])]
    `(let [fn-map# (merge {:display-name (name '~name)
                           :namespace ~(str cljs.analyzer/*cljs-ns*)}
                          ~fn-map)
           fn-map# (dmohs.react.core/wrap-fn-defs fn-map#)
           api-keys-to-check# (disj common/react-component-api-method-keys
                                    :get-default-props :render)
           api-keys-used# (set (keys fn-map#))]
       (if-not (cljs.core/exists? ~name)
         (def ~name (dmohs.react.core/create-class fn-map#))
         ;; Assume hot-reload. Instead of redefining the symbol, modify the prototype by replacing
         ;; the methods.
         (let [prototype# (.-prototype ~name)]
           ;; Default props are set on the constructor, so we must replace them here.
           (aset ~name "defaultProps"
                 (if (contains? fn-map# :get-default-props)
                   ((get fn-map# :get-default-props))
                   nil))
           (doseq [[~'k ~'f] (dissoc fn-map# :get-default-props)]
             (aset prototype# (name ~'k) ~'f))
           (doseq [~'k api-keys-to-check#]
             (aset (if (= ~'k :get-initial-state) (.-constructor prototype#) prototype#)
                   (common/kw->camel ~'k)
                   (if (contains? api-keys-used# ~'k)
                     (dmohs.react.core/create-camel-cased-react-method-wrapper ~'k)
                     nil))))))))

(defmacro defc- [& args]
  `(defc ~@args))
