# react-cljs

A ClojureScript wrapper for React.

There are a number of React-like libraries for ClojureScript (e.g., Om, Reagent, Rum). Many use React under the covers.

By contrast, this library simply exposes an interface to React in a ClojureScript-friendly way. It is for developers who, like myself, want to use React directly from a ClojureScript application.

### Add dependency:

```clj
[dmohs/react "0.2.12"]
```

### Quickstart Template:

```bash
lein new dmohs.cljs-react my-project
```

See [dmohs.cljs-react template](https://github.com/dmohs/cljs-react-template) for usage.

## Top-Level API

The Top-Level API closely follows React's Top-Level API:

https://facebook.github.io/react/docs/top-level-api.html

### React.Component

*Not applicable.*

### React.createClass

```clj
;; (:require [dmohs/react :as react])
(def MyComponent
  (react/create-class
   {:get-initial-state (fn [] {:click-count 0})
    :render
    (fn [{:keys [this props state refs]}]
      [:div {:style {:border "1px solid black"}}
       "Hello there, " (:name props)
       [:div {:ref "clickable-div"
              :onClick (fn [e] (swap! state update-in [:click-count] inc))}
        "I have been clicked " (:click-count @state) " times."]])
    :component-did-mount
    (fn {:keys [refs]}
      (.focus (@refs "clickable-div")))}))
```

or, using the `defc` macro:

```clj
(react/defc MyComponent
  {:get-initial-state ...
   :render ...
   ...})
```

The `render` method can return either an element or a vector (as in the above example).

### React.createElement

```clj
(def component (react/create-element MyComponent {:initial-click-count 15}))
(react/create-element
 :div {:className "alert" :style {:backgroundColor "red"}}
 component)

;; Vector syntax
(react/create-element [:div {:className "alert"}
                       "Child 1" "Child 2"
                       [MyComponent {:initial-click-count 15}]])
```

### React.cloneElement

*Not yet implemented.*

### React.createFactory

```clj
(create/create-factory string-or-react-class)
```

### React.isValidElement

```clj
(react/valid-element? x)
```

### ReactDOM.render

```clj
(react/render element container callback hot-reload?)
```

If `hot-reload?` is true, component state will be preserved before unmounting and loaded into the newly-rendered tree. This makes Figwheel usage quite easy. I usually pass `goog.DEBUG` as this parameter so it is automatically turned off for minimized/production builds.

### ReactDOM.unmountComponentAtNode

```clj
(react/unmount-component-at-node container)
```

### ReactDOM.findDOMNode

```clj
(create/find-dom-node element)
```

## Component Specifications

Component specifications closely follow React's Component Specifications:

https://facebook.github.io/react/docs/component-specs.html

React methods are defined using Clojure naming conventions (`:get-initial-state` corresponds to `getInitialState`). Additional methods become part of the object, so `:add-foo` can be called like so:
```clj
(react/call :add-foo this "argument 1" "argument 2")
;; or
(react/call :add-foo (@refs "some-child") "argument 1" "argument 2")
```

The special case of calling `this` can be shortened to:
```clj
(react/call-self :add-foo "argument 1" ...)
```

Methods are passed a map with the appropriate keys defined:

```clj
{:this this ; the component instance
 :props props
 :state state ; state atom
 :after-update ; [1]
 :refs refs ; [2]
 :locals ; [3]
 :prev-props prevProps ; when applicable
 :prev-state prevState ; "
 :next-props nextProps ; "
 :next-state nextState ; "
 }
```

1. This is used when you would pass a callback to `setState`, e.g., `(after-update #(.focus (react/find-dom-node this)))`.
2. The `refs` atom allows accessing `this.refs` as a map (e.g., `(.focus (@refs "my-text-box"))`).
3. Convenience atom for local variables. Instead of, e.g., `(set! (.-myTimer this) (js/setTimeout ...))`, you can do `(swap! locals assoc :my-timer (js/setTimeout ...))`.

Note: for non-api methods (like `:add-foo` above), this map is the first argument before any arguments passed when calling the method using `react/call`.

Modifying the `state` atom implicitly calls `this.setState`. This maintains the behavior of React's `this.state` in the way that updates (via `swap!` or `reset!`) are not visible in `@state` until after the component is re-rendered.

Note that `propTypes`, `statics`, and `mixins` are not yet implemented.
