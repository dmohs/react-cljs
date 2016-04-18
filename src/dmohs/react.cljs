(ns dmohs.react
  (:require-macros [dmohs.react :refer [defc]])
  (:require [dmohs.react.core :as core]))


(defn create-class [fn-map]
  "See: https://github.com/dmohs/react-cljs#reactcreateclass"
  (core/create-class (core/wrap-fn-defs fn-map)))


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


(defn valid-element?
  "See: https://facebook.github.io/react/docs/top-level-api.html#react.isvalidelement"
  [x]
  (core/valid-element? x))


;;
;; ReactDOM
;;


(defn render
  "Similar to React.render."
  ([element container] (core/render element container))
  ([element container callback] (core/render element container callback)))


(defn unmount-component-at-node
  "Similar to React.unmountComponentAtNode."
  [container]
  (core/unmount-component-at-node container))


(defn find-dom-node
  "See: https://facebook.github.io/react/docs/top-level-api.html#reactdom.finddomnode"
  [instance]
  (core/find-dom-node instance))


(defn call
  "Calls a method on a component instance."
  [method-key instance & method-args]
  (apply core/call method-key instance method-args))


(defn get-display-name [instance]
  (core/get-display-name instance))


;;
;; Devcards Helpers
;;


(defc DevcardsComponent
  "Protects children from getting re-rendered when reporting state via the data-atom."
  {:render
   (fn [{:keys [props locals]}]
     (let [data-atom (:data-atom props)
           f (:render-fn props)]
       (f data-atom (:owner props) {:initial-state-override (:state @data-atom)
                                    :on-state-change
                                    (fn [new-state]
                                      (swap! locals assoc :suppress-next-update? true)
                                      (swap! data-atom assoc :state new-state))})))
   :should-component-update
   (fn [{:keys [locals]}]
     (if (:suppress-next-update? @locals)
       (do
         (swap! locals assoc :suppress-next-update? false)
         false)
       true))})


(defn wrap-devcard-fn [f]
  "Pass a single function that takes three parameters: data-atom, owner, and devcard-props. Merge
   devcard-props with your component's props to preserve and view the component's state, even across
   figwheel reloads."
  (fn [data-atom owner]
    (create-element DevcardsComponent {:data-atom data-atom :owner owner :render-fn f})))
