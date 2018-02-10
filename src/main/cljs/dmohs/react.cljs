(ns dmohs.react
  (:require-macros [dmohs.react :refer [defc defc-]])
  (:require [dmohs.react.core :as core]))


(defn create-class [fn-map]
  "See: https://github.com/dmohs/react-cljs#reactcreateclass
   If :trace? is true, component method calls will be printed to the console."
  (core/create-class (core/wrap-fn-defs fn-map)))


(defn set-trace-count-limit! [limit]
  "If this limit is exceeded while tracing, an error will be thrown. Set to nil for no limit."
  (set! core/trace-count-limit limit))


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
  "See: https://reactjs.org/docs/react-api.html#isvalidelement"
  [x]
  (core/valid-element? x))


(defn force-update
  "Causes an update. If given a callback, calls it after the update completes."
  ([instance] (core/force-update instance))
  ([instance f]
   (core/force-update instance f)))


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
  "See: https://reactjs.org/docs/react-dom.html#finddomnode"
  [instance]
  (core/find-dom-node instance))


(defn create-portal
  "See: https://reactjs.org/docs/portals.html"
  [child container]
  (core/create-portal (create-element child) container))


(defn call
  "Calls a method on a component instance."
  [method-key instance & method-args]
  (assert (keyword? method-key) (str "Not a keyword: " method-key))
  (apply core/call method-key instance method-args))


(defn get-display-name [instance]
  (core/get-display-name instance))


;;
;; Extra Goodies
;;

(defn after-update
  "Calls the function with any given args directly after the component has been updated from the
  last state change. Causes an update if no state change is pending."
  [instance f & args]
  (apply core/after-update instance f args))

(defn method
  "Returns the method with the given key. Subsequent calls return the same (identical) function."
  [instance method-key]
  (assert (keyword? method-key) (str "Not a keyword: " method-key))
  (core/get-bound-method instance method-key))


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
