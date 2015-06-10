(ns dmohs.react.core
  (:require [cljsjs.react :as React]))


(defn rlog [& args]
  (let [arr (array)]
    (doseq [x args] (.push arr x))
    (js/console.log.apply js/console arr))
  (last args))


(defn jslog [& args]
  (apply rlog (map clj->js args)))


(defn cljslog [& args]
  (apply rlog (map pr-str args)))


(defn- props [instance]
  (let [defaults (.. instance -props -cljsDefaultProps)]
    (if defaults
      (merge defaults (.. instance -props -cljsProps))
      (.. instance -props -cljsProps))))


(defn- state [instance]
  (.-cljsState instance))


(deftype Refs->Clj [instance]
  IDeref
  (-deref [this]
    (js->clj (.. instance -refs))))


(defn- refs [instance]
  (Refs->Clj. instance))


(def serialized-state-queue (atom []))


(def hot-reload-enabled? (atom false))


(def hot-reloading? (atom false))


(defn initialize-touch-events [should-use-touch?]
  (React.initializeTouchEvents should-use-touch?))


(defn create-element
  [type-or-vec props & children]
  (if (vector? type-or-vec)
    (apply create-element type-or-vec)
    (let [tag? (or (string? type-or-vec) (keyword? type-or-vec))
          type (if tag? (name type-or-vec) type-or-vec)
          children (reduce (fn [r c]
                             (if (seq? c)
                               (vec (concat r c))
                               (conj r c)))
                           []
                           children)
          children (map (fn [x]
                          (if (vector? x)
                            (apply create-element x)
                            x))
                        children)]
      (if tag?
        (apply React.createElement type (clj->js props) children)
        (if (or (nil? props) (empty? props))
          (apply React.createElement type nil children)
          (let [js-props #js{}
                ref (props :ref)
                key (props :key)
                props (dissoc props :ref :key)]
            (set! (.. js-props -cljsProps) props)
            (when ref (set! (.. js-props -ref) ref))
            (when key (set! (.. js-props -key) key))
            (apply React.createElement type js-props children)))))))


(def react-instance-method-keys
  #{:__reactAutoBindMap :_reactInternalInstance :childContextTypes
    :componentDidMount :componentDidUpdate
    :componentWillMount :componentWillReceiveProps :componentWillUnmount :componentWillUpdate
    :constructor :context :contextTypes :forceUpdate :getChildContext :getDOMNode :getDefaultProps
    :getInitialState :isMounted :mixins :propTypes :props :refs :render
    :replaceProps :replaceState :setProps :setState :shouldComponentUpdate :state :statics
    :updateComponent})


(defn- default-arg-map [this]
  (let [instance-method-keys (set (goog.object.getKeys this))
        instance-method-keys (apply disj instance-method-keys
                                    (map name react-instance-method-keys))
        instance-method-keys (apply disj instance-method-keys
                                    (map name #{:cljsState}))]
    (merge
     {:this this :props (props this) :state (state this) :refs (refs this)}
     (reduce (fn [r k] (assoc r (keyword k) (aget this k))) {} instance-method-keys))))


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
                                 (get-initial-state (dissoc (default-arg-map this) :refs))))
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
         (let [rendered (render (default-arg-map this))]
           (if (vector? rendered)
             (apply create-element rendered)
             rendered))))
      :component-will-unmount
      (fn []
        (this-as
         this
         (remove-watch (state this) ::set-state-on-update)
         (when @hot-reloading?
           (swap! serialized-state-queue conj @(state this)))
         (when component-will-unmount (component-will-unmount (default-arg-map this)))))})))


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
            (fn [] (this-as this #js{:cljsDefaultProps (x {:this this})}))))
    (when-let [x (:display-name fn-map)] (set! (. class-def -displayName) x))
    (when-let [x (:component-will-mount fn-map)]
      (set! (. class-def -componentWillMount)
            (fn [] (this-as this (x (dissoc (default-arg-map this) :refs))))))
    (when-let [x (:component-did-mount fn-map)]
      (set! (. class-def -componentDidMount)
            (fn [] (this-as this (x (default-arg-map this))))))
    (when-let [x (:component-will-receive-props fn-map)]
      (set! (. class-def -componentWillReceiveProps)
            (fn [nextProps]
              (this-as
               this
               (x (assoc (default-arg-map this) :next-props (.-cljsProps nextProps)))))))
    (when-let [x (:should-component-update fn-map)]
      (set! (. class-def -shouldComponentUpdate)
            (fn [nextProps nextState]
              (this-as
               this
               (x (assoc (default-arg-map this)
                         :next-props (.-cljsProps nextProps)
                         :next-state (.-snapshot nextState)))))))
    (when-let [x (:component-will-update fn-map)]
      (set! (. class-def -componentWillUpdate)
            (fn [nextProps nextState]
              (this-as
               this
               (x (assoc (default-arg-map this)
                         :next-props (.-cljsProps nextProps)
                         :next-state (.-snapshot nextState)))))))
    (when-let [x (:component-did-update fn-map)]
      (set! (. class-def -componentDidUpdate)
            (fn [prevProps prevState]
              (this-as
               this
               (x (assoc (default-arg-map this)
                         :prev-props (.-cljsProps prevProps)
                         :prev-state (.-snapshot prevState)))))))
    (set! (. class-def -componentWillUnmount) (:component-will-unmount fn-map))
    (let [remaining-methods (apply dissoc fn-map react-component-api-method-keys)]
      (doseq [[k f] remaining-methods]
        (aset class-def (name k) (fn [& args]
                                   (this-as this (apply f (default-arg-map this) args))))))
    (React.createClass class-def)))


(defn render
  ([element container] (render element container nil))
  ([element container callback]
     (when @hot-reload-enabled?
       (reset! serialized-state-queue [])
       (reset! hot-reloading? true))
     (React.render element container callback)
     (reset! hot-reloading? false)))


(defn create-factory [type]
  (React.createFactory type))


(defn unmount-component-at-node [container]
  (React.unmountComponentAtNode container))


;; (defn pass-to [component property & prepended-arg-fns]
;;   (when-let [f (pval component property)]
;;     (fn [& args]
;;       (apply f (concat (map (fn [f] (f)) prepended-arg-fns) args)))))
