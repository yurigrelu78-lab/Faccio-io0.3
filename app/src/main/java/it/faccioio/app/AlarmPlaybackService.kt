package it.faccioio.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

/** Keeps alarm playback independent from whether Android can display AlarmActivity. */
class AlarmPlaybackService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAlarm()
            stopSelf()
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Attività"
        val reminderTime = intent?.getLongExtra(EXTRA_REMINDER_TIME, 0L) ?: 0L
        val notificationId = intent?.getIntExtra(EXTRA_NOTIFICATION_ID, title.hashCode())
            ?: title.hashCode()

        createPlaybackChannel()
        startForeground(notificationId, buildNotification(title, reminderTime, notificationId))
        startAlarm(title, reminderTime)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }

    private fun buildNotification(title: String, reminderTime: Long, notificationId: Int) =
        NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Sveglia Faccio io")
            .setContentText(title)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(alarmScreenIntent(title, reminderTime, notificationId), true)
            .setContentIntent(alarmScreenIntent(title, reminderTime, notificationId))
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

    private fun alarmScreenIntent(title: String, reminderTime: Long, notificationId: Int): PendingIntent {
        val intent = Intent(this, AlarmActivity::class.java).apply {
            putExtra("task_title", title)
            putExtra("reminder_time", reminderTime)
            putExtra("notification_id", notificationId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun startAlarm(title: String, reminderTime: Long) {
        if (mediaPlayer?.isPlaying == true) return

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        requestExclusiveAudioFocus(audioAttributes)

        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(audioAttributes)
            setDataSource(this@AlarmPlaybackService, alarmUri)
            isLooping = true
            prepare()
            start()
        }

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 800, 400, 800)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
        recordSystemDiagnosticEvent(
            this,
            "RIPRODUZIONE SVEGLIA AVVIATA",
            "titolo=$title; previsto=$reminderTime; servizio=foreground"
        )
    }

    private fun requestExclusiveAudioFocus(attributes: AudioAttributes) {
        audioManager = getSystemService(AudioManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { }
                .build()
            audioManager?.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                null,
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            )
        }
    }

    private fun stopAlarm() {
        mediaPlayer?.runCatching {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        }
        audioFocusRequest = null
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) audioManager?.abandonAudioFocus(null)
        audioManager = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createPlaybackChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            ALARM_CHANNEL_ID,
            "Sveglie Faccio io",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Sveglie insistenti associate alle attività"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ALARM_CHANNEL_ID = "faccio_io_alarms_v2"
        const val ACTION_STOP = "it.faccioio.app.action.STOP_ALARM"
        const val EXTRA_TITLE = "task_title"
        const val EXTRA_REMINDER_TIME = "reminder_time"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
