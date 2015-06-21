(ns dmohs.react
  (:require-macros dmohs.react)
  (:require [dmohs.react.core :as core]))


(defn create-class [fn-map]
  "See: https://github.com/dmohs/react-cljs#reactcreateclass"
  (core/create-class fn-map))


(defn create-element
  "See: https://github.com/dmohs/react-cljs#reactcreateelement"
  ([type-or-vec] (create-element type-or-vec nil))
  ([type-or-vec props & children]
     (apply core/create-element type-or-vec props children)))


(defn clone-element
  "TODO"
  [element props & children]
  (assert false "Not yet implemented."))


(defn create-factory
  "Similar to React.createFactory."
  [type]
  (core/create-factory type))


(defn render
  "Similar to React.render. If hot-reload? is true, component state will be preserved."
  ([element container] (render element container nil false))
  ([element container callback] (render element container callback false))
  ([element container callback hot-reload?]
     (core/render element container callback hot-reload?)))


(defn unmount-component-at-node
  "Similar to React.unmountComponentAtNode."
  [container]
  (core/unmount-component-at-node container))


(defn call
  "Calls a method on a component instance."
  [method-key instance & method-args]
  (apply core/call method-key instance method-args))


(defn initialize-touch-events [should-use-touch?]
  (core/initialize-touch-events should-use-touch?))


(defn- get-display-name [instance]
  (.. instance -constructor -displayName))
