# react-cljs [![Clojars Project](https://img.shields.io/clojars/v/dmohs/react.svg)](https://clojars.org/dmohs/react)

A ClojureScript wrapper for React.

There are a number of React-like libraries for ClojureScript (e.g., Om, Reagent, Rum). This one sets itself apart by embracing the following two philosophies:
1. React is very well-engineered.
2. Objects are an excellent abstraction for UI components.

Because React is well-engineered, this wrapper aims to provide the absolute minimum interface necessary to make React usage seamless from ClojureScript. The primary benefit is that nearly every tool available to React developers in JavaScript (e.g., the full component lifecycle, refs, etc.) is available to users of this wrapper. The secondary benefit is that reading React's documentation, and using good judgement about how ClojureScript differs from JavaScript, is often enough to fully understand how to write code using this wrapper.

Since objects are an excellent abstraction for UI components, this wrapper eschews the usual practice of a function-first interface. Ad-hoc component methods (e.g., `:-handle-submit-button-click`) are natural and encouraged (we tend to prefix private methods with a hyphen by convention, which is not enforced).

Take a look at the [examples](http://dmohs.github.io/react-cljs/examples/) and the [examples source](https://github.com/dmohs/react-cljs/blob/master/src/test/cljs/webui/main.cljs) for usage. An understanding of React via React's excellent documentation will aid in understanding these examples.

If you'd like to try out the examples yourself with Figwheel's amazing hot-reloader, you'll need ruby and docker. Then run:
```sh
./project.rb start
```
to start Figwheel. When it has finished compiling, open http://localhost:3449/.

## Goodies

- **Built-in support for hot-reloading**. If you use, for example, [Figwheel](https://github.com/bhauman/lein-figwheel) to hot-reload files on change, React components created with the `defc` macro will be patched automatically.
- **Method tracing**. Including `:trace? true` in the class definition map will cause every method call to emit a message to the console. This also attempts to break infinite loops by setting a ceiling on the number of traces in a short time period.
- **React Developer Tools support**. Copies component state and props into plain JavaScript in non-optimized compilation modes so it is easier to use React Developer Tools (Chrome extension) to inspect components.
- **Bound methods**. Call `(r/method this :your-method)` to retrieve a method from your instance. Subsequent calls return an identical (not just equal) function, so it can be used in things like `addEventListener`/`removeEventListener`.
- **abind** *experimental*. Binds an atom passed as a property to a key in state. Whenever the atom's value changes, the corresponding state key will receive the new value (and cause a re-render).

### Add dependency:

```cljs
[dmohs/react "1.2.0+15.5.4-1" :as r]
```

## Top-Level API

The Top-Level API closely follows React's Top-Level API:

https://facebook.github.io/react/docs/top-level-api.html

### React.Component

*Not applicable.*

### React.createClass

```cljs
;; (:require [dmohs/react :as r])
(def MyComponent
  (r/create-class
   {:get-initial-state (fn [] {:click-count 0})
    :render
    (fn [{:keys [this props state refs]}]
      [:div {:style {:border "1px solid black"}}
       "Hello there, " (:name props)
       [:div {:ref "clickable-div"
              :on-click (fn [e] (swap! state update-in [:click-count] inc))}
        "I have been clicked " (:click-count @state) " times."]])
    :component-did-mount
    (fn [{:keys [refs]}]
      (.focus (@refs "clickable-div")))}))
```

or, using the `defc` macro (preferred and supports hot-reloading):

```cljs
(r/defc MyComponent
  {:get-initial-state ...
   :render ...
   ...})
```

The `render` method can return either an element or a vector (as in the above example). Pass `:trace? true` for method tracing:

```cljs
(r/defc MyComponent
  {:trace? true
   :get-initial-state ...
   :render ...
   ...})
```

### React.createElement

```cljs
(r/defc MyComponent ...)
(def PlainReactComponent (js/React.createClass ...))
(r/create-element
 :div {:class-name "alert" :style {:background-color "red"}}
 (r/create-element MyComponent {:foo "foo"})
 (r/create-element PlainReactComponent {:bar "bar"}))

;; Vector syntax
(r/create-element [:div {:class-name "alert"}
                       "Child 1" "Child 2"
                       [MyComponent {:initial-click-count 15}]
                       [PlainReactComponent {:initial-click-count 21}]])
```

### React.cloneElement

*Not yet implemented.*

### React.createFactory

```cljs
(r/create-factory string-or-react-class)
```

### React.isValidElement

```cljs
(r/valid-element? x)
```

### ReactDOM.render

```cljs
(r/render element container callback)
```

### ReactDOM.unmountComponentAtNode

```cljs
(r/unmount-component-at-node container)
```

### ReactDOM.findDOMNode

```cljs
(r/find-dom-node element)
```

## Component Specifications

Component specifications closely follow React's Component Specifications:

https://facebook.github.io/react/docs/component-specs.html

React methods are defined using Clojure naming conventions (`:get-initial-state` corresponds to `getInitialState`). Additional methods become part of the object (as in React), so `:add-foo` can be called like so:
```cljs
(r/call :add-foo this "argument 1" "argument 2")
;; or
(r/call :add-foo (@refs "some-child") "argument 1" "argument 2")
```

Additionally, components implement `IFn`, so the above calls can be shortened to:
```cljs
(this :add-foo "argument 1" "argument 2")
;; and
((@refs "some-child") :add-foo "argument 1" "argument 2")
```

Methods are passed a map with the appropriate keys defined:

```cljs
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
 :abind ; [4]
 }
```

1. This is used when you would pass a callback to `setState`, e.g.,

   `(after-update #(.focus (r/find-dom-node this)))`. `after-update` is also defined as a root-level function, so this is identical: `(r/after-update this #(.focus (r/find-dom-node this)))`
2. The `refs` atom allows accessing `this.refs` as a map, e.g., `(.focus (@refs "my-text-box"))`.
3. Convenience atom for local variables. Instead of, e.g.,

   `(set! (.-myTimer this) (js/setTimeout ...))`, you can do

   `(swap! locals assoc :my-timer (js/setTimeout ...))`.
4. Bind a property atom to a key in state, e.g.,

   `(abind :foo)` or `(abind :foo :my-state-key)`

   Returns {state-key value-of-atom} for use in `:get-initial-state`.

Note: for non-api methods (like `:add-foo` above), this map is the first argument before any arguments passed when calling the method using `r/call` or via `this`.

Modifying the `state` atom implicitly calls `this.setState`. This maintains the behavior of React's `this.state` in the way that updates (via `swap!` or `reset!`) are not visible in `@state` until after the component is re-rendered.

Note that `propTypes`, `statics`, and `mixins` are not yet implemented. They may never be, since Clojure to some extent obviates the need for some of these utilities.
