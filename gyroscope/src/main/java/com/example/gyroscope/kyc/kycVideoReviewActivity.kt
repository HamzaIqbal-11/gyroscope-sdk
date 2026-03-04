package com.example.gyroscope.kyc

import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import java.io.File

/**
 * KycVideoReviewActivity — Review + Upload selfie video
 *
 * - Plays recorded video in a VideoView
 * - Tap to play/pause
 * - Retake → go back to camera
 * - Confirm → upload via multipart with progress
 * - Max 20MB file size check
 *
 * Upload endpoint: POST /player/uploadplayerKycSelfieVideo/{playerId}
 * Field: video (multipart file, video/mp4)
 */
class KycVideoReviewActivity : Activity() {

    companion object {
        private const val TAG = "KycVideoReview"

        const val EXTRA_API_URL = "kycApiUrl"
        const val EXTRA_PLAYER_ID = "kycPlayerId"
        const val EXTRA_HEADERS = "kycHeaders"
        const val EXTRA_VIDEO_PATH = "kycVideoPath"

        private const val MAX_VIDEO_SIZE_MB = 20
    }

    private var apiUrl = ""
    private var playerId = ""
    private var headers = mutableMapOf<String, String>()
    private var videoPath: String? = null

    // Views
    private lateinit var rootLayout: FrameLayout
    private lateinit var videoView: VideoView
    private lateinit var playIcon: TextView
    private lateinit var retakeBtn: TextView
    private lateinit var confirmBtn: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var progressContainer: LinearLayout
    private lateinit var btnRow: LinearLayout

    private var isPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        apiUrl = intent.getStringExtra(EXTRA_API_URL) ?: ""
        playerId = intent.getStringExtra(EXTRA_PLAYER_ID) ?: ""
        videoPath = intent.getStringExtra(EXTRA_VIDEO_PATH)

        val hBundle = intent.getBundleExtra(EXTRA_HEADERS)
        if (hBundle != null) {
            for (key in hBundle.keySet()) headers[key] = hBundle.getString(key, "")
        }

        @Suppress("DEPRECATION")
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        buildUI()
        initVideo()
    }

    private fun buildUI() {
        rootLayout = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#0D1117")) }

        val scroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            isVerticalScrollBarEnabled = false
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(48), dp(16), dp(40))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Title
        content.addView(TextView(this).apply {
            text = "Verify for correctness"
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(30) }
        })

        // ── Video Container (dotted border) ──
        val videoContainer = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setStroke(dp(2), Color.YELLOW)
                setColor(Color.BLACK)
            }
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(220)).apply { bottomMargin = dp(10) }
        }

        videoView = VideoView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH).apply {
                gravity = Gravity.CENTER
            }
        }
        videoContainer.addView(videoView)

        // Play icon overlay
        playIcon = TextView(this).apply {
            text = "▶"
            setTextColor(Color.WHITE)
            textSize = 48f
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }
        videoContainer.addView(playIcon)

        // Tap to play/pause
        videoContainer.setOnClickListener { togglePlayback() }

        content.addView(videoContainer)

        // ── Upload Progress ──
        progressContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(40), dp(16), dp(40), dp(16))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(8))
            max = 100
            progress = 0
            progressDrawable.setColorFilter(Color.parseColor("#FFC107"), PorterDuff.Mode.SRC_IN)
        }
        progressContainer.addView(progressBar)
        progressText = TextView(this).apply {
            text = "Video Uploading..."
            setTextColor(Color.parseColor("#B0B0B0"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }
        progressContainer.addView(progressText)
        content.addView(progressContainer)

        content.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(20))
        })

        // ── Buttons Row ──
        btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        retakeBtn = TextView(this).apply {
            text = "Retake"
            setTextColor(Color.BLACK)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#FFC107"))
            }
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(10) }
            setOnClickListener {
                videoView.stopPlayback()
                setResult(RESULT_CANCELED)
                finish()
            }
        }
        btnRow.addView(retakeBtn)

        confirmBtn = TextView(this).apply {
            text = "Confirm"
            setTextColor(Color.BLACK)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#4CAF50"))
            }
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(10) }
            setOnClickListener { uploadVideo() }
        }
        btnRow.addView(confirmBtn)

        content.addView(btnRow)

        scroll.addView(content)
        rootLayout.addView(scroll)
        setContentView(rootLayout)
    }

    // ── Video Playback ───────────────────────────────────────────────────────

    private fun initVideo() {
        if (videoPath == null) {
            Toast.makeText(this, "Video not found", Toast.LENGTH_SHORT).show()
            return
        }

        videoView.setVideoPath(videoPath)
        videoView.setOnPreparedListener { mp ->
            mp.isLooping = false
            playIcon.visibility = View.VISIBLE
        }
        videoView.setOnCompletionListener {
            isPlaying = false
            playIcon.visibility = View.VISIBLE
        }
    }

    private fun togglePlayback() {
        if (isPlaying) {
            videoView.pause()
            isPlaying = false
            playIcon.visibility = View.VISIBLE
        } else {
            videoView.start()
            isPlaying = true
            playIcon.visibility = View.GONE
        }
    }

    // ── Upload ───────────────────────────────────────────────────────────────

    private fun uploadVideo() {
        val file = File(videoPath ?: return)
        if (!file.exists()) {
            Toast.makeText(this, "Video file not found", Toast.LENGTH_SHORT).show()
            return
        }

        // File size check (max 20MB)
        val sizeMB = file.length() / (1024 * 1024)
        if (sizeMB > MAX_VIDEO_SIZE_MB) {
            Toast.makeText(this, "Video size must not exceed ${MAX_VIDEO_SIZE_MB}MB", Toast.LENGTH_LONG).show()
            return
        }

        // Show progress, hide buttons
        btnRow.visibility = View.GONE
        progressContainer.visibility = View.VISIBLE
        progressBar.progress = 0

        // Pause video if playing
        if (isPlaying) {
            videoView.pause()
            isPlaying = false
        }

        Thread {
            try {
                val url = "$apiUrl/player/uploadplayerKycSelfieVideo/$playerId"

                val result = KycUploadHelper.upload(
                    url = url,
                    headers = headers,
                    files = listOf("video" to file),
                    contentType = "video/mp4",
                    onProgress = { progress ->
                        runOnUiThread {
                            progressBar.progress = (progress * 100).toInt()
                        }
                    }
                )

                runOnUiThread {
                    progressContainer.visibility = View.GONE

                    if (result.success) {
                        Toast.makeText(this, result.message.ifEmpty { "Video uploaded!" }, Toast.LENGTH_LONG).show()
                        setResult(RESULT_OK, Intent().apply {
                            putExtra("uploadSuccess", true)
                            putExtra("uploadMessage", result.message)
                        })
                        finish()
                    } else {
                        Toast.makeText(this, result.message.ifEmpty { "Upload failed" }, Toast.LENGTH_LONG).show()
                        btnRow.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Upload error: ${e.message}")
                runOnUiThread {
                    progressContainer.visibility = View.GONE
                    btnRow.visibility = View.VISIBLE
                    Toast.makeText(this, "Something went wrong. Please try again.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onBackPressed() {
        // Force retake or confirm — no back
    }

    override fun onDestroy() {
        videoView.stopPlayback()
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    private val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
}