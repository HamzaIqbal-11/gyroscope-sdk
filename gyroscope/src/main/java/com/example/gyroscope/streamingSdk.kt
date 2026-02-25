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
import com.pedro.rtplibrary.rtmp.RtmpDisplay
import com.pedro.rtmp.utils.ConnectCheckerRtmp
import java.io.File

class StreamingSDK(
    private val context: Context,
    private val gyroscopeSDK: GyroscopeSDK
) {

    companion object {
        private const val TAG = "StreamingSDK"

        // Broadcast Actions
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

    private val connectChecker = object : ConnectCheckerRtmp {
        override fun onConnectionSuccessRtmp() {
            Log.d(TAG, "RTMP Connected successfully")
            broadcastManager.sendBroadcast(Intent(ACTION_STREAMING_STARTED))
        }

        override fun onConnectionFailedRtmp(reason: String) {
            Log.e(TAG, "RTMP connection failed: $reason")
        }

        override fun onNewBitrateRtmp(bitrate: Long) {
            // Optional: can broadcast bitrate if needed
        }

        override fun onDisconnectRtmp() {
            Log.d(TAG, "RTMP disconnected")
        }
    }

    /**
     * Start session + gyroscope + streaming/recording
     * @param streamUrl RTMP URL (e.g. rtmp://your-server/live/key) or null for local MP4 only
     */
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

        // Start gyroscope
        gyroscopeSDK.start(
            samplingRate = samplingRate,
            autoLog = autoLog,
            onData = { data -> _onGyroReading(data) }
        )

        // Setup screen streaming/recording
        setupScreenStreaming()

        isStreaming = true
        mainHandler.postDelayed(idleChecker, 200)

        // Notify Flutter
        broadcastManager.sendBroadcast(Intent(ACTION_SESSION_STARTED).apply {
            putExtra("sessionId", sessionId)
            putExtra("gameId", gameId)
            putExtra("startTimeMs", sessionStartMs)
        })

        Log.d(TAG, "StreamingSDK session started: $sessionId | RTMP: ${streamUrl != null}")
        return sessionId!!
    }

    private fun setupScreenStreaming() {
        localRecordFile = File(context.externalCacheDir, "session_${sessionId}.mp4")

        rtmpDisplay = RtmpDisplay(context, connectChecker)

        // Prepare video & audio (adjust resolution/bitrate as needed)
        rtmpDisplay?.prepareVideo(1280, 720, 30)  // 720p 30fps
        rtmpDisplay?.prepareAudio(128 * 1024, 44100, true, false, false)

        if (streamUrl != null) {
            // Live RTMP streaming
            rtmpDisplay?.startStream(streamUrl!!)
        } else {
            // Local MP4 recording
            rtmpDisplay?.startRecord(localRecordFile!!.absolutePath)
        }
    }

    /**
     * Call this after user grants MediaProjection permission
     * (from Flutter plugin after startActivityForResult)
     */
    fun setMediaProjection(resultCode: Int, data: Intent?) {
        if (data == null) return
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
        rtmpDisplay?.setIntentResult(resultCode, data)
        Log.d(TAG, "MediaProjection set successfully")
    }

    fun pauseSession() {
        if (!isStreaming || isPaused) return
        isPaused = true
        pauseStartMs = System.currentTimeMillis()
        rtmpDisplay?.pauseRecord()
        broadcastManager.sendBroadcast(Intent(ACTION_STREAMING_PAUSED))
        Log.d(TAG, "Streaming paused")
    }

    fun resumeSession() {
        if (!isStreaming || !isPaused) return
        totalPausedMs += (System.currentTimeMillis() - pauseStartMs)
        isPaused = false
        rtmpDisplay?.resumeRecord()
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

        // Cleanup
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

    // Data classes
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