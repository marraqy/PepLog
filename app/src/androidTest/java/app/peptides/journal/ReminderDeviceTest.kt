package app.peptides.journal

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.ZonedDateTime

class ReminderDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun realAlarmInBackgroundNotificationTapPauseAndDisable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        val db = JournalDb.get(context)
        val settings = context.getSharedPreferences("settings", 0)
        val oldLanguage = settings.getBoolean("pt", false)
        val oldEnabled = Reminders.enabled(context)
        val backup = runBlocking { db.seed(context); Backup.decode(Backup.export(db)) }
        fun shell(command: String) { ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() } }
        try {
            if (!Reminders.allowed(context)) {
                Reminders.preferences(context).edit().putBoolean("enabled", true).commit()
                runBlocking { Reminders.refresh(context) }
                assertEquals(0L, Reminders.preferences(context).getLong("next", 0))
            }
            if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= 31) shell("appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow")
            settings.edit().putBoolean("pt", false).commit()
            Reminders.preferences(context).edit().clear().commit()
            compose.activityRule.scenario.recreate()
            val c = Calculator.calculate("1", "2", "1", "1", "100", "1")
            val entry = Entry(peptideId = "bpc-157", peptideName = "Reminder example", dose = "1", mass = "2", water = "1", capacity = "1", scale = "100", tick = "1",
                concentration = c.concentration.toString(), volume = c.volume.toString(), units = c.units.toString(), doses = c.doses.toString(), remainder = c.remainder.toString())
            var next = ZonedDateTime.now().plusMinutes(1).withSecond(0).withNano(0)
            if (next.toInstant().toEpochMilli() - System.currentTimeMillis() < 15_000) next = next.plusMinutes(1)
            val day = next.toLocalDate().toString()
            val time = next.toLocalTime().toString()
            val protocol = Protocol(name = "Offline reminder test", start = day, status = "active", items = listOf(
                ProtocolItem(entry = entry, from = day, days = (1..7).toList(), times = listOf(time))))
            runBlocking { db.dao().clearProtocols(); db.saveProtocol(protocol) }
            compose.waitUntil(15000) { compose.onAllNodes(hasContentDescription("Settings") or hasContentDescription("Configurações")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasContentDescription("Settings") or hasContentDescription("Configurações")).performClick()
            compose.onNodeWithText("English").performClick()
            compose.onNodeWithContentDescription("Local reminders").performScrollTo().performClick()
            compose.waitUntil(15000) { Reminders.preferences(context).getLong("next", 0) == next.toInstant().toEpochMilli() }
            assertTrue(Reminders.exact(context))
            // No activity in the foreground when Android delivers the alarm.
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            val deadline = next.toInstant().toEpochMilli() + 30_000
            while (manager.activeNotifications.none { it.id == 71 } && System.currentTimeMillis() < deadline) Thread.sleep(250)
            val notification = manager.activeNotifications.single { it.id == 71 }.notification
            assertTrue(notification.extras.getString(Notification.EXTRA_BIG_TEXT)!!.contains("Reminder example"))
            assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
            assertNotNull(notification.publicVersion)
            assertTrue(runBlocking { db.dao().allProtocols().single().document }.contains("\"logs\":[]"))
            val scheduledAfter = Reminders.preferences(context).getLong("next", 0)
            assertTrue(scheduledAfter > next.toInstant().toEpochMilli())
            runBlocking { Reminders.refresh(context) }
            assertEquals(scheduledAfter, Reminders.preferences(context).getLong("next", 0))
            shell("cmd statusbar expand-notifications")
            instrumentation.uiAutomation.waitForIdle(500, 5000)
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            File(context.getExternalFilesDir(null), "reminder-notification-en.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            shell("cmd statusbar collapse")
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            notification.contentIntent.send()
            compose.waitUntil(15000) { compose.onAllNodesWithText("Today.").fetchSemanticsNodes().isNotEmpty() }
            runBlocking { db.saveProtocol(protocol.copy(status = "paused")); Reminders.refresh(context, clearNotification = true) }
            assertEquals(0, Reminders.preferences(context).getLong("next", 0))
            assertTrue(manager.activeNotifications.none { it.id == 71 })
            runBlocking { db.saveProtocol(protocol); Reminders.refresh(context) }
            assertTrue(Reminders.preferences(context).getLong("next", 0) > 0)
            Reminders.preferences(context).edit().putLong("next", 0).commit()
            context.sendBroadcast(Intent(context, ReminderReceiver::class.java).setAction("test.reschedule"))
            compose.waitUntil(15000) { Reminders.preferences(context).getLong("next", 0) > 0 }
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithContentDescription("Local reminders").performScrollTo().performClick()
            compose.waitUntil(15000) { Reminders.preferences(context).getLong("next", 0) == 0L }
            assertFalse(Reminders.enabled(context))
        } finally {
            settings.edit().putBoolean("pt", oldLanguage).commit()
            Reminders.preferences(context).edit().putBoolean("enabled", oldEnabled).commit()
            runBlocking { Backup.restore(db, backup); Reminders.refresh(context, deliver = false, clearNotification = true) }
        }
    }
}
