import SwiftUI
import ComposeApp
import Sentry
import SMBClient
import Foundation
import Dispatch
import Darwin

//@main
//struct iOSApp: App {
//    init() {
//        SentrySetupKt.initializeSentry()
//    }
//    var body: some Scene {
//        WindowGroup {
//            ContentView()
//        }
//    }
//}


// Wrapper for iOS 13 Compat
// https://stackoverflow.com/questions/62935053/use-main-in-xcode-12
@main
struct iOSApp {
    private static func bootstrapServices() {
        SentrySetupKt.initializeSentry()
        registerSmbBridgeInvoker()
    }

    static func main() {
        bootstrapServices()

        if #available(iOS 14.0, *) {
            ShowcaseApp.main()
        } else {
            UIApplicationMain(CommandLine.argc, CommandLine.unsafeArgv, nil, NSStringFromClass(AppDelegate.self))
        }
    }
}

private func registerSmbBridgeInvoker() {
    SmbBridgeRegistryKt.registerSmbBridgeInvoker { requestJson in
        requestJson.withCString { requestPtr in
            guard let responsePtr = ShowcaseSmbInvoke(requestPtr) else {
                return nil
            }
            defer {
                free(responsePtr)
            }
            return String(cString: responsePtr)
        }
    }
}

private struct SMBBridgeRequest: Codable {
    let action: String
    let host: String?
    let port: Int?
    let user: String?
    let password: String?
    let sessionId: String?
    let share: String?
    let path: String?
}

private struct SMBBridgeShare: Codable {
    let name: String
}

private struct SMBBridgeEntry: Codable {
    let name: String
    let isDirectory: Bool
    let size: Int64
    let lastWriteTimeMillis: Int64
}

private struct SMBBridgeResponse: Codable {
    let ok: Bool
    let error: String?
    let sessionId: String?
    let shares: [SMBBridgeShare]
    let entries: [SMBBridgeEntry]
    let dataBase64: String?

    static func success(
        sessionId: String? = nil,
        shares: [SMBBridgeShare] = [],
        entries: [SMBBridgeEntry] = [],
        dataBase64: String? = nil
    ) -> SMBBridgeResponse {
        SMBBridgeResponse(
            ok: true,
            error: nil,
            sessionId: sessionId,
            shares: shares,
            entries: entries,
            dataBase64: dataBase64
        )
    }

    static func failure(_ message: String) -> SMBBridgeResponse {
        SMBBridgeResponse(
            ok: false,
            error: message,
            sessionId: nil,
            shares: [],
            entries: [],
            dataBase64: nil
        )
    }
}

private final class SMBBridgeSession {
    let client: SMBClient
    var connectedShare: String?
    let lock = NSLock()

    init(client: SMBClient) {
        self.client = client
    }
}

private enum SMBBridgeSessionStore {
    private static var sessions: [String: SMBBridgeSession] = [:]
    private static let lock = NSLock()

    static func insert(session: SMBBridgeSession) -> String {
        let sessionId = UUID().uuidString
        lock.withLock {
            sessions[sessionId] = session
        }
        return sessionId
    }

    static func get(sessionId: String) -> SMBBridgeSession? {
        lock.withLock {
            sessions[sessionId]
        }
    }

    static func remove(sessionId: String) -> SMBBridgeSession? {
        lock.withLock {
            sessions.removeValue(forKey: sessionId)
        }
    }
}

@_cdecl("ShowcaseSmbInvoke")
public func ShowcaseSmbInvoke(_ requestJson: UnsafePointer<CChar>?) -> UnsafeMutablePointer<CChar>? {
    guard let requestJson else {
        return encodeBridgeResponse(.failure("empty_request"))
    }

    let rawRequest = String(cString: requestJson)

    do {
        let requestData = Data(rawRequest.utf8)
        let request = try JSONDecoder().decode(SMBBridgeRequest.self, from: requestData)
        let response = try handleBridgeRequest(request)
        return encodeBridgeResponse(response)
    } catch {
        return encodeBridgeResponse(.failure(error.localizedDescription))
    }
}

private func handleBridgeRequest(_ request: SMBBridgeRequest) throws -> SMBBridgeResponse {
    switch request.action {
    case "open":
        return try handleOpen(request)
    case "close":
        return try handleClose(request)
    case "listShares":
        return try handleListShares(request)
    case "listDirectory":
        return try handleListDirectory(request)
    case "readFile":
        return try handleReadFile(request)
    default:
        return .failure("unsupported_action")
    }
}

