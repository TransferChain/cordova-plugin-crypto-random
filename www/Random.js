const exec = require('cordova/exec')

// PLUGIN CONTRACT AND CALLER RESPONSIBILITIES
//
// Call CryptoKit.Random.randomBytes(length) after deviceready. length is a
// positive integer in bytes (1..1,048,576). The Promise resolves to Uint8Array;
// for example, randomBytes(16) returns 16 random bytes, not 16 encoded characters.
// A rejected request has no usable partial result. Callers must not fill missing
// bytes with a fallback source or reuse a previous key/IV after failure.
//
// The native implementation performs allocation and generation off the UI thread.
// This module only adapts the Cordova result; it does not own the application's
// returned byte array. The caller controls that array's lifetime and disposal.
// The application Cryptography service adds queueing and transaction cleanup;
// calling this standalone plugin directly bypasses those application policies.
module.exports = {
  // length is a byte count. Wrap Cordova's binary result as Uint8Array;
  // randomness comes from the native OS provider.
  randomBytes(length) {
    return new Promise((resolve, reject) => {
      // The service/action pair routes to the native plugin. Native code checks the
      // requested size before allocation; success returns bytes through Cordova's
      // binary callback rather than application-managed Base64 conversion.
      exec(
        (buffer) => resolve(new Uint8Array(buffer)),
        reject,
        'Random',
        'randomBytes',
        [{ length }]
      )
    })
  }
}
