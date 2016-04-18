# 1.0

- Hot reloading now replaces methods in a React component's prototype instead of saving and
  restoring. This is safer and doesn't cause `component-did-mount` to fire.
- A component now implements the IFn protocol, allowing method calls, e.g.,
  `(react/call instance :do-stuff)`, to be shortened to, e.g., `(instance :do-stuff)`.
