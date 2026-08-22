package it.faccioio.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlin.math.*

data class DepartureEstimate(
    val departureTime: Long,
    val travelMinutes: Int,
    val marginMinutes: Int
)

internal fun estimateDepartureFromCurrentLocation(
    context: Context,
    appointmentTime: Long,
    destinationLatitude: Double,
    destinationLongitude: Double,
    transport: String,
    safety: String,
    result: (DepartureEstimate?) -> Unit
) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        (context as? Activity)?.let {
            ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 2002)
        }
        result(null)
        return
    }
    LocationServices.getFusedLocationProviderClient(context).lastLocation
        .addOnSuccessListener { location ->
            if (location == null) { result(null); return@addOnSuccessListener }
            val directKm = haversineKm(location.latitude, location.longitude, destinationLatitude, destinationLongitude)
            val roadKm = directKm * when (transport) { "A piedi" -> 1.18; "Bicicletta" -> 1.22; else -> 1.32 }
            val speed = when (transport) {
                "A piedi" -> 4.5
                "Bicicletta" -> 15.0
                else -> when { roadKm < 15 -> 30.0; roadKm < 60 -> 50.0; else -> 65.0 }
            }
            val travel = max(1, ceil(roadKm / speed * 60.0).toInt())
            val margin = when (safety) {
                "Ridotto" -> ceil(travel * 0.15).toInt() + 5
                "Prudente" -> ceil(travel * 0.45).toInt() + 15
                else -> ceil(travel * 0.30).toInt() + 10
            }
            result(DepartureEstimate(appointmentTime - (travel + margin) * 60_000L, travel, margin))
        }.addOnFailureListener { result(null) }
}

private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthKm = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return earthKm * 2 * atan2(sqrt(a), sqrt(1 - a))
}
