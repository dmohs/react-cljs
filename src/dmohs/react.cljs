(ns dmohs.react
  (:require [cljsjs.react :as React]))


(defn initialize-touch-events [should-use-touch?]
  (React.initializeTouchEvents should-use-touch?))


(defn rlog [& args]
  (let [arr (array)]
    (doseq [x args] (.push arr x))
    (js/console.log.apply js/console arr))
  (last args))


(defn jslog [& args]
  (apply rlog (map clj->js args)))


(defn cljslog [& args]
  (apply rlog (map pr-str args)))


;; (defn pass-to [component property & prepended-arg-fns]
;;   (when-let [f (pval component property)]
;;     (fn [& args]
;;       (apply f (concat (map (fn [f] (f)) prepended-arg-fns) args)))))


(defn create-element
  ([type-or-vec] (create-element type-or-vec {}))
  ([type-or-vec props & children]
     (if (vector? type-or-vec)
       (apply create-element type-or-vec)
       (let [tag? (or (string? type-or-vec) (keyword? type-or-vec))
             type (if tag? (name type-or-vec) type-or-vec)
             ref (props :ref)
             children (reduce (fn [r c]
                                (if (seq? c)
                                  (vec (concat r c))
                                  (conj r c)))
                              []
                              children)
             children (map (fn [x]
                             (if (vector? x)
                               (create-element x)
                               x))
                           children)]
         (if tag?
           (apply React.createElement type (clj->js props) children)
           (let [js-props #js{}
                 ref (props :ref)
                 key (props :key)
                 props (dissoc props :ref :key)]
             (set! (.. js-props -cljsProps) props)
             (when ref (set! (.. js-props -ref) ref))
             (when key (set! (.. js-props -key) key))
             (apply React.createElement type js-props children)))))))


(defn- props [instance]
  (let [defaults (.. instance -props -cljsDefaultProps)]
    (if defaults
      (merge defaults (.. instance -props -cljsProps))
      (.. instance -props -cljsProps))))


(defn- state [instance]
  (.-cljsState instance))


(deftype DelayedJs->Clj [js]
  IDeref
  (-deref [this]
    (js->clj js)))


(defn- refs [instance]
  (DelayedJs->Clj. (.. instance -refs)))


(defn- get-display-name [instance]
  (.. instance -constructor -displayName))


(defn- to-vec [x-or-xs]
  (cond
   (nil? x-or-xs) nil
   (coll? x-or-xs) (vec x-or-xs)
   :else [x-or-xs]))


(defn children [component]
  (when-let [p (.-props component)]
    (to-vec (.-children p))))


(defn child [component]
  (when-let [p (.-props component)]
    (assert (not (coll? (.-children p))))
    (.-children p)))


(def serialized-state-queue (atom []))


(def hot-reload-enabled? (atom false))


(def hot-reloading? (atom false))


(defn enable-hot-reload! []
  (reset! hot-reload-enabled? true))


(defn disable-hot-reload! []
  (reset! hot-reload-enabled? false))


(defn- wrap-fn-defs [fn-map]
  (let [{:keys [get-initial-state render component-will-unmount]} fn-map]
    (merge
     fn-map
     {:get-initial-state
      (fn []
        (this-as
         this
         (let [initial-state (if (and @hot-reloading? (pos? (count @serialized-state-queue)))
                               (let [s (first @serialized-state-queue)]
                                 (swap! serialized-state-queue rest)
                                 s)
                               (when get-initial-state
                                 (get-initial-state this (props this))))
               state-atom (atom initial-state)]
           (set! (.-cljsState this) state-atom)
           (add-watch state-atom ::set-state-on-update
                      (fn [k r os ns]
                        (.setState this #js{:snapshot ns})))
           #js{:snapshot @state-atom})))
      :render
      (fn []
        (this-as
         this
         (let [rendered (render this (props this) (state this) (refs this))]
           (if (vector? rendered)
             (create-element rendered)
             rendered))))
      :component-will-unmount
      (fn []
        (this-as
         this
         (remove-watch (state this) ::set-state-on-update)
         (when @hot-reloading?
           (swap! serialized-state-queue conj @(state this)))
         (when component-will-unmount (component-will-unmount this))))})))


(defn create-class [fn-map]
  (let [class-def #js{}
        fn-map (wrap-fn-defs fn-map)]
    ;; https://facebook.github.io/react/docs/component-specs.html
    ;; Order is the same as the above documentation.
    (set! (. class-def -render) (:render fn-map))
    (set! (. class-def -getInitialState) (:get-initial-state fn-map))
    ;; TODO: propTypes, mixins, statics
    (when-let [x (:get-default-props fn-map)]
      (set! (. class-def -getDefaultProps)
            (fn [] (this-as this #js{:cljsDefaultProps (x this)}))))
    (when-let [x (:display-name fn-map)] (set! (. class-def -displayName) x))
    (when-let [x (:component-will-mount fn-map)]
      (set! (. class-def -componentWillMount)
            (fn [] (this-as this (x this (props this) (state this))))))
    (when-let [x (:component-did-mount fn-map)]
      (set! (. class-def -componentDidMount)
            (fn [] (this-as this (x this (props this) (state this) (refs this))))))
    (when-let [x (:component-will-receive-props fn-map)]
      (set! (. class-def -componentWillReceiveProps)
            (fn [nextProps]
              (this-as
               this
               (x this (.-cljsProps nextProps) (props this) (state this) (refs this))))))
    (when-let [x (:should-component-update fn-map)]
      (set! (. class-def -shouldComponentUpdate)
            (fn [nextProps nextState]
              (this-as
               this
               (x this
                  (.-cljsProps nextProps)
                  (.-snapshot nextState)
                  (props this)
                  (state this)
                  (refs this))))))
    (when-let [x (:component-will-update fn-map)]
      (set! (. class-def -componentWillUpdate)
            (fn [nextProps nextState]
              (this-as
               this
               (x this
                  (.-cljsProps nextProps)
                  (.-snapshot nextState)
                  (props this)
                  (state this)
                  (refs this))))))
    (when-let [x (:component-did-update fn-map)]
      (set! (. class-def -componentDidUpdate)
            (fn [prevProps prevState]
              (this-as
               this
               (x this
                  (.-cljsProps prevProps)
                  (.-snapshot prevState)
                  (props this)
                  (state this)
                  (refs this))))))
    (set! (. class-def -componentWillUnmount) (:component-will-unmount fn-map))
    (React.createClass class-def)))


(defn render
  ([element container] (render element container nil))
  ([element container callback]
     (when @hot-reload-enabled?
       (reset! serialized-state-queue [])
       (reset! hot-reloading? true))
     (React.render element container callback)
     (reset! hot-reloading? false)))
