package it.faccioio.app

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun shareDiagnosticReport(context: Context, report: String): Boolean = runCatching {
    val directory = File(context.cacheDir, "diagnostics").apply { mkdirs() }
    directory.listFiles()?.forEach { file ->
        if (System.currentTimeMillis() - file.lastModified() > 7L * 24L * 60L * 60L * 1000L) file.delete()
    }
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ITALIAN).format(Date())
    val file = File(directory, "Faccio-io-diagnostica-$stamp.txt")
    file.writeText(report)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Diagnostica Faccio io")
                putExtra(Intent.EXTRA_TEXT, "Rapporto diagnostico Faccio io in allegato.")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Condividi diagnostica"
        )
    )
    true
}.getOrDefault(false)
