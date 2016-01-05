(ns dmohs.react.core
  (:require
   [cljsjs.react :as React]
   [cljsjs.react.dom :as ReactDOM]))


(defn rlog [& args]
  (let [arr (array)]
    (doseq [x args] (.push arr x))
    (js/console.log.apply js/console arr))
  (last args))


(defn jslog [& args]
  (apply rlog (map clj->js args)))


(defn cljslog [& args]
  (apply rlog (map pr-str args)))


(defn get-display-name [instance]
  (.. instance -constructor -displayName))


(defn- props [instance]
  (let [defaults (aget instance "props" "cljsDefault")]
    (if defaults
      (merge defaults (.. instance -props -cljs))
      (.. instance -props -cljs))))


(defn- atom-like-state-swap! [instance & swap-args]
  (let [new-value (apply swap! (.. instance -cljsState) swap-args)]
    (.setState instance #js{:cljs new-value})
    (when-let [on-state-change (:on-state-change (props instance))]
      (on-state-change new-value))
    new-value))


(deftype AtomLikeState [instance]
  IDeref
  (-deref [this]
    (when-let [state (.. instance -state)]
      (.. state -cljs)))
  ISwap
  (-swap! [this f] (atom-like-state-swap! instance f))
  (-swap! [this f a] (atom-like-state-swap! instance f a))
  (-swap! [this f a b] (atom-like-state-swap! instance f a b))
  (-swap! [this f a b xs] (apply atom-like-state-swap! instance f a b xs))
  IReset
  (-reset! [this new-value]
    (reset! (.. instance -cljsState) new-value)
    (.setState instance #js{:cljs new-value})
    (when-let [on-state-change (:on-state-change (props instance))]
      (on-state-change new-value))
    new-value))


(defn- state [instance]
  (AtomLikeState. instance))


(deftype Refs->Clj [instance]
  IDeref
  (-deref [this]
    (js->clj (.. instance -refs))))


(defn- refs [instance]
  (Refs->Clj. instance))


(defn- locals [instance]
  (.-cljsLocals instance))


(def serialized-state-queue (atom []))


(def hot-reloading? (atom false))


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
            (set! (.. js-props -cljs) props)
            (when ref (set! (.. js-props -ref) ref))
            (when key (set! (.. js-props -key) key))
            (apply React.createElement type js-props children)))))))


(defn- default-arg-map [this]
  {:this this :props (props this) :state (state this) :refs (refs this) :locals (locals this)
   :after-update (fn [callback] (.setState this #js{} callback))})


(defn- wrap-fn-defs [fn-map]
  (let [{:keys [get-initial-state render component-will-unmount]} fn-map]
    (merge
     fn-map
     {:get-initial-state
      (fn []
        (this-as
         this
         (let [locals-atom (atom nil)
               state (if (and @hot-reloading? (pos? (count @serialized-state-queue)))
                       (let [s (first @serialized-state-queue)]
                         (swap! serialized-state-queue rest)
                         s)
                       (when get-initial-state
                         (get-initial-state (default-arg-map this))))
               state (merge state (:initial-state-override (props this)))]
           (set! (.-cljsLocals this) locals-atom)
           (set! (.. this -cljsState) (atom state))
           (when-let [on-state-change (:on-state-change (props this))]
             ;; This is during a render and usually called by something that will update its own
             ;; state (causing a re-render), so to be safe we report it after the event loop.
             (js/setTimeout #(on-state-change state) 0))
           #js{:cljs state})))
      :render
      (fn []
        (this-as
         this
         (let [rendered (if @hot-reloading?
                          (try (render (default-arg-map this))
                               (catch js/Object e
                                 (.log js/window.console (.-stack e))
                                 (create-element :div {:style {:color "red"}}
                                                 "Render failed. See console for details.")))
                          (render (default-arg-map this)))]
           (if (vector? rendered)
             (apply create-element rendered)
             rendered))))
      :component-will-unmount
      (fn []
        (this-as
         this
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
            (fn [] (this-as this #js{:cljsDefault (.call x this {:this this})}))))
    (when-let [x (:display-name fn-map)] (set! (. class-def -displayName) x))
    (when-let [x (:component-will-mount fn-map)]
      (set! (. class-def -componentWillMount)
            (fn [] (this-as this (.call x this (dissoc (default-arg-map this) :refs))))))
    (when-let [x (:component-did-mount fn-map)]
      (set! (. class-def -componentDidMount)
            (fn [] (this-as this (.call x this (default-arg-map this))))))
    (when-let [x (:component-will-receive-props fn-map)]
      (set! (. class-def -componentWillReceiveProps)
            (fn [nextProps]
              (this-as
               this
               (.call x this (assoc (default-arg-map this) :next-props (.-cljs nextProps)))))))
    (when-let [x (:should-component-update fn-map)]
      (set! (. class-def -shouldComponentUpdate)
            (fn [nextProps nextState]
              (this-as
               this
               (.call x this (assoc (default-arg-map this)
                                    :next-props (.-cljs nextProps)
                                    :next-state (.-cljs nextState)))))))
    (when-let [x (:component-will-update fn-map)]
      (set! (. class-def -componentWillUpdate)
            (fn [nextProps nextState]
              (this-as
               this
               (.call x this (assoc (default-arg-map this)
                                    :next-props (.-cljs nextProps)
                                    :next-state (.-cljs nextState)))))))
    (when-let [x (:component-did-update fn-map)]
      (set! (. class-def -componentDidUpdate)
            (fn [prevProps prevState]
              (this-as
               this
               (.call x this (assoc (default-arg-map this)
                                    :prev-props (.-cljs prevProps)
                                    :prev-state (.-cljs prevState)))))))
    (set! (. class-def -componentWillUnmount) (:component-will-unmount fn-map))
    (let [remaining-methods (apply dissoc fn-map react-component-api-method-keys)]
      (doseq [[k f] remaining-methods]
        (aset class-def (name k) (fn [& args]
                                   (this-as this (apply f (default-arg-map this) args))))))
    (React.createClass class-def)))


(defn create-factory [type]
  (React.createFactory type))


(defn valid-element? [x]
  (React.isValidElement x))


(defn render
  ([element container] (render element container nil false))
  ([element container callback] (render element container callback false))
  ([element container callback hot-reload?]
   (when hot-reload?
  (reset! serialized-state-queue [])
  (reset! hot-reloading? true)
  ;; React sometimes does not fully unmount before re-rendering. Since hot-reloading depends
  ;; on a consistent unmount/mount order to reload the state, we need to unmount first.
     (ReactDOM.unmountComponentAtNode container))
   (let [component (ReactDOM.render element container callback)]
     (when hot-reload?
  (reset! hot-reloading? false))
     component)))


(defn unmount-component-at-node [container]
  (ReactDOM.unmountComponentAtNode container))


(defn find-dom-node [instance]
  (ReactDOM.findDOMNode instance))


(defn call [k instance & args]
  (assert (keyword? k) (str "Not a keyword: " k))
  (let [instance (if (= instance :this) (this-as this this) instance)
        m (aget instance (name k))]
    (assert m (str "Method " k " not found on component '" (get-display-name instance) "'"))
    (apply m args)))


;; (defn pass-to [component property & prepended-arg-fns]
;;   (when-let [f (pval component property)]
;;     (fn [& args]
;;       (apply f (concat (map (fn [f] (f)) prepended-arg-fns) args)))))