private func handleOpen(_ request: SMBBridgeRequest) throws -> SMBBridgeResponse {
    let host = try required(request.host, field: "host")
    let user = try required(request.user, field: "user")
    let password = try required(request.password, field: "password")
    let port = request.port ?? 445

    let client = SMBClient(host: host, port: port)
    try blockingAwait {
        try await client.login(username: user, password: password)
    }

    let session = SMBBridgeSession(client: client)
    let sessionId = SMBBridgeSessionStore.insert(session: session)
    return .success(sessionId: sessionId)
}

private func handleClose(_ request: SMBBridgeRequest) throws -> SMBBridgeResponse {
    let sessionId = try required(request.sessionId, field: "sessionId")
    guard let session = SMBBridgeSessionStore.remove(sessionId: sessionId) else {
        return .success()
    }

    session.lock.withLock {
        _ = try? blockingAwait {
            if session.connectedShare != nil {
                try await session.client.disconnectShare()
            }
            try await session.client.logoff()
        }
        session.connectedShare = nil
    }

    return .success()
}

private func handleListShares(_ request: SMBBridgeRequest) throws -> SMBBridgeResponse {
    let session = try getSession(request)

    let shares = try session.lock.withLock {
        let shareValues: [Any] = try blockingAwait {
            try await session.client.listShares()
        }

        return shareValues
            .compactMap(extractShare)
    }

    return .success(shares: shares)
}

private func handleListDirectory(_ request: SMBBridgeRequest) throws -> SMBBridgeResponse {
    let session = try getSession(request)
    let share = try required(request.share, field: "share")
    let normalizedDirectoryPath = normalizedPath(request.path)

    let entries = try session.lock.withLock {
        try ensureConnectedShare(session, share: share)

        let fileValues: [Any] = try blockingAwait {
            try await session.client.listDirectory(path: normalizedDirectoryPath)
        }

        return fileValues
            .map(extractEntry)
            .filter { !$0.name.isEmpty }
    }

    return .success(entries: entries)
}

private func handleReadFile(_ request: SMBBridgeRequest) throws -> SMBBridgeResponse {
    let session = try getSession(request)
    let share = try required(request.share, field: "share")
    let normalizedFilePath = normalizedPath(request.path)

    if normalizedFilePath == "/" {
        return .failure("missing_file_path")
    }

    let fileData = try session.lock.withLock {
        try ensureConnectedShare(session, share: share)
        return try blockingAwait {
            try await session.client.download(path: normalizedFilePath)
        }
    }

    return .success(dataBase64: fileData.base64EncodedString())
}

private func ensureConnectedShare(_ session: SMBBridgeSession, share: String) throws {
    if session.connectedShare == share {
        return
    }

    if session.connectedShare != nil {
        _ = try? blockingAwait {
            try await session.client.disconnectShare()
        }
    }

    try blockingAwait {
        try await session.client.connectShare(share)
    }
    session.connectedShare = share
}

private func required(_ value: String?, field: String) throws -> String {
    guard let value, !value.isEmpty else {
        throw NSError(
            domain: "ShowcaseSmbBridge",
            code: -1,
            userInfo: [NSLocalizedDescriptionKey: "missing_field_\(field)"]
        )
    }
    return value
}

private func getSession(_ request: SMBBridgeRequest) throws -> SMBBridgeSession {
    let sessionId = try required(request.sessionId, field: "sessionId")
    guard let session = SMBBridgeSessionStore.get(sessionId: sessionId) else {
        throw NSError(
            domain: "ShowcaseSmbBridge",
            code: -2,
            userInfo: [NSLocalizedDescriptionKey: "session_not_found"]
        )
    }
    return session
}

private func normalizedPath(_ path: String?) -> String {
    guard let path, !path.isEmpty else {
        return "/"
    }

    let trimmed = path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
    if trimmed.isEmpty {
        return "/"
    }
    return "/\(trimmed)"
}

private func extractShare(_ value: Any) -> SMBBridgeShare? {
    if let share = value as? Share {
        // Keep only regular disk shares for user browsing.
        // Exclude protocol/system shares such as IPC/print/device and administrative special shares.
        let baseType = share.type.rawValue & 0x0FFFFFFF
        let isDiskTree = baseType == 0
        let isSystemOrHiddenShare = !isDiskTree || share.type.contains(.special) || share.type.contains(.temporary)

        if isSystemOrHiddenShare {
            return nil
        }

        if share.name.isEmpty {
            return nil
        }

        return SMBBridgeShare(name: share.name)
    }

    if let text = value as? String {
        if text.isEmpty {
            return nil
        }
        if text.hasSuffix("$") || text.caseInsensitiveCompare("IPC$") == .orderedSame {
            return nil
        }
        return SMBBridgeShare(name: text)
    }

    let mirror = Mirror(reflecting: value)
    var name = ""
    var isIpc = false

    for child in mirror.children {
        guard let label = child.label else { continue }
        if label == "name", let text = child.value as? String {
            name = text
        } else if label == "type", let shareType = child.value as? Share.ShareType {
            if shareType.contains(.ipc) {
                isIpc = true
            }
        }
    }

    if name.isEmpty || isIpc || name.hasSuffix("$") || name.caseInsensitiveCompare("IPC$") == .orderedSame {
        return nil
    }

    return SMBBridgeShare(name: name)
}

