(ns dmohs.react.core
  (:require-macros [dmohs.react.core :refer [if-not-optimized]])
  (:require
   clojure.string
   [dmohs.react.common :as common]
   dmohs.react.deps))


(defn get-display-name [instance]
  (.. instance -constructor -displayName))


(defn- props [instance]
  (aget (.. instance -props) "cljs"))


(defn- maybe-report-state-change [instance new-state]
  (when-let [on-state-change (:on-state-change (props instance))]
    ;; This is sometimes called during a render and often called by something that will update its
    ;; own state (causing a re-render), so to be safe we report it after the event loop.
    (js/setTimeout #(on-state-change new-state) 0)))


(defn- atom-like-state-swap! [instance & swap-args]
  (let [new-value (apply swap! (aget instance "cljsState") swap-args)]
    (.setState instance (if-not-optimized
                          #js{:cljs new-value :js (clj->js new-value)}
                          #js{:cljs new-value}))
    (maybe-report-state-change instance new-value)
    new-value))


(deftype AtomLikeState [instance]
  IDeref
  (-deref [this]
    (when-let [state (.. instance -state)]
      (aget state "cljs")))
  ISwap
  (-swap! [this f] (atom-like-state-swap! instance f))
  (-swap! [this f a] (atom-like-state-swap! instance f a))
  (-swap! [this f a b] (atom-like-state-swap! instance f a b))
  (-swap! [this f a b xs] (apply atom-like-state-swap! instance f a b xs))
  IReset
  (-reset! [this new-value]
    (reset! (aget instance "cljsState") new-value)
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
  (aget instance "cljsLocals"))


(defn- transform-keys [keyfn xs]
  ;; Structure taken from js->clj source.
  (let [f (fn thisfn [x]
            (cond
              (map? x)
              (reduce-kv (fn [r k v] (assoc r (keyfn k) (thisfn v))) {} x)
              (seq? x)
              (doall (map thisfn x))
              (coll? x)
              (into (empty x) (map thisfn x))
              :else x))]
    (f xs)))


(defn- camel-case-keys [m]
  (transform-keys
   (fn [k]
     (if (and (keyword? k)
              (let [n (name k)]
                (and
                 (not (clojure.string/starts-with? n "data-"))
                 (not (clojure.string/starts-with? n "aria-")))))
       (let [words (clojure.string/split (name k) #"-")
             cased (map #(str (clojure.string/upper-case (subs % 0 1)) (subs % 1)) (rest words))]
         (keyword (apply str (first words) cased)))
       k))
   m))


(defn create-element
  [type-or-vec props & children]
  (if (vector? type-or-vec)
    (apply create-element type-or-vec)
    (letfn [(flatten-children [children]
                              (reduce
                               (fn [r c]
                                 (if (seq? c) (vec (concat r (flatten-children c))) (conj r c)))
                               []
                               children))]
      (let [tag? (keyword? type-or-vec)
            type (if tag? (name type-or-vec) type-or-vec)
            children (flatten-children children)
            children (map (fn [x]
                            (if (vector? x)
                              (apply create-element x)
                              x))
                          children)]
        (if (or tag? (not (aget (.-prototype type) "react-cljs?")))
          (apply js/React.createElement type (clj->js (camel-case-keys props)) children)
          (let [js-props #js{}
                {:keys [ref key]} props
                default-props (aget (.-constructor (.-prototype type)) "defaultProps")
                props (merge (when default-props (aget default-props "cljsDefault"))
                             (dissoc props :ref :key))]
            (aset js-props "cljs" props)
            (if-not-optimized
              (aset js-props "js"
                    (clj->js (merge {:.ns (aget (.-prototype type) "namespace")} props)))
              nil)
            (when ref (aset js-props "ref" ref))
            (when key (aset js-props "key" key))
            (apply js/React.createElement type js-props children)))))))


(defn call [k instance & args]
  (let [m (aget instance (name k))]
    (when-not m
      (throw (js/Error. (str "Method " k " not found on component " (get-display-name instance)))))
    (.apply m instance (to-array args))))

(defn get-bound-method [instance k]
  (let [method-name (name k)
        bound-method-name (str method-name "__bound")
        bound-method (aget instance bound-method-name)]
    (or bound-method
        (let [method (or (aget instance method-name)
                         (throw
                           (js/Error. (str "Method " k " not found on component "
                                           (get-display-name instance)))))
              bound-method (fn [& args] (apply instance k args))]
          (aset instance bound-method-name bound-method)
          bound-method))))

(defn after-update [instance f & args]
  (.setState instance #js{} (fn [] (apply f args))))

(defn- bind-prop-atom
  ([this prop-key] (bind-prop-atom this prop-key prop-key))
  ([this prop-key state-key]
   (let [props (props this)
         _ (assert (contains? props prop-key) (str "No prop found at key: " prop-key))
         a (get props prop-key)]
     (assert (satisfies? IWatchable a) (str "No watchable atom found at key: " prop-key))
     (add-watch a this (fn [_ _ old-value new-value]
                         (swap! (state this) assoc state-key new-value)))
     (let [bound-props (or (aget this "cljsBoundProps") {})]
       (aset this "cljsBoundProps" (assoc bound-props prop-key state-key)))
     {state-key @a})))


(defn- default-arg-map [this]
  {:this this :props (props this) :state (state this) :refs (refs this) :locals (locals this)
   :after-update (partial after-update this)
   :abind (partial bind-prop-atom this)})


(defn- arg-map->js [arg-map]
  (clj->js (merge arg-map
                  (when-let [x (:state arg-map)] {:state @x})
                  (when-let [x (:refs arg-map)] {:refs @x})
                  (when-let [x (:locals arg-map)] {:locals @x}))))


(def trace-count-limit 1000)


(defn- log-start [display-name k arg-map args]
  (let [this (:this arg-map)
        trace-count (when this (or (aget this "internal-trace-count") 1))
        formatted-trace-count (when trace-count (str ":" trace-count))
        log-args [(str "<" display-name k (or formatted-trace-count ""))]
        log-args (if (empty? arg-map) log-args (conj log-args "\n" (arg-map->js arg-map)))
        log-args (if (empty? args) log-args (conj log-args "\n" (clj->js args)))
        log-args (if (= (count log-args) 1) [(str (log-args 0) ">")] (conj log-args "\n>"))]
    (.apply (.-log js/console) js/console (to-array log-args))
    (when trace-count
      (if (and (not (nil? trace-count-limit)) (> trace-count trace-count-limit))
        (throw "Trace count limit exceeded")
        (do
          (aset this "internal-trace-count" (inc trace-count))
          (js/clearTimeout (aget this "internal-trace-timeout"))
          (aset this "internal-trace-timeout"
                (js/setTimeout #(aset this "internal-trace-count" nil) 200)))))))


(defn- log-end [display-name k result]
  (let [log-result? (when (contains? #{:get-default-props :get-initial-state
                                       :should-component-update} k)
                      true)
        log-args [(str "</" display-name k)]
        log-args (if log-result? (conj log-args "\n" (clj->js result) "\n") log-args)
        log-args (if log-result? (conj log-args ">") [(str (log-args 0) ">")])]
    (.apply (.-log js/console) js/console (to-array log-args))))


(defn- call-fn [_ f arg-map & [args]]
  (apply f arg-map args))


(defn- call-fn-with-trace [display-name k f arg-map & [args]]
  (log-start display-name k arg-map args)
  (let [result (apply f arg-map args)]
    (log-end display-name k result)
    result))


(defn- wrap-non-react-methods [fn-map call-fn]
  (let [non-react-methods (apply dissoc fn-map :namespace
                            common/react-component-api-method-keys)]
    (reduce-kv
     (fn [r k f]
       (assoc r k (fn [& args] (this-as this (call-fn k f (default-arg-map this) args)))))
     fn-map
     non-react-methods)))


(defn- wrap-get-initial-state [fn-map call-fn]
  ;; Initialize the component here since this is the first method called.
  (let [{:keys [get-initial-state]} fn-map
        wrapped (fn []
                  (this-as
                   this
                   (let [state (when get-initial-state
                                 (call-fn
                                  :get-initial-state get-initial-state (default-arg-map this)))
                         state (merge state (:initial-state-override (props this)))]
                     (if (aget this "cljsState")
                       ;; State already exists. Component was probably hot-reloaded,
                       ;; so don't initialize.
                       (if-not-optimized
                         #js{:cljs state :js (clj->js state)}
                         #js{:cljs state})
                       (let [locals-atom (atom nil)]
                         (aset  this "cljsLocals" locals-atom)
                         (aset this "cljsState" (atom state))
                         (maybe-report-state-change this state)
                         (if-not-optimized
                           #js{:cljs state :js (clj->js state)}
                           #js{:cljs state}))))))]
    (assoc fn-map :get-initial-state wrapped)))


(defn- wrap-component-will-receive-props [fn-map call-fn]
  (let [{:keys [component-will-receive-props]} fn-map
        wrapped (fn [next-props]
                  (let [next-props (aget next-props "cljs")]
                    (this-as
                     this
                     (when-let [bound-props (aget this "cljsBoundProps")]
                       (doseq [[prop-key state-key] bound-props]
                         (let [a (get next-props prop-key)]
                           (swap! (state this) assoc state-key @a)
                           (add-watch a this (fn [_ _ old-value new-value]
                                               (swap! (state this) assoc state-key new-value))))))
                     (when component-will-receive-props
                       (call-fn :component-will-receive-props component-will-receive-props
                                (assoc (default-arg-map this)
                                       :next-props next-props))))))]
    (assoc fn-map :component-will-receive-props wrapped)))


(defn- wrap-render [fn-map call-fn]
  (let [{:keys [render]} fn-map
        wrapped (fn []
                  (this-as
                   this
                   (let [rendered (try
                                    (call-fn :render render (default-arg-map this))
                                    (catch js/Object e
                                      (.log js/window.console (.-stack e))
                                      (create-element :div {:style {:color "red"}}
                                                      "Render failed. See console for details.")))]
                     (if (vector? rendered)
                       (apply create-element rendered)
                       rendered))))]
    (assoc fn-map :render wrapped)))


(defn- create-default-wrapper [call-fn]
  (fn [k f] (fn [] (this-as this (call-fn k f (default-arg-map this))))))


(defn- wrap-if-present [fn-map k create-wrapper]
  (if-let [f (get fn-map k)]
    (assoc fn-map k (create-wrapper k f))
    fn-map))


(defn wrap-fn-defs [fn-map]
  ;; TODO: propTypes, mixins, statics
  (let [trace? (:trace? fn-map)
        fn-map (dissoc fn-map :trace?)
        call-fn (if trace?
                  (partial call-fn-with-trace (get fn-map :display-name "UnnamedComponent"))
                  call-fn)
        render-fn (:render fn-map)]
    (assert (fn? render-fn)
            (str "render is required and must be a function. render is: " (pr-str render-fn)))
    (-> fn-map
        (wrap-non-react-methods call-fn)
        (wrap-get-initial-state call-fn)
        (wrap-render call-fn)
        (wrap-if-present
         :get-default-props
         (fn [k f]
           (fn []
             #js{:cljsDefault (call-fn k f {})})))
        (wrap-if-present :component-will-mount (create-default-wrapper call-fn))
        (wrap-if-present :component-did-mount (create-default-wrapper call-fn))
        (wrap-component-will-receive-props call-fn)
        (wrap-if-present
         :should-component-update
         (fn [k f]
           (fn [next-props next-state]
             (this-as
              this
              (call-fn k f (assoc (default-arg-map this)
                                  :next-props (aget next-props "cljs")
                                  :next-state (aget next-state "cljs")))))))
        (wrap-if-present
         :component-will-update
         (fn [k f]
           (fn [next-props next-state]
             (this-as
              this
              (call-fn k f (assoc (default-arg-map this)
                                  :next-props (aget next-props "cljs")
                                  :next-state (aget next-state "cljs")))))))
        (wrap-if-present
         :component-did-update
         (fn [k f]
           (fn [prev-props prev-state]
             (this-as
              this
              (call-fn k f (assoc (default-arg-map this)
                                  :prev-props (aget prev-props "cljs")
                                  :prev-state (aget prev-state "cljs")))))))
        (wrap-if-present :component-will-unmount (create-default-wrapper call-fn)))))


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


(defn- extend-react-class-with-ifn [react-class]
  (extend-type react-class
    IFn
    (-invoke ([this method-keyword] (call method-keyword this))
             ([this method-keyword a] (call method-keyword this a))
             ([this method-keyword a b] (call method-keyword this a b))
             ([this method-keyword a b c] (call method-keyword this a b c))
             ([this method-keyword a b c d] (call method-keyword this a b c d))
             ([this method-keyword a b c d e] (call method-keyword this a b c d e))
             ([this method-keyword a b c d e f] (call method-keyword this a b c d e f))
             ([this method-keyword a b c d e f g] (call method-keyword this a b c d e f g))
             ([this method-keyword a b c d e f g h] (call method-keyword this a b c d e f g h))
             ([this method-keyword a b c d e f g h i]
              (call method-keyword this a b c d e f g h i))
             ([this method-keyword a b c d e f g h i j]
              (call method-keyword this a b c d e f g h i j))
             ([this method-keyword a b c d e f g h i j k]
              (call method-keyword this a b c d e f g h i j k))
             ([this method-keyword a b c d e f g h i j k l]
              (call method-keyword this a b c d e f g h i j k l))
             ([this method-keyword a b c d e f g h i j k l m]
              (call method-keyword this a b c d e f g h i j k l m))
             ([this method-keyword a b c d e f g h i j k l m n]
              (call method-keyword this a b c d e f g h i j k l m n))
             ([this method-keyword a b c d e f g h i j k l m n o]
              (call method-keyword this a b c d e f g h i j k l m n o))
             ([this method-keyword a b c d e f g h i j k l m n o p]
              (call method-keyword this a b c d e f g h i j k l m n o p))
             ([this method-keyword a b c d e f g h i j k l m n o p q]
              (call method-keyword this a b c d e f g h i j k l m n o p q))
             ([this method-keyword a b c d e f g h i j k l m n o p q r]
              (call method-keyword this a b c d e f g h i j k l m n o p q r))
             ([this method-keyword a b c d e f g h i j k l m n o p q r s]
              (call method-keyword this a b c d e f g h i j k l m n o p q r s))
             ([this method-keyword a b c d e f g h i j k l m n o p q r s rest]
              (apply call method-keyword this a b c d e f g h i j k l m n o p q r s rest)))))


(defn create-class [fn-map]
  (let [class-def #js{:autobind false :react-cljs? true}]
    (doseq [[k f] fn-map]
      (aset class-def (name k) f)
      ;; React, being Javascript, likes camel-case.
      (when (contains? (disj common/react-component-api-method-keys :render) k)
        (aset class-def (common/kw->camel k)
              (if (= k :display-name)
                f
                (create-camel-cased-react-method-wrapper k)))))
    (let [class (js/createReactClass class-def)]
      (extend-react-class-with-ifn class)
      class)))

(defn create-factory [type]
  (js/React.createFactory type))


(defn valid-element? [x]
  (js/React.isValidElement x))


(defn render
  ([element container] (js/ReactDOM.render element container))
  ([element container callback] (js/ReactDOM.render element container callback)))


(defn unmount-component-at-node [container]
  (js/ReactDOM.unmountComponentAtNode container))


(defn find-dom-node [instance]
  (js/ReactDOM.findDOMNode instance))


;; (defn pass-to [component property & prepended-arg-fns]
;;   (when-let [f (pval component property)]
;;     (fn [& args]
;;       (apply f (concat (map (fn [f] (f)) prepended-arg-fns) args)))))
