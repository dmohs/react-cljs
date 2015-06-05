(ns dmohs.react
  (:require [dmohs.react.core :as core]))


;;
;; React ClojureScript Top-Level API. Follows React's Top-Level API:
;; https://facebook.github.io/react/docs/top-level-api.html
;;


(defn create-class [fn-map]
  "Creates and returns a React class from the supplied map, similar to React.createClass.

Each function receives a map of values containing the current instance's properties:
{:this instance
 :props props
 :state state ; state atom
 :refs refs ; refs atom
 :prev-props ; when appropriate
 :next-props ; when appropriate
 ...
}

Usage:
(create-class
 {:get-default-props
  (fn [] {:starting-value 17})
  :get-initial-state
  (fn [{:keys [props]}] {:value (props :starting-value)})
  :render
  (fn [{:keys [state]}]
    [:div {:ref \"the-only-div\" :onClick (fn [e] (swap! state update-in [:value] inc))}
      \"You've clicked me \" (:value @state) \" times!\"])
  :component-did-mount
  (fn [{:keys [refs]}]
    (.focus (.getDOMNode (@refs \"the-only-div\"))))

Note that render can return either an element (via create-element) or a vector which will be
automatically passed to create-element.
"
  (core/create-class fn-map))


(defn create-element
  "Creates and returns a new ReactElement of the given type, similar to React.createElement. The
first argument can also be a keyword (e.g., :div) which is simply converted to a string. The
argument may also be a vector (e.g., [:div {:style {:color \"blue\"}} \"Stuff\"]) with arguments
in the same order."
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
