package com.gramavaxi.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class LiveLocationClient(
    context: Context,
    private val onLocation: (Location) -> Unit,
    private val onUnavailable: (LocationIssue) -> Unit
) {
    private val appContext = context.applicationContext
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)
    private val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
        .setMinUpdateIntervalMillis(FASTEST_LOCATION_INTERVAL_MS)
        .setWaitForAccurateLocation(false)
        .build()
    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(onLocation)
        }
    }

    fun start() {
        if (!hasLocationPermission()) {
            onUnavailable(LocationIssue.PermissionMissing)
            return
        }
        if (!isLocationEnabled()) {
            onUnavailable(LocationIssue.LocationDisabled)
            return
        }
        try {
            fusedClient.lastLocation
                .addOnSuccessListener { location -> location?.let(onLocation) }
                .addOnFailureListener { error ->
                    Log.w(TAG, "Unable to read last known location", error)
                }
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener { error ->
                    Log.e(TAG, "Unable to request live location updates", error)
                    onUnavailable(LocationIssue.UpdateFailed)
                }
        } catch (error: SecurityException) {
            Log.e(TAG, "Location permission was revoked before updates started", error)
            onUnavailable(LocationIssue.PermissionMissing)
        }
    }

    fun stop() {
        fusedClient.removeLocationUpdates(callback)
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun isLocationEnabled(): Boolean {
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return runCatching {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
    }

    enum class LocationIssue {
        PermissionMissing,
        LocationDisabled,
        UpdateFailed
    }

    private companion object {
        const val LOCATION_INTERVAL_MS = 5_000L
        const val FASTEST_LOCATION_INTERVAL_MS = 2_000L
        const val TAG = "LiveLocationClient"
    }
}
