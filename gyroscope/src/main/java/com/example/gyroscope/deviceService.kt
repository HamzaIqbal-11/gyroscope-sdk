package com.earnscape.gyroscopesdk

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.UUID

/**
 * DeviceService — SDK Reusable
 *
 * Handles device ID (generate/persist) and location.
 * No API calls — returns data to host app (Flutter).
 *
 * Usage via MethodChannel:
 *   getDeviceInfo  → { deviceId, manufacturer, model, osVersion, latitude, longitude }
 *   getDeviceId    → { deviceId }
 *   getLocation    → { latitude, longitude }
 */
object DeviceService {

    private const val TAG = "DeviceService"
    private const val PREFS_NAME = "earnscape_sdk_prefs"
    private const val KEY_DEVICE_ID = "persistent_device_id"

    /**
     * Get or create persistent device ID
     */
    fun getOrCreateDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Try saved ID
        val saved = prefs.getString(KEY_DEVICE_ID, null)
        if (!saved.isNullOrBlank()) {
            Log.d(TAG, "✅ Loaded device ID: $saved")
            return saved
        }

        // 2. Try Android ID
        var deviceId: String? = null
        try {
            deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (_: Exception) {}

        // 3. Fallback → UUID
        if (deviceId.isNullOrBlank()) {
            deviceId = UUID.randomUUID().toString()
        }

        // Save
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        Log.d(TAG, "✅ Generated device ID: $deviceId")
        return deviceId
    }

    /**
     * Get device details (manufacturer, model, OS)
     */
    fun getDeviceDetails(): Map<String, String> {
        return mapOf(
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "osVersion" to Build.VERSION.RELEASE,
            "sdkVersion" to Build.VERSION.SDK_INT.toString(),
            "brand" to Build.BRAND,
            "product" to Build.PRODUCT
        )
    }

    /**
     * Get last known location (no API call, just reads GPS)
     * Returns lat/lng or 0/0 if unavailable
     */
    @SuppressLint("MissingPermission")
    fun getLocation(context: Context, callback: (Map<String, Double>) -> Unit) {
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            Log.w(TAG, "⚠️ Location permission not granted")
            callback(mapOf("latitude" to 0.0, "longitude" to 0.0))
            return
        }

        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val handler = android.os.Handler(android.os.Looper.getMainLooper())

            // Pick best available provider
            val provider = when {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }

            if (provider == null) {
                Log.w(TAG, "⚠️ No location provider available")
                // Try last known as fallback
                val last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                callback(mapOf(
                    "latitude" to (last?.latitude ?: 0.0),
                    "longitude" to (last?.longitude ?: 0.0)
                ))
                return
            }

            // Timeout — return 0,0 after 15 seconds
            val timeoutRunnable = Runnable {
                Log.w(TAG, "⏰ Location timeout, trying last known")
                val last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                callback(mapOf(
                    "latitude" to (last?.latitude ?: 0.0),
                    "longitude" to (last?.longitude ?: 0.0)
                ))
            }
            handler.postDelayed(timeoutRunnable, 15000)

            // Request fresh location
            lm.requestSingleUpdate(provider, object : android.location.LocationListener {
                override fun onLocationChanged(location: android.location.Location) {
                    handler.removeCallbacks(timeoutRunnable)
                    Log.d(TAG, "📍 Fresh location: ${location.latitude}, ${location.longitude}")
                    callback(mapOf(
                        "latitude" to location.latitude,
                        "longitude" to location.longitude
                    ))
                }
                @Deprecated("Deprecated") override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
                override fun onProviderEnabled(p: String) {}
                override fun onProviderDisabled(p: String) {
                    handler.removeCallbacks(timeoutRunnable)
                    callback(mapOf("latitude" to 0.0, "longitude" to 0.0))
                }
            }, android.os.Looper.getMainLooper())

            Log.d(TAG, "🔄 Requesting fresh location from $provider...")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Location error: ${e.message}")
            callback(mapOf("latitude" to 0.0, "longitude" to 0.0))
        }
    }

    /**
     * Get everything — device ID + details + location (async via callback)
     */
    fun getFullDeviceInfo(context: Context, callback: (Map<String, Any>) -> Unit) {
        val deviceId = getOrCreateDeviceId(context)
        val details = getDeviceDetails()

        getLocation(context) { location ->
            val result = mapOf(
                "deviceId" to deviceId,
                "manufacturer" to (details["manufacturer"] ?: ""),
                "model" to (details["model"] ?: ""),
                "osVersion" to (details["osVersion"] ?: ""),
                "sdkVersion" to (details["sdkVersion"] ?: ""),
                "brand" to (details["brand"] ?: ""),
                "product" to (details["product"] ?: ""),
                "latitude" to (location["latitude"] ?: 0.0),
                "longitude" to (location["longitude"] ?: 0.0)
            )
            callback(result)
        }
    }
}