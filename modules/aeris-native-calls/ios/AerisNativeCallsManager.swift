import AVFoundation
import CallKit
import Foundation
import PushKit
import UIKit

extension Notification.Name {
  static let aerisVoipTokenChanged = Notification.Name("AerisVoipTokenChanged")
  static let aerisNativeCallAnswered = Notification.Name("AerisNativeCallAnswered")
  static let aerisNativeCallEnded = Notification.Name("AerisNativeCallEnded")
  static let aerisCallAudioActivated = Notification.Name("AerisCallAudioActivated")
  static let aerisCallAudioDeactivated = Notification.Name("AerisCallAudioDeactivated")
}

public final class AerisNativeCallsManager: NSObject, PKPushRegistryDelegate, CXProviderDelegate, @unchecked Sendable {
  public static let shared = AerisNativeCallsManager()

  private let provider: CXProvider
  private let callController = CXCallController()
  private var pushRegistry: PKPushRegistry?
  private var pendingAnswerActions: [UUID: CXAnswerCallAction] = [:]
  private let tokenDefaultsKey = "aeris.voip.push.token"
  private let pendingActionKey = "aeris.native.call.pending.action"
  private let pendingCallIdKey = "aeris.native.call.pending.id"
  private let iso8601Formatter: ISO8601DateFormatter = {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    return formatter
  }()

  public override init() {
    let configuration = CXProviderConfiguration()
    configuration.supportsVideo = true
    configuration.maximumCallGroups = 1
    configuration.maximumCallsPerCallGroup = 1
    configuration.supportedHandleTypes = [.generic]
    configuration.includesCallsInRecents = true
    provider = CXProvider(configuration: configuration)
    super.init()
    provider.setDelegate(self, queue: .main)
  }

  public func start() {
    DispatchQueue.main.async {
      guard self.pushRegistry == nil else { return }
      let registry = PKPushRegistry(queue: .main)
      registry.delegate = self
      registry.desiredPushTypes = [.voIP]
      self.pushRegistry = registry
    }
  }

  public func currentVoipToken() -> String? {
    UserDefaults.standard.string(forKey: tokenDefaultsKey)
  }

  public func consumePendingAction() -> [String: String]? {
    let defaults = UserDefaults.standard
    guard
      let action = defaults.string(forKey: pendingActionKey),
      let callId = defaults.string(forKey: pendingCallIdKey)
    else {
      return nil
    }
    defaults.removeObject(forKey: pendingActionKey)
    defaults.removeObject(forKey: pendingCallIdKey)
    return ["action": action, "callId": callId]
  }

  public func reportIncomingCall(
    callId: String,
    callerName: String,
    hasVideo: Bool,
    completion: @escaping (Error?) -> Void
  ) {
    guard let uuid = UUID(uuidString: callId) else {
      completion(NSError(domain: "AerisNativeCalls", code: 1, userInfo: [
        NSLocalizedDescriptionKey: "Invalid call UUID"
      ]))
      return
    }

    let update = CXCallUpdate()
    update.remoteHandle = CXHandle(type: .generic, value: callerName)
    update.localizedCallerName = callerName
    update.hasVideo = hasVideo
    update.supportsHolding = false
    update.supportsGrouping = false
    update.supportsUngrouping = false
    update.supportsDTMF = false
    provider.reportNewIncomingCall(with: uuid, update: update, completion: completion)
  }

  public func fulfillAnswer(callId: String, success: Bool) {
    clearPendingAction(callId: callId)
    guard
      let uuid = UUID(uuidString: callId),
      let action = pendingAnswerActions.removeValue(forKey: uuid)
    else {
      return
    }
    if success {
      action.fulfill()
    } else {
      action.fail()
    }
  }

  public func reportCallEnded(callId: String, failed: Bool = false) {
    clearPendingAction(callId: callId)
    guard let uuid = UUID(uuidString: callId) else { return }
    pendingAnswerActions.removeValue(forKey: uuid)?.fail()
    provider.reportCall(
      with: uuid,
      endedAt: Date(),
      reason: failed ? .failed : .remoteEnded
    )
  }

