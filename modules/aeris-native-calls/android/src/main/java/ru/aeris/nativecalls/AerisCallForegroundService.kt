package ru.aeris.nativecalls

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

internal class AerisCallForegroundService : Service() {
  override fun onCreate() {
    super.onCreate()
    createChannel()
    AerisNativeCallsManager.initialize(applicationContext)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val callId = intent?.getStringExtra(EXTRA_CALL_ID) ?: return START_NOT_STICKY
    when (intent.action) {
      ACTION_ANSWER -> {
        AerisNativeCallsManager.answerFromSystem(callId)
        return START_NOT_STICKY
      }
      ACTION_DECLINE -> {
        AerisNativeCallsManager.declineFromSystem(callId)
        stopSelf()
        return START_NOT_STICKY
      }
    }

    val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "AERIS Call"
    val hasVideo = intent.getBooleanExtra(EXTRA_HAS_VIDEO, false)
    val notification = incomingNotification(callId, callerName, hasVideo)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
    return START_NOT_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun incomingNotification(callId: String, callerName: String, hasVideo: Boolean): Notification {
    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra(EXTRA_CALL_ID, callId)
    }
    val contentIntent = launchIntent?.let {
      PendingIntent.getActivity(
        this,
        callId.hashCode(),
        it,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )
    }
    val answerIntent = servicePendingIntent(ACTION_ANSWER, callId, 1)
    val declineIntent = servicePendingIntent(ACTION_DECLINE, callId, 2)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val caller = Person.Builder().setName(callerName).setImportant(true).build()
      return Notification.Builder(this, CHANNEL_ID)
        .setSmallIcon(applicationInfo.icon)
        .setContentTitle(callerName)
        .setContentText(if (hasVideo) "Входящий видеозвонок" else "Входящий аудиозвонок")
        .setContentIntent(contentIntent)
        .setCategory(Notification.CATEGORY_CALL)
        .setOngoing(true)
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .setStyle(Notification.CallStyle.forIncomingCall(caller, declineIntent, answerIntent))
        .build()
    }

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(applicationInfo.icon)
      .setContentTitle(callerName)
      .setContentText(if (hasVideo) "Входящий видеозвонок" else "Входящий аудиозвонок")
      .setContentIntent(contentIntent)
      .setCategory(NotificationCompat.CATEGORY_CALL)
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setOngoing(true)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .addAction(0, "Отклонить", declineIntent)
      .addAction(0, "Ответить", answerIntent)
      .build()
  }

  private fun servicePendingIntent(action: String, callId: String, requestOffset: Int): PendingIntent {
    val intent = Intent(this, AerisCallForegroundService::class.java).apply {
      this.action = action
      putExtra(EXTRA_CALL_ID, callId)
    }
    return PendingIntent.getService(
      this,
      callId.hashCode() + requestOffset,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  private fun createChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = getSystemService(NotificationManager::class.java)
    val channel = NotificationChannel(
      CHANNEL_ID,
      "Входящие звонки",
      NotificationManager.IMPORTANCE_HIGH
    ).apply {
      description = "Системные уведомления о входящих звонках AERIS Call"
      lockscreenVisibility = Notification.VISIBILITY_PUBLIC
      setSound(null, null)
    }
    manager.createNotificationChannel(channel)
  }

  companion object {
    private const val CHANNEL_ID = "aeris_incoming_calls"
    private const val NOTIFICATION_ID = 7411
    private const val ACTION_ANSWER = "ru.aeris.nativecalls.ANSWER"
    private const val ACTION_DECLINE = "ru.aeris.nativecalls.DECLINE"
    private const val EXTRA_CALL_ID = "callId"
    private const val EXTRA_CALLER_NAME = "callerName"
    private const val EXTRA_HAS_VIDEO = "hasVideo"

    fun start(context: Context, callId: String, callerName: String, hasVideo: Boolean) {
      val intent = Intent(context, AerisCallForegroundService::class.java).apply {
        putExtra(EXTRA_CALL_ID, callId)
        putExtra(EXTRA_CALLER_NAME, callerName)
        putExtra(EXTRA_HAS_VIDEO, hasVideo)
      }
      ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context, callId: String) {
      context.stopService(
        Intent(context, AerisCallForegroundService::class.java).apply {
          putExtra(EXTRA_CALL_ID, callId)
        }
      )
    }
  }
}
