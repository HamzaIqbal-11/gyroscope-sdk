package com.example.gyroscope.kyc

import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.widget.*
import java.io.File

/**
 * KycVideoReviewActivity — Review selfie video (NO upload)
 *
 * - Plays recorded video
 * - Tap to play/pause
 * - Retake → go back to camera
 * - Confirm → return video path to parent
 */
class KycVideoReviewActivity : Activity() {

    companion object {
        const val EXTRA_VIDEO_PATH = "kycVideoPath"
        private const val MAX_VIDEO_SIZE_MB = 20
    }

    private var videoPath: String? = null

    // Views
    private lateinit var videoView: VideoView
    private lateinit var playIcon: TextView

    private var isPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        videoPath = intent.getStringExtra(EXTRA_VIDEO_PATH)

        @Suppress("DEPRECATION")
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        buildUI()
        initVideo()
    }

    private fun buildUI() {
        val rootLayout = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#0D1117")) }

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

        // Video Container
        val videoContainer = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setStroke(dp(2), Color.YELLOW)
                setColor(Color.BLACK)
            }
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(220)).apply { bottomMargin = dp(10) }
        }

        videoView = VideoView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH).apply { gravity = Gravity.CENTER }
        }
        videoContainer.addView(videoView)

        playIcon = TextView(this).apply {
            text = "▶"
            setTextColor(Color.WHITE)
            textSize = 48f
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }
        videoContainer.addView(playIcon)
        videoContainer.setOnClickListener { togglePlayback() }
        content.addView(videoContainer)

        // File size info
        videoPath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                val sizeMB = file.length() / (1024.0 * 1024.0)
                content.addView(TextView(this).apply {
                    text = "Size: %.1f MB".format(sizeMB)
                    setTextColor(Color.parseColor("#888888"))
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setPadding(0, dp(4), 0, dp(16))
                })
            }
        }

        content.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(20))
        })

        // Buttons Row
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        btnRow.addView(TextView(this).apply {
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
        })

        btnRow.addView(TextView(this).apply {
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
            setOnClickListener { confirmAndReturn() }
        })

        content.addView(btnRow)
        scroll.addView(content)
        rootLayout.addView(scroll)
        setContentView(rootLayout)
    }

    private fun initVideo() {
        if (videoPath == null) {
            Toast.makeText(this, "Video not found", Toast.LENGTH_SHORT).show()
            return
        }
        videoView.setVideoPath(videoPath)
        videoView.setOnPreparedListener { playIcon.visibility = View.VISIBLE }
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

    // ── Confirm → return video path (NO upload) ─────────────────────────────

    private fun confirmAndReturn() {
        val file = File(videoPath ?: return)
        if (!file.exists()) {
            Toast.makeText(this, "Video file not found", Toast.LENGTH_SHORT).show()
            return
        }

        val sizeMB = file.length() / (1024 * 1024)
        if (sizeMB > MAX_VIDEO_SIZE_MB) {
            Toast.makeText(this, "Video exceeds ${MAX_VIDEO_SIZE_MB}MB limit", Toast.LENGTH_LONG).show()
            return
        }

        setResult(RESULT_OK, Intent().apply {
            putExtra("selfieVideo", videoPath)
        })
        finish()
    }

    override fun onBackPressed() {
        // Force retake or confirm
    }

    override fun onDestroy() {
        videoView.stopPlayback()
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    private val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
}