package it.faccioio.app

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

internal fun arrivalGeofenceId(task: TaskItem) =
    "arrival_${task.appointmentTime}_${task.title.hashCode()}"

internal fun ensureLocationPermissions(context: Context): Boolean {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        (context as? Activity)?.let {
            ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 2001)
        }
        Toast.makeText(context, "Concedi la posizione precisa e riprova", Toast.LENGTH_LONG).show()
        return false
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        Toast.makeText(context, "Nelle autorizzazioni della posizione scegli Consenti sempre, poi torna nell’app", Toast.LENGTH_LONG).show()
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        return false
    }
    return true
}

internal fun registerArrivalGeofence(context: Context, id: String, title: String, latitude: Double, longitude: Double, result: (Boolean) -> Unit = {}) {
    if (!ensureLocationPermissions(context)) {
        recordSystemDiagnosticEvent(context, "GEOFENCE NON REGISTRATO", "titolo=$title; motivo=permessi posizione")
        result(false)
        return
    }
    val geofence = Geofence.Builder().setRequestId(id)
        .setCircularRegion(latitude, longitude, 200f)
        .setExpirationDuration(Geofence.NEVER_EXPIRE)
        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER).build()
    val request = GeofencingRequest.Builder().setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER).addGeofence(geofence).build()
    val intent = Intent(context, GeofenceReceiver::class.java).putExtra("task_title", title)
    val pending = PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    try {
        LocationServices.getGeofencingClient(context).addGeofences(request, pending)
            .addOnSuccessListener {
                recordSystemDiagnosticEvent(context, "GEOFENCE REGISTRATO", "titolo=$title; raggio=200m")
                result(true)
            }.addOnFailureListener { error ->
                recordSystemDiagnosticEvent(context, "GEOFENCE NON REGISTRATO", "titolo=$title; errore=${error.javaClass.simpleName}")
                result(false)
            }
    } catch (error: SecurityException) {
        recordSystemDiagnosticEvent(context, "GEOFENCE NON REGISTRATO", "titolo=$title; errore=SecurityException")
        result(false)
    }
}

internal fun removeArrivalGeofence(context: Context, id: String) {
    LocationServices.getGeofencingClient(context).removeGeofences(listOf(id))
        .addOnSuccessListener { recordSystemDiagnosticEvent(context, "GEOFENCE RIMOSSO", "id=${id.hashCode()}") }
        .addOnFailureListener { error -> recordSystemDiagnosticEvent(context, "RIMOZIONE GEOFENCE FALLITA", "id=${id.hashCode()}; errore=${error.javaClass.simpleName}") }
}
