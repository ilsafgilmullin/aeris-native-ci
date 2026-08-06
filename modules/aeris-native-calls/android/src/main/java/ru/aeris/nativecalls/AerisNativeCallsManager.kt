package ru.aeris.nativecalls

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.DisconnectCause
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlResult
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

internal object AerisNativeCallsManager {
  private data class ManagedCall(
    val callId: String,
    val callerName: String,
    val callType: Int,
    var control: CallControlScope? = null
  )

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val calls = ConcurrentHashMap<String, ManagedCall>()
  private var applicationContext: Context? = null
  private var callsManager: CallsManager? = null
  private var eventSink: ((String, Map<String, Any?>) -> Unit)? = null

  fun initialize(context: Context) {
    if (applicationContext != null) return
    val appContext = context.applicationContext
    applicationContext = appContext
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      callsManager = CallsManager(appContext)
    }
  }

  fun setEventSink(sink: ((String, Map<String, Any?>) -> Unit)?) {
    eventSink = sink
  }

  fun consumePendingAction(): Bundle? {
    val context = applicationContext ?: return null
    val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    val action = preferences.getString(KEY_PENDING_ACTION, null) ?: return null
    val callId = preferences.getString(KEY_PENDING_CALL_ID, null) ?: return null
    preferences.edit().remove(KEY_PENDING_ACTION).remove(KEY_PENDING_CALL_ID).apply()
    return Bundle().apply {
      putString("action", action)
      putString("callId", callId)
    }
  }

  fun reportIncomingCall(callId: String, callerName: String, hasVideo: Boolean) {
    val context = applicationContext ?: return
    if (calls.containsKey(callId)) return

    val callType = if (hasVideo) {
      CallAttributesCompat.CALL_TYPE_VIDEO_CALL
    } else {
      CallAttributesCompat.CALL_TYPE_AUDIO_CALL
    }
    val managed = ManagedCall(callId, callerName, callType)
    calls[callId] = managed
    AerisCallForegroundService.start(context, callId, callerName, hasVideo)

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      emit("onIncomingCall", callId)
      return
    }

    val manager = callsManager ?: return
    val attributes = CallAttributesCompat(
      callerName,
      Uri.parse("aeriscall:call/$callId"),
      CallAttributesCompat.DIRECTION_INCOMING,
      callType,
      CallAttributesCompat.SUPPORTS_SET_INACTIVE
    )

    scope.launch {
      try {
        manager.addCall(
          attributes,
          onAnswer = { requestedType ->
            storePendingAction("answer", callId)
            emit("onAnswerCall", callId, "callType" to requestedType)
          },
          onDisconnect = { cause ->
            storePendingAction("end", callId)
            emit("onEndCall", callId, "reason" to cause.code)
            finishLocally(callId)
          },
          onSetActive = {
            emit("onSetActive", callId)
          },
          onSetInactive = {
            emit("onSetInactive", callId)
          }
        ) {
          managed.control = this
          emit("onIncomingCall", callId)
        }
      } catch (error: Throwable) {
        emit("onNativeCallError", callId, "message" to (error.message ?: "Core-Telecom error"))
        finishLocally(callId)
      }
    }
  }

  fun answerFromSystem(callId: String) {
    val managed = calls[callId] ?: return
    storePendingAction("answer", callId)
    val control = managed.control
    if (control == null) {
      emit("onAnswerCall", callId, "callType" to managed.callType)
      return
    }

    scope.launch {
      when (control.answer(managed.callType)) {
        is CallControlResult.Success -> emit("onAnswerCall", callId, "callType" to managed.callType)
        is CallControlResult.Error -> emit(
          "onNativeCallError",
          callId,
          "message" to "Android не смог принять звонок"
        )
      }
    }
  }

  fun reportAnswerResult(callId: String, success: Boolean) {
    clearPendingAction(callId)
    if (success) return
    disconnect(callId, DisconnectCause.REMOTE)
  }

  fun declineFromSystem(callId: String) {
    storePendingAction("end", callId)
    disconnect(callId, DisconnectCause.REJECTED)
    emit("onEndCall", callId, "reason" to DisconnectCause.REJECTED)
  }

  fun reportCallEnded(callId: String, failed: Boolean) {
    clearPendingAction(callId)
    disconnect(callId, if (failed) DisconnectCause.MISSED else DisconnectCause.REMOTE)
  }

  fun requestEndCall(callId: String) {
    storePendingAction("end", callId)
    disconnect(callId, DisconnectCause.LOCAL)
    emit("onEndCall", callId, "reason" to DisconnectCause.LOCAL)
  }

  private fun disconnect(callId: String, causeCode: Int) {
    val managed = calls[callId]
    val control = managed?.control
    if (control == null) {
      finishLocally(callId)
      return
    }

    scope.launch {
      control.disconnect(DisconnectCause(causeCode))
      finishLocally(callId)
    }
  }

  private fun finishLocally(callId: String) {
    calls.remove(callId)
    applicationContext?.let { AerisCallForegroundService.stop(it, callId) }
  }

  private fun storePendingAction(action: String, callId: String) {
    applicationContext
      ?.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
      ?.edit()
      ?.putString(KEY_PENDING_ACTION, action)
      ?.putString(KEY_PENDING_CALL_ID, callId)
      ?.apply()
  }

  private fun clearPendingAction(callId: String) {
    val context = applicationContext ?: return
    val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    if (preferences.getString(KEY_PENDING_CALL_ID, null) != callId) return
    preferences.edit().remove(KEY_PENDING_ACTION).remove(KEY_PENDING_CALL_ID).apply()
  }

  private fun emit(event: String, callId: String, vararg values: Pair<String, Any>) {
    val body = buildMap<String, Any?> {
      put("callId", callId)
      values.forEach { (key, value) -> put(key, value) }
    }
    eventSink?.invoke(event, body)
  }

  private const val PREFERENCES = "aeris.native.calls"
  private const val KEY_PENDING_ACTION = "pendingAction"
  private const val KEY_PENDING_CALL_ID = "pendingCallId"
}
