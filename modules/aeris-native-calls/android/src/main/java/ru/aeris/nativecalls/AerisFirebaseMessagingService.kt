package ru.aeris.nativecalls

import android.content.Context
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal class AerisFirebaseMessagingService : FirebaseMessagingService() {
  override fun onCreate() {
    super.onCreate()
    AerisNativeCallsManager.initialize(applicationContext)
  }

  override fun onNewToken(token: String) {
    super.onNewToken(token)
    getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_FCM_TOKEN, token)
      .apply()
    AerisNativeCallsModuleEvents.emit(
      EVENT_PUSH_TOKEN,
      mapOf("token" to token)
    )
  }

  override fun onMessageReceived(message: RemoteMessage) {
    super.onMessageReceived(message)
    val data = message.data
    if (data["type"] != "incoming-call" || isExpired(data["expiresAt"])) return

    val callId = data["callId"] ?: return
    val callerName = data["callerName"] ?: "AERIS Call"
    val hasVideo = data["kind"] == "video"
    AerisNativeCallsManager.reportIncomingCall(callId, callerName, hasVideo)
  }

  private fun isExpired(expiresAt: String?): Boolean {
    if (expiresAt.isNullOrBlank()) return false
    return try {
      val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
      }
      val expiration = parser.parse(expiresAt) ?: return false
      expiration.time <= System.currentTimeMillis()
    } catch (_: ParseException) {
      false
    }
  }

  companion object {
    const val PREFERENCES = "aeris.native.calls"
    const val KEY_FCM_TOKEN = "fcmToken"
    const val EVENT_PUSH_TOKEN = "onPushToken"
  }
}

internal object AerisNativeCallsModuleEvents {
  @Volatile
  var sink: ((String, Map<String, Any?>) -> Unit)? = null

  fun emit(event: String, body: Map<String, Any?>) {
    try {
      sink?.invoke(event, body)
    } catch (_: Throwable) {
      // React Native may not be initialized while the push service is waking the app.
    }
  }
}
