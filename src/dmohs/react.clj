(ns dmohs.react)


(defmacro defc [name doc-string-or-fn-map & [fn-map]]
  (let [[doc-string fn-map] (if (string? doc-string-or-fn-map)
                              [doc-string-or-fn-map fn-map]
                              [nil doc-string-or-fn-map])]
    `(def ~name (dmohs.react/create-class (merge {:display-name (name '~name)} ~fn-map)))))


(defmacro call-self [method-key & args]
  (let [this-sym (gensym "this")]
    `(cljs.core/this-as ~this-sym (dmohs.react/call ~method-key ~this-sym ~@args))))
