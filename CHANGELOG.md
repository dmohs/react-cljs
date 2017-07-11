# 1.2.2+15.5.4-1

- Bug fixes.
- `defc-` for "private" components (Isaac Zarsky). Like `defn-`, these are not enforced in ClojureScript, but provide hints about intent.

# 1.2.0+15.5.4-0

- Also don't convert aria attributes to camel-case since.
- `after-update` added as a function (alternative to getting through the argument map).
- `method` returns the bound method of the given name. Useful for non-React event handlers since
  you must provide an identical function when removing.

# 1.1.2+15.5.4-0

- Bump React version.
- Add `cljsjs/create-react-class` to silence deprecation warning of React.createClass.
- Don't convert data attributes to camel-case since those need to remain kebab-case.

# 1.0.2+15.0.2

- Bump React to version 15.

# 1.0.1+0.14.3-1

- Added the React version to the "build metadata" of this library's version number.

# 1.0.0

- Hot reloading now replaces methods in a React component's prototype instead of saving and
  restoring. This is safer and doesn't cause `component-did-mount` to fire.
- Components now implement the `IFn` protocol, allowing method calls, e.g.,
  `(react/call instance :do-stuff)`, to be shortened to, e.g., `(instance :do-stuff)`.
- State and props are duplicated as plain JavaScript objects to make them easy to view in React's
  developer tools for Chrome.
- Including `:trace? true` when creating a class will cause the component to log every method call.
- `create-element` will now accept a plain JavaScript React element.
- Safer internal variable handling to make sure things don't break with advanced optimizations.
