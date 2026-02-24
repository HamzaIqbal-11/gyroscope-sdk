package com.earnscape.gyroscopesdk

import android.content.Context
import android.content.Intent
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlin.getValue

/**
 * StreamingSDK
 * Game session management + gyroscope data streaming
 * GyroscopeSDK ke saath milke kaam karta hai
 *
 * Usage:
 *   val gyroSdk = GyroscopeSDK(context)
 *   val streamSdk = StreamingSDK(context, gyroSdk)
 *   streamSdk.startSession("ball_tilt_game")
 *   streamSdk.stopSession()
 */
class StreamingSDK(
    private val context: Context,
    private val gyroscopeSDK: GyroscopeSDK
) {

    companion object {
        private const val TAG = "StreamingSDK"

        // ── Broadcast Actions ──────────────────────────────────────────────────
        // Flutter side inhe GyroscopeReceiver mein receive karega
        const val ACTION_SESSION_STARTED  = "com.earnscape.gyroscopesdk.SESSION_STARTED"
        const val ACTION_SESSION_STOPPED  = "com.earnscape.gyroscopesdk.SESSION_STOPPED"
        const val ACTION_GYRO_DATA        = "com.earnscape.gyroscopesdk.GYRO_DATA"
        const val ACTION_GYRO_IDLE        = "com.earnscape.gyroscopesdk.GYRO_IDLE"
        const val ACTION_GYRO_ACTIVE      = "com.earnscape.gyroscopesdk.GYRO_ACTIVE"
    }

    private val broadcastManager by lazy { LocalBroadcastManager.getInstance(context) }
    private val mainHandler = Handler(Looper.getMainLooper())

    // Session state
    private var isStreaming = false
    private var sessionId: String? = null
    private var gameId: String? = null
    private var sessionStartMs: Long = 0L

    // Idle tracking
    private var isIdle = false
    private var lastMovementMs: Long = 0L

    // Data buffer — session end pe backend ko bhejne ke liye
    private val readingsBuffer = mutableListOf<ReadingData>()

    // Idle checker — har 200ms check karo
    private val idleChecker = object : Runnable {
        override fun run() {
            if (!isStreaming) return
            val now = System.currentTimeMillis()
            // 500ms koi movement nahi → idle
            if (!isIdle && now - lastMovementMs > 500) {
                isIdle = true
                Log.d(TAG, "Phone idle")
                broadcastManager.sendBroadcast(Intent(ACTION_GYRO_IDLE).apply {
                    putExtra("sessionId", sessionId)
                    putExtra("timestampMs", now)
                })
            }
            mainHandler.postDelayed(this, 200)
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Game session shuru karo
     * Gyroscope bhi automatically start ho jata hai
     *
     * @param gameId       Game ka naam e.g. "ball_tilt_game"
     * @param samplingRate Gyro speed (default: SENSOR_DELAY_GAME)
     * @param autoLog      Logcat mein print karo
     * @return sessionId
     */
    fun startSession(
        gameId: String,
        samplingRate: Int = SensorManager.SENSOR_DELAY_GAME,
        autoLog: Boolean = false
    ): String {
        if (isStreaming) stopSession()

        this.gameId = gameId
        this.sessionId = "session_${System.currentTimeMillis()}"
        this.sessionStartMs = System.currentTimeMillis()
        this.lastMovementMs = System.currentTimeMillis()
        this.isIdle = false
        this.readingsBuffer.clear()

        // Gyroscope start karo
        gyroscopeSDK.start(
            samplingRate = samplingRate,
            autoLog = autoLog,
            onData = { data -> _onGyroReading(data) }
        )

        isStreaming = true

        // Idle checker start karo
        mainHandler.postDelayed(idleChecker, 200)

        // Broadcast: session started
        broadcastManager.sendBroadcast(Intent(ACTION_SESSION_STARTED).apply {
            putExtra("sessionId", sessionId)
            putExtra("gameId", gameId)
            putExtra("startTimeMs", sessionStartMs)
        })

        Log.d(TAG, "Session started: $sessionId | game: $gameId")
        return sessionId!!
    }

    /**
     * Game session band karo
     * Gyroscope bhi automatically stop ho jata hai
     * Returns full session data for backend
     */
    fun stopSession(): SessionResult {
        val endTimeMs = System.currentTimeMillis()

        // Gyroscope stop karo
        gyroscopeSDK.stop()

        // Idle checker stop karo
        mainHandler.removeCallbacks(idleChecker)
        isStreaming = false

        val result = SessionResult(
            sessionId = sessionId ?: "",
            gameId = gameId ?: "",
            startTimeMs = sessionStartMs,
            endTimeMs = endTimeMs,
            durationMs = endTimeMs - sessionStartMs,
            totalReadings = readingsBuffer.size,
            readings = readingsBuffer.toList()
        )

        // Broadcast: session stopped
        broadcastManager.sendBroadcast(Intent(ACTION_SESSION_STOPPED).apply {
            putExtra("sessionId", sessionId)
            putExtra("gameId", gameId)
            putExtra("startTimeMs", sessionStartMs)
            putExtra("endTimeMs", endTimeMs)
            putExtra("durationMs", endTimeMs - sessionStartMs)
            putExtra("totalReadings", readingsBuffer.size)
        })

        Log.d(TAG, "Session stopped: $sessionId | readings: ${readingsBuffer.size} | duration: ${endTimeMs - sessionStartMs}ms")

        // Reset state
        sessionId = null
        gameId = null
        readingsBuffer.clear()

        return result
    }

    /** Is session currently active? */
    fun isActive() = isStreaming

    /** Current session ID */
    fun getSessionId() = sessionId

    /** Get buffered readings (for manual backend send) */
    fun getBufferedReadings() = readingsBuffer.toList()

    // ── Internal ───────────────────────────────────────────────────────────────

    private fun _onGyroReading(data: GyroscopeSDK.GyroData) {
        if (!isStreaming) return

        // Idle / Active detection
        if (!data.isIdle) {
            lastMovementMs = System.currentTimeMillis()
            if (isIdle) {
                isIdle = false
                Log.d(TAG, "Phone active again")
                broadcastManager.sendBroadcast(Intent(ACTION_GYRO_ACTIVE).apply {
                    putExtra("sessionId", sessionId)
                    putExtra("timestampMs", System.currentTimeMillis())
                })
            }
        }

        // Buffer reading
        val reading = ReadingData(
            x = data.x,
            y = data.y,
            z = data.z,
            timestampNs = data.timestampNs,
            timestampMs = System.currentTimeMillis(),
            isIdle = isIdle
        )
        readingsBuffer.add(reading)

        // Broadcast to Flutter
        broadcastManager.sendBroadcast(Intent(ACTION_GYRO_DATA).apply {
            putExtra("x", data.x)
            putExtra("y", data.y)
            putExtra("z", data.z)
            putExtra("timestampNs", data.timestampNs)
            putExtra("sessionId", sessionId)
            putExtra("gameId", gameId)
            putExtra("isIdle", isIdle)
        })
    }

    // ── Data Models ────────────────────────────────────────────────────────────

    data class ReadingData(
        val x: Float,
        val y: Float,
        val z: Float,
        val timestampNs: Long,
        val timestampMs: Long,
        val isIdle: Boolean
    )

    data class SessionResult(
        val sessionId: String,
        val gameId: String,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val durationMs: Long,
        val totalReadings: Int,
        val readings: List<ReadingData>
    )
}