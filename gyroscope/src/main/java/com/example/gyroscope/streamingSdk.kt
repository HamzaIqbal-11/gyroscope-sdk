package com.earnscape.gyroscopesdk
import android.content.Context
import android.content.Intent
import android.hardware.SensorManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.sources.audio.InternalAudioSource
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.audio.MixAudioSource
import com.pedro.encoder.input.sources.audio.NoAudioSource
import com.pedro.encoder.input.sources.video.NoVideoSource
import com.pedro.encoder.input.sources.video.ScreenSource
import com.pedro.library.generic.GenericStream
import java.io.File
import java.io.IOException

/**
 * StreamingSDK - Handles screen streaming (RTMP/RTSP) + gyroscope data collection.
 *
 * Uses official RootEncoder 2.6.7 GenericStream API.
 * GenericStream auto-detects protocol (RTMP/RTSP/SRT) from the stream URL.
 * Uses ScreenSource for video and MixAudioSource for combined mic + game audio.
 *
 * Usage:
 *   1. Call setMediaProjection() with the result from MediaProjection permission
 *   2. Call startSession() with your RTMP/RTSP URL
 *   3. Call stopSession() when done
 */
class StreamingSDK(
    private val context: Context,
    private val gyroscopeSDK: GyroscopeSDK
) {

    companion object {
        private const val TAG = "StreamingSDK"

        // Broadcast actions
        const val ACTION_SESSION_STARTED     = "com.earnscape.gyroscopesdk.SESSION_STARTED"
        const val ACTION_SESSION_STOPPED     = "com.earnscape.gyroscopesdk.SESSION_STOPPED"
        const val ACTION_GYRO_DATA           = "com.earnscape.gyroscopesdk.GYRO_DATA"
        const val ACTION_GYRO_IDLE           = "com.earnscape.gyroscopesdk.GYRO_IDLE"
        const val ACTION_GYRO_ACTIVE         = "com.earnscape.gyroscopesdk.GYRO_ACTIVE"
        const val ACTION_STREAMING_STARTED   = "com.earnscape.gyroscopesdk.STREAMING_STARTED"
        const val ACTION_STREAMING_PAUSED    = "com.earnscape.gyroscopesdk.STREAMING_PAUSED"
        const val ACTION_STREAMING_RESUMED   = "com.earnscape.gyroscopesdk.STREAMING_RESUMED"
        const val ACTION_STREAMING_STOPPED   = "com.earnscape.gyroscopesdk.STREAMING_STOPPED"
        const val ACTION_STREAMING_ERROR     = "com.earnscape.gyroscopesdk.STREAMING_ERROR"

        // Default stream settings
        private const val DEFAULT_WIDTH = 1280
        private const val DEFAULT_HEIGHT = 720
        private const val DEFAULT_BITRATE = 300 * 1024
        private const val DEFAULT_FPS = 20
        private const val DEFAULT_ROTATION = 0
        private const val DEFAULT_AUDIO_BITRATE = 128 * 1024
        private const val DEFAULT_SAMPLE_RATE = 44100
    }

    private val broadcastManager = LocalBroadcastManager.getInstance(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    // Session state
    private var isStreaming = false
    private var isPaused = false
    private var sessionId: String? = null
    private var gameId: String? = null
    private var sessionStartMs: Long = 0L
    private var totalPausedMs: Long = 0L
    private var pauseStartMs: Long = 0L

    // Gyroscope data buffer
    private val readingsBuffer = mutableListOf<ReadingData>()

    // RootEncoder GenericStream (handles RTMP, RTSP, SRT based on URL)
    private var genericStream: GenericStream? = null
    private var streamUrl: String? = null
    private var localRecordFile: File? = null

    // MediaProjection for screen capture + internal audio
    private val mediaProjectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var projectionResultCode: Int = 0
    private var projectionData: Intent? = null

    // Current audio mode tracking (for mute/unmute by switching sources)
    private var currentAudioMode: AudioMode = AudioMode.NONE
    private var isMicMuted = false
    private var isDeviceAudioMuted = false

    // Idle detection
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

    // ─── ConnectChecker ─────────────────────────────────────────────

    private val connectChecker = object : ConnectChecker {

        override fun onConnectionSuccess() {
            Log.d(TAG, "Stream connected")
            broadcastManager.sendBroadcast(Intent(ACTION_STREAMING_STARTED).apply {
                putExtra("sessionId", sessionId)
            })
        }

        override fun onConnectionFailed(reason: String) {
            Log.e(TAG, "Stream connection failed: $reason")
            broadcastManager.sendBroadcast(Intent(ACTION_STREAMING_ERROR).apply {
                putExtra("sessionId", sessionId)
                putExtra("reason", reason)
            })
        }

        override fun onNewBitrate(bitrate: Long) {}

        override fun onDisconnect() {
            Log.d(TAG, "Stream disconnected")
        }

        override fun onConnectionStarted(url: String) {
            Log.d(TAG, "Connection started to: $url")
        }

        override fun onAuthError() {
            Log.e(TAG, "Authentication error")
            broadcastManager.sendBroadcast(Intent(ACTION_STREAMING_ERROR).apply {
                putExtra("sessionId", sessionId)
                putExtra("reason", "auth_error")
            })
        }

        override fun onAuthSuccess() {
            Log.d(TAG, "Authentication success")
        }
    }

    // ─── Public API ─────────────────────────────────────────────────

    /**
     * Store MediaProjection result. Must be called BEFORE startSession().
     */
    fun setMediaProjection(resultCode: Int, data: Intent?) {
        if (data == null) {
            Log.e(TAG, "MediaProjection data is null")
            return
        }
        projectionResultCode = resultCode
        projectionData = data
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
        Log.d(TAG, "MediaProjection set")
    }

    /**
     * Start a streaming + gyroscope session.
     */
    fun startSession(
        gameId: String,
        streamUrl: String? = null,
        samplingRate: Int = SensorManager.SENSOR_DELAY_GAME,
        autoLog: Boolean = false,
        config: StreamConfig = StreamConfig()
    ): String {
        if (isStreaming) stopSession()

        this.gameId = gameId
        this.sessionId = "session_${System.currentTimeMillis()}"
        this.sessionStartMs = System.currentTimeMillis()
        this.lastMovementMs = System.currentTimeMillis()
        this.streamUrl = streamUrl
        this.isPaused = false
        this.totalPausedMs = 0L
        this.isMicMuted = false
        this.isDeviceAudioMuted = false
        readingsBuffer.clear()

        gyroscopeSDK.start(samplingRate, autoLog) { data -> onGyroReading(data) }
        setupStreaming(config)

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

    /**
     * Pause the current session (stops recording temporarily).
     */
    fun pauseSession() {
        if (!isStreaming || isPaused) return
        isPaused = true
        pauseStartMs = System.currentTimeMillis()

        genericStream?.let {
            if (it.isRecording) it.stopRecord()
        }

        broadcastManager.sendBroadcast(Intent(ACTION_STREAMING_PAUSED).apply {
            putExtra("sessionId", sessionId)
        })
        Log.d(TAG, "Session paused")
    }

    /**
     * Resume the current session.
     */
    fun resumeSession() {
        if (!isStreaming || !isPaused) return
        totalPausedMs += (System.currentTimeMillis() - pauseStartMs)
        isPaused = false

        if (localRecordFile != null && streamUrl == null) {
            try {
                genericStream?.startRecord(localRecordFile!!.absolutePath) { status ->
                    Log.d(TAG, "Record status: $status")
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to resume recording: ${e.message}")
            }
        }

        broadcastManager.sendBroadcast(Intent(ACTION_STREAMING_RESUMED).apply {
            putExtra("sessionId", sessionId)
        })
        Log.d(TAG, "Session resumed")
    }

    /**
     * Stop the current session and return results.
     */
    fun stopSession(): SessionResult {
        val endTimeMs = System.currentTimeMillis()
        val activeDurationMs = (endTimeMs - sessionStartMs) - totalPausedMs

        gyroscopeSDK.stop()

        genericStream?.let {
            if (it.isStreaming) it.stopStream()
            if (it.isRecording) it.stopRecord()
            it.release()
        }
        genericStream = null

        mediaProjection?.stop()
        mediaProjection = null

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
        currentAudioMode = AudioMode.NONE

        Log.d(TAG, "Session stopped. Duration: ${activeDurationMs}ms")
        return result
    }

    // ─── Audio Controls ─────────────────────────────────────────────

    /**
     * Mute/unmute the microphone.
     * Works by switching the audio source on the fly:
     *   - If both were active (MIXED) and mic is muted → switch to DEVICE_ONLY
     *   - If mic was solo (MIC_ONLY) and muted → switch to NONE
     *   - Unmuting reverses the above
     */
    fun setMicrophoneMuted(muted: Boolean) {
        if (isMicMuted == muted) return
        isMicMuted = muted
        applyEffectiveAudioMode()
        Log.d(TAG, "Microphone ${if (muted) "muted" else "unmuted"}")
    }

    /**
     * Mute/unmute the device (game) audio.
     * Works by switching the audio source on the fly.
     */
    fun setDeviceAudioMuted(muted: Boolean) {
        if (isDeviceAudioMuted == muted) return
        isDeviceAudioMuted = muted
        applyEffectiveAudioMode()
        Log.d(TAG, "Device audio ${if (muted) "muted" else "unmuted"}")
    }

    fun isMicrophoneMuted(): Boolean = isMicMuted
    fun isDeviceAudioMuted(): Boolean = isDeviceAudioMuted

    /**
     * Switch audio source mode on the fly.
     * This sets the base mode; mute flags are reset.
     */
    fun setAudioMode(mode: AudioMode) {
        currentAudioMode = mode
        isMicMuted = false
        isDeviceAudioMuted = false
        applyAudioSource(mode)
    }

    // ─── Query State ────────────────────────────────────────────────

    fun isActive() = isStreaming
    fun isSessionPaused() = isPaused
    fun getSessionId() = sessionId
    fun getBufferedReadings() = readingsBuffer.toList()
    fun isStreamConnected(): Boolean = genericStream?.isStreaming == true

    // ─── Private Implementation ─────────────────────────────────────

    private fun setupStreaming(config: StreamConfig) {
        val mp = mediaProjection
        if (mp == null) {
            Log.e(TAG, "MediaProjection not set! Call setMediaProjection() first.")
            return
        }

        // GenericStream auto-detects protocol from URL (rtmp://, rtsp://, srt://)
        val stream = GenericStream(context, connectChecker, NoVideoSource(), NoAudioSource())

        // Force render ensures constant FPS even when screen content doesn't change
        stream.getGlInterface().setForceRender(true, config.fps)

        // Prepare video encoder
        val videoReady = try {
            stream.prepareVideo(
                config.width,
                config.height,
                config.videoBitrate,
                rotation = config.rotation
            )
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Video prepare failed: ${e.message}")
            false
        }

        // Prepare audio encoder
        val audioReady = try {
            stream.prepareAudio(
                config.sampleRate,
                config.stereo,
                config.audioBitrate
            )
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Audio prepare failed: ${e.message}")
            false
        }

        if (!videoReady || !audioReady) {
            Log.e(TAG, "Prepare failed (video=$videoReady, audio=$audioReady)")
            stream.release()
            return
        }

        // Video: screen capture via MediaProjection
        try {
            stream.changeVideoSource(ScreenSource(context, mp))
            Log.d(TAG, "Video: Screen capture")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set screen source: ${e.message}")
            stream.release()
            return
        }

        // Audio: set initial audio source based on config
        try {
            if (config.enableAudio) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && config.enableDeviceAudio) {
                    // Mixed audio: mic + game/device audio
                    // MixAudioSource takes MediaProjection and internally handles both sources
                    stream.changeAudioSource(MixAudioSource(mp))
                    currentAudioMode = AudioMode.MIXED
                    Log.d(TAG, "Audio: Mixed (mic + device)")
                } else {
                    // Microphone only
                    stream.changeAudioSource(MicrophoneSource())
                    currentAudioMode = AudioMode.MIC_ONLY
                    Log.d(TAG, "Audio: Microphone only")
                }
            } else {
                currentAudioMode = AudioMode.NONE
                Log.d(TAG, "Audio: Disabled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio source failed: ${e.message}. Continuing video-only.")
            currentAudioMode = AudioMode.NONE
        }

        genericStream = stream

        // Start streaming to URL or recording locally
        if (streamUrl != null) {
            stream.startStream(streamUrl!!)
            Log.d(TAG, "Streaming to: $streamUrl")
        } else {
            localRecordFile = File(context.externalCacheDir, "session_${sessionId}.mp4")
            try {
                stream.startRecord(localRecordFile!!.absolutePath) { status ->
                    Log.d(TAG, "Record status: $status")
                }
                Log.d(TAG, "Recording to: ${localRecordFile?.absolutePath}")
            } catch (e: IOException) {
                Log.e(TAG, "Failed to start recording: ${e.message}")
            }
        }
    }

    /**
     * Based on the current base audio mode + mute flags, determine
     * which actual audio source to apply.
     */
    private fun applyEffectiveAudioMode() {
        val effective = when (currentAudioMode) {
            AudioMode.MIXED -> {
                when {
                    isMicMuted && isDeviceAudioMuted -> AudioMode.NONE
                    isMicMuted -> AudioMode.DEVICE_ONLY
                    isDeviceAudioMuted -> AudioMode.MIC_ONLY
                    else -> AudioMode.MIXED
                }
            }
            AudioMode.MIC_ONLY -> {
                if (isMicMuted) AudioMode.NONE else AudioMode.MIC_ONLY
            }
            AudioMode.DEVICE_ONLY -> {
                if (isDeviceAudioMuted) AudioMode.NONE else AudioMode.DEVICE_ONLY
            }
            AudioMode.NONE -> AudioMode.NONE
        }
        applyAudioSource(effective)
    }

    /**
     * Actually switch the audio source on the GenericStream.
     */
    private fun applyAudioSource(mode: AudioMode) {
        val stream = genericStream ?: return
        val mp = mediaProjection ?: return

        try {
            when (mode) {
                AudioMode.MIC_ONLY -> {
                    stream.changeAudioSource(MicrophoneSource())
                    Log.d(TAG, "Audio source → MIC_ONLY")
                }
                AudioMode.DEVICE_ONLY -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        stream.changeAudioSource(InternalAudioSource(mp))
                        Log.d(TAG, "Audio source → DEVICE_ONLY")
                    } else {
                        Log.w(TAG, "Device audio requires Android 10+")
                    }
                }
                AudioMode.MIXED -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        stream.changeAudioSource(MixAudioSource(mp))
                        Log.d(TAG, "Audio source → MIXED")
                    } else {
                        stream.changeAudioSource(MicrophoneSource())
                        Log.w(TAG, "Mixed audio requires Android 10+, using mic only")
                    }
                }
                AudioMode.NONE -> {
                    stream.changeAudioSource(NoAudioSource())
                    Log.d(TAG, "Audio source → NONE")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change audio source to $mode: ${e.message}")
        }
    }

    private fun onGyroReading(data: GyroscopeSDK.GyroData) {
        if (!isStreaming) return

        if (!data.isIdle) {
            lastMovementMs = System.currentTimeMillis()
            if (isIdle) {
                isIdle = false
                broadcastManager.sendBroadcast(Intent(ACTION_GYRO_ACTIVE).apply {
                    putExtra("sessionId", sessionId)
                })
            }
        }

        val reading = ReadingData(
            x = data.x, y = data.y, z = data.z,
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

    // ─── Data Classes ───────────────────────────────────────────────

    data class ReadingData(
        val x: Float, val y: Float, val z: Float,
        val timestampNs: Long, val timestampMs: Long, val isIdle: Boolean
    )

    data class SessionResult(
        val sessionId: String, val gameId: String,
        val startTimeMs: Long, val endTimeMs: Long, val durationMs: Long,
        val totalReadings: Int, val readings: List<ReadingData>,
        val recordPath: String? = null
    )

    data class StreamConfig(
        val width: Int = DEFAULT_WIDTH,
        val height: Int = DEFAULT_HEIGHT,
        val fps: Int = DEFAULT_FPS,
        val videoBitrate: Int = DEFAULT_BITRATE,
        val audioBitrate: Int = DEFAULT_AUDIO_BITRATE,
        val sampleRate: Int = DEFAULT_SAMPLE_RATE,
        val stereo: Boolean = true,
        val rotation: Int = DEFAULT_ROTATION,
        val enableAudio: Boolean = true,
        val enableDeviceAudio: Boolean = true
    )

    enum class AudioMode {
        MIC_ONLY, DEVICE_ONLY, MIXED, NONE
    }
}