//package com.earnscape.gyroscopesdk
//
//import android.content.Context
//import android.content.Intent
//import android.hardware.SensorManager
//import android.os.Handler
//import android.os.Looper
//import android.util.Log
//import androidx.localbroadcastmanager.content.LocalBroadcastManager
//import kotlin.getValue
//
///**
// * StreamingSDK
// * Game session management + gyroscope data streaming
// * GyroscopeSDK ke saath milke kaam karta hai
// *
// * Usage:
// *   val gyroSdk = GyroscopeSDK(context)
// *   val streamSdk = StreamingSDK(context, gyroSdk)
// *   streamSdk.startSession("ball_tilt_game")
// *   streamSdk.stopSession()
// */
//class StreamingSDK(
//    private val context: Context,
//    private val gyroscopeSDK: GyroscopeSDK
//) {
//
//    companion object {
//        private const val TAG = "StreamingSDK"
//
//        // ── Broadcast Actions ──────────────────────────────────────────────────
//        // Flutter side inhe GyroscopeReceiver mein receive karega
//        const val ACTION_SESSION_STARTED  = "com.earnscape.gyroscopesdk.SESSION_STARTED"
//        const val ACTION_SESSION_STOPPED  = "com.earnscape.gyroscopesdk.SESSION_STOPPED"
//        const val ACTION_GYRO_DATA        = "com.earnscape.gyroscopesdk.GYRO_DATA"
//        const val ACTION_GYRO_IDLE        = "com.earnscape.gyroscopesdk.GYRO_IDLE"
//        const val ACTION_GYRO_ACTIVE      = "com.earnscape.gyroscopesdk.GYRO_ACTIVE"
//    }
//
//    private val broadcastManager by lazy { LocalBroadcastManager.getInstance(context) }
//    private val mainHandler = Handler(Looper.getMainLooper())
//
//    // Session state
//    private var isStreaming = false
//    private var sessionId: String? = null
//    private var gameId: String? = null
//    private var sessionStartMs: Long = 0L
//
//    // Idle tracking
//    private var isIdle = false
//    private var lastMovementMs: Long = 0L
//
//    // Data buffer — session end pe backend ko bhejne ke liye
//    private val readingsBuffer = mutableListOf<ReadingData>()
//
//    // Idle checker — har 200ms check karo
//    private val idleChecker = object : Runnable {
//        override fun run() {
//            if (!isStreaming) return
//            val now = System.currentTimeMillis()
//            // 500ms koi movement nahi → idle
//            if (!isIdle && now - lastMovementMs > 500) {
//                isIdle = true
//                Log.d(TAG, "Phone idle")
//                broadcastManager.sendBroadcast(Intent(ACTION_GYRO_IDLE).apply {
//                    putExtra("sessionId", sessionId)
//                    putExtra("timestampMs", now)
//                })
//            }
//            mainHandler.postDelayed(this, 200)
//        }
//    }
//
//    // ── Public API ─────────────────────────────────────────────────────────────
//
//    /**
//     * Game session shuru karo
//     * Gyroscope bhi automatically start ho jata hai
//     *
//     * @param gameId       Game ka naam e.g. "ball_tilt_game"
//     * @param samplingRate Gyro speed (default: SENSOR_DELAY_GAME)
//     * @param autoLog      Logcat mein print karo
//     * @return sessionId
//     */
//    fun startSession(
//        gameId: String,
//        samplingRate: Int = SensorManager.SENSOR_DELAY_GAME,
//        autoLog: Boolean = false
//    ): String {
//        if (isStreaming) stopSession()
//
//        this.gameId = gameId
//        this.sessionId = "session_${System.currentTimeMillis()}"
//        this.sessionStartMs = System.currentTimeMillis()
//        this.lastMovementMs = System.currentTimeMillis()
//        this.isIdle = false
//        this.readingsBuffer.clear()
//
//        // Gyroscope start karo
//        gyroscopeSDK.start(
//            samplingRate = samplingRate,
//            autoLog = autoLog,
//            onData = { data -> _onGyroReading(data) }
//        )
//
//        isStreaming = true
//
//        // Idle checker start karo
//        mainHandler.postDelayed(idleChecker, 200)
//
//        // Broadcast: session started
//        broadcastManager.sendBroadcast(Intent(ACTION_SESSION_STARTED).apply {
//            putExtra("sessionId", sessionId)
//            putExtra("gameId", gameId)
//            putExtra("startTimeMs", sessionStartMs)
//        })
//
//        Log.d(TAG, "Session started: $sessionId | game: $gameId")
//        return sessionId!!
//    }
//
//    /**
//     * Game session band karo
//     * Gyroscope bhi automatically stop ho jata hai
//     * Returns full session data for backend
//     */
//    fun stopSession(): SessionResult {
//        val endTimeMs = System.currentTimeMillis()
//
//        // Gyroscope stop karo
//        gyroscopeSDK.stop()
//
//        // Idle checker stop karo
//        mainHandler.removeCallbacks(idleChecker)
//        isStreaming = false
//
//        val result = SessionResult(
//            sessionId = sessionId ?: "",
//            gameId = gameId ?: "",
//            startTimeMs = sessionStartMs,
//            endTimeMs = endTimeMs,
//            durationMs = endTimeMs - sessionStartMs,
//            totalReadings = readingsBuffer.size,
//            readings = readingsBuffer.toList()
//        )
//
//        // Broadcast: session stopped
//        broadcastManager.sendBroadcast(Intent(ACTION_SESSION_STOPPED).apply {
//            putExtra("sessionId", sessionId)
//            putExtra("gameId", gameId)
//            putExtra("startTimeMs", sessionStartMs)
//            putExtra("endTimeMs", endTimeMs)
//            putExtra("durationMs", endTimeMs - sessionStartMs)
//            putExtra("totalReadings", readingsBuffer.size)
//        })
//
//        Log.d(TAG, "Session stopped: $sessionId | readings: ${readingsBuffer.size} | duration: ${endTimeMs - sessionStartMs}ms")
//
//        // Reset state
//        sessionId = null
//        gameId = null
//        readingsBuffer.clear()
//
//        return result
//    }
//
//    /** Is session currently active? */
//    fun isActive() = isStreaming
//
//    /** Current session ID */
//    fun getSessionId() = sessionId
//
//    /** Get buffered readings (for manual backend send) */
//    fun getBufferedReadings() = readingsBuffer.toList()
//
//    // ── Internal ───────────────────────────────────────────────────────────────
//
//    private fun _onGyroReading(data: GyroscopeSDK.GyroData) {
//        if (!isStreaming) return
//
//        // Idle / Active detection
//        if (!data.isIdle) {
//            lastMovementMs = System.currentTimeMillis()
//            if (isIdle) {
//                isIdle = false
//                Log.d(TAG, "Phone active again")
//                broadcastManager.sendBroadcast(Intent(ACTION_GYRO_ACTIVE).apply {
//                    putExtra("sessionId", sessionId)
//                    putExtra("timestampMs", System.currentTimeMillis())
//                })
//            }
//        }
//
//        // Buffer reading
//        val reading = ReadingData(
//            x = data.x,
//            y = data.y,
//            z = data.z,
//            timestampNs = data.timestampNs,
//            timestampMs = System.currentTimeMillis(),
//            isIdle = isIdle
//        )
//        readingsBuffer.add(reading)
//
//        // Broadcast to Flutter
//        broadcastManager.sendBroadcast(Intent(ACTION_GYRO_DATA).apply {
//            putExtra("x", data.x)
//            putExtra("y", data.y)
//            putExtra("z", data.z)
//            putExtra("timestampNs", data.timestampNs)
//            putExtra("sessionId", sessionId)
//            putExtra("gameId", gameId)
//            putExtra("isIdle", isIdle)
//        })
//    }
//
//    // ── Data Models ────────────────────────────────────────────────────────────
//
//    data class ReadingData(
//        val x: Float,
//        val y: Float,
//        val z: Float,
//        val timestampNs: Long,
//        val timestampMs: Long,
//        val isIdle: Boolean
//    )
//
//    data class SessionResult(
//        val sessionId: String,
//        val gameId: String,
//        val startTimeMs: Long,
//        val endTimeMs: Long,
//        val durationMs: Long,
//        val totalReadings: Int,
//        val readings: List<ReadingData>
//    )
//}

