import ExpoModulesCore
import Foundation

public final class AerisNativeCallsModule: Module, @unchecked Sendable {
  private var observers: [NSObjectProtocol] = []

  public func definition() -> ModuleDefinition {
    Name("AerisNativeCalls")

    Events(
      "onVoipToken",
      "onAnswerCall",
      "onEndCall",
      "onAudioSessionActivated",
      "onAudioSessionDeactivated"
    )

    OnCreate {
      AerisNativeCallsManager.shared.start()
      self.installObservers()
    }

    OnDestroy {
      self.removeObservers()
    }

    Function("getVoipToken") {
      AerisNativeCallsManager.shared.currentVoipToken()
    }

    Function("getPendingCallAction") {
      AerisNativeCallsManager.shared.consumePendingAction()
    }

    AsyncFunction("reportIncomingCallAsync") {
      (callId: String, callerName: String, hasVideo: Bool, promise: Promise) in
      AerisNativeCallsManager.shared.reportIncomingCall(
        callId: callId,
        callerName: callerName,
        hasVideo: hasVideo
      ) { error in
        if let error {
          promise.reject("ERR_NATIVE_CALL", error.localizedDescription)
        } else {
          promise.resolve(nil)
        }
      }
    }.runOnQueue(.main)

    AsyncFunction("reportAnswerResult") { (callId: String, success: Bool) in
      AerisNativeCallsManager.shared.fulfillAnswer(callId: callId, success: success)
    }.runOnQueue(.main)

    AsyncFunction("reportCallEnded") { (callId: String, failed: Bool) in
      AerisNativeCallsManager.shared.reportCallEnded(callId: callId, failed: failed)
    }.runOnQueue(.main)

    AsyncFunction("requestEndCall") { (callId: String) in
      AerisNativeCallsManager.shared.requestEndCall(callId: callId)
    }.runOnQueue(.main)
  }

  private func installObservers() {
    guard observers.isEmpty else { return }
    let center = NotificationCenter.default

    observers.append(
      center.addObserver(forName: .aerisVoipTokenChanged, object: nil, queue: .main) {
        [weak self] notification in
        let token = notification.userInfo?["token"]
        self?.sendEvent("onVoipToken", ["token": token ?? NSNull()])
      }
    )
    observers.append(
      center.addObserver(forName: .aerisNativeCallAnswered, object: nil, queue: .main) {
        [weak self] notification in
        guard let callId = notification.userInfo?["callId"] as? String else { return }
        self?.sendEvent("onAnswerCall", ["callId": callId])
      }
    )
    observers.append(
      center.addObserver(forName: .aerisNativeCallEnded, object: nil, queue: .main) {
        [weak self] notification in
        guard let callId = notification.userInfo?["callId"] as? String else { return }
        self?.sendEvent("onEndCall", ["callId": callId])
      }
    )
    observers.append(
      center.addObserver(forName: .aerisCallAudioActivated, object: nil, queue: .main) {
        [weak self] _ in
        self?.sendEvent("onAudioSessionActivated")
      }
    )
    observers.append(
      center.addObserver(forName: .aerisCallAudioDeactivated, object: nil, queue: .main) {
        [weak self] _ in
        self?.sendEvent("onAudioSessionDeactivated")
      }
    )
  }

  private func removeObservers() {
    let center = NotificationCenter.default
    observers.forEach { center.removeObserver($0) }
    observers.removeAll()
  }
}
