package it.faccioio.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat

class AlarmActivity : ComponentActivity() {
    private var notificationId: Int = 0
    private var title: String = "Attività"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recordSystemDiagnosticEvent(
            this,
            "SCHERMATA SVEGLIA APERTA",
            "titolo=${intent.getStringExtra("task_title")}; previsto=${intent.getLongExtra("reminder_time", 0L)}"
        )
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        title = intent.getStringExtra("task_title") ?: "Attività"
        notificationId = intent.getIntExtra("notification_id", title.hashCode())

        setContent {
            FaccioIoTheme(loadThemeMode(this@AlarmActivity)) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AlarmScreen(
                        title = title,
                        onStop = { stopAndClose() },
                        onSnooze = { snooze() }
                    )
                }
            }
        }
    }

    private fun snooze() {
        val newTime = System.currentTimeMillis() + 10L * 60L * 1000L
        if (scheduleReminder(this, title, newTime, isAlarm = true)) {
            Toast.makeText(this, "Sveglia rimandata di 10 minuti", Toast.LENGTH_SHORT).show()
            stopAndClose()
        }
    }

    private fun stopAndClose() {
        stopService(Intent(this, AlarmPlaybackService::class.java).apply {
            action = AlarmPlaybackService.ACTION_STOP
        })
        NotificationManagerCompat.from(this).cancel(notificationId)
        finishAndRemoveTask()
    }

    companion object {
        const val ALARM_CHANNEL_ID = AlarmPlaybackService.ALARM_CHANNEL_ID
    }
}

@Composable
private fun AlarmScreen(title: String, onStop: () -> Unit, onSnooze: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Faccio io", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Text("È il momento di:", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(40.dp))
        Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
            Text("Spegni")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth()) {
            Text("Rimanda di 10 minuti")
        }
    }
}
