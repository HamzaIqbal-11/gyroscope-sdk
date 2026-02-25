package com.earnscape.gyroscopesdk

import android.content.Context
import android.content.Intent
import android.hardware.SensorManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.pedro.library.base.DisplayBase
import com.pedro.library.rtmp.RtmpDisplay
import com.pedro.library.rtsp.RtspDisplay
import com.pedro.common.ConnectChecker
import java.io.File

class StreamingSDK(
    private val context: Context,
    private val gyroscopeSDK: GyroscopeSDK
) {

    companion object {
        private const val TAG = "StreamingSDK"

        const val ACTION_SESSION_STARTED = "com.earnscape.gyroscopesdk.SESSION_STARTED"
        const val ACTION_SESSION_STOPPED = "com.earnscape.gyroscopesdk.SESSION_STOPPED"
        const val ACTION_GYRO_DATA       = "com.earnscape.gyroscopesdk.GYRO_DATA"
        const val ACTION_GYRO_IDLE       = "com.earnscape.gyroscopesdk.GYRO_IDLE"
        const val ACTION_GYRO_ACTIVE     = "com.earnscape.gyroscopesdk.GYRO_ACTIVE"
        const val ACTION_STREAMING_STARTED = "com.earnscape.gyroscopesdk.STREAMING_STARTED"
        const val ACTION_STREAMING_PAUSED  = "com.earnscape.gyroscopesdk.STREAMING_PAUSED"
        const val ACTION_STREAMING_RESUMED = "com.earnscape.gyroscopesdk.STREAMING_RESUMED"
        const val ACTION_STREAMING_STOPPED = "com.earnscape.gyroscopesdk.STREAMING_STOPPED"
    }

    private val broadcastManager = LocalBroadcastManager.getInstance(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isStreaming = false
    private var sessionId: String? = null
    private var gameId: String? = null
    private var sessionStartMs: Long = 0L
    private var totalPausedMs: Long = 0L

    private val readingsBuffer = mutableListOf<ReadingData>()

    private var rtmpDisplay: RtmpDisplay? = null
    private var mediaProjection: MediaProjection? = null
    private var streamUrl: String? = null
    private var localRecordFile: File? = null

    private var isPaused = false
    private var pauseStartMs: Long = 0L

    private val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    private var isIdle = false
    private var lastMovementMs: Long = 0L

    private val idleChecker = object : Runnable {
        override fun run() {
            if (!isStreaming) return
            val now = System.currentTimeMillis()
            if (!isIdle && now - lastMovementMs > 500) {
                isIdle = true
                broadcastManager.sendBroadcast(Intent(ACTION_GYRO_IDLE).apply {
                    putExtra("sessionId", sessionId)
                    putExtra("timestampMs", now)
                })
            }
            mainHandler.postDelayed(this, 200)
        }
    }

    private val connectChecker = object : ConnectChecker {

        override fun onConnectionSuccess() {
            Log.d(TAG, "RTMP Connected")
            broadcastManager.sendBroadcast(Intent(ACTION_STREAMING_STARTED))
        }

        override fun onConnectionFailed(reason: String) {
            Log.e(TAG, "Failed: $reason")
            // Optional: you can add auto-reconnect logic here if desired
        }

        override fun onNewBitrate(bitrate: Long) {
            // ignore (or log if you want: Log.d(TAG, "New bitrate: $bitrate bps"))
        }

        override fun onDisconnect() {
            Log.d(TAG, "Disconnected")
            // Optional: broadcast something like ACTION_STREAMING_STOPPED if needed
        }

        // ── Required by the interface in 2.6.x ──
        override fun onConnectionStarted(url: String) {
            // Called when connection attempt begins (before success/fail)
            // Usually safe to ignore for simple use-cases
            Log.d(TAG, "Connection started to: $url")
        }

        override fun onAuthError() {
            // Some RTMP servers (e.g. with login/password) call this on bad credentials
            Log.e(TAG, "Authentication error")
            // You can stop stream, show toast, etc.
        }

        override fun onAuthSuccess() {
            // Called on successful auth (rare for public RTMP endpoints)
            Log.d(TAG, "Authentication success")
        }
    }

    fun startSession(
        gameId: String,
        streamUrl: String? = null,
        samplingRate: Int = SensorManager.SENSOR_DELAY_GAME,
        autoLog: Boolean = false
    ): String {
        if (isStreaming) stopSession()

        this.gameId = gameId
        this.sessionId = "session_${System.currentTimeMillis()}"
        this.sessionStartMs = System.currentTimeMillis()
        this.lastMovementMs = System.currentTimeMillis()
        this.streamUrl = streamUrl
        this.isPaused = false
        this.totalPausedMs = 0L
        readingsBuffer.clear()

        gyroscopeSDK.start(samplingRate, autoLog) { data -> _onGyroReading(data) }

        setupScreenStreaming()

        isStreaming = true
        mainHandler.postDelayed(idleChecker, 200)

        broadcastManager.sendBroadcast(Intent(ACTION_SESSION_STARTED).apply {
            putExtra("sessionId", sessionId)
            putExtra("gameId", gameId)
            putExtra("startTimeMs", sessionStartMs)
        })

        Log.d(TAG, "Session started: $sessionId")
        return sessionId!!
    }

    private fun setupScreenStreaming() {
        localRecordFile = File(context.externalCacheDir, "session_${sessionId}.mp4")

        // Fixed constructor: context first, then likely a Boolean flag (try false first), then connectChecker
        rtmpDisplay = RtmpDisplay(context, false, connectChecker)  // ← add false (or true) as 2nd param

        // If the above still complains about types / missing params, try these variants one by one:
        // rtmpDisplay = RtmpDisplay(context, connectChecker = connectChecker)          // if named args allowed & no Boolean
        // rtmpDisplay = RtmpDisplay(context, true, connectChecker)                    // try true if false fails
        // rtmpDisplay = RtmpDisplay(context, useOpus = false, connectChecker = connectChecker)  // if named

        rtmpDisplay?.prepareVideo(1280, 720, 30)
        rtmpDisplay?.prepareAudio(128 * 1024, 44100, true, false, false)  // stereo? last false = no echo cancel?

        if (streamUrl != null) {
            rtmpDisplay?.startStream(streamUrl!!)
        } else {
            rtmpDisplay?.startRecord(localRecordFile!!.absolutePath)
        }
    }

    fun setMediaProjection(resultCode: Int, data: Intent?) {
        if (data == null) return
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
        rtmpDisplay?.setIntentResult(resultCode, data)
        Log.d(TAG, "MediaProjection set")
    }

    fun pauseSession() {
        if (!isStreaming || isPaused) return
        isPaused = true
        pauseStartMs = System.currentTimeMillis()

        rtmpDisplay?.stopRecord()  // Pause by stopping record

        broadcastManager.sendBroadcast(Intent(ACTION_STREAMING_PAUSED))
        Log.d(TAG, "Streaming paused")
    }

    fun resumeSession() {
        if (!isStreaming || !isPaused) return
        totalPausedMs += (System.currentTimeMillis() - pauseStartMs)
        isPaused = false

        if (localRecordFile != null) {
            rtmpDisplay?.startRecord(localRecordFile!!.absolutePath)  // Resume by restarting on same file (appends)
        } else if (streamUrl != null) {
            rtmpDisplay?.startStream(streamUrl!!)
        }

        broadcastManager.sendBroadcast(Intent(ACTION_STREAMING_RESUMED))
        Log.d(TAG, "Streaming resumed")
    }

    fun stopSession(): SessionResult {
        val endTimeMs = System.currentTimeMillis()
        val activeDurationMs = (endTimeMs - sessionStartMs) - totalPausedMs

        gyroscopeSDK.stop()
        rtmpDisplay?.stopStream()
        rtmpDisplay?.stopRecord()
        rtmpDisplay = null

        mainHandler.removeCallbacks(idleChecker)
        isStreaming = false

        val result = SessionResult(
            sessionId = sessionId ?: "",
            gameId = gameId ?: "",
            startTimeMs = sessionStartMs,
            endTimeMs = endTimeMs,
            durationMs = activeDurationMs,
            totalReadings = readingsBuffer.size,
            readings = readingsBuffer.toList(),
            recordPath = localRecordFile?.absolutePath
        )

        broadcastManager.sendBroadcast(Intent(ACTION_SESSION_STOPPED))
        broadcastManager.sendBroadcast(Intent(ACTION_STREAMING_STOPPED))

        sessionId = null
        gameId = null
        readingsBuffer.clear()
        localRecordFile = null

        Log.d(TAG, "Session stopped. Duration: ${activeDurationMs}ms | Recorded: ${result.recordPath}")
        return result
    }

    private fun _onGyroReading(data: GyroscopeSDK.GyroData) {
        if (!isStreaming) return

        if (!data.isIdle) lastMovementMs = System.currentTimeMillis()

        val reading = ReadingData(
            x = data.x,
            y = data.y,
            z = data.z,
            timestampNs = data.timestampNs,
            timestampMs = System.currentTimeMillis(),
            isIdle = data.isIdle
        )
        readingsBuffer.add(reading)

        broadcastManager.sendBroadcast(Intent(ACTION_GYRO_DATA).apply {
            putExtra("x", data.x)
            putExtra("y", data.y)
            putExtra("z", data.z)
            putExtra("timestampNs", data.timestampNs)
            putExtra("sessionId", sessionId)
            putExtra("gameId", gameId)
            putExtra("isIdle", data.isIdle)
        })
    }

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
        val readings: List<ReadingData>,
        val recordPath: String? = null
    )

    fun isActive() = isStreaming
    fun getSessionId() = sessionId
    fun getBufferedReadings() = readingsBuffer.toList()
}