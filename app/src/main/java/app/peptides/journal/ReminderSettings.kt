package app.peptides.journal

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch

@Composable internal fun ReminderSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(Reminders.enabled(context)) }
    var revision by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val allowed = remember(revision, enabled) { Reminders.allowed(context) }
    val exact = remember(revision, enabled) { Reminders.exact(context) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        revision++; scope.launch { Reminders.refresh(context) }
    }
    Text(t("Local reminders", "Lembretes locais"), style = MaterialTheme.typography.titleLarge)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(t("Remind me of active protocols", "Lembrar protocolos ativos"), Modifier.weight(1f))
        Switch(enabled, { value ->
            enabled = value
            Reminders.preferences(context).edit().putBoolean("enabled", value).apply()
            if (value && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            scope.launch { Reminders.refresh(context, clearNotification = !value) }
        }, modifier = Modifier.semantics { contentDescription = "Local reminders" })
    }
    Text(t("Works offline, even with the app closed. Tap a reminder to open Today; nothing is marked done automatically.",
        "Funciona offline, mesmo com o app fechado. Toque no lembrete para abrir Hoje; nada é marcado como realizado automaticamente."))
    if (enabled) {
        if (!allowed) Text(t("Notifications are blocked in Android. Allow them to receive reminders.", "As notificações estão bloqueadas no Android. Permita para receber lembretes."), color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) {
            Text(t("Notification settings", "Configurar notificações"))
        }
        if (!exact && Build.VERSION.SDK_INT >= 31) {
            Text(t("Android may delay reminders. Allow precise alarms to request delivery at the scheduled time.",
                "O Android pode atrasar os lembretes. Permita alarmes precisos para solicitar o aviso no horário agendado."))
            OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) }) {
                Text(t("Allow precise alarms", "Permitir alarmes precisos"))
            }
        }
        Text(t("Restarting the phone restores future reminders. Force-stopping the app blocks them until it is reopened. Battery restrictions and Do Not Disturb can affect alerts.",
            "Reiniciar o celular restaura os próximos lembretes. Forçar a parada do app bloqueia os avisos até reabri-lo. Restrições de bateria e Não perturbe podem afetar os alertas."), style = MaterialTheme.typography.bodySmall)
    }
}
