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
