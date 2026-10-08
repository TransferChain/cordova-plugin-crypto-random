import Foundation
import CoreFoundation
import Security
#if canImport(Cordova)
import Cordova
#endif

@objc(Random)
final class Random: CDVPlugin {
    @objc(randomBytes:)
    func randomBytes(_ command: CDVInvokedUrlCommand) {
        run("randomBytes", command)
    }

    // Generation runs on Cordova's GCD background queue. Keep local Data alive
    // while constructing the binary callback, then clean it up with defer.
    private func run(_ action: String, _ command: CDVInvokedUrlCommand) {
        commandDelegate.run(inBackground: {
            do {
                guard command.arguments.count == 1,
                      let options = command.arguments[0] as? [String: Any] else {
                    throw Self.invalid("Expected one options object.")
                }

                var value = try Self.perform(options)

                defer { value.resetBytes(in: 0..<value.count) }

                let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAsArrayBuffer: value)

                self.commandDelegate.send(result, callbackId: command.callbackId)
            } catch let error as Failure {
                self.fail(command, error.code, error.message)
            } catch {
                self.fail(command, "OPERATION_FAILED", "Native Random operation failed.")
            }
        })
    }

    // SecRandomCopyBytes uses Apple's secure randomness provider. If the OS
    // reports failure, reject the call rather than generating fallback bytes.
    private static func perform(_ options: [String: Any]) throws -> Data {
        // GENERATION PIPELINE
        // First validate an integer byte count, then allocate exactly that many bytes,
        // then ask the OS-backed provider to fill the allocation. The size limit prevents
        // a malformed bridge request from allocating an arbitrary-sized result.
        //
        // A successful result has exactly the requested length; random bytes need not
        // be printable and can include zero. Do not treat zero bytes as an empty result.
        // Statistical appearance is not an error check: rely on provider success/failure,
        // and never retry solely because output happens to match a previous prefix.
        let length = try integer(options, "length", 1, 1048576)

        // STORAGE VS ENTROPY
        // Data(count:) allocates writable storage, initially zeroed. It becomes a usable
        // random result only after SecRandomCopyBytes succeeds. Native bounds guarantee
        // a positive allocation before the baseAddress is passed to Security.framework.
        // Zero bytes are valid random output and must not be filtered or regenerated.
        // Use provider status as the success signal, not statistical appearance; the
        // local working storage and the caller's returned bytes have distinct lifetimes.
        var output = Data(count: length)

        defer { output.resetBytes(in: 0..<output.count) }

        // Borrow the mutable buffer only for this synchronous OS call. The validated
        // positive length guarantees that the destination has storage.
        let status = output.withUnsafeMutableBytes { buffer in
            SecRandomCopyBytes(kSecRandomDefault, length, buffer.baseAddress!)
        }

        guard status == errSecSuccess else {
            throw Failure(code: "OPERATION_FAILED", message: "Native random generation failed.")
        }

        return output
    }

    private static func integer(_ options: [String: Any], _ name: String,
                                _ min: Int, _ max: Int) throws -> Int {
        // JSON booleans also bridge to NSNumber. Exclude CFBoolean explicitly so true
        // cannot accidentally become a one-byte request.
        guard let value = options[name] as? NSNumber,
              CFGetTypeID(value) != CFBooleanGetTypeID() else {
            throw invalid("\(name) must be an integer.")
        }

        let number = value.doubleValue

        guard number.isFinite, number.rounded(.towardZero) == number,
              number >= Double(min), number <= Double(max) else {
            throw invalid("\(name) is outside the supported integer range.")
        }

        return Int(number)
    }

    private static func invalid(_ message: String) -> Failure {
        return Failure(code: "INVALID_ARGUMENT", message: message)
    }

    private struct Failure: Error {
        let code: String
        let message: String
    }

    private func fail(_ command: CDVInvokedUrlCommand, _ code: String, _ message: String) {
        let error = ["code": code, "message": message,
                     "name": code == "INVALID_ARGUMENT" ? "ArgumentError" : "OperationError"]

        commandDelegate.send(CDVPluginResult(status: CDVCommandStatus_ERROR,
                                            messageAs: error), callbackId: command.callbackId)
    }
}
