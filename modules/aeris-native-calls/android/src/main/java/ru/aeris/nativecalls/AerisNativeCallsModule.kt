package ru.aeris.nativecalls

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class AerisNativeCallsModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("AerisNativeCalls")

    Events(
      "onPushToken",
      "onIncomingCall",
      "onAnswerCall",
      "onEndCall",
      "onSetActive",
      "onSetInactive",
      "onNativeCallError"
    )

    OnCreate {
      AerisNativeCallsManager.initialize(context)
      val sink = { event: String, body: Map<String, Any?> ->
        try {
          sendEvent(event, body)
        } catch (_: Throwable) {
          // The native call remains valid even if React Native is not mounted yet.
        }
      }
      AerisNativeCallsManager.setEventSink(sink)
      AerisNativeCallsModuleEvents.sink = sink
    }

    OnDestroy {
      AerisNativeCallsManager.setEventSink(null)
      AerisNativeCallsModuleEvents.sink = null
    }

    Function("getPendingCallAction") {
      AerisNativeCallsManager.consumePendingAction()
    }

    AsyncFunction("getPushTokenAsync") { promise: Promise ->
      val stored = context.getSharedPreferences(
        AerisFirebaseMessagingService.PREFERENCES,
        Context.MODE_PRIVATE
      ).getString(AerisFirebaseMessagingService.KEY_FCM_TOKEN, null)

      try {
        FirebaseMessaging.getInstance().token
          .addOnSuccessListener { token ->
            context.getSharedPreferences(
              AerisFirebaseMessagingService.PREFERENCES,
              Context.MODE_PRIVATE
            ).edit().putString(AerisFirebaseMessagingService.KEY_FCM_TOKEN, token).apply()
            promise.resolve(token)
          }
          .addOnFailureListener { error ->
            if (stored != null) {
              promise.resolve(stored)
            } else {
              promise.reject("PUSH_TOKEN_UNAVAILABLE", error.message, error)
            }
          }
      } catch (error: Throwable) {
        if (stored != null) {
          promise.resolve(stored)
        } else {
          promise.reject("FIREBASE_NOT_CONFIGURED", error.message, error)
        }
      }
    }

    AsyncFunction("reportIncomingCallAsync") {
      callId: String, callerName: String, hasVideo: Boolean ->
      AerisNativeCallsManager.reportIncomingCall(callId, callerName, hasVideo)
    }

    Function("reportAnswerResult") { callId: String, success: Boolean ->
      AerisNativeCallsManager.reportAnswerResult(callId, success)
    }

    Function("reportCallEnded") { callId: String, failed: Boolean ->
      AerisNativeCallsManager.reportCallEnded(callId, failed)
    }

    Function("requestEndCall") { callId: String ->
      AerisNativeCallsManager.requestEndCall(callId)
    }
  }

  private val context: Context
    get() = checkNotNull(appContext.reactContext) { "React context is unavailable" }
}
