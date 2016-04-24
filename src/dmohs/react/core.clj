(ns dmohs.react.core
  (:require
   [cljs.env :as env]))


(defmacro if-not-optimized [true-form false-form]
  (if (and env/*compiler*
           (let [{:keys [optimizations]} (get @env/*compiler* :options)]
             (or (nil? optimizations) (= optimizations :none))))
    true-form
    false-form))
