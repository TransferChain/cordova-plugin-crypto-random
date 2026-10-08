# Usage and API

All examples run inside an async function after Cordova `deviceready`. They call
the public JavaScript module registered by plugin.xml. Consumer functions such
as consumeKey are placeholders supplied by the caller.

## Interface

`CryptoKit.Random.randomBytes(length)` returns Promise<Uint8Array>. Length is an
integer byte count from 1 to 1048576. There is no seeded or Math.random
fallback. Native OS generation runs outside the UI thread.

```js
const bytes = await CryptoKit.Random.randomBytes(32)
try {
  await consumeBytes(bytes)
} finally {
  bytes.fill(0)
}
```

consumeBytes is your asynchronous consumer. The returned bytes belong to you.
Rejecting a request does not supply a partial usable result.

## Errors and lifecycle

Handle rejected promises or failure callbacks explicitly. Do not substitute
plaintext, predictable keys or weaker algorithms after failure. Keep caller
buffers valid until async work completes, and wipe only buffers you own.

[Developer guide](../DEVELOPMENT.md) · [Security policy](../SECURITY.md) ·
[README](../README.md)
