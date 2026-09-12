package app.peptides.journal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.Locale

@Composable internal fun AppLanguage(portuguese: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val localized = remember(context, configuration, portuguese) {
        val localizedResources = context.createConfigurationContext(Configuration(configuration).apply {
            setLocale(if (portuguese) Locale.forLanguageTag("pt-BR") else Locale.US)
        }).resources
        // Keep the Activity in the context chain for activity-result and back handlers.
        object : ContextWrapper(context) {
            override fun getResources() = localizedResources
            override fun getAssets() = localizedResources.assets
        }
    }
    CompositionLocalProvider(LocalLanguage provides portuguese, LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration, content = content)
}

@Composable internal fun currentDay(now: () -> ZonedDateTime): LocalDate {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val clock by rememberUpdatedState(now)
    var day by remember { mutableStateOf(clock().toLocalDate()) }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(context, lifecycle) {
        fun refresh() { day = clock().toLocalDate(); revision++ }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = refresh()
        }
        lifecycle.addObserver(observer)
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_DATE_CHANGED)
        })
        refresh()
        onDispose { lifecycle.removeObserver(observer); context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(revision) {
        while (true) {
            val instant = clock()
            day = instant.toLocalDate()
            delay(Duration.between(instant, instant.toLocalDate().plusDays(1).atStartOfDay(instant.zone)).toMillis().coerceAtLeast(1))
        }
    }
    return day
}
