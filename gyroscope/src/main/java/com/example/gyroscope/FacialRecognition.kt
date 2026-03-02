package com.earnscape.gyroscopesdk

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * FaceRecognitionActivity
 *
 * Native Activity opened by SDK — replaces the Flutter FacialRecognition screen.
 * Shows camera preview with face guide overlay, capture button.
 * On capture → sends face image to backend → returns result to Flutter.
 *
 * Usage from Flutter:
 *   await _overlayChannel.invokeMethod('openFaceRecognition', {
 *     'playerId': '14086',
 *     'apiUrl': 'https://your-backend.com/app/face/verify',
 *   });
 */
class FaceRecognitionActivity : Activity() {

    companion object {
        private const val TAG = "FaceRecognition"
        private const val CAMERA_PERMISSION_CODE = 3001

        const val EXTRA_PLAYER_ID = "playerId"
        const val EXTRA_API_URL = "apiUrl"
        const val EXTRA_STREAM_KEY = "streamKey"

        // Result keys
        const val RESULT_SUCCESS = "success"
        const val RESULT_ERROR = "error"
        const val RESULT_MESSAGE = "message"
    }

    // ── Camera ───────────────────────────────────────────────────────────────
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var previewSize = Size(640, 480)

    // ── Views ────────────────────────────────────────────────────────────────
    private lateinit var textureView: TextureView
    private lateinit var overlayView: FaceGuideOverlay
    private lateinit var captureButton: FrameLayout
    private lateinit var statusText: TextView
    private lateinit var instructionText: TextView
    private lateinit var loaderLayout: FrameLayout
    private lateinit var loaderText: TextView

    // ── State ────────────────────────────────────────────────────────────────
    private var playerId: String = ""
    private var apiUrl: String = ""
    private var streamKey: String = ""
    private var isCapturing = false

