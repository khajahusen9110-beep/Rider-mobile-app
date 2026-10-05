package com.example.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** Address search and reverse geocoding with the platform geocoder (no API key needed). */
class Geocoding(context: Context) {
    private val geocoder = Geocoder(context.applicationContext, Locale.forLanguageTag("en-IN"))

    val isAvailable: Boolean get() = Geocoder.isPresent()

    /** Places matching [query], preferring results near [near]. */
    suspend fun search(query: String, near: Place?): List<Place> {
        if (query.trim().length < 3 || !isAvailable) return emptyList()
        val text = if (query.contains("india", ignoreCase = true)) query else "$query, India"
        val results = withTimeoutOrNull(TIMEOUT_MS) { fromName(text) }.orEmpty()
        val places = results.mapNotNull { it.toPlace() }.distinctBy { it.address }
        return if (near == null) places else places.sortedBy { it.distanceTo(near) }
    }

    /** A readable address for a point, or a coordinate label when the geocoder has nothing. */
    suspend fun addressFor(lat: Double, lng: Double): String {
        val address = if (isAvailable) withTimeoutOrNull(TIMEOUT_MS) { fromLocation(lat, lng) }?.firstOrNull() else null
        return address?.label() ?: String.format(Locale.US, "%.5f, %.5f", lat, lng)
    }

    private suspend fun fromName(text: String): List<Address> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocationName(text, MAX_RESULTS, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) { if (cont.isActive) cont.resume(addresses) }
                    override fun onError(errorMessage: String?) { if (cont.isActive) cont.resume(emptyList()) }
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocationName(text, MAX_RESULTS) }.getOrNull().orEmpty()
            }
        }

    private suspend fun fromLocation(lat: Double, lng: Double): List<Address> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) { if (cont.isActive) cont.resume(addresses) }
                    override fun onError(errorMessage: String?) { if (cont.isActive) cont.resume(emptyList()) }
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(lat, lng, 1) }.getOrNull().orEmpty()
            }
        }

    private fun Address.toPlace(): Place? = if (hasLatitude() && hasLongitude()) Place(latitude, longitude, label()) else null

    private fun Address.label(): String {
        val line = getAddressLine(0)
        if (!line.isNullOrBlank()) return line
        return listOfNotNull(featureName, subLocality, locality, adminArea).distinct().joinToString(", ")
    }

    private companion object {
        const val MAX_RESULTS = 8
        const val TIMEOUT_MS = 8_000L
    }
}
