package com.earnscape.gyroscopesdk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.earnscape.gyroscopesdk.GyroscopeSDK
import com.earnscape.gyroscopesdk.StreamingSDK

class ScreenRecordingService : Service() {

    companion object {
        private const val TAG = "ScreenRecordingService"
        private const val NOTIFICATION_ID = 9999
        private const val CHANNEL_ID = "screen_recording_channel"

        const val ACTION_START = "com.earnscape.gyroscopesdk.START_RECORDING"
        const val ACTION_STOP  = "com.earnscape.gyroscopesdk.STOP_RECORDING"

        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        const val EXTRA_GAME_ID = "gameId"
        const val EXTRA_STREAM_URL = "streamUrl"

        @Volatile
        var isRunning = false
            private set
    }

    private var streamingSDK: StreamingSDK? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.d(TAG, "ACTION_START received")

                // MUST call startForeground IMMEDIATELY (Android 10+ requirement)
                startForeground(NOTIFICATION_ID, buildNotification())
                Log.d(TAG, "Foreground service started with notification")

                // Extract MediaProjection data
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
                val gameId = intent.getStringExtra(EXTRA_GAME_ID) ?: "unknown"
                val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL)
                val streamKey = intent.getStringExtra("streamKey") ?: ""
                val audio = intent.getStringExtra("audio") ?: "true"
                val playerName = intent.getStringExtra("playerName") ?: ""
                val gameName = intent.getStringExtra("gameName") ?: ""

                Log.d(TAG, "GameId: $gameId")
                Log.d(TAG, "StreamUrl: $streamUrl")
                Log.d(TAG, "StreamKey: $streamKey")
                Log.d(TAG, "Audio: $audio")

                if (data == null) {
                    Log.e(TAG, "MediaProjection data is null! Cannot start.")
                    stopSelf()
                    return START_NOT_STICKY
                }

                try {
                    // Create StreamingSDK
                    val gyroSDK = GyroscopeSDK(applicationContext)
                    streamingSDK = StreamingSDK(applicationContext, gyroSDK)

                    // Step 1: Set MediaProjection (MUST happen before startSession)
                    streamingSDK!!.setMediaProjection(resultCode, data)
                    Log.d(TAG, "✅ MediaProjection set successfully")

                    // Step 2: Configure stream
                    val config = StreamingSDK.StreamConfig(
                        width = 1280,
                        height = 720,
                        fps = 20,
                        videoBitrate = 300 * 1024,
                        audioBitrate = 128 * 1024,
                        sampleRate = 44100,
                        stereo = true,
                        enableAudio = audio == "true",
                        enableDeviceAudio = true
                    )

                    // Step 3: Start session with stream URL
                    val sessionId = streamingSDK!!.startSession(
                        gameId = gameId,
                        streamUrl = streamUrl,  // null = local recording, URL = RTMP/RTSP/SRT
                        config = config
                    )

                    isRunning = true
                    Log.d(TAG, "✅ Recording started. Session: $sessionId")
                    Log.d(TAG, "✅ Streaming to: ${streamUrl ?: "local recording"}")

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to start streaming: ${e.message}", e)
                    stopSelf()
                }
            }

            ACTION_STOP -> {
                Log.d(TAG, "ACTION_STOP received")
                stopRecording()
                stopSelf()
            }

            else -> {
                Log.d(TAG, "Unknown action: ${intent?.action}")
            }
        }

        return START_NOT_STICKY
    }

    private fun stopRecording() {
        try {
            streamingSDK?.stopSession()
            Log.d(TAG, "✅ Recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}", e)
        }
        streamingSDK = null
        isRunning = false
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        stopRecording()
        super.onDestroy()
    }

    // ── Notification ──

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Game Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when screen recording is active"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("Game Recording Active")
            .setContentText("Screen is being captured and streamed")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }
}