package com.earnscape.gyroscopesdk

import android.content.Context
import android.util.Log
import android.app.Activity
import android.content.Intent
import android.os.Build

/**
 * SantriqxSDK — Main Entry Point
 *
 * Initialize with appId + apiSecretKey.
 * SDK fetches config, manages all services internally.
 *
 * Flutter usage:
 *   await invokeMethod('initSdk', { 'appId': 'NX-992', 'apiSecretKey': 'sk_xxx', 'baseUrl': '...' });
 *   await invokeMethod('registerDevice', {});
 *   await invokeMethod('startStreaming', {});
 *   await invokeMethod('uploadFace', { 'username': 'Hamza' });
 *   await invokeMethod('recordTransaction', { 'transaction_hash': '0x...', ... });
 */
object SantriqxSDK {

    private const val TAG = "SantriqxSDK"

    var appId: String = ""
        private set
    var apiSecretKey: String = ""
        private set
    var baseUrl: String = DEFAULT_BASE_URL    // ← default set
        private set
    var organizationId: String = ""
        private set
    var productId: String = ""
        private set

    private const val DEFAULT_BASE_URL = "http://136.113.114.24:3000/app"

    private var isInitialized = false
    private var config: Map<String, Any>? = null
    private var services: List<Map<String, Any>> = emptyList()


    // ── Recording state (class ke top pe variables ke saath add karo) ──
    private var pendingRtmpUrl: String? = null
    private var pendingStreamKey: String? = null
    private var pendingDeviceId: String? = null

    /**
     * Initialize SDK with credentials
     */
    fun init(appId: String, apiSecretKey: String,
             organizationId: String = "", productId: String = "",baseUrl: String = "") {
        this.appId = appId
        this.apiSecretKey = apiSecretKey
        if (baseUrl.isNotEmpty()) {
            this.baseUrl = baseUrl
            Log.d(TAG, "✅ Using custom baseUrl: $baseUrl")
        } else {
            this.baseUrl = DEFAULT_BASE_URL
            Log.d(TAG, "✅ Using default baseUrl: $DEFAULT_BASE_URL")
        }
//        this.baseUrl = BASE_URL
        if (organizationId.isNotEmpty()) this.organizationId = organizationId
        if (productId.isNotEmpty()) this.productId = productId
        this.isInitialized = true

        ApiService.setGlobalHeaders(mapOf(
            "X-Api-Key" to apiSecretKey
        ))
    }

    /**
     * Fetch config from backend — returns active services
     */
    fun endStream(streamKey: String, callback: (Map<String, Any>) -> Unit) {
        ensureInitialized()
        Thread {
            val result = ApiService.post("$baseUrl/stream/internal/ended", mapOf(
                "streamKey" to streamKey
            ))
            Log.d(TAG, "📡 Stream ended")
            callback(result)
        }.start()
    }

    fun fetchConfig(callback: (Map<String, Any>) -> Unit) {
        ensureInitialized()
        Thread {
            val result = ApiService.get("$baseUrl/config/$appId"
            )
            if (result["success"] == true) {
                val dataStr = result["data"]
                if (dataStr != null) {
                    try {
                        // data String hai — parse karo
                        val json = org.json.JSONObject(dataStr.toString())
                        organizationId = json.optInt("organization_id", 0).toString()
                        productId = json.optInt("product_id", 0).toString()

                        // Services parse
                        val servicesArr = json.optJSONArray("services")
                        if (servicesArr != null) {
                            val list = mutableListOf<Map<String, Any>>()
                            for (i in 0 until servicesArr.length()) {
                                val s = servicesArr.getJSONObject(i)
                                list.add(mapOf(
                                    "name" to s.optString("name"),
                                    "is_active" to s.optBoolean("is_active", false)
                                ))
                            }
                            services = list
                        }
                     //   Log.d(TAG, "✅ Config: org=$organizationId, product=$productId, services=${services.size}")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Config parse error: ${e.message}")
                    }
                }
            }
            callback(result)
        }.start()
    }

    /**
     * Check if a service is active
     */
    fun isServiceActive(serviceName: String): Boolean {
        return services.any {
            it["name"] == serviceName && it["is_active"] == true
        }
    }

