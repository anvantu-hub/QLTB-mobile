package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
data class LocationHistoryItem(
    val old_latitude: Double? = null,
    val old_longitude: Double? = null,
    val new_latitude: Double? = null,
    val new_longitude: Double? = null,
    val google_maps_url: String? = "",
    val updated_by: String? = "",
    val note: String? = "",
    val timestamp: String = ""
)

private val locationJsonSerializer = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

object LocationService {

    fun buildGoogleMapsUrl(lat: Double, lng: Double): String {
        return "https://www.google.com/maps/search/?api=1&query=$lat,$lng"
    }

    fun isValidGoogleMapsUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return false
        val lower = trimmed.lowercase(Locale.ROOT)
        return (lower.startsWith("http://") || lower.startsWith("https://")) &&
                (lower.contains("google.com/maps") || lower.contains("maps.google.com") || lower.contains("maps.app.goo.gl") || lower.contains("goo.gl/maps"))
    }

    fun appendLocationHistory(
        existingJson: String,
        oldLat: Double?,
        oldLng: Double?,
        newLat: Double?,
        newLng: Double?,
        mapsUrl: String = "",
        updatedBy: String = "",
        note: String = ""
    ): String {
        val list = try {
            if (existingJson.isNotBlank() && existingJson.trim() != "[]") {
                locationJsonSerializer.decodeFromString<List<LocationHistoryItem>>(existingJson).toMutableList()
            } else {
                mutableListOf()
            }
        } catch (_: Exception) {
            mutableListOf()
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timestamp = sdf.format(Date())

        list.add(
            LocationHistoryItem(
                old_latitude = oldLat,
                old_longitude = oldLng,
                new_latitude = newLat,
                new_longitude = newLng,
                google_maps_url = mapsUrl,
                updated_by = updatedBy,
                note = note,
                timestamp = timestamp
            )
        )

        return locationJsonSerializer.encodeToString(list)
    }

    fun openGoogleMaps(
        context: Context,
        latitude: Double?,
        longitude: Double?,
        googleMapsUrl: String = "",
        label: String = "Vị trí"
    ): Boolean {
        if (latitude != null && longitude != null) {
            val encodedLabel = Uri.encode(label)
            val geoUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($encodedLabel)")
            val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(mapIntent)
                return true
            } catch (_: Exception) {
                // Google Maps app not available, open in browser
                val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$latitude,$longitude")
                val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                return try {
                    context.startActivity(webIntent)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }

        if (googleMapsUrl.isNotBlank()) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(googleMapsUrl.trim())).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return try {
                context.startActivity(webIntent)
                true
            } catch (_: Exception) {
                false
            }
        }

        return false
    }

    fun openDirections(
        context: Context,
        latitude: Double?,
        longitude: Double?,
        googleMapsUrl: String = ""
    ): Boolean {
        if (latitude != null && longitude != null) {
            val navUri = Uri.parse("google.navigation:q=$latitude,$longitude")
            val navIntent = Intent(Intent.ACTION_VIEW, navUri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(navIntent)
                return true
            } catch (_: Exception) {
                // Fallback to browser directions
                val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$latitude,$longitude")
                val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                return try {
                    context.startActivity(webIntent)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }

        if (googleMapsUrl.isNotBlank()) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(googleMapsUrl.trim())).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return try {
                context.startActivity(webIntent)
                true
            } catch (_: Exception) {
                false
            }
        }

        return false
    }
}

data class GpsLocationData(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null
)

sealed class LocationFetchResult {
    data class Success(val data: GpsLocationData) : LocationFetchResult()
    data class GpsDisabled(val message: String) : LocationFetchResult()
    data class PermissionDenied(val message: String) : LocationFetchResult()
    data class Error(val message: String) : LocationFetchResult()
}

object LocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun isGpsEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        val gps = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val network = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        return gps || network
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation(context: Context): LocationFetchResult {
        if (!hasLocationPermission(context)) {
            return LocationFetchResult.PermissionDenied("Ứng dụng chưa được cấp quyền truy cập vị trí.")
        }

        if (!isGpsEnabled(context)) {
            return LocationFetchResult.GpsDisabled("Dịch vụ vị trí (GPS) trên thiết bị đang tắt. Vui lòng bật vị trí trong Cài đặt thiết bị.")
        }

        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()

            var loc: Location? = null
            try {
                loc = fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).awaitResult()
            } catch (_: Exception) {}

            if (loc == null) {
                try {
                    loc = fusedClient.lastLocation.awaitResult()
                } catch (_: Exception) {}
            }

            if (loc == null) {
                // Fallback to LocationManager
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                if (lm != null) {
                    loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                }
            }

            if (loc != null && loc.latitude in -90.0..90.0 && loc.longitude in -180.0..180.0) {
                val accuracy = if (loc.hasAccuracy()) loc.accuracy else null
                LocationFetchResult.Success(
                    GpsLocationData(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        accuracy = accuracy
                    )
                )
            } else {
                LocationFetchResult.Error("Không thể xác định tọa độ GPS lúc này. Vui lòng kiểm tra lại thiết bị hoặc di chuyển ra khu vực thông thoáng hơn.")
            }
        } catch (e: Exception) {
            LocationFetchResult.Error("Lỗi đọc cảm biến GPS: ${e.localizedMessage ?: "Không xác định"}")
        }
    }
}

private suspend fun <T> Task<T>.awaitResult(): T? = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result ->
        if (cont.isActive) cont.resume(result)
    }
    addOnFailureListener { exc ->
        if (cont.isActive) cont.resumeWithException(exc)
    }
    addOnCanceledListener {
        if (cont.isActive) cont.cancel()
    }
}