  public func requestEndCall(callId: String) {
    guard let uuid = UUID(uuidString: callId) else { return }
    let transaction = CXTransaction(action: CXEndCallAction(call: uuid))
    callController.request(transaction) { _ in }
  }

  public func pushRegistry(
    _ registry: PKPushRegistry,
    didUpdate pushCredentials: PKPushCredentials,
    for type: PKPushType
  ) {
    guard type == .voIP else { return }
    let token = pushCredentials.token.map { String(format: "%02x", $0) }.joined()
    UserDefaults.standard.set(token, forKey: tokenDefaultsKey)
    NotificationCenter.default.post(
      name: .aerisVoipTokenChanged,
      object: nil,
      userInfo: ["token": token]
    )
  }

  public func pushRegistry(
    _ registry: PKPushRegistry,
    didInvalidatePushTokenFor type: PKPushType
  ) {
    guard type == .voIP else { return }
    UserDefaults.standard.removeObject(forKey: tokenDefaultsKey)
    NotificationCenter.default.post(
      name: .aerisVoipTokenChanged,
      object: nil,
      userInfo: ["token": NSNull()]
    )
  }

  public func pushRegistry(
    _ registry: PKPushRegistry,
    didReceiveIncomingPushWith payload: PKPushPayload,
    for type: PKPushType,
    completion: @escaping @Sendable () -> Void
  ) {
    guard type == .voIP else {
      completion()
      return
    }

    let dictionary = payload.dictionaryPayload
    guard
      let callId = dictionary["callId"] as? String,
      let callerName = dictionary["callerName"] as? String
    else {
      completion()
      return
    }
    let hasVideo = (dictionary["kind"] as? String) == "video"
    let expired = (dictionary["expiresAt"] as? String)
      .flatMap { iso8601Formatter.date(from: $0) }
      .map { $0 <= Date() }
      ?? false

    reportIncomingCall(callId: callId, callerName: callerName, hasVideo: hasVideo) { [weak self] error in
      if error == nil, expired, let uuid = UUID(uuidString: callId) {
        self?.provider.reportCall(with: uuid, endedAt: Date(), reason: .unanswered)
      }
      completion()
    }
  }

  public func providerDidReset(_ provider: CXProvider) {
    pendingAnswerActions.values.forEach { $0.fail() }
    pendingAnswerActions.removeAll()
  }

  public func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
    let callId = action.callUUID.uuidString.lowercased()
    pendingAnswerActions[action.callUUID] = action
    storePendingAction("answer", callId: callId)
    NotificationCenter.default.post(
      name: .aerisNativeCallAnswered,
      object: nil,
      userInfo: ["callId": callId]
    )
  }

  public func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
    let callId = action.callUUID.uuidString.lowercased()
    pendingAnswerActions.removeValue(forKey: action.callUUID)?.fail()
    storePendingAction("end", callId: callId)
    NotificationCenter.default.post(
      name: .aerisNativeCallEnded,
      object: nil,
      userInfo: ["callId": callId]
    )
    action.fulfill()
  }

  public func provider(_ provider: CXProvider, didActivate audioSession: AVAudioSession) {
    NotificationCenter.default.post(name: .aerisCallAudioActivated, object: nil)
  }

  public func provider(_ provider: CXProvider, didDeactivate audioSession: AVAudioSession) {
    NotificationCenter.default.post(name: .aerisCallAudioDeactivated, object: nil)
  }

  private func storePendingAction(_ action: String, callId: String) {
    let defaults = UserDefaults.standard
    defaults.set(action, forKey: pendingActionKey)
    defaults.set(callId, forKey: pendingCallIdKey)
  }

  private func clearPendingAction(callId: String) {
    let defaults = UserDefaults.standard
    guard defaults.string(forKey: pendingCallIdKey) == callId else { return }
    defaults.removeObject(forKey: pendingActionKey)
    defaults.removeObject(forKey: pendingCallIdKey)
  }
}