    /**
     * Register device with backend
     */
    fun registerDevice(context: Context, callback: (Map<String, Any>) -> Unit) {
        ensureInitialized()
        val deviceId = DeviceService.getOrCreateDeviceId(context)
        val details = DeviceService.getDeviceDetails()

        DeviceService.getLocation(context) { location ->
            Thread {
                val result = ApiService.post("$baseUrl/activity/register", mapOf(
                    "deviceId" to deviceId,
                    "lat" to (location["latitude"] ?: 0.0),
                    "long" to (location["longitude"] ?: 0.0),
                    "manufacturer" to (details["manufacturer"] ?: ""),
                    "model" to (details["model"] ?: ""),
                    "appId" to appId
                ))
                Log.d(TAG, "📡 Device registered ")
                callback(result)
            }.start()
        }
    }

    /**
     * Start streaming — get RTMP credentials from backend
     */
    fun startStream(context: Context, callback: (Map<String, Any>) -> Unit) {
        ensureInitialized()
        val deviceId = DeviceService.getOrCreateDeviceId(context)

        Thread {
            val result = ApiService.post("$baseUrl/stream/start", mapOf(
                "organization_id" to organizationId,
                "product_id" to productId,
                "deviceId" to deviceId,
                "streamTitle" to "Recording",
                "streamerName" to "device"
            ))
            Log.d(TAG, "📡 Stream start ")
            callback(result)
        }.start()
    }

    /**
     * Get stream details (recording URL etc)
     */
    fun getStreamDetails(streamKey: String, callback: (Map<String, Any>) -> Unit) {
        ensureInitialized()
        Thread {
            val result = ApiService.get("$baseUrl/stream/$streamKey")
          //  Log.d(TAG, "📡 Stream details ")
            callback(result)
        }.start()
    }

    /**
     * Upload face image to backend
     */
    fun uploadFace(context: Context, imagePath: String, username: String, callback: (Map<String, Any>) -> Unit) {
        ensureInitialized()
        val deviceId = DeviceService.getOrCreateDeviceId(context)

        Thread {
            val result = ApiService.postMultipart(
                url = "$baseUrl/facial/save-embedding",
                fields = mapOf(
                    "organization_id" to organizationId,
                    "product_id" to productId,
                    "device_id" to deviceId,
                    "username" to username
                ),
                filePath = imagePath,
                fileFieldName = "image",
                fileName = "face.png"
            )
            Log.d(TAG, "📡 Face upload: $result")
            callback(result)
        }.start()
    }

    /**
     * Record a transaction
     */
    fun recordTransaction(context: Context, fields: Map<String, String>, callback: (Map<String, Any>) -> Unit) {
        ensureInitialized()
        val deviceId = DeviceService.getOrCreateDeviceId(context)

        // Validate first
        val validated = TransactionService.validateTransaction(fields + mapOf("device_id" to deviceId))
        if (validated["validated"] != true) {
            callback(validated)
            return
        }

        Thread {
            val result = ApiService.post("$baseUrl/transaction/record", validated + mapOf(
                "organization_id" to organizationId,
                "product_id" to productId,
                "token_symbol" to (fields["token_symbol"] ?: "usdt")
            ))
            Log.d(TAG, "📡 Transaction recorded: $result")
            callback(result)
        }.start()
    }

    /**
     * Fetch transaction history
     */
    fun getTransactions(context: Context, callback: (Map<String, Any>) -> Unit) {
        ensureInitialized()
        val deviceId = DeviceService.getOrCreateDeviceId(context)

        Thread {
            val result = ApiService.get(
                "$baseUrl/transaction/$deviceId?organization_id=$organizationId&product_id=$productId"
            )
            Log.d(TAG, "📡 Transactions: $result")
            callback(result)
        }.start()
    }

    /**
     * Send sensor data (gyro + accel) to backend
     */
    fun sendSensorData(context: Context, gyroX: Double, gyroY: Double, gyroZ: Double,
                       accelX: Double, accelY: Double, accelZ: Double,
                       callback: (Map<String, Any>) -> Unit) {
        ensureInitialized()
        val deviceId = DeviceService.getOrCreateDeviceId(context)

        Thread {
            val result = ApiService.post("$baseUrl/activity/update", mapOf(
                "deviceId" to deviceId,
                "organization_id" to organizationId,
                "product_id" to productId,
                "gyro_x" to gyroX,
                "gyro_y" to gyroY,
                "gyro_z" to gyroZ,
                "accel_x" to accelX,
                "accel_y" to accelY,
                "accel_z" to accelZ
            ))
            callback(result)
        }.start()
    }

    private fun ensureInitialized() {
        if (!isInitialized) throw IllegalStateException("SantriqxSDK not initialized. Call init() first.")
    }


    fun startRecording(
        activity: Activity,
        requestCode: Int = 3001,
        onSuccess: (streamKey: String, rtmpUrl: String) -> Unit,
        onError: (String) -> Unit
    ) {
        ensureInitialized()

        // 1. Backend API se RTMP URL lo
        startStream(activity) { result ->
            val dataStr = result["data"]
            if (dataStr == null) {
                activity.runOnUiThread { onError("No stream data from backend") }
                return@startStream
            }

            try {
                val json = org.json.JSONObject(dataStr.toString())
                val rtmpUrl = json.optString("rtmpUrl")
                val streamKey = json.optString("streamKey")

                if (rtmpUrl.isEmpty() || streamKey.isEmpty()) {
                    activity.runOnUiThread { onError("Invalid stream data") }
                    return@startStream
                }

                // Store for when permission is granted
                pendingRtmpUrl = "$rtmpUrl/$streamKey"
                pendingStreamKey = streamKey
                pendingDeviceId = DeviceService.getOrCreateDeviceId(activity)

                activity.runOnUiThread {
                    // 2. Request MediaProjection permission
                    StreamingSDK.requestMediaProjectionPermission(activity, requestCode)
                    onSuccess(streamKey, rtmpUrl)
                }
            } catch (e: Exception) {
                activity.runOnUiThread { onError("Parse error: ${e.message}") }
            }
        }
    }


    fun handleRecordingResult(
        activity: Activity,
        requestCode: Int,
        expectedCode: Int,
        resultCode: Int,
        data: Intent?,
        onGranted: () -> Unit,
        onDenied: () -> Unit
    ) {
        StreamingSDK.handleMediaProjectionResult(
            requestCode = requestCode,
            expectedCode = expectedCode,
            resultCode = resultCode,
            data = data,
            onGranted = { code, projData ->
                Log.d(TAG, "✅ MediaProjection granted, starting service")

                // 3. ScreenRecordingService start karo
                val intent = Intent(activity, ScreenRecordingService::class.java).apply {
                    action = ScreenRecordingService.ACTION_START
                    putExtra(ScreenRecordingService.EXTRA_RESULT_CODE, code)
                    putExtra(ScreenRecordingService.EXTRA_DATA, projData)
                    putExtra(ScreenRecordingService.EXTRA_STREAM_URL, pendingRtmpUrl ?: "")
                    putExtra(ScreenRecordingService.EXTRA_GAME_ID, "recording")
                    putExtra("streamKey", pendingStreamKey ?: "")
                    putExtra("audio", "false")
                    putExtra("streamTitle", "recording")
                    putExtra("playerId", pendingDeviceId ?: "")
                    putExtra("playerName", pendingDeviceId ?: "")
                    putExtra("gameName", "Recording")
                    putExtra("videoEnabled", "false")
                    putExtra("targetPackageName", "")
                    putExtra("minimumStreamSpeed", "1")
                    putExtra("badConnectionTimeout", "30")
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    activity.startForegroundService(intent)
                } else {
                    activity.startService(intent)
                }

                onGranted()
            },
            onDenied = {
                Log.e(TAG, "❌ MediaProjection denied")
                onDenied()
            }
        )
    }

    fun stopRecording(context: Context) {
        ensureInitialized()

        // 1. Stop service
        context.startService(Intent(context, ScreenRecordingService::class.java).apply {
            action = ScreenRecordingService.ACTION_STOP
        })

        // 2. Notify backend
        pendingStreamKey?.let { key ->
            endStream(key) { result ->
                Log.d(TAG, "Stream ended API: $result")
            }
        }

        // Clear state
        pendingRtmpUrl = null
        pendingStreamKey = null
        pendingDeviceId = null
    }

}