import ExpoModulesCore
import UIKit

public class AerisNativeCallsAppDelegateSubscriber: ExpoAppDelegateSubscriber {
  public func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
  ) -> Bool {
    AerisNativeCallsManager.shared.start()
    return true
  }
}
