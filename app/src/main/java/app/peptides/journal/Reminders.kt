package app.peptides.journal

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.time.ZoneId

object Reminders {
    const val CHANNEL = "protocol_reminders"
    const val ALARM = "app.peptides.journal.REMINDER"
    const val OPEN_TODAY = "app.peptides.journal.OPEN_TODAY"
    private val mutex = Mutex()
    fun preferences(context: Context) = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
    fun enabled(context: Context) = preferences(context).getBoolean("enabled", false)
    fun exact(context: Context): Boolean = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    fun allowed(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    private fun pending(context: Context) = PendingIntent.getBroadcast(context, 71,
        Intent(context, ReminderReceiver::class.java).setAction(ALARM), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    suspend fun refresh(context: Context, deliver: Boolean = true, clearNotification: Boolean = false) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val prefs = preferences(context)
            val alarms = context.getSystemService(AlarmManager::class.java)
            val manager = context.getSystemService(NotificationManager::class.java)
            val pt = context.getSharedPreferences("settings", 0).getBoolean("pt", false)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, if (pt) "Lembretes de protocolos" else "Protocol reminders", NotificationManager.IMPORTANCE_DEFAULT))
            if (clearNotification || !enabled(context)) manager.cancel(71)
            val old = prefs.getLong("next", 0)
            val zone = ZoneId.systemDefault()
            val sameZone = prefs.getString("zone", "") == zone.id
            val now = System.currentTimeMillis()
            val protocols = if (enabled(context)) JournalDb.get(context).dao().allProtocols().map { ProtocolCodec.decode(JSONObject(it.document)) } else emptyList()
            val due = if (deliver && sameZone && old > 0 && allowed(context)) ReminderPlan.due(protocols, old, now, zone) else emptyList()
            val next = if (allowed(context)) ReminderPlan.next(protocols, now, zone) else null
            alarms.cancel(pending(context))
            prefs.edit().putLong("next", next ?: 0).putString("zone", zone.id).commit()
            if (next != null) {
                try {
                    if (exact(context)) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending(context))
                    else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending(context))
                } catch (_: SecurityException) {
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending(context))
                }
            }
            if (due.isNotEmpty()) {
                val title = if (pt) "PepLog · Lembrete de protocolo" else "PepLog · Protocol reminder"
                val summary = if (pt) "${due.size} item(ns) na agenda. Toque para abrir Hoje." else "${due.size} scheduled item(s). Tap to open Today."
                val details = due.take(5).joinToString("\n") { "${it.time} · ${it.item.entry.peptideName} — ${it.protocol.name}" }
                val open = PendingIntent.getActivity(context, 71, Intent(context, MainActivity::class.java).setAction(OPEN_TODAY)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val public = Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_reminder).setContentTitle("PepLog")
                    .setContentText(if (pt) "Confira sua agenda." else "Check your schedule.").build()
                val notification = Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_reminder).setContentTitle(title)
                    .setContentText(summary).setStyle(Notification.BigTextStyle().bigText("$summary\n$details"))
                    .setContentIntent(open).setAutoCancel(true).setCategory(Notification.CATEGORY_REMINDER)
                    .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(public).build()
                try { manager.notify(71, notification) } catch (_: SecurityException) { /* Permission changed while preparing the reminder. */ }
            }
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { Reminders.refresh(context.applicationContext, deliver = intent.action == Reminders.ALARM) }
            catch (error: Exception) { android.util.Log.e("PepLogReminders", "Could not refresh reminders", error) }
            finally { result.finish() }
        }
    }
}
