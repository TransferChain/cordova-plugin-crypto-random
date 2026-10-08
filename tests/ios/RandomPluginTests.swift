import XCTest
import Cordova
import CryptoTestSupport
@testable import NativeCryptoPlugins

final class RandomPluginTests: XCTestCase {
    private func invoke(_ plugin: CDVPlugin, _ action: String, _ args: [Any],
                        file: StaticString = #filePath, line: UInt = #line) throws -> CDVPluginResult {
        let done = expectation(description: action)
        done.assertForOverFulfill = true
        let delegate = CryptoTestDelegate()
        var captured: CDVPluginResult?
        delegate.onResult = { result in
            captured = result
            done.fulfill()
        }
        plugin.commandDelegate = delegate
        let command = CDVInvokedUrlCommand(arguments: args, callbackId: "test",
                                          className: "test", methodName: action)
        _ = plugin.perform(NSSelectorFromString(action + ":"), with: command)
        wait(for: [done], timeout: 60)
        return try XCTUnwrap(captured, file: file, line: line)
    }

    private func ok(_ plugin: CDVPlugin, _ action: String, _ args: [Any],
                    file: StaticString = #filePath, line: UInt = #line) throws -> CDVPluginResult {
        let result = try invoke(plugin, action, args)
        XCTAssertEqual(result.status.intValue, Int(CDVCommandStatus_OK.rawValue), file: file, line: line)
        return result
    }

    private func rejects(_ plugin: CDVPlugin, _ action: String, _ args: [Any], _ code: String,
                         file: StaticString = #filePath, line: UInt = #line) throws {
        let result = try invoke(plugin, action, args)
        XCTAssertEqual(result.status.intValue, Int(CDVCommandStatus_ERROR.rawValue), file: file, line: line)
        let error = try XCTUnwrap(result.message as? [String: Any])
        XCTAssertEqual(error["code"] as? String, code, file: file, line: line)
        XCTAssertFalse((error["message"] as? String ?? "").isEmpty, file: file, line: line)
    }

    private func binary(_ message: Any?) throws -> Data {
        let value = try XCTUnwrap(message as? [String: Any])
        XCTAssertEqual(value["CDVType"] as? String, "ArrayBuffer")
        let encoded = try XCTUnwrap(value["data"] as? String)
        return try XCTUnwrap(Data(base64Encoded: encoded))
    }

    private func parts(_ result: CDVPluginResult) throws -> [Any] {
        let message = try XCTUnwrap(result.message as? [String: Any])
        XCTAssertEqual(message["CDVType"] as? String, "MultiPart")
        return try XCTUnwrap(message["messages"] as? [Any])
    }

    private func hex(_ bytes: Data) -> String {
        return bytes.map { String(format: "%02x", $0) }.joined()
    }

    private func unhex(_ text: String) -> Data {
        let characters = Array(text)
        return Data(stride(from: 0, to: characters.count, by: 2).map {
            UInt8(String(characters[$0...($0 + 1)]), radix: 16)!
        })
    }

    func testRandomBinaryAndBounds() throws {
        let a = try binary(ok(Random(), "randomBytes", [["length": 32]]).message)
        let b = try binary(ok(Random(), "randomBytes", [["length": 32]]).message)
        XCTAssertEqual(a.count, 32)
        XCTAssertNotEqual(a, b)
        for value in [0, -1, 1048577, 1.5, true, "32", NSNull()] as [Any] {
            try rejects(Random(), "randomBytes", [["length": value]], "INVALID_ARGUMENT")
        }
    }

}