    private val mainHandler = Handler(Looper.getMainLooper())

    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fullscreen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }

        // Get params from intent
        playerId = intent.getStringExtra(EXTRA_PLAYER_ID) ?: ""
        apiUrl = intent.getStringExtra(EXTRA_API_URL) ?: ""
        streamKey = intent.getStringExtra(EXTRA_STREAM_KEY) ?: ""

        Log.d(TAG, "FaceRecognition opened: playerId=$playerId, apiUrl=$apiUrl")

        buildUI()
        checkCameraPermission()
    }

    // ── Build UI Programmatically ────────────────────────────────────────────

    private fun buildUI() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        // ── 1. Camera preview (TextureView) ──
        textureView = TextureView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(textureView)

        // ── 2. Face guide overlay (dark borders + clear center box + grid) ──
        overlayView = FaceGuideOverlay(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(overlayView)

        // ── 3. Bottom panel (instruction + capture button) ──
        val bottomPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.BLACK)
            setPadding(0, dp(20), 0, dp(40))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }

        // Instruction text
        instructionText = TextView(this).apply {
            text = "Fit your face in the box"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, 0, 0, dp(20))
        }
        bottomPanel.addView(instructionText)

        // Capture button (outer ring + inner white circle)
        captureButton = FrameLayout(this).apply {
            val size = dp(72)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER
            }

            // Outer ring
            val outerRing = View(this@FaceRecognitionActivity).apply {
                layoutParams = FrameLayout.LayoutParams(size, size)
                background = createRingDrawable(size)
            }
            addView(outerRing)

            // Inner white circle
            val innerSize = dp(58)
            val innerCircle = View(this@FaceRecognitionActivity).apply {
                layoutParams = FrameLayout.LayoutParams(innerSize, innerSize, Gravity.CENTER)
                background = createCircleDrawable(Color.WHITE, innerSize)
            }
            addView(innerCircle)

            setOnClickListener {
                if (!isCapturing) {
                    captureImage()
                }
            }
        }
        bottomPanel.addView(captureButton)

        root.addView(bottomPanel)

        // ── 4. Back button (top left) ──
        val backButton = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.WHITE)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = FrameLayout.LayoutParams(dp(56), dp(56)).apply {
                topMargin = dp(40)
                leftMargin = dp(8)
            }
            setOnClickListener {
                setResult(RESULT_CANCELED)
                finish()
            }
        }
        root.addView(backButton)

        // ── 5. Loading overlay (hidden initially) ──
        loaderLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#80000000"))
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            val loaderContainer = LinearLayout(this@FaceRecognitionActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(20), dp(24), dp(20))
                background = createRoundedRect(Color.parseColor("#4E3E474F"), dp(20).toFloat())
                layoutParams = FrameLayout.LayoutParams(dp(280), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            }

            val progressBar = ProgressBar(this@FaceRecognitionActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                    gravity = Gravity.CENTER
                }
            }
            loaderContainer.addView(progressBar)

            loaderText = TextView(this@FaceRecognitionActivity).apply {
                text = "Analyzing your face, this may take a few minutes"
                setTextColor(Color.WHITE)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, dp(16), 0, 0)
            }
            loaderContainer.addView(loaderText)

            addView(loaderContainer)
        }
        root.addView(loaderLayout)

        setContentView(root)
    }

    // ── Camera Permission ────────────────────────────────────────────────────

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        } else {
            startCamera()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Log.e(TAG, "Camera permission denied")
                returnError("Camera permission denied")
            }
        }
    }

    // ── Start Camera ─────────────────────────────────────────────────────────

    private fun startCamera() {
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                openFrontCamera()
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                releaseCamera()
                return true
            }
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }
    }

    private fun openFrontCamera() {
        try {
            val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

            var frontCameraId: String? = null
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                if (chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    frontCameraId = id

                    val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    val sizes = map?.getOutputSizes(SurfaceTexture::class.java)
                    if (sizes != null && sizes.isNotEmpty()) {
                        previewSize = sizes
                            .filter { it.width in 480..1280 }
                            .minByOrNull { Math.abs(it.width * it.height - 640 * 480) }
                            ?: sizes[0]
                    }
                    break
                }
            }

            if (frontCameraId == null) {
                returnError("No front camera found")
                return
            }

            cameraThread = HandlerThread("FaceCameraThread").also { it.start() }
            cameraHandler = Handler(cameraThread!!.looper)

            // ImageReader for capture
            imageReader = ImageReader.newInstance(
                previewSize.width, previewSize.height,
                ImageFormat.JPEG, 2
            )

            Log.d(TAG, "Opening camera: $frontCameraId (${previewSize.width}x${previewSize.height})")

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return
            }

            cameraManager.openCamera(frontCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    Log.d(TAG, "✅ Camera opened")
                    cameraDevice = camera
                    createPreviewSession()
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera error: $error")
                    camera.close()
                    cameraDevice = null
                    mainHandler.post { returnError("Camera error: $error") }
                }
            }, cameraHandler)

        } catch (e: Exception) {
            Log.e(TAG, "Camera open failed: ${e.message}")
            returnError("Camera error: ${e.message}")
        }
    }

    private fun createPreviewSession() {
        val camera = cameraDevice ?: return
        val st = textureView.surfaceTexture ?: return
        val reader = imageReader ?: return

        try {
            st.setDefaultBufferSize(previewSize.width, previewSize.height)
            val previewSurface = Surface(st)

            camera.createCaptureSession(
                listOf(previewSurface, reader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                addTarget(previewSurface)
                                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                            }
                            session.setRepeatingRequest(request.build(), null, cameraHandler)
                            Log.d(TAG, "✅ Preview running")
                        } catch (e: Exception) {
                            Log.e(TAG, "Preview failed: ${e.message}")
                        }
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Session config failed")
                        mainHandler.post { returnError("Camera session failed") }
                    }
                },
                cameraHandler
            )
        } catch (e: Exception) {
            Log.e(TAG, "Create session failed: ${e.message}")
        }
    }

    // ── Capture Image ────────────────────────────────────────────────────────

    private fun captureImage() {
        val camera = cameraDevice ?: return
        val session = captureSession ?: return
        val reader = imageReader ?: return

        isCapturing = true
        showLoader(true)

        try {
            reader.setOnImageAvailableListener({ imgReader ->
                val image = imgReader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)

                    Log.d(TAG, "📸 Image captured (${bytes.size} bytes)")

                    // Send to backend
                    Thread { sendToBackend(bytes) }.start()

                } finally {
                    image.close()
                }
            }, cameraHandler)

            val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(reader.surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                set(CaptureRequest.JPEG_QUALITY, 85.toByte())
            }
            session.capture(captureRequest.build(), null, cameraHandler)

        } catch (e: Exception) {
            Log.e(TAG, "Capture failed: ${e.message}")
            isCapturing = false
            mainHandler.post { showLoader(false) }
        }
    }

    // ── Send to Backend ──────────────────────────────────────────────────────

    private fun sendToBackend(jpegBytes: ByteArray) {
        try {
            // Compress
            val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val compressedBytes = outputStream.toByteArray()
            bitmap.recycle()

            val base64Image = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)

            Log.d(TAG, "Sending face to: $apiUrl (${compressedBytes.size} bytes)")

            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000

            val jsonPayload = """
                {
                    "p_id": $playerId,
                    "streamKey": "$streamKey",
                    "image": "$base64Image",
                    "timestamp": ${System.currentTimeMillis()}
                }
            """.trimIndent()

            connection.outputStream.use { os ->
                os.write(jsonPayload.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = connection.responseCode
            val responseBody = try {
                connection.inputStream.bufferedReader().readText()
            } catch (e: Exception) {
                connection.errorStream?.bufferedReader()?.readText() ?: ""
            }
            connection.disconnect()

            Log.d(TAG, "✅ Backend response: HTTP $responseCode — $responseBody")

            mainHandler.post {
                showLoader(false)
                isCapturing = false

                if (responseCode in 200..299) {
                    returnSuccess(responseBody)
                } else {
                    returnError("Server error: HTTP $responseCode")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Backend error: ${e.message}")
            mainHandler.post {
                showLoader(false)
                isCapturing = false
                returnError("Network error: ${e.message}")
            }
        }
    }

    // ── Return Results to Flutter ────────────────────────────────────────────

    private fun returnSuccess(response: String) {
        val resultIntent = Intent().apply {
            putExtra(RESULT_SUCCESS, true)
            putExtra(RESULT_MESSAGE, response)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun returnError(error: String) {
        val resultIntent = Intent().apply {
            putExtra(RESULT_SUCCESS, false)
            putExtra(RESULT_ERROR, error)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    // ── Loader ───────────────────────────────────────────────────────────────

    private fun showLoader(show: Boolean) {
        loaderLayout.visibility = if (show) View.VISIBLE else View.GONE
        captureButton.isEnabled = !show
    }

    // ── Release Camera ───────────────────────────────────────────────────────

    private fun releaseCamera() {
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
            cameraThread?.quitSafely()
            cameraThread = null
            cameraHandler = null
        } catch (e: Exception) {
            Log.e(TAG, "Release error: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseCamera()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun createRingDrawable(size: Int): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(dp(3), Color.WHITE)
            setSize(size, size)
        }
    }

    private fun createCircleDrawable(color: Int, size: Int): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(color)
            setSize(size, size)
        }
    }

    private fun createRoundedRect(color: Int, radius: Float): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
        }
    }

    // ── Face Guide Overlay (dark borders + clear center + grid) ──────────────

    class FaceGuideOverlay(context: android.content.Context) : View(context) {

        private val shadePaint = Paint().apply {
            color = Color.parseColor("#88000000")
            style = Paint.Style.FILL
        }

        private val borderPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f * resources.displayMetrics.density
            isAntiAlias = true
        }

        private val gridPaint = Paint().apply {
            color = Color.parseColor("#40FFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = 1f * resources.displayMetrics.density
        }

        private val cornerPaint = Paint().apply {
            color = Color.parseColor("#00FF88")  // green corners
            style = Paint.Style.STROKE
            strokeWidth = 4f * resources.displayMetrics.density
            strokeCap = Paint.Cap.ROUND
        }

        private val boxWidthRatio = 0.75f
        private val boxHeightRatio = 0.45f
        private val cornerLength = 30f * resources.displayMetrics.density

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val w = width.toFloat()
            val h = height.toFloat()

            val boxW = w * boxWidthRatio
            val boxH = h * boxHeightRatio
            val boxLeft = (w - boxW) / 2f
            val boxTop = (h - boxH) / 2f - 40 * resources.displayMetrics.density // slightly above center
            val boxRight = boxLeft + boxW
            val boxBottom = boxTop + boxH

            val radius = 12f * resources.displayMetrics.density

            // ── Dark shade around the box ──
            // Top
            canvas.drawRect(0f, 0f, w, boxTop, shadePaint)
            // Bottom
            canvas.drawRect(0f, boxBottom, w, h, shadePaint)
            // Left
            canvas.drawRect(0f, boxTop, boxLeft, boxBottom, shadePaint)
            // Right
            canvas.drawRect(boxRight, boxTop, w, boxBottom, shadePaint)

            // ── Box border ──
            val boxRect = RectF(boxLeft, boxTop, boxRight, boxBottom)
            canvas.drawRoundRect(boxRect, radius, radius, borderPaint)

            // ── Grid lines (rule of thirds) ──
            val thirdW = boxW / 3f
            val thirdH = boxH / 3f
            // Vertical
            canvas.drawLine(boxLeft + thirdW, boxTop, boxLeft + thirdW, boxBottom, gridPaint)
            canvas.drawLine(boxLeft + 2 * thirdW, boxTop, boxLeft + 2 * thirdW, boxBottom, gridPaint)
            // Horizontal
            canvas.drawLine(boxLeft, boxTop + thirdH, boxRight, boxTop + thirdH, gridPaint)
            canvas.drawLine(boxLeft, boxTop + 2 * thirdH, boxRight, boxTop + 2 * thirdH, gridPaint)

            // ── Green corner accents ──
            // Top-left
            canvas.drawLine(boxLeft, boxTop + cornerLength, boxLeft, boxTop, cornerPaint)
            canvas.drawLine(boxLeft, boxTop, boxLeft + cornerLength, boxTop, cornerPaint)
            // Top-right
            canvas.drawLine(boxRight - cornerLength, boxTop, boxRight, boxTop, cornerPaint)
            canvas.drawLine(boxRight, boxTop, boxRight, boxTop + cornerLength, cornerPaint)
            // Bottom-left
            canvas.drawLine(boxLeft, boxBottom - cornerLength, boxLeft, boxBottom, cornerPaint)
            canvas.drawLine(boxLeft, boxBottom, boxLeft + cornerLength, boxBottom, cornerPaint)
            // Bottom-right
            canvas.drawLine(boxRight - cornerLength, boxBottom, boxRight, boxBottom, cornerPaint)
            canvas.drawLine(boxRight, boxBottom - cornerLength, boxRight, boxBottom, cornerPaint)
        }
    }
}