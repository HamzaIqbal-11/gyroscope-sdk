//package com.example.gyroscope.kyc
//
//import android.app.Activity
//import android.Manifest
//import android.content.Context
//import android.content.Intent
//import android.content.pm.PackageManager
//import android.graphics.*
//import android.graphics.drawable.GradientDrawable
//import android.hardware.camera2.*
//import android.media.MediaRecorder
//import android.os.Build
//import android.os.Bundle
//import android.os.CountDownTimer
//import android.os.Handler
//import android.os.HandlerThread
//import android.util.Log
//import android.util.Size
//import android.view.*
//import android.widget.*
//import java.io.File
//
///**
// * KycVideoCaptureActivity — Record selfie video
// *
// * - Front camera (mirrored preview)
// * - 3 second countdown before recording
// * - 5 second recording
// * - Shows remaining time
// * - Prompt: "The Quick brown fox jumps over the lazy dog"
// * - After recording → KycVideoReviewActivity
// */
//class KycVideoCaptureActivity : Activity() {
//
//    companion object {
//        private const val TAG = "KycVideoCapture"
//
//        private const val RC_REVIEW = 5030
//        private const val RC_AUDIO_PERM = 5031
//        private const val COUNTDOWN_SECONDS = 3
//        private const val RECORDING_SECONDS = 5
//    }
//
//    // Camera2
//    private var cameraDevice: CameraDevice? = null
//    private var captureSession: CameraCaptureSession? = null
//    private var cameraThread: HandlerThread? = null
//    private var cameraHandler: Handler? = null
//    private var textureView: TextureView? = null
//    private var cameraId: String? = null
//
//    // Recording
//    private var mediaRecorder: MediaRecorder? = null
//    private var videoFilePath: String? = null
//    private var isRecording = false
//
//    // Views
//    private lateinit var countdownText: TextView
//    private lateinit var timerBadge: TextView
//    private lateinit var recordBtn: View
//    private lateinit var recordBtnOuter: FrameLayout
//    private lateinit var recordBtnBorder: GradientDrawable
//
//    // Permission overlay
//    private var permissionOverlay: FrameLayout? = null
//    private var permissionBlocked = false
//
//    // ── Lifecycle ────────────────────────────────────────────────────────────
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        @Suppress("DEPRECATION")
//        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
//
//        buildUI()
//        startCameraThread()
//
//        // Check mic permission immediately
//        checkMicPermission()
//    }
//
//    private fun checkMicPermission() {
//        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
//            permissionBlocked = true
//            showPermissionOverlay()
//            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RC_AUDIO_PERM)
//        } else {
//            permissionBlocked = false
//            hidePermissionOverlay()
//        }
//    }
//
//    private fun showPermissionOverlay() {
//        if (permissionOverlay != null) return
//
//        permissionOverlay = FrameLayout(this).apply {
//            setBackgroundColor(Color.parseColor("#E6000000"))
//            isClickable = true  // block touches behind
//
//            val card = LinearLayout(this@KycVideoCaptureActivity).apply {
//                orientation = LinearLayout.VERTICAL
//                gravity = Gravity.CENTER
//                setPadding(60, 50, 60, 50)
//                background = GradientDrawable().apply {
//                    setColor(Color.parseColor("#1A1A3E"))
//                    cornerRadius = 32f
//                    setStroke(2, Color.parseColor("#FF6B6B"))
//                }
//
//                // Icon
//                val icon = TextView(this@KycVideoCaptureActivity).apply {
//                    text = "\uD83C\uDFA4" // microphone emoji
//                    textSize = 40f
//                    gravity = Gravity.CENTER
//                }
//                addView(icon)
//
//                // Title
//                val title = TextView(this@KycVideoCaptureActivity).apply {
//                    text = "Microphone Permission Required"
//                    setTextColor(Color.WHITE)
//                    textSize = 18f
//                    gravity = Gravity.CENTER
//                    setPadding(0, 24, 0, 12)
//                    paint.isFakeBoldText = true
//                }
//                addView(title)
//
//                // Description
//                val desc = TextView(this@KycVideoCaptureActivity).apply {
//                    text = "You need to speak a sentence during the video recording. Please allow microphone access to continue."
//                    setTextColor(Color.parseColor("#AAAAAA"))
//                    textSize = 14f
//                    gravity = Gravity.CENTER
//                    setPadding(0, 0, 0, 30)
//                }
//                addView(desc)
//
//                // Allow button
//                val allowBtn = TextView(this@KycVideoCaptureActivity).apply {
//                    text = "GRANT PERMISSION"
//                    setTextColor(Color.WHITE)
//                    textSize = 15f
//                    gravity = Gravity.CENTER
//                    setPadding(0, 28, 0, 28)
//                    paint.isFakeBoldText = true
//                    background = GradientDrawable().apply {
//                        setColor(Color.parseColor("#667EEA"))
//                        cornerRadius = 16f
//                    }
//                    setOnClickListener {
//                        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
//                            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RC_AUDIO_PERM)
//                        } else {
//                            // User selected "Don't ask again" — go to app settings
//                            try {
//                                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
//                                intent.data = android.net.Uri.parse("package:$packageName")
//                                startActivity(intent)
//                            } catch (_: Exception) {}
//                        }
//                    }
//                }
//                val btnParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
//                addView(allowBtn, btnParams)
//
//                // Cancel button
//                val cancelBtn = TextView(this@KycVideoCaptureActivity).apply {
//                    text = "Go Back"
//                    setTextColor(Color.parseColor("#888888"))
//                    textSize = 13f
//                    gravity = Gravity.CENTER
//                    setPadding(0, 24, 0, 0)
//                    setOnClickListener {
//                        setResult(Activity.RESULT_CANCELED)
//                        finish()
//                    }
//                }
//                addView(cancelBtn)
//            }
//
//            val cardParams = FrameLayout.LayoutParams(
//                FrameLayout.LayoutParams.MATCH_PARENT,
//                FrameLayout.LayoutParams.WRAP_CONTENT
//            ).apply {
//                gravity = Gravity.CENTER
//                marginStart = 48
//                marginEnd = 48
//            }
//            addView(card, cardParams)
//        }
//
//        // Add overlay on top of everything
//        val root = window.decorView.findViewById<FrameLayout>(android.R.id.content)
//        root.addView(permissionOverlay, FrameLayout.LayoutParams(
//            FrameLayout.LayoutParams.MATCH_PARENT,
//            FrameLayout.LayoutParams.MATCH_PARENT
//        ))
//    }
//
//    private fun hidePermissionOverlay() {
//        permissionOverlay?.let {
//            val root = window.decorView.findViewById<FrameLayout>(android.R.id.content)
//            root.removeView(it)
//            permissionOverlay = null
//        }
//    }
//
//    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        if (requestCode == RC_AUDIO_PERM) {
//            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                permissionBlocked = false
//                hidePermissionOverlay()
//            }
//            // If denied, overlay stays — user must grant or go back
//        }
//    }
//
//    override fun onResume() {
//        super.onResume()
//        // Recheck permission (user may have granted in settings)
//        if (permissionBlocked) {
//            checkMicPermission()
//        }
//        if (textureView?.isAvailable == true) {
//            openCamera()
//        }
//    }
//
//    override fun onPause() {
//        stopRecordingSilently()
//        closeCamera()
//        super.onPause()
//    }
//
//    override fun onDestroy() {
//        stopCameraThread()
//        super.onDestroy()
//    }
//
//    // ── Build UI ─────────────────────────────────────────────────────────────
//
//    private fun buildUI() {
//        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
//
//        // Camera preview (mirrored for front camera)
//        textureView = TextureView(this).apply {
//            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
//            scaleX = -1f // Mirror horizontally
//            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
//                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) { openCamera() }
//                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
//                override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
//                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
//            }
//        }
//        root.addView(textureView)
//
//        // ── Timer badge (top right) — hidden until recording ──
//        timerBadge = TextView(this).apply {
//            text = "$RECORDING_SECONDS"
//            setTextColor(Color.WHITE)
//            textSize = 18f
//            typeface = Typeface.DEFAULT_BOLD
//            gravity = Gravity.CENTER
//            setPadding(dp(12), dp(4), dp(12), dp(4))
//            background = GradientDrawable().apply {
//                cornerRadius = dp(16).toFloat()
//                setColor(Color.parseColor("#4CAF50"))
//            }
//            visibility = View.GONE
//            layoutParams = FrameLayout.LayoutParams(WRAP, WRAP).apply {
//                gravity = Gravity.TOP or Gravity.END
//                topMargin = dp(30)
//                rightMargin = dp(20)
//            }
//        }
//        root.addView(timerBadge)
//
//        // ── Top label ──
//        val topLabel = FrameLayout(this).apply {
//            layoutParams = FrameLayout.LayoutParams(MATCH, WRAP).apply {
//                gravity = Gravity.TOP
//                topMargin = dp(50)
//                leftMargin = dp(20)
//                rightMargin = dp(20)
//            }
//        }
//        topLabel.addView(TextView(this).apply {
//            text = "Read the sentence below"
//            setTextColor(Color.WHITE)
//            textSize = 16f
//            typeface = Typeface.DEFAULT_BOLD
//            gravity = Gravity.CENTER
//            setPadding(dp(16), dp(14), dp(16), dp(14))
//            background = GradientDrawable().apply {
//                cornerRadius = dp(16).toFloat()
//                setColor(Color.parseColor("#4DFFFFFF"))
//                setStroke(dp(1), Color.WHITE)
//            }
//        })
//        root.addView(topLabel)
//
//        // ── Countdown overlay (center) ──
//        countdownText = TextView(this).apply {
//            text = ""
//            setTextColor(Color.WHITE)
//            textSize = 72f
//            typeface = Typeface.DEFAULT_BOLD
//            gravity = Gravity.CENTER
//            visibility = View.GONE
//            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH).apply {
//                gravity = Gravity.CENTER
//            }
//        }
//        root.addView(countdownText)
//
//        // ── Bottom panel ──
//        val bottomPanel = LinearLayout(this).apply {
//            orientation = LinearLayout.VERTICAL
//            setBackgroundColor(Color.parseColor("#CC000000"))
//            gravity = Gravity.CENTER_HORIZONTAL
//            setPadding(dp(16), dp(12), dp(16), dp(48)) // fallback padding
//            layoutParams = FrameLayout.LayoutParams(MATCH, WRAP).apply {
//                gravity = Gravity.BOTTOM
//            }
//        }
//
//        // Adjust bottom padding for navigation bar
//        root.setOnApplyWindowInsetsListener { _, insets ->
//            @Suppress("DEPRECATION")
//            val navHeight = insets.systemWindowInsetBottom
//            bottomPanel.setPadding(dp(16), dp(12), dp(16), navHeight + dp(12))
//            insets
//        }
//
//        bottomPanel.addView(TextView(this).apply {
//            text = "Please say"
//            setTextColor(Color.WHITE)
//            textSize = 16f
//            typeface = Typeface.DEFAULT_BOLD
//            gravity = Gravity.CENTER
//        })
//
//        bottomPanel.addView(TextView(this).apply {
//            text = "\"The Quick brown fox\njumps over the lazy dog\""
//            setTextColor(Color.parseColor("#B0B0B0"))
//            textSize = 14f
//            gravity = Gravity.CENTER
//            setPadding(0, dp(8), 0, dp(16))
//        })
//
//        // Record button
//        recordBtnBorder = GradientDrawable().apply {
//            shape = GradientDrawable.OVAL
//            setStroke(dp(3), Color.WHITE)
//            setColor(Color.TRANSPARENT)
//        }
//        recordBtnOuter = FrameLayout(this).apply {
//            setPadding(dp(5), dp(5), dp(5), dp(5))
//            background = recordBtnBorder
//            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply {
//                gravity = Gravity.CENTER
//            }
//        }
//        recordBtn = View(this).apply {
//            background = GradientDrawable().apply {
//                shape = GradientDrawable.OVAL
//                setColor(Color.WHITE)
//            }
//            layoutParams = FrameLayout.LayoutParams(dp(56), dp(56)).apply {
//                gravity = Gravity.CENTER
//            }
//            setOnClickListener { onRecordPressed() }
//        }
//        recordBtnOuter.addView(recordBtn)
//        bottomPanel.addView(recordBtnOuter)
//
//        root.addView(bottomPanel)
//        setContentView(root)
//    }
//
//    // ── Camera2 ──────────────────────────────────────────────────────────────
//
//    private fun startCameraThread() {
//        cameraThread = HandlerThread("KycVideoCamera").also { it.start() }
//        cameraHandler = Handler(cameraThread!!.looper)
//    }
//
//    private fun stopCameraThread() {
//        cameraThread?.quitSafely()
//        try { cameraThread?.join() } catch (_: Exception) {}
//        cameraThread = null
//        cameraHandler = null
//    }
//
//    private fun openCamera() {
//        try {
//            val mgr = getSystemService(Context.CAMERA_SERVICE) as CameraManager
//
//            // Find front camera
//            for (id in mgr.cameraIdList) {
//                val chars = mgr.getCameraCharacteristics(id)
//                val facing = chars.get(CameraCharacteristics.LENS_FACING)
//                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
//                    cameraId = id
//                    break
//                }
//            }
//
//            if (cameraId == null) {
//                Log.e(TAG, "No front camera found")
//                Toast.makeText(this, "No front camera found", Toast.LENGTH_SHORT).show()
//                finish()
//                return
//            }
//
//            mgr.openCamera(cameraId!!, object : CameraDevice.StateCallback() {
//                override fun onOpened(camera: CameraDevice) {
//                    cameraDevice = camera
//                    createPreviewSession()
//                }
//                override fun onDisconnected(camera: CameraDevice) {
//                    camera.close(); cameraDevice = null
//                }
//                override fun onError(camera: CameraDevice, error: Int) {
//                    camera.close(); cameraDevice = null
//                    Log.e(TAG, "Camera error: $error")
//                }
//            }, cameraHandler)
//
//        } catch (e: SecurityException) {
//            Log.e(TAG, "Camera permission not granted")
//            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
//            finish()
//        } catch (e: Exception) {
//            Log.e(TAG, "Failed to open camera: ${e.message}")
//            finish()
//        }
//    }
//
//    private fun createPreviewSession() {
//        try {
//            val texture = textureView?.surfaceTexture ?: return
//            texture.setDefaultBufferSize(640, 480)
//            val surface = Surface(texture)
//
//            val previewRequest = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)?.apply {
//                addTarget(surface)
//            } ?: return
//
//            cameraDevice?.createCaptureSession(
//                listOf(surface),
//                object : CameraCaptureSession.StateCallback() {
//                    override fun onConfigured(session: CameraCaptureSession) {
//                        captureSession = session
//                        try { session.setRepeatingRequest(previewRequest.build(), null, cameraHandler) }
//                        catch (e: Exception) { Log.e(TAG, "Preview failed: ${e.message}") }
//                    }
//                    override fun onConfigureFailed(session: CameraCaptureSession) {
//                        Log.e(TAG, "Session configure failed")
//                    }
//                },
//                cameraHandler
//            )
//        } catch (e: Exception) {
//            Log.e(TAG, "Create preview failed: ${e.message}")
//        }
//    }
//
//    private fun closeCamera() {
//        try {
//            captureSession?.close(); captureSession = null
//            cameraDevice?.close(); cameraDevice = null
//        } catch (_: Exception) {}
//    }
//
//    // ── Recording ────────────────────────────────────────────────────────────
//
//    private fun onRecordPressed() {
//        if (isRecording || permissionBlocked) return
//        startCountdown()
//    }
//
//    private fun startCountdown() {
//        recordBtn.isEnabled = false
//        countdownText.visibility = View.VISIBLE
//
//        object : CountDownTimer((COUNTDOWN_SECONDS * 1000).toLong(), 1000) {
//            override fun onTick(ms: Long) {
//                val sec = (ms / 1000) + 1
//                countdownText.text = "$sec"
//            }
//            override fun onFinish() {
//                countdownText.visibility = View.GONE
//                startRecording()
//            }
//        }.start()
//    }
//
//    private fun startRecording() {
//        try {
//            // Close preview session first
//            captureSession?.close()
//            captureSession = null
//
//            // Prepare output file
//            val dir = File(cacheDir, "kyc_videos")
//            if (!dir.exists()) dir.mkdirs()
//            videoFilePath = "${dir.absolutePath}/kyc_video_${System.currentTimeMillis()}.mp4"
//
//            // Setup MediaRecorder
//            mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//                MediaRecorder(this)
//            } else {
//                @Suppress("DEPRECATION")
//                MediaRecorder()
//            }).apply {
//                setAudioSource(MediaRecorder.AudioSource.MIC)
//                setVideoSource(MediaRecorder.VideoSource.SURFACE)
//                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
//                setOutputFile(videoFilePath)
//                setVideoEncodingBitRate(2_000_000)
//                setVideoFrameRate(30)
//                setVideoSize(640, 480)
//                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
//                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
//                setAudioEncodingBitRate(128_000)
//                setAudioSamplingRate(44100)
//
//                // Orientation for front camera
//                setOrientationHint(270)
//
//                prepare()
//            }
//
//            // Create recording session
//            val texture = textureView?.surfaceTexture ?: return
//            texture.setDefaultBufferSize(640, 480)
//            val previewSurface = Surface(texture)
//            val recorderSurface = mediaRecorder!!.surface
//
//            val captureRequest = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)?.apply {
//                addTarget(previewSurface)
//                addTarget(recorderSurface)
//            } ?: return
//
//            cameraDevice?.createCaptureSession(
//                listOf(previewSurface, recorderSurface),
//                object : CameraCaptureSession.StateCallback() {
//                    override fun onConfigured(session: CameraCaptureSession) {
//                        captureSession = session
//                        try {
//                            session.setRepeatingRequest(captureRequest.build(), null, cameraHandler)
//                            mediaRecorder?.start()
//                            isRecording = true
//
//                            runOnUiThread {
//                                // Update UI to red
//                                (recordBtn.background as GradientDrawable).setColor(Color.RED)
//                                recordBtnBorder.setStroke(dp(3), Color.RED)
//                                timerBadge.visibility = View.VISIBLE
//                                startRecordingTimer()
//                            }
//                        } catch (e: Exception) {
//                            Log.e(TAG, "Start recording failed: ${e.message}")
//                        }
//                    }
//                    override fun onConfigureFailed(session: CameraCaptureSession) {
//                        Log.e(TAG, "Recording session configure failed")
//                    }
//                },
//                cameraHandler
//            )
//
//        } catch (e: Exception) {
//            Log.e(TAG, "Setup recording failed: ${e.message}", e)
//            isRecording = false
//            recordBtn.isEnabled = true
//        }
//    }
//
//    private fun startRecordingTimer() {
//        var remaining = RECORDING_SECONDS
//
//        object : CountDownTimer((RECORDING_SECONDS * 1000).toLong(), 1000) {
//            override fun onTick(ms: Long) {
//                remaining = ((ms / 1000) + 1).toInt()
//                timerBadge.text = "$remaining"
//            }
//            override fun onFinish() {
//                timerBadge.text = "0"
//                stopRecordingAndReview()
//            }
//        }.start()
//    }
//
//    private fun stopRecordingAndReview() {
//        try {
//            mediaRecorder?.stop()
//            mediaRecorder?.release()
//            mediaRecorder = null
//            isRecording = false
//
//            Log.d(TAG, "Video saved: $videoFilePath")
//
//            // Check file size
//            val videoFile = File(videoFilePath!!)
//            Log.d(TAG, "Video size: ${videoFile.length() / 1024} KB")
//
//            // Navigate to review
//            val intent = Intent(this, KycVideoReviewActivity::class.java).apply {
//                putExtra(KycVideoReviewActivity.EXTRA_VIDEO_PATH, videoFilePath)
//            }
//            startActivityForResult(intent, RC_REVIEW)
//
//        } catch (e: Exception) {
//            Log.e(TAG, "Stop recording failed: ${e.message}")
//            isRecording = false
//            recordBtn.isEnabled = true
//        }
//    }
//
//    private fun stopRecordingSilently() {
//        try {
//            if (isRecording) {
//                mediaRecorder?.stop()
//            }
//            mediaRecorder?.release()
//            mediaRecorder = null
//            isRecording = false
//        } catch (_: Exception) {}
//    }
//
//    // ── Activity Result ──────────────────────────────────────────────────────
//
//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//        if (requestCode == RC_REVIEW) {
//            if (resultCode == RESULT_OK) {
//                // Confirmed → pass video path back
//                setResult(RESULT_OK, data)
//                finish()
//            } else {
//                // Retake → reset and reopen camera
//                isRecording = false
//                (recordBtn.background as? GradientDrawable)?.setColor(Color.WHITE)
//                recordBtnBorder.setStroke(dp(3), Color.WHITE)
//                timerBadge.visibility = View.GONE
//                recordBtn.isEnabled = true
//                openCamera()
//            }
//        }
//    }
//
//    override fun onBackPressed() { setResult(RESULT_CANCELED); finish() }
//
//    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
//    private val MATCH = FrameLayout.LayoutParams.MATCH_PARENT
//    private val WRAP = FrameLayout.LayoutParams.WRAP_CONTENT
//}

package com.example.gyroscope.kyc

import android.app.Activity
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.*
import java.io.File

/**
 * KycVideoCaptureActivity — Record selfie video
 *
 * - Front camera (mirrored preview)
 * - 3 second countdown before recording
 * - 5 second recording
 * - Shows remaining time
 * - Prompt: "The Quick brown fox jumps over the lazy dog"
 * - After recording → KycVideoReviewActivity
 */
class KycVideoCaptureActivity : Activity() {

    companion object {
        private const val TAG = "KycVideoCapture"

        private const val RC_REVIEW = 5030
        private const val RC_AUDIO_PERM = 5031
        private const val COUNTDOWN_SECONDS = 3
        private const val RECORDING_SECONDS = 5
    }

    // Camera2
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var textureView: TextureView? = null
    private var cameraId: String? = null

    // Recording
    private var mediaRecorder: MediaRecorder? = null
    private var videoFilePath: String? = null
    private var isRecording = false

    // Views
    private lateinit var countdownText: TextView
    private lateinit var timerBadge: TextView
    private lateinit var recordBtn: View
    private lateinit var recordBtnOuter: FrameLayout
    private lateinit var recordBtnBorder: GradientDrawable

    // Permission overlay
    private var permissionOverlay: FrameLayout? = null
    private var permissionBlocked = false

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        buildUI()
        startCameraThread()

        // Check mic permission immediately
        checkMicPermission()
    }

    private fun checkMicPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionBlocked = true
            showPermissionOverlay()
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RC_AUDIO_PERM)
        } else {
            permissionBlocked = false
            hidePermissionOverlay()
        }
    }

    private fun showPermissionOverlay() {
        if (permissionOverlay != null) return

        permissionOverlay = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#E6000000"))
            isClickable = true  // block touches behind

            val card = LinearLayout(this@KycVideoCaptureActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(60, 50, 60, 50)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1A1A3E"))
                    cornerRadius = 32f
                    setStroke(2, Color.parseColor("#FF6B6B"))
                }

                // Icon
                val icon = TextView(this@KycVideoCaptureActivity).apply {
                    text = "\uD83C\uDFA4" // microphone emoji
                    textSize = 40f
                    gravity = Gravity.CENTER
                }
                addView(icon)

                // Title
                val title = TextView(this@KycVideoCaptureActivity).apply {
                    text = "Microphone Permission Required"
                    setTextColor(Color.WHITE)
                    textSize = 18f
                    gravity = Gravity.CENTER
                    setPadding(0, 24, 0, 12)
                    paint.isFakeBoldText = true
                }
                addView(title)

                // Description
                val desc = TextView(this@KycVideoCaptureActivity).apply {
                    text = "You need to speak a sentence during the video recording. Please allow microphone access to continue."
                    setTextColor(Color.parseColor("#AAAAAA"))
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, 30)
                }
                addView(desc)

                // Allow button
                val allowBtn = TextView(this@KycVideoCaptureActivity).apply {
                    text = "GRANT PERMISSION"
                    setTextColor(Color.WHITE)
                    textSize = 15f
                    gravity = Gravity.CENTER
                    setPadding(0, 28, 0, 28)
                    paint.isFakeBoldText = true
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#667EEA"))
                        cornerRadius = 16f
                    }
                    setOnClickListener {
                        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RC_AUDIO_PERM)
                        } else {
                            // User selected "Don't ask again" — go to app settings
                            try {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                intent.data = android.net.Uri.parse("package:$packageName")
                                startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    }
                }
                val btnParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                addView(allowBtn, btnParams)

                // Cancel button
                val cancelBtn = TextView(this@KycVideoCaptureActivity).apply {
                    text = "Go Back"
                    setTextColor(Color.parseColor("#888888"))
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setPadding(0, 24, 0, 0)
                    setOnClickListener {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                }
                addView(cancelBtn)
            }

            val cardParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                marginStart = 48
                marginEnd = 48
            }
            addView(card, cardParams)
        }

        // Add overlay on top of everything
        val root = window.decorView.findViewById<FrameLayout>(android.R.id.content)
        root.addView(permissionOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    private fun hidePermissionOverlay() {
        permissionOverlay?.let {
            val root = window.decorView.findViewById<FrameLayout>(android.R.id.content)
            root.removeView(it)
            permissionOverlay = null
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_AUDIO_PERM) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                permissionBlocked = false
                hidePermissionOverlay()
            }
            // If denied, overlay stays — user must grant or go back
        }
    }

    override fun onResume() {
        super.onResume()
        // Recheck permission (user may have granted in settings)
        if (permissionBlocked) {
            checkMicPermission()
        }
        if (textureView?.isAvailable == true) {
            openCamera()
        }
    }

    override fun onPause() {
        stopRecordingSilently()
        closeCamera()
        super.onPause()
    }

    override fun onDestroy() {
        stopCameraThread()
        super.onDestroy()
    }

    // ── Build UI ─────────────────────────────────────────────────────────────

    private fun buildUI() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        // Camera preview (mirrored for front camera)
        textureView = TextureView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            scaleX = -1f // Mirror horizontally
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) { openCamera() }
                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
            }
        }
        root.addView(textureView)

        // ── Timer badge (top right) — hidden until recording ──
        timerBadge = TextView(this).apply {
            text = "$RECORDING_SECONDS"
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(4), dp(12), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#4CAF50"))
            }
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(WRAP, WRAP).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dp(30)
                rightMargin = dp(20)
            }
        }
        root.addView(timerBadge)

        // ── Top label ──
        val topLabel = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, WRAP).apply {
                gravity = Gravity.TOP
                topMargin = dp(50)
                leftMargin = dp(20)
                rightMargin = dp(20)
            }
        }
        topLabel.addView(TextView(this).apply {
            text = "Read the sentence below"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#4DFFFFFF"))
                setStroke(dp(1), Color.WHITE)
            }
        })
        root.addView(topLabel)

        // ── Countdown overlay (center) ──
        countdownText = TextView(this).apply {
            text = ""
            setTextColor(Color.WHITE)
            textSize = 72f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH).apply {
                gravity = Gravity.CENTER
            }
        }
        root.addView(countdownText)

        // ── Bottom panel ──
        val bottomPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#CC000000"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(12), dp(16), dp(48)) // fallback padding
            layoutParams = FrameLayout.LayoutParams(MATCH, WRAP).apply {
                gravity = Gravity.BOTTOM
            }
        }

        // Adjust bottom padding for navigation bar
        root.setOnApplyWindowInsetsListener { _, insets ->
            @Suppress("DEPRECATION")
            val navHeight = insets.systemWindowInsetBottom
            bottomPanel.setPadding(dp(16), dp(12), dp(16), navHeight + dp(12))
            insets
        }

        bottomPanel.addView(TextView(this).apply {
            text = "Please say"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })

        bottomPanel.addView(TextView(this).apply {
            text = "\"The Quick brown fox\njumps over the lazy dog\""
            setTextColor(Color.parseColor("#B0B0B0"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(16))
        })

        // Record button
        recordBtnBorder = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setStroke(dp(3), Color.WHITE)
            setColor(Color.TRANSPARENT)
        }
        recordBtnOuter = FrameLayout(this).apply {
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = recordBtnBorder
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply {
                gravity = Gravity.CENTER
            }
        }
        recordBtn = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            layoutParams = FrameLayout.LayoutParams(dp(56), dp(56)).apply {
                gravity = Gravity.CENTER
            }
            setOnClickListener { onRecordPressed() }
        }
        recordBtnOuter.addView(recordBtn)
        bottomPanel.addView(recordBtnOuter)

        root.addView(bottomPanel)
        setContentView(root)
    }

    // ── Camera2 ──────────────────────────────────────────────────────────────

    private fun startCameraThread() {
        cameraThread = HandlerThread("KycVideoCamera").also { it.start() }
        cameraHandler = Handler(cameraThread!!.looper)
    }

    private fun stopCameraThread() {
        cameraThread?.quitSafely()
        try { cameraThread?.join() } catch (_: Exception) {}
        cameraThread = null
        cameraHandler = null
    }

    private fun openCamera() {
        try {
            val mgr = getSystemService(Context.CAMERA_SERVICE) as CameraManager

            // Find front camera
            for (id in mgr.cameraIdList) {
                val chars = mgr.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    cameraId = id
                    break
                }
            }

            if (cameraId == null) {
                Log.e(TAG, "No front camera found")
                Toast.makeText(this, "No front camera found", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            mgr.openCamera(cameraId!!, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createPreviewSession()
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close(); cameraDevice = null
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close(); cameraDevice = null
                    Log.e(TAG, "Camera error: $error")
                }
            }, cameraHandler)

        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission not granted")
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera: ${e.message}")
            finish()
        }
    }

    private fun createPreviewSession() {
        try {
            val texture = textureView?.surfaceTexture ?: return
            texture.setDefaultBufferSize(640, 480)
            val surface = Surface(texture)

            val previewRequest = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)?.apply {
                addTarget(surface)
            } ?: return

            cameraDevice?.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try { session.setRepeatingRequest(previewRequest.build(), null, cameraHandler) }
                        catch (e: Exception) { Log.e(TAG, "Preview failed: ${e.message}") }
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Session configure failed")
                    }
                },
                cameraHandler
            )
        } catch (e: Exception) {
            Log.e(TAG, "Create preview failed: ${e.message}")
        }
    }

    private fun closeCamera() {
        try {
            captureSession?.close(); captureSession = null
            cameraDevice?.close(); cameraDevice = null
        } catch (_: Exception) {}
    }

    // ── Recording ────────────────────────────────────────────────────────────

    private fun onRecordPressed() {
        if (isRecording || permissionBlocked) return
        startCountdown()
    }

    private fun startCountdown() {
        recordBtn.isEnabled = false
        countdownText.visibility = View.VISIBLE

        object : CountDownTimer((COUNTDOWN_SECONDS * 1000).toLong(), 1000) {
            override fun onTick(ms: Long) {
                val sec = (ms / 1000) + 1
                countdownText.text = "$sec"
            }
            override fun onFinish() {
                countdownText.visibility = View.GONE
                startRecording()
            }
        }.start()
    }

    private fun startRecording() {
        try {
            // Close preview session first
            captureSession?.close()
            captureSession = null

            // Prepare output file
            val dir = File(cacheDir, "kyc_videos")
            if (!dir.exists()) dir.mkdirs()
            videoFilePath = "${dir.absolutePath}/kyc_video_${System.currentTimeMillis()}.mp4"

            // Setup MediaRecorder
            mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(videoFilePath)
                setVideoEncodingBitRate(2_000_000)
                setVideoFrameRate(30)
                setVideoSize(640, 480)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44100)

                // Orientation for front camera
                setOrientationHint(270)

                prepare()
            }

            // Create recording session
            val texture = textureView?.surfaceTexture ?: return
            texture.setDefaultBufferSize(640, 480)
            val previewSurface = Surface(texture)
            val recorderSurface = mediaRecorder!!.surface

            val captureRequest = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)?.apply {
                addTarget(previewSurface)
                addTarget(recorderSurface)
            } ?: return

            cameraDevice?.createCaptureSession(
                listOf(previewSurface, recorderSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            session.setRepeatingRequest(captureRequest.build(), null, cameraHandler)
                            mediaRecorder?.start()
                            isRecording = true

                            runOnUiThread {
                                // Update UI to red
                                (recordBtn.background as GradientDrawable).setColor(Color.RED)
                                recordBtnBorder.setStroke(dp(3), Color.RED)
                                timerBadge.visibility = View.VISIBLE
                                startRecordingTimer()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Start recording failed: ${e.message}")
                        }
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Recording session configure failed")
                    }
                },
                cameraHandler
            )

        } catch (e: Exception) {
            Log.e(TAG, "Setup recording failed: ${e.message}", e)
            isRecording = false
            recordBtn.isEnabled = true
        }
    }

    private fun startRecordingTimer() {
        var remaining = RECORDING_SECONDS

        object : CountDownTimer((RECORDING_SECONDS * 1000).toLong(), 1000) {
            override fun onTick(ms: Long) {
                remaining = ((ms / 1000) + 1).toInt()
                timerBadge.text = "$remaining"
            }
            override fun onFinish() {
                timerBadge.text = "0"
                stopRecordingAndReview()
            }
        }.start()
    }

    private fun stopRecordingAndReview() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false

            Log.d(TAG, "Video saved: $videoFilePath")

            // Check file size
            val videoFile = File(videoFilePath!!)
            Log.d(TAG, "Video size: ${videoFile.length() / 1024} KB")

            // Navigate to review
            val intent = Intent(this, KycVideoReviewActivity::class.java).apply {
                putExtra(KycVideoReviewActivity.EXTRA_VIDEO_PATH, videoFilePath)
            }
            startActivityForResult(intent, RC_REVIEW)

        } catch (e: Exception) {
            Log.e(TAG, "Stop recording failed: ${e.message}")
            isRecording = false
            recordBtn.isEnabled = true
        }
    }

    private fun stopRecordingSilently() {
        try {
            if (isRecording) {
                mediaRecorder?.stop()
            }
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
        } catch (_: Exception) {}
    }

    // ── Activity Result ──────────────────────────────────────────────────────

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_REVIEW) {
            if (resultCode == RESULT_OK) {
                // Confirmed → pass video path back
                setResult(RESULT_OK, data)
                finish()
            } else {
                // Retake → reset and reopen camera
                isRecording = false
                (recordBtn.background as? GradientDrawable)?.setColor(Color.WHITE)
                recordBtnBorder.setStroke(dp(3), Color.WHITE)
                timerBadge.visibility = View.GONE
                recordBtn.isEnabled = true
                openCamera()
            }
        }
    }

    override fun onBackPressed() { setResult(RESULT_CANCELED); finish() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val MATCH = FrameLayout.LayoutParams.MATCH_PARENT
    private val WRAP = FrameLayout.LayoutParams.WRAP_CONTENT
}