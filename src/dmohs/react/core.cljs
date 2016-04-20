(ns dmohs.react.core
  (:require-macros [dmohs.react.core :refer [if-not-optimized]])
  (:require
   [cljsjs.react :as React]
   [cljsjs.react.dom :as ReactDOM]
   [dmohs.react.common :as common]))


;; TOOD: debug component method calls


(defn get-display-name [instance]
  (.. instance -constructor -displayName))


(defn- props [instance]
  (.. instance -props -cljs))


(defn- maybe-report-state-change [instance new-state]
  (when-let [on-state-change (:on-state-change (props instance))]
    ;; This is sometimes called during a render and often called by something that will update its
    ;; own state (causing a re-render), so to be safe we report it after the event loop.
    (js/setTimeout #(on-state-change new-state) 0)))


(defn- atom-like-state-swap! [instance & swap-args]
  (let [new-value (apply swap! (.. instance -cljsState) swap-args)]
    (.setState instance (if-not-optimized
                          #js{:cljs new-value :js (clj->js new-value)}
                          #js{:cljs new-value}))
    (maybe-report-state-change instance new-value)
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
    (.setState instance (if-not-optimized
                          #js{:cljs new-value :js (clj->js new-value)}
                          #js{:cljs new-value}))
    (maybe-report-state-change instance new-value)
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
        (let [js-props #js{}
              {:keys [ref key]} props
              default-props (aget (.-constructor (.-prototype type)) "defaultProps")
              props (merge (when default-props (.-cljsDefault default-props))
                           (dissoc props :ref :key))]
          (set! (.. js-props -cljs) props)
          (if-not-optimized (set! (.. js-props -js) (clj->js props)) nil)
          (when ref (set! (.. js-props -ref) ref))
          (when key (set! (.. js-props -key) key))
          (apply React.createElement type js-props children))))))


(defn- default-arg-map [this]
  {:this this :props (props this) :state (state this) :refs (refs this) :locals (locals this)
   :after-update (fn [callback] (.setState this #js{} callback))})


(defn call [k instance & args]
  (assert (keyword? k) (str "Not a keyword: " k))
  (let [m (aget instance (name k))]
    (assert m (str "Method " k " not found on component '" (get-display-name instance) "'"))
    (.apply m instance (to-array args))))


(defn- wrap-non-react-methods [fn-map]
  (let [non-react-methods (apply dissoc fn-map common/react-component-api-method-keys)]
    (reduce-kv
     (fn [r k f]
       (assoc r k (fn [& args] (this-as this (apply f (default-arg-map this) args)))))
     fn-map
     non-react-methods)))


(defn- wrap-get-initial-state [fn-map]
  ;; Initialize the component here since this is the first method called.
  (let [{:keys [get-initial-state]} fn-map
        wrapped (fn []
                  (this-as
                   this
                   (let [state (when get-initial-state (get-initial-state (default-arg-map this)))
                         state (merge state (:initial-state-override (props this)))]
                     (if (.. this -cljsState)
                       ;; State already exists. Component was probably hot-reloaded,
                       ;; so don't initialize.
                       (if-not-optimized
                         #js{:cljs state :js (clj->js state)}
                         #js{:cljs state})
                       (let [locals-atom (atom nil)]
                         (set! (.-cljsLocals this) locals-atom)
                         (set! (.. this -cljsState) (atom state))
                         (maybe-report-state-change this state)
                         (if-not-optimized
                           #js{:cljs state :js (clj->js state)}
                           #js{:cljs state}))))))]
    (assoc fn-map :get-initial-state wrapped)))


(defn- wrap-render [fn-map]
  (let [{:keys [render]} fn-map
        wrapped (fn []
                  (this-as
                   this
                   (let [rendered (try (render (default-arg-map this))
                                    (catch js/Object e
                                      (.log js/window.console (.-stack e))
                                      (create-element :div {:style {:color "red"}}
                                                      "Render failed. See console for details.")))]
                     (if (vector? rendered)
                       (apply create-element rendered)
                       rendered))))]
    (assoc fn-map :render wrapped)))


(defn- wrap-if-present [fn-map k create-wrapper]
  (if-let [f (get fn-map k)]
    (assoc fn-map k (create-wrapper f))
    fn-map))


(defn- create-default-wrapper [f]
  (fn [] (this-as this (.call f this (default-arg-map this)))))


(defn wrap-fn-defs [fn-map]
  ;; TODO: propTypes, mixins, statics
  (-> fn-map
      wrap-non-react-methods
      wrap-get-initial-state wrap-render
      (wrap-if-present
       :get-default-props
       (fn [f] (fn [] (this-as this #js{:cljsDefault (.call f this {:this this})}))))
      (wrap-if-present :component-will-mount create-default-wrapper)
      (wrap-if-present :component-did-mount create-default-wrapper)
      (wrap-if-present
       :component-will-receive-props
       (fn [f]
         (fn [nextProps]
           (this-as
            this
            (.call f this (assoc (default-arg-map this) :next-props (.-cljs nextProps)))))))
      (wrap-if-present
       :should-component-update
       (fn [f]
         (fn [nextProps nextState]
           (this-as
            this
            (.call f this (assoc (default-arg-map this)
                                 :next-props (.-cljs nextProps)
                                 :next-state (.-cljs nextState)))))))
      (wrap-if-present
       :component-will-update
       (fn [f]
         (fn [nextProps nextState]
           (this-as
            this
            (.call f this (assoc (default-arg-map this)
                                 :next-props (.-cljs nextProps)
                                 :next-state (.-cljs nextState)))))))
      (wrap-if-present
       :component-did-update
       (fn [f]
         (fn [prevProps prevState]
           (this-as
            this
            (.call f this (assoc (default-arg-map this)
                                 :prev-props (.-cljs prevProps)
                                 :prev-state (.-cljs prevState)))))))
      (wrap-if-present :component-will-unmount create-default-wrapper)))


(defn create-camel-cased-react-method-wrapper [k]
  ;; For the normal lifecycle methods, we just bounce the call to the similarly-named instance
  ;; method.
  (fn [& args]
    (this-as
     this
     (let [proto (if (= k :get-default-props)
                   (.-prototype this)
                   (.getPrototypeOf js/Object this))]
       (.apply (aget proto (name k)) this (to-array args))))))


(defn create-class [fn-map]
  (let [class-def #js{:autobind false}]
    (doseq [[k f] fn-map]
      (aset class-def (name k) f)
      ;; React, being Javascript, likes camel-case.
      (when (contains? (disj common/react-component-api-method-keys :render) k)
        (aset class-def (common/kw->camel k)
              (if (= k :display-name)
                f
                (create-camel-cased-react-method-wrapper k)))))
    (let [class (React.createClass class-def)]
      (extend-type class
        IFn
        (-invoke ([this method-keyword & args]
                  (apply call method-keyword this args))))
      class)))


(defn create-factory [type]
  (React.createFactory type))


(defn valid-element? [x]
  (React.isValidElement x))


(defn render
  ([element container] (ReactDOM.render element container))
  ([element container callback] (ReactDOM.render element container callback)))


(defn unmount-component-at-node [container]
  (ReactDOM.unmountComponentAtNode container))


(defn find-dom-node [instance]
  (ReactDOM.findDOMNode instance))


;; (defn pass-to [component property & prepended-arg-fns]
;;   (when-let [f (pval component property)]
;;     (fn [& args]
;;       (apply f (concat (map (fn [f] (f)) prepended-arg-fns) args)))))
