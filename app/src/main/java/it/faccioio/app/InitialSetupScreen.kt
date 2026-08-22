package it.faccioio.app

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private const val SETUP_PREFS = "faccio_io_setup"
private const val SETUP_COMPLETE = "initial_setup_complete"
private const val AUTO_START_CONFIRMED = "auto_start_confirmed"

internal fun isInitialSetupComplete(context: Context): Boolean =
    context.getSharedPreferences(SETUP_PREFS, Context.MODE_PRIVATE)
        .getBoolean(SETUP_COMPLETE, false)

internal fun markInitialSetupComplete(context: Context) {
    context.getSharedPreferences(SETUP_PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(SETUP_COMPLETE, true).apply()
}

@Composable
fun InitialSetupScreen(onComplete: () -> Unit, onClose: (() -> Unit)? = null) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    val setupPrefs = remember {
        context.getSharedPreferences(SETUP_PREFS, Context.MODE_PRIVATE)
    }
    var autoStartConfirmed by remember {
        mutableStateOf(setupPrefs.getBoolean(AUTO_START_CONFIRMED, false))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }
    val foregroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh++ }
    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh++ }

    val notificationOk = refresh.let { notificationsAreEnabled(context) }
    val foregroundLocationOk = refresh.let { foregroundLocationIsEnabled(context) }
    val backgroundLocationOk = refresh.let { backgroundLocationIsEnabled(context) }
    val exactAlarmOk = refresh.let { exactAlarmsAreEnabled(context) }
    val batteryOk = refresh.let { batteryIsUnrestricted(context) }
    val allReady = notificationOk && foregroundLocationOk && backgroundLocationOk &&
        exactAlarmOk && batteryOk && autoStartConfirmed

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Configura Faccio io",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            if (onClose != null) TextButton(onClick = onClose) { Text("Chiudi") }
        }
        Text("Completa questi passaggi per ricevere promemoria anche con l’app chiusa e usare gli avvisi legati ai luoghi.")
        Spacer(Modifier.height(16.dp))

        SetupCard(
            title = "Notifiche",
            description = "Mostra e fa vibrare i promemoria.",
            ready = notificationOk,
            button = "Consenti"
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openAppDetails(context, settingsLauncher::launch)
            }
        }

        SetupCard(
            title = "Posizione precisa",
            description = "Serve per cercare luoghi, stimare la partenza e rilevare l’arrivo.",
            ready = foregroundLocationOk,
            button = "Consenti"
        ) {
            foregroundLocationLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        SetupCard(
            title = "Posizione sempre consentita",
            description = "Permette “Avvisami quando arrivo” anche con l’app chiusa.",
            ready = backgroundLocationOk,
            button = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "Apri impostazioni" else "Consenti",
            enabled = foregroundLocationOk
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                openAppDetails(context, settingsLauncher::launch)
            } else {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }

        SetupCard(
            title = "Sveglie e promemoria",
            description = "Autorizza gli allarmi esatti all’orario stabilito.",
            ready = exactAlarmOk,
            button = "Apri impostazioni"
        ) {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            launchSafely(context, intent, settingsLauncher::launch)
        }

        SetupCard(
            title = "Batteria senza limitazioni",
            description = "Evita che il sistema interrompa promemoria e controlli in background.",
            ready = batteryOk,
            button = "Configura"
        ) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            launchSafely(context, intent, settingsLauncher::launch)
        }

        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Avvio automatico", fontWeight = FontWeight.Bold)
                Text(
                    if (Build.MANUFACTURER.equals("Blackview", ignoreCase = true))
                        "Sul Blackview: Gestione sistema → Stivaletto → attiva Faccio io."
                    else
                        "Nelle impostazioni del telefono abilita l’avvio automatico per Faccio io, se presente."
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    openAppDetails(context, settingsLauncher::launch)
                }) { Text("Apri impostazioni app") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = autoStartConfirmed,
                        onCheckedChange = {
                            autoStartConfirmed = it
                            setupPrefs.edit().putBoolean(AUTO_START_CONFIRMED, it).apply()
                        }
                    )
                    Text("Ho attivato l’avvio automatico")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onComplete,
            enabled = allReady,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (allReady) "Inizia a usare Faccio io" else "Completa tutti i passaggi") }
        Text(
            "Puoi riaprire questa verifica dal pulsante Impostazioni nella schermata principale.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun SetupCard(
    title: String,
    description: String,
    ready: Boolean,
    button: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (ready) "✓ Attivo" else "Da configurare")
            }
            Text(description)
            if (!ready) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onClick, enabled = enabled) { Text(button) }
            }
        }
    }
}

private fun notificationsAreEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED)

private fun foregroundLocationIsEnabled(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun backgroundLocationIsEnabled(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun exactAlarmsAreEnabled(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

private fun batteryIsUnrestricted(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(context.packageName)

private fun openAppDetails(context: Context, launch: (Intent) -> Unit) {
    launchSafely(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        },
        launch
    )
}

private fun launchSafely(context: Context, intent: Intent, launch: (Intent) -> Unit) {
    if (intent.resolveActivity(context.packageManager) != null) {
        launch(intent)
    } else {
        launch(Intent(Settings.ACTION_SETTINGS))
    }
}
