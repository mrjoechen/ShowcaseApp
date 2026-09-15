import SwiftUI
import ComposeApp
import Sentry
import SMBClient
import Foundation
import Dispatch
import Darwin

// Register the native SMB bridge before SwiftUI creates the Compose content.
@main
struct iOSApp {
    private static func bootstrapServices() {
        registerSmbBridgeInvoker()
    }

    static func main() {
        bootstrapServices()

        ShowcaseApp.main()
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
    let destination: String?
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
    case "downloadFile":
        return try handleDownloadFile(request)
    case "validate":
        let session = try getSession(request)
        _ = try session.lock.withLock {
            try awaitSession(session) { try await session.client.session.echo() }
        }
        return .success()
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
    do {
        try blockingAwait(onTimeout: { client.session.disconnect() }) {
            try await client.login(username: user, password: password)
        }
    } catch {
        client.session.disconnect()
        throw error
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
        // Retiring an idle/failed connection must not delay the next image on a dead server.
        session.client.session.disconnect()
        session.connectedShare = nil
    }

    return .success()
}

private func handleListShares(_ request: SMBBridgeRequest) throws -> SMBBridgeResponse {
    let session = try getSession(request)

    let shares = try session.lock.withLock {
        let shareValues: [Any] = try awaitSession(session) {
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

        let fileValues: [Any] = try awaitSession(session) {
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
        return try awaitSession(session) {
            try await session.client.download(path: normalizedFilePath)
        }
    }

    return .success(dataBase64: fileData.base64EncodedString())
}

/// Kotlin owns this unique temporary file. Only completed bytes can be published to its cache.
private func handleDownloadFile(_ request: SMBBridgeRequest) throws -> SMBBridgeResponse {
    let session = try getSession(request)
    let share = try required(request.share, field: "share")
    let remotePath = normalizedPath(request.path)
    let destination = URL(fileURLWithPath: try required(request.destination, field: "destination"))
    try session.lock.withLock {
        try ensureConnectedShare(session, share: share)
        // Create the temporary file exclusively and report write errors through errno.
        var descriptor = Darwin.open(destination.path, O_WRONLY | O_CREAT | O_EXCL, S_IRUSR | S_IWUSR)
        guard descriptor >= 0 else { throw fileWriteError() }
        defer { if descriptor >= 0 { Darwin.close(descriptor) } }
        let reader = session.client.fileReader(path: remotePath)
        var timedOut = false
        let disconnectOnTimeout = {
            timedOut = true
            session.connectedShare = nil
            session.client.session.disconnect()
        }
        do {
            let size = try blockingAwait(onTimeout: disconnectOnTimeout) { try await reader.fileSize }
            var offset: UInt64 = 0
            while offset < size {
                let readOffset = offset
                let length = UInt32(min(1024 * 1024, size - offset))
                // Bound each network wait, not the entire large-file download. The async
                // worker only returns a chunk; it never writes after a timeout returns.
                let chunk = try blockingAwait(onTimeout: disconnectOnTimeout) {
                    try await reader.read(offset: readOffset, length: length)
                }
                guard !chunk.isEmpty && chunk.count <= Int(length) else {
                    throw URLError(.networkConnectionLost)
                }
                try writeImageChunk(chunk, to: descriptor)
                offset += UInt64(chunk.count)
            }
            try blockingAwait(onTimeout: disconnectOnTimeout) { try await reader.close() }
        } catch {
            // A timed-out read can still be unwinding. Its disconnected session must not
            // receive another asynchronous command on the same reader.
            if !timedOut {
                _ = try? blockingAwait(onTimeout: disconnectOnTimeout) { try await reader.close() }
            }
            throw error
        }
        let closeResult = Darwin.close(descriptor)
        descriptor = -1
        guard closeResult == 0 else { throw fileWriteError() }
    }
    return .success()
}

private func fileWriteError() -> POSIXError {
    POSIXError(POSIXErrorCode(rawValue: errno) ?? .EIO)
}

private func writeImageChunk(_ data: Data, to descriptor: Int32) throws {
    try data.withUnsafeBytes { (bytes: UnsafeRawBufferPointer) in
        guard let base = bytes.baseAddress else { return }
        var offset = 0
        while offset < bytes.count {
            let count = Darwin.write(descriptor, base.advanced(by: offset), bytes.count - offset)
            if count < 0 {
                if errno == EINTR { continue }
                throw fileWriteError()
            }
            guard count > 0 else { throw POSIXError(.EIO) }
            offset += count
        }
    }
}

private func ensureConnectedShare(_ session: SMBBridgeSession, share: String) throws {
    if session.connectedShare == share {
        return
    }

    if session.connectedShare != nil {
        try awaitSession(session) {
            try await session.client.disconnectShare()
        }
        session.connectedShare = nil
    }

    try awaitSession(session) {
        try await session.client.connectShare(share)
    }
    session.connectedShare = share
}

/// Called under the session lock; a timeout invalidates the transport before releasing that lock.
private func awaitSession<T>(_ session: SMBBridgeSession, _ operation: @escaping () async throws -> T) throws -> T {
    try blockingAwait(onTimeout: {
        session.connectedShare = nil
        session.client.session.disconnect()
    }, operation)
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

private final class BridgeResult<T>: @unchecked Sendable {
    let lock = NSLock()
    var value: Result<T, Error>?
}

private func blockingAwait<T>(onTimeout: @escaping () -> Void = {}, _ operation: @escaping () async throws -> T) throws -> T {
    let semaphore = DispatchSemaphore(value: 0)
    let result = BridgeResult<T>()

    let task = Task {
        do {
            let value = try await operation()
            result.lock.withLock { result.value = .success(value) }
        } catch {
            result.lock.withLock { result.value = .failure(error) }
        }
        semaphore.signal()
    }

    if semaphore.wait(timeout: .now() + 30) == .timedOut {
        task.cancel()
        onTimeout()
        throw NSError(domain: "ShowcaseSmbBridge", code: -4,
            userInfo: [NSLocalizedDescriptionKey: "SMB operation timed out"])
    }

    guard let finalResult = result.lock.withLock({ result.value }) else {
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