private func extractEntry(_ value: Any) -> SMBBridgeEntry {
    if let file = value as? File {
        return SMBBridgeEntry(
            name: file.name,
            isDirectory: file.isDirectory,
            size: Int64(clamping: file.size),
            lastWriteTimeMillis: Int64(file.lastWriteTime.timeIntervalSince1970 * 1000)
        )
    }

    var name = ""
    var isDirectory = false
    var size: Int64 = 0
    var lastWriteTimeMillis: Int64 = 0

    let mirror = Mirror(reflecting: value)
    for child in mirror.children {
        guard let label = child.label else { continue }

        switch label {
        case "name":
            if let text = child.value as? String {
                name = text
            }
        case "isDirectory", "directory":
            isDirectory = parseBool(child.value) ?? isDirectory
        case "type":
            if let typeText = child.value as? String {
                isDirectory = typeText.uppercased().contains("DIRECTORY")
            }
        case "size", "fileSize":
            size = parseInt64(child.value) ?? size
        case "fileStat":
            if let fileStat = child.value as? FileStat {
                isDirectory = fileStat.isDirectory
                size = Int64(clamping: fileStat.size)
                lastWriteTimeMillis = Int64(fileStat.lastWriteTime.timeIntervalSince1970 * 1000)
            }
        case "lastWriteTime", "lastModified", "modificationDate", "updatedAt", "createTime":
            if let date = child.value as? Date {
                lastWriteTimeMillis = Int64(date.timeIntervalSince1970 * 1000)
            } else if let millis = parseInt64(child.value) {
                lastWriteTimeMillis = millis
            }
        default:
            continue
        }
    }

    return SMBBridgeEntry(
        name: name,
        isDirectory: isDirectory,
        size: size,
        lastWriteTimeMillis: lastWriteTimeMillis
    )
}

private func parseBool(_ value: Any) -> Bool? {
    if let boolValue = value as? Bool {
        return boolValue
    }
    if let numberValue = value as? NSNumber {
        return numberValue.boolValue
    }
    return nil
}

private func parseInt64(_ value: Any) -> Int64? {
    switch value {
    case let intValue as Int:
        return Int64(intValue)
    case let int8Value as Int8:
        return Int64(int8Value)
    case let int16Value as Int16:
        return Int64(int16Value)
    case let int32Value as Int32:
        return Int64(int32Value)
    case let int64Value as Int64:
        return int64Value
    case let uintValue as UInt:
        return Int64(uintValue)
    case let uint8Value as UInt8:
        return Int64(uint8Value)
    case let uint16Value as UInt16:
        return Int64(uint16Value)
    case let uint32Value as UInt32:
        return Int64(uint32Value)
    case let uint64Value as UInt64:
        return Int64(clamping: uint64Value)
    case let numberValue as NSNumber:
        return numberValue.int64Value
    case let stringValue as String:
        return Int64(stringValue)
    default:
        return nil
    }
}

private func encodeBridgeResponse(_ response: SMBBridgeResponse) -> UnsafeMutablePointer<CChar>? {
    do {
        let data = try JSONEncoder().encode(response)
        let responseString = String(data: data, encoding: .utf8) ?? "{\"ok\":false,\"error\":\"encode_failed\",\"shares\":[],\"entries\":[]}"
        return strdup(responseString)
    } catch {
        return strdup("{\"ok\":false,\"error\":\"encode_failed\",\"shares\":[],\"entries\":[]}")
    }
}

private func blockingAwait<T>(_ operation: @escaping () async throws -> T) throws -> T {
    let semaphore = DispatchSemaphore(value: 0)
    var result: Result<T, Error>?

    Task {
        do {
            let value = try await operation()
            result = .success(value)
        } catch {
            result = .failure(error)
        }
        semaphore.signal()
    }

    semaphore.wait()

    guard let finalResult = result else {
        throw NSError(
            domain: "ShowcaseSmbBridge",
            code: -3,
            userInfo: [NSLocalizedDescriptionKey: "unknown_bridge_error"]
        )
    }

    return try finalResult.get()
}

private extension NSLock {
    func withLock<T>(_ block: () throws -> T) rethrows -> T {
        lock()
        defer { unlock() }
        return try block()
    }
}
