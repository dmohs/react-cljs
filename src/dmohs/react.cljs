(ns dmohs.react
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
  "Similar to React.render. If hot reloading is enabled, component state will be preserved."
  ([element container] (render element container nil))
  ([element container callback]
     (core/render element container callback)))


(defn unmount-component-at-node
  "Similar to React.unmountComponentAtNode."
  [container]
  (core/unmount-component-at-node container))


(defn call
  "Calls a method on a component instance."
  [method-key instance & method-args]
  (apply core/call method-key instance method-args))


(defn enable-hot-reload!
  "Call this before re-rendering to preserve component state between renders."
  []
  (reset! core/hot-reload-enabled? true))


(defn disable-hot-reload! []
  "Stop preserving component state between renders."
  (reset! core/hot-reload-enabled? false))


(defn initialize-touch-events [should-use-touch?]
  (core/initialize-touch-events should-use-touch?))


(defn- get-display-name [instance]
  (.. instance -constructor -displayName))
