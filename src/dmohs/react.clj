(ns dmohs.react)


(defmacro defc [name doc-string-or-fn-map & [fn-map]]
  (let [[doc-string fn-map] (if (string? doc-string-or-fn-map)
                              [doc-string-or-fn-map fn-map]
                              [nil doc-string-or-fn-map])]
    `(def ~name (dmohs.react/create-class (merge {:display-name (name '~name)} ~fn-map)))))
