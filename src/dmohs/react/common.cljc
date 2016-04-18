(ns dmohs.react.common
  (:require clojure.string))


(def react-component-api-method-keys
  #{:render
    :get-initial-state
    :get-default-props
    :display-name
    :component-will-mount
    :component-did-mount
    :component-will-receive-props
    :should-component-update
    :component-will-update
    :component-did-update
    :component-will-unmount})


(defn kw->camel [k]
  (clojure.string/replace
   (name k) #"[-]([a-z])" #(clojure.string/upper-case (second %))))
