package it.faccioio.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

private const val ALARM_DIAGNOSTIC_WORK = "faccio_io_alarm_diagnostic"

class AlarmDiagnosticWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        captureAlarmDiagnosticSnapshot(applicationContext, "controllo periodico WorkManager")
        return Result.success()
    }
}

internal fun startAlarmDiagnosticMonitoring(context: Context) {
    val request = PeriodicWorkRequestBuilder<AlarmDiagnosticWorker>(15, TimeUnit.MINUTES).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        ALARM_DIAGNOSTIC_WORK,
        ExistingPeriodicWorkPolicy.UPDATE,
        request
    )
    recordSystemDiagnosticEvent(context, "MONITORAGGIO PERIODICO ATTIVO", "intervallo minimo=15 minuti")
}
