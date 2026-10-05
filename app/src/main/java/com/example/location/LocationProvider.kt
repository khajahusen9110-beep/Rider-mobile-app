package com.example.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Device GPS through Google Play services. */
object LocationProvider {

    private val _lastLocation = MutableStateFlow<Location?>(null)

    /** The latest fix from either a one-shot request or the online service. */
    val lastLocation: StateFlow<Location?> = _lastLocation

    internal fun publish(location: Location) {
        _lastLocation.value = location
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** A fresh fix, or the last known one, or null if location is off. */
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): Location? {
        if (!hasPermission(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cancel = CancellationTokenSource()
        val fresh = withTimeoutOrNull(15_000) {
            runCatching { client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancel.token).await() }.getOrNull()
        }
        if (fresh == null) cancel.cancel()
        val location = fresh ?: runCatching { client.lastLocation.await() }.getOrNull()
        location?.let(::publish)
        return location
    }
}
