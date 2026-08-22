package it.faccioio.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class RoutePlan(
    val originLatitude: Double,
    val originLongitude: Double,
    val stops: List<TaskItem>,
    val directKilometers: Double
)

internal fun optimizeRouteFromCurrentLocation(
    context: Context,
    candidates: List<TaskItem>,
    result: (RoutePlan?) -> Unit
) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        (context as? Activity)?.let {
            ActivityCompat.requestPermissions(
                it,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                2003
            )
        }
        result(null)
        return
    }
    LocationServices.getFusedLocationProviderClient(context).lastLocation
        .addOnSuccessListener { location ->
            if (location == null) {
                result(null)
                return@addOnSuccessListener
            }
            val remaining = candidates
                .filter { it.latitude != null && it.longitude != null && !it.completed }
                .take(10)
                .toMutableList()
            val ordered = mutableListOf<TaskItem>()
            var latitude = location.latitude
            var longitude = location.longitude
            var kilometers = 0.0
            while (remaining.isNotEmpty()) {
                val next = remaining.minBy { task ->
                    routeDistanceKm(latitude, longitude, task.latitude!!, task.longitude!!)
                }
                kilometers += routeDistanceKm(latitude, longitude, next.latitude!!, next.longitude!!)
                latitude = next.latitude!!
                longitude = next.longitude!!
                ordered += next
                remaining.remove(next)
            }
            result(RoutePlan(location.latitude, location.longitude, ordered, kilometers))
        }
        .addOnFailureListener { result(null) }
}

internal fun openRouteInGoogleMaps(context: Context, plan: RoutePlan) {
    val destination = plan.stops.lastOrNull() ?: return
    val waypoints = plan.stops.dropLast(1).joinToString("|") {
        "${it.latitude},${it.longitude}"
    }
    val uri = Uri.parse(
        "https://www.google.com/maps/dir/?api=1" +
            "&origin=${plan.originLatitude},${plan.originLongitude}" +
            "&destination=${destination.latitude},${destination.longitude}" +
            (if (waypoints.isBlank()) "" else "&waypoints=${Uri.encode(waypoints)}") +
            "&travelmode=driving"
    )
    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
}

private fun routeDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthKm = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return earthKm * 2 * atan2(sqrt(a), sqrt(1 - a))
}