package com.earnscape.gyroscopesdk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.pedro.library.rtmp.RtmpDisplay
import java.io.File
import java.time.Instant

/**
 * StreamingSDK
 * Game session management + gyroscope data streaming + screen recording/streaming
 * GyroscopeSDK ke saath milke kaam karta hai
 * Ab yeh screen capture/record bhi karta hai aur backend (RTMP/ local file) pe save/stream karta hai
 *
 * Usage:
 *   val gyroSdk = GyroscopeSDK(context)
 *   val streamSdk = StreamingSDK(context, gyroSdk)
 *   streamSdk.startSession("ball_tilt_game", "rtmp://your-backend-url/stream-key")
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
        const val ACTION_STREAMING_STARTED = "com.earnscape.gyroscopesdk.STREAMING_STARTED"
        const val ACTION_STREAMING_STOPPED = "com.earnscape.gyroscopesdk.STREAMING_STOPPED"
        const val ACTION_STREAMING_PAUSED  = "com.earnscape.gyroscopesdk.STREAMING_PAUSED"
        const val ACTION_STREAMING_RESUMED = "com.earnscape.gyroscopesdk.STREAMING_RESUMED"

        // Notification constants
        const val CHANNEL_ID = "StreamingSDKChannel"
        const val NOTIFICATION_ID = 101
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

    // Screen recording/streaming variables (inspired from ScreenRecordingService.kt)
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var rtmpDisplay: RtmpDisplay? = null  // For RTMP streaming to backend
    private var streamUrl: String? = null  // Backend RTMP URL
    private var localRecordFile: File? = null  // Local file for recording
    private var isPaused = false
    private var pauseStartMs: Long = 0L
    private var totalPausedMs: Long = 0L

    // MediaProjectionManager
    private val mediaProjectionManager: MediaProjectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

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
     * Screen capture/record/stream bhi start ho jata hai
     *
     * @param gameId       Game ka naam e.g. "ball_tilt_game"
     * @param streamUrl    Backend RTMP URL for live streaming (optional, if null to local record only)
     * @param samplingRate Gyro speed (default: SENSOR_DELAY_GAME)
     * @param autoLog      Logcat mein print karo
     * @return sessionId
     */
    fun startSession(
        gameId: String,
        streamUrl: String? = null,  // Backend URL for streaming
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
        this.streamUrl = streamUrl
        this.isPaused = false
        this.totalPausedMs = 0L

        // Gyroscope start karo
        gyroscopeSDK.start(
            samplingRate = samplingRate,
            autoLog = autoLog,
            onData = { data -> _onGyroReading(data) }
        )

        // Screen recording/streaming setup (MediaProjection required - assume permission granted or handle externally)
        // NOTE: MediaProjection requires user consent via startActivityForResult in Activity
        // For SDK, assume it's passed or handle in calling code
        setupScreenCapture()  // This needs MediaProjection - see note below

        isStreaming = true

        // Idle checker start karo
        mainHandler.postDelayed(idleChecker, 200)

        // Broadcast: session started
        broadcastManager.sendBroadcast(Intent(ACTION_SESSION_STARTED).apply {
            putExtra("sessionId", sessionId)
            putExtra("gameId", gameId)
            putExtra("startTimeMs", sessionStartMs)
        })

        // Broadcast: streaming started
        broadcastManager.sendBroadcast(Intent(ACTION_STREAMING_STARTED).apply {
            putExtra("sessionId", sessionId)
            putExtra("startTimeMs", sessionStartMs)
        })

        Log.d(TAG, "Session started: $sessionId | game: $gameId | streamUrl: $streamUrl")
        return sessionId!!
    }

    /**
     * Game session band karo
     * Gyroscope bhi automatically stop ho jata hai
     * Screen recording/streaming bhi stop ho jata hai
     * Returns full session data for backend (gyro + recording path if local)
     */
    fun stopSession(): SessionResult {
        val endTimeMs = System.currentTimeMillis()

        // Gyroscope stop karo
        gyroscopeSDK.stop()

        // Screen recording/streaming stop karo
        stopScreenCapture()

        // Idle checker stop karo
        mainHandler.removeCallbacks(idleChecker)
        isStreaming = false

        val result = SessionResult(
            sessionId = sessionId ?: "",
            gameId = gameId ?: "",
            startTimeMs = sessionStartMs,
            endTimeMs = endTimeMs,
            durationMs = (endTimeMs - sessionStartMs) - totalPausedMs,
            totalReadings = readingsBuffer.size,
            readings = readingsBuffer.toList(),
            recordPath = localRecordFile?.absolutePath  // If local recording
        )

        // Broadcast: session stopped
        broadcastManager.sendBroadcast(Intent(ACTION_SESSION_STOPPED).apply {
            putExtra("sessionId", sessionId)
            putExtra("gameId", gameId)
            putExtra("startTimeMs", sessionStartMs)
            putExtra("endTimeMs", endTimeMs)
            putExtra("durationMs", (endTimeMs - sessionStartMs) - totalPausedMs)
            putExtra("totalReadings", readingsBuffer.size)
        })

        // Broadcast: streaming stopped
        broadcastManager.sendBroadcast(Intent(ACTION_STREAMING_STOPPED).apply {
            putExtra("sessionId", sessionId)
            putExtra("endTimeMs", endTimeMs)
        })

        Log.d(TAG, "Session stopped: $sessionId | readings: ${readingsBuffer.size} | duration: ${(endTimeMs - sessionStartMs) - totalPausedMs}ms | recordPath: ${result.recordPath}")

        // Reset state
        sessionId = null
        gameId = null
        readingsBuffer.clear()
        streamUrl = null
        localRecordFile = null

        return result
    }

    /** Pause the streaming/recording */
    fun pauseSession() {
        if (!isStreaming || isPaused) return
        isPaused = true
        pauseStartMs = System.currentTimeMillis()

        // Pause recording/streaming
        mediaRecorder?.pause()
        rtmpDisplay?.pauseRecord()

        broadcastManager.sendBroadcast(Intent(ACTION_STREAMING_PAUSED).apply {
            putExtra("sessionId", sessionId)
            putExtra("pauseTimeMs", pauseStartMs)
        })

        Log.d(TAG, "Session paused: $sessionId")
    }

    /** Resume the streaming/recording */
    fun resumeSession() {
        if (!isStreaming || !isPaused) return
        val pauseDuration = System.currentTimeMillis() - pauseStartMs
        totalPausedMs += pauseDuration
        isPaused = false

        // Resume recording/streaming
        mediaRecorder?.resume()
        rtmpDisplay?.resumeRecord()

        broadcastManager.sendBroadcast(Intent(ACTION_STREAMING_RESUMED).apply {
            putExtra("sessionId", sessionId)
            putExtra("resumeTimeMs", System.currentTimeMillis())
        })

        Log.d(TAG, "Session resumed: $sessionId after ${pauseDuration}ms pause")
    }

    /** Is session currently active? */
    fun isActive() = isStreaming

    /** Current session ID */
    fun getSessionId() = sessionId

    /** Get buffered readings (for manual backend send) */
    fun getBufferedReadings() = readingsBuffer.toList()

    // ── Internal: Gyro Handling ────────────────────────────────────────────────

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

    // ── Internal: Screen Capture/Streaming ─────────────────────────────────────

    private fun setupScreenCapture() {
        // NOTE: MediaProjection requires user consent. In real app, start MediaProjection intent from Activity
        // and pass resultCode and data to this SDK via a setter method, e.g. setMediaProjection(resultCode, data)
        // For demo, assume it's set up externally or skip if null

        // Create notification channel (for foreground service if needed)
        createNotificationChannel()

        // Local recording setup
        localRecordFile = File(context.externalCacheDir, "session_${sessionId}.mp4")
        mediaRecorder = MediaRecorder().apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(localRecordFile?.absolutePath)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(1280, 720)  // Adjust as needed
            setVideoFrameRate(30)
            prepare()
        }

        // If streamUrl provided, setup RTMP streaming (using pedro library as in your files)
        if (streamUrl != null) {
            rtmpDisplay = RtmpDisplay(context, true, null)  // Adjust as per your library usage
            rtmpDisplay?.startStream(streamUrl)
        }

        // Start recording
        mediaRecorder?.start()

        // VirtualDisplay setup (needs mediaProjection)
        // virtualDisplay = mediaProjection?.createVirtualDisplay(...)  // Implement based on your needs
    }

    private fun stopScreenCapture() {
        mediaRecorder?.stop()
        mediaRecorder?.release()
        mediaRecorder = null

        rtmpDisplay?.stopStream()
        rtmpDisplay = null

        virtualDisplay?.release()
        virtualDisplay = null

        mediaProjection?.stop()
        mediaProjection = null
    }

    // Set MediaProjection from Activity result (call this after user consent)
    fun setMediaProjection(resultCode: Int, data: Intent?) {
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data ?: return)
        // Now you can create VirtualDisplay if needed
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Streaming SDK Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
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
        val readings: List<ReadingData>,
        val recordPath: String? = null  // Path to local recording file
    )
}