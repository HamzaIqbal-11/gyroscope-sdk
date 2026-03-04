
//
//package com.earnscape.gyroscopesdk
//
//import android.Manifest
//import android.app.Activity
//import android.content.Intent
//import android.content.pm.PackageManager
//import android.graphics.*
//import android.hardware.camera2.*
//import android.media.ImageReader
//import android.os.Build
//import android.os.Bundle
//import android.os.Handler
//import android.os.HandlerThread
//import android.os.Looper
//import android.util.Log
//import android.util.Size
//import android.view.*
//import android.widget.*
//import androidx.core.app.ActivityCompat
//import androidx.core.content.ContextCompat
//import org.json.JSONObject
//import java.io.ByteArrayOutputStream
//import java.net.HttpURLConnection
//import java.net.URL
//
///**
// * FaceRecognitionActivity
// *
// * Fully configurable from Flutter — no hardcoded field names or URLs.
// *
// * Flutter call:
// *   await _overlayChannel.invokeMethod('openFaceRecognition', {
// *     'apiUrl': 'https://facialserver.earnscape.com/facial-recognition',
// *
// *     // Multipart fields to send
// *     'fields': {
// *       'playerId': '14086',
// *       'streamKey': 'ABC-DEF',
// *       // any extra fields backend needs
// *     },
// *
// *     // Field name for image file in multipart
// *     'imageFieldName': 'image',
// *
// *     // Response JSON field names
// *     'responseStatusField': 'status',
// *     'responseMessageField': 'message',
// *     'responseMatchField': 'matchFound',
// *
// *     // Which status codes mean success (exit screen)
// *     'successStatuses': [200, 202],
// *
// *     // Which status+match combo means "face exists" (exit screen)
// *     'existsStatus': 401,
// *     'existsMatchValue': true,
// *
// *     // Which status means "multiple faces" (stay, retry)
// *     'multipleStatus': 402,
// *   });
// */
//class FaceRecognitionActivity : Activity() {
//
//    companion object {
//        private const val TAG = "FaceRecognition"
//        private const val CAMERA_PERMISSION_CODE = 3001
//
//        // Intent extras
//        const val EXTRA_API_URL = "apiUrl"
//        const val EXTRA_FIELDS = "fields"
//        const val EXTRA_IMAGE_FIELD_NAME = "imageFieldName"
//        const val EXTRA_RESPONSE_STATUS_FIELD = "responseStatusField"
//        const val EXTRA_RESPONSE_MESSAGE_FIELD = "responseMessageField"
//        const val EXTRA_RESPONSE_MATCH_FIELD = "responseMatchField"
//        const val EXTRA_SUCCESS_STATUSES = "successStatuses"
//        const val EXTRA_EXISTS_STATUS = "existsStatus"
//        const val EXTRA_EXISTS_MATCH_VALUE = "existsMatchValue"
//        const val EXTRA_MULTIPLE_STATUS = "multipleStatus"
//
//        // Result keys (returned to Flutter)
//        const val RESULT_SUCCESS = "success"
//        const val RESULT_ERROR = "error"
//        const val RESULT_MESSAGE = "message"
//
//        // Timeouts
//        private const val CONNECT_TIMEOUT_MS = 120_000
//        private const val READ_TIMEOUT_MS = 120_000
//
//        // Image
//        private const val MAX_IMAGE_WIDTH = 480
//        private const val JPEG_QUALITY = 40
//    }
//
//    // ── Camera ───────────────────────────────────────────────────────────────
//    private var cameraDevice: CameraDevice? = null
//    private var captureSession: CameraCaptureSession? = null
//    private var imageReader: ImageReader? = null
//    private var cameraThread: HandlerThread? = null
//    private var cameraHandler: Handler? = null
//    private var previewSize = Size(640, 480)
//
//    // ── Views ────────────────────────────────────────────────────────────────
//    private lateinit var textureView: TextureView
//    private lateinit var overlayView: FaceGuideOverlay
//    private lateinit var captureButton: FrameLayout
//    private lateinit var instructionText: TextView
//    private lateinit var loaderLayout: FrameLayout
//    private lateinit var loaderText: TextView
//    private lateinit var errorBanner: LinearLayout
//    private lateinit var errorText: TextView
//    private lateinit var retryButton: TextView
//
//    // ── Config (all from Flutter) ────────────────────────────────────────────
//    private var apiUrl: String = ""
//    private var fields: Map<String, String> = emptyMap()        // multipart text fields
//    private var imageFieldName: String = "image"                 // multipart image field name
//    private var responseStatusField: String = "status"           // JSON response key for status
//    private var responseMessageField: String = "message"         // JSON response key for message
//    private var responseMatchField: String = "matchFound"        // JSON response key for match
//    private var successStatuses: List<Int> = listOf(200, 202)    // status codes = success → exit
//    private var existsStatus: Int = 401                          // status code for "face exists"
//    private var existsMatchValue: Boolean = true                 // match value for "face exists"
//    private var multipleStatus: Int = 402                        // status code for "multiple faces"
//
//    // ── State ────────────────────────────────────────────────────────────────
//    private var isCapturing = false
//    private val mainHandler = Handler(Looper.getMainLooper())
//
//    // ─────────────────────────────────────────────────────────────────────────
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        // ── Read ALL config from intent (set by Flutter) ──
//        apiUrl = intent.getStringExtra(EXTRA_API_URL) ?: ""
//        imageFieldName = intent.getStringExtra(EXTRA_IMAGE_FIELD_NAME) ?: "image"
//        responseStatusField = intent.getStringExtra(EXTRA_RESPONSE_STATUS_FIELD) ?: "status"
//        responseMessageField = intent.getStringExtra(EXTRA_RESPONSE_MESSAGE_FIELD) ?: "message"
//        responseMatchField = intent.getStringExtra(EXTRA_RESPONSE_MATCH_FIELD) ?: "matchFound"
//        existsStatus = intent.getIntExtra(EXTRA_EXISTS_STATUS, 401)
//        existsMatchValue = intent.getBooleanExtra(EXTRA_EXISTS_MATCH_VALUE, true)
//        multipleStatus = intent.getIntExtra(EXTRA_MULTIPLE_STATUS, 402)
//
//        // Read fields map
//        val fieldsBundle = intent.getBundleExtra(EXTRA_FIELDS)
//        if (fieldsBundle != null) {
//            val map = mutableMapOf<String, String>()
//            for (key in fieldsBundle.keySet()) {
//                map[key] = fieldsBundle.getString(key) ?: ""
//            }
//            fields = map
//        }
//
//        // Read success statuses array
//        val statusArray = intent.getIntArrayExtra(EXTRA_SUCCESS_STATUSES)
//        if (statusArray != null && statusArray.isNotEmpty()) {
//            successStatuses = statusArray.toList()
//        }
//
//        Log.d(TAG, "Config: apiUrl=$apiUrl, fields=$fields, imageField=$imageFieldName")
//        Log.d(TAG, "Response: statusField=$responseStatusField, messageField=$responseMessageField, matchField=$responseMatchField")
//        Log.d(TAG, "Success: statuses=$successStatuses, existsStatus=$existsStatus, multipleStatus=$multipleStatus")
//
//        buildUI()
//
//        // Fullscreen AFTER setContentView
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//            window.insetsController?.hide(WindowInsets.Type.statusBars())
//        } else {
//            @Suppress("DEPRECATION")
//            window.setFlags(
//                WindowManager.LayoutParams.FLAG_FULLSCREEN,
//                WindowManager.LayoutParams.FLAG_FULLSCREEN
//            )
//        }
//
//        checkCameraPermission()
//    }
//
//    // ── Build UI ─────────────────────────────────────────────────────────────
//
//    private fun buildUI() {
//        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
//
//        // 1. Camera preview
//        textureView = TextureView(this).apply {
//            layoutParams = FrameLayout.LayoutParams(
//                FrameLayout.LayoutParams.MATCH_PARENT,
//                FrameLayout.LayoutParams.MATCH_PARENT
//            )
//        }
//        root.addView(textureView)
//
//        // 2. Face guide overlay
//        overlayView = FaceGuideOverlay(this).apply {
//            layoutParams = FrameLayout.LayoutParams(
//                FrameLayout.LayoutParams.MATCH_PARENT,
//                FrameLayout.LayoutParams.MATCH_PARENT
//            )
//        }
//        root.addView(overlayView)
//
//        // 3. Error banner
//        errorBanner = LinearLayout(this).apply {
//            orientation = LinearLayout.HORIZONTAL
//            gravity = Gravity.CENTER_VERTICAL
//            setPadding(dp(16), dp(12), dp(16), dp(12))
//            background = makeRoundedBg("#CC331111")
//            visibility = View.GONE
//            layoutParams = FrameLayout.LayoutParams(
//                FrameLayout.LayoutParams.MATCH_PARENT,
//                FrameLayout.LayoutParams.WRAP_CONTENT
//            ).apply {
//                topMargin = dp(100); leftMargin = dp(16); rightMargin = dp(16)
//            }
//        }
//
//        errorBanner.addView(TextView(this).apply {
//            text = "⚠️"; textSize = 20f; setPadding(0, 0, dp(10), 0)
//        })
//
//        errorText = TextView(this).apply {
//            setTextColor(Color.WHITE); textSize = 13f
//            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
//        }
//        errorBanner.addView(errorText)
//
//        retryButton = TextView(this).apply {
//            text = "RETRY"
//            setTextColor(Color.parseColor("#FF6B6B")); textSize = 14f
//            typeface = Typeface.create("sans-serif-bold", Typeface.BOLD)
//            setPadding(dp(12), dp(6), dp(12), dp(6))
//            setOnClickListener { hideError(); captureImage() }
//        }
//        errorBanner.addView(retryButton)
//        root.addView(errorBanner)
//
//        // 4. Bottom panel
//        val bottomPanel = LinearLayout(this).apply {
//            orientation = LinearLayout.VERTICAL
//            gravity = Gravity.CENTER_HORIZONTAL
//            setBackgroundColor(Color.BLACK)
//            setPadding(0, dp(20), 0, dp(40))
//            layoutParams = FrameLayout.LayoutParams(
//                FrameLayout.LayoutParams.MATCH_PARENT,
//                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
//            )
//        }
//
//        instructionText = TextView(this).apply {
//            text = "Fit your face in the box"
//            setTextColor(Color.WHITE); textSize = 18f; gravity = Gravity.CENTER
//            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
//            setPadding(0, 0, 0, dp(20))
//        }
//        bottomPanel.addView(instructionText)
//
//        val btnSize = dp(72)
//        captureButton = FrameLayout(this).apply {
//            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply { gravity = Gravity.CENTER }
//            addView(View(this@FaceRecognitionActivity).apply {
//                layoutParams = FrameLayout.LayoutParams(btnSize, btnSize)
//                background = createRingDrawable(btnSize)
//            })
//            val innerSize = dp(58)
//            addView(View(this@FaceRecognitionActivity).apply {
//                layoutParams = FrameLayout.LayoutParams(innerSize, innerSize, Gravity.CENTER)
//                background = createCircleDrawable(Color.WHITE, innerSize)
//            })
//            setOnClickListener { if (!isCapturing) { hideError(); captureImage() } }
//        }
//        bottomPanel.addView(captureButton)
//        root.addView(bottomPanel)
//
//        // 5. Back button
//        root.addView(ImageView(this).apply {
//            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
//            setColorFilter(Color.WHITE)
//            setPadding(dp(16), dp(16), dp(16), dp(16))
//            layoutParams = FrameLayout.LayoutParams(dp(56), dp(56)).apply {
//                topMargin = dp(40); leftMargin = dp(8)
//            }
//            setOnClickListener { setResult(RESULT_CANCELED); finish() }
//        })
//
//        // 6. Loader
//        loaderLayout = FrameLayout(this).apply {
//            setBackgroundColor(Color.parseColor("#80000000"))
//            visibility = View.GONE
//            layoutParams = FrameLayout.LayoutParams(
//                FrameLayout.LayoutParams.MATCH_PARENT,
//                FrameLayout.LayoutParams.MATCH_PARENT
//            )
//            val container = LinearLayout(this@FaceRecognitionActivity).apply {
//                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
//                setPadding(dp(24), dp(20), dp(24), dp(20))
//                background = makeRoundedBg("#4E3E474F")
//                layoutParams = FrameLayout.LayoutParams(dp(280), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
//            }
//            container.addView(ProgressBar(this@FaceRecognitionActivity).apply {
//                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { gravity = Gravity.CENTER }
//            })
//            loaderText = TextView(this@FaceRecognitionActivity).apply {
//                text = "Analyzing your face, this may take a few minutes..."
//                setTextColor(Color.WHITE); textSize = 14f; gravity = Gravity.CENTER
//                setPadding(0, dp(16), 0, 0)
//            }
//            container.addView(loaderText)
//            addView(container)
//        }
//        root.addView(loaderLayout)
//
//        setContentView(root)
//    }
//
//    // ── Camera ───────────────────────────────────────────────────────────────
//
//    private fun checkCameraPermission() {
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
//            != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
//        } else {
//            startCamera()
//        }
//    }
//
//    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        if (requestCode == CAMERA_PERMISSION_CODE) {
//            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startCamera()
//            else showError("Camera permission required. Please grant it in Settings.")
//        }
//    }
//
//    private fun startCamera() {
//        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
//            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) { openFrontCamera() }
//            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
//            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean { releaseCamera(); return true }
//            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
//        }
//    }
//
//    private fun openFrontCamera() {
//        try {
//            val cm = getSystemService(CAMERA_SERVICE) as CameraManager
//            var frontId: String? = null
//            for (id in cm.cameraIdList) {
//                val chars = cm.getCameraCharacteristics(id)
//                if (chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
//                    frontId = id
//                    val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
//                    val sizes = map?.getOutputSizes(SurfaceTexture::class.java)
//                    if (sizes != null && sizes.isNotEmpty()) {
//                        previewSize = sizes.filter { it.width in 480..1280 }
//                            .minByOrNull { Math.abs(it.width * it.height - 640 * 480) } ?: sizes[0]
//                    }
//                    break
//                }
//            }
//            if (frontId == null) { showError("No front camera found."); return }
//
//            cameraThread = HandlerThread("FaceCamThread").also { it.start() }
//            cameraHandler = Handler(cameraThread!!.looper)
//            imageReader = ImageReader.newInstance(previewSize.width, previewSize.height, ImageFormat.JPEG, 2)
//
//            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
//
//            cm.openCamera(frontId, object : CameraDevice.StateCallback() {
//                override fun onOpened(camera: CameraDevice) { cameraDevice = camera; createPreviewSession() }
//                override fun onDisconnected(camera: CameraDevice) { camera.close(); cameraDevice = null }
//                override fun onError(camera: CameraDevice, error: Int) {
//                    camera.close(); cameraDevice = null
//                    mainHandler.post { showError("Something went wrong. Please try again.") }
//                }
//            }, cameraHandler)
//        } catch (e: Exception) {
//            showError("Something went wrong. Please try again.")
//        }
//    }
//
//    private fun createPreviewSession() {
//        val camera = cameraDevice ?: return
//        val st = textureView.surfaceTexture ?: return
//        val reader = imageReader ?: return
//        try {
//            st.setDefaultBufferSize(previewSize.width, previewSize.height)
//            val surface = Surface(st)
//            camera.createCaptureSession(listOf(surface, reader.surface), object : CameraCaptureSession.StateCallback() {
//                override fun onConfigured(session: CameraCaptureSession) {
//                    captureSession = session
//                    try {
//                        val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
//                            addTarget(surface)
//                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
//                        }
//                        session.setRepeatingRequest(req.build(), null, cameraHandler)
//                    } catch (_: Exception) {}
//                }
//                override fun onConfigureFailed(session: CameraCaptureSession) {
//                    mainHandler.post { showError("Something went wrong. Please try again.") }
//                }
//            }, cameraHandler)
//        } catch (_: Exception) {}
//    }
//
//    // ── Capture ──────────────────────────────────────────────────────────────
//
//    private fun captureImage() {
//        val camera = cameraDevice ?: return
//        val session = captureSession ?: return
//        val reader = imageReader ?: return
//
//        isCapturing = true
//        showLoader(true, "Analyzing your face, this may take a few minutes...")
//
//        try {
//            reader.setOnImageAvailableListener({ imgReader ->
//                val image = imgReader.acquireLatestImage() ?: return@setOnImageAvailableListener
//                try {
//                    val buffer = image.planes[0].buffer
//                    val bytes = ByteArray(buffer.remaining())
//                    buffer.get(bytes)
//                    Log.d(TAG, "📸 Captured (${bytes.size} bytes)")
//                    Thread { sendToBackend(bytes) }.start()
//                } finally { image.close() }
//            }, cameraHandler)
//
//            val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
//                addTarget(reader.surface)
//                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
//                set(CaptureRequest.JPEG_QUALITY, 85.toByte())
//            }
//            session.capture(req.build(), null, cameraHandler)
//        } catch (e: Exception) {
//            isCapturing = false
//            mainHandler.post { showLoader(false); showError("Something went wrong. Please try again.") }
//        }
//    }
//
//    // ── Send to Backend (Multipart — all fields from Flutter config) ─────────
//
//    private fun sendToBackend(jpegBytes: ByteArray) {
//        try {
//            // Resize + compress
//            val original = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
//            val scale = if (original.width > MAX_IMAGE_WIDTH) MAX_IMAGE_WIDTH.toFloat() / original.width else 1f
//            val resized = Bitmap.createScaledBitmap(
//                original, (original.width * scale).toInt(), (original.height * scale).toInt(), true
//            )
//            original.recycle()
//            val bos = ByteArrayOutputStream()
//            resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, bos)
//            val compressed = bos.toByteArray()
//            resized.recycle()
//
//            Log.d(TAG, "Sending to: $apiUrl (${compressed.size} bytes, fields=$fields)")
//
//            // Multipart
//            val boundary = "----FaceBoundary${System.currentTimeMillis()}"
//            val crlf = "\r\n"
//            val dd = "--"
//
//            val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
//                requestMethod = "POST"
//                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
//                doOutput = true
//                connectTimeout = CONNECT_TIMEOUT_MS
//                readTimeout = READ_TIMEOUT_MS
//            }
//
//            conn.outputStream.use { os ->
//                // ── All text fields from Flutter config ──
//                for ((key, value) in fields) {
//                    os.write("$dd$boundary$crlf".toByteArray())
//                    os.write("Content-Disposition: form-data; name=\"$key\"$crlf".toByteArray())
//                    os.write(crlf.toByteArray())
//                    os.write("$value$crlf".toByteArray())
//                }
//
//                // ── Image file (field name from Flutter config) ──
//                os.write("$dd$boundary$crlf".toByteArray())
//                os.write("Content-Disposition: form-data; name=\"$imageFieldName\"; filename=\"face.jpg\"$crlf".toByteArray())
//                os.write("Content-Type: image/jpeg$crlf".toByteArray())
//                os.write(crlf.toByteArray())
//                os.write(compressed)
//                os.write(crlf.toByteArray())
//
//                // End
//                os.write("$dd$boundary$dd$crlf".toByteArray())
//                os.flush()
//            }
//
//            val responseCode = conn.responseCode
//            val body = try { conn.inputStream.bufferedReader().readText() }
//            catch (_: Exception) { conn.errorStream?.bufferedReader()?.readText() ?: "" }
//            conn.disconnect()
//
//            Log.d(TAG, "Response: HTTP $responseCode — $body")
//
//            mainHandler.post {
//                showLoader(false); isCapturing = false
//                handleResponse(body)
//            }
//
//        } catch (e: java.net.SocketTimeoutException) {
//            mainHandler.post { showLoader(false); isCapturing = false; showError("Server is taking too long. Please try again.") }
//        } catch (e: java.net.UnknownHostException) {
//            mainHandler.post { showLoader(false); isCapturing = false; showError("No internet connection. Please check and try again.") }
//        } catch (e: Exception) {
//            Log.e(TAG, "Error: ${e.message}")
//            mainHandler.post { showLoader(false); isCapturing = false; showError("Something went wrong. Please try again.") }
//        }
//    }
//
//    // ── Handle Response (all field names from Flutter config) ─────────────────
//
//    private fun handleResponse(body: String) {
//        try {
//            val json = JSONObject(body)
//            val status = json.optInt(responseStatusField, -1)
//            val message = json.optString(responseMessageField, "")
//            val matchFound = json.optBoolean(responseMatchField, false)
//
//            Log.d(TAG, "Parsed: $responseStatusField=$status, $responseMessageField=$message, $responseMatchField=$matchFound")
//
//            when {
//                // SUCCESS → exit
//                status in successStatuses -> {
//                    showSuccessAndExit(message.ifEmpty { "Face registered successfully!" }, body)
//                }
//
//                // FACE EXISTS → exit
//                status == existsStatus && matchFound == existsMatchValue -> {
//                    showSuccessAndExit(message.ifEmpty { "Face already registered!" }, body)
//                }
//
//                // NO FACE / MISMATCH → stay, retry
//                status == existsStatus && matchFound != existsMatchValue -> {
//                    showError(message.ifEmpty { "No face detected. Please try again." })
//                }
//
//                // MULTIPLE FACES → stay, retry
//                status == multipleStatus -> {
//                    showError(message.ifEmpty { "Multiple faces detected. Only one face allowed." })
//                }
//
//                // ANYTHING ELSE → stay, retry
//                else -> {
//                    showError(message.ifEmpty { "Something went wrong. Please try again." })
//                }
//            }
//        } catch (e: Exception) {
//            showError("Something went wrong. Please try again.")
//        }
//    }
//
//    // ── Success → exit ───────────────────────────────────────────────────────
//
//    private fun showSuccessAndExit(msg: String, raw: String) {
//        errorBanner.visibility = View.VISIBLE
//        errorBanner.background = makeRoundedBg("#CC114411")
//        errorText.text = "✅  $msg"
//        retryButton.visibility = View.GONE
//
//        mainHandler.postDelayed({
//            setResult(RESULT_OK, Intent().apply {
//                putExtra(RESULT_SUCCESS, true)
//                putExtra(RESULT_MESSAGE, raw)
//            })
//            finish()
//        }, 1500)
//    }
//
//    // ── Error → stay ─────────────────────────────────────────────────────────
//
//    private fun showError(msg: String) {
//        errorBanner.visibility = View.VISIBLE
//        errorBanner.background = makeRoundedBg("#CC331111")
//        errorText.text = msg
//        retryButton.visibility = View.VISIBLE
//        instructionText.text = "Try again"
//        mainHandler.postDelayed({ hideError() }, 8000)
//    }
//
//    private fun hideError() {
//        errorBanner.visibility = View.GONE
//        instructionText.text = "Fit your face in the box"
//    }
//
//    // ── Loader ───────────────────────────────────────────────────────────────
//
//    private fun showLoader(show: Boolean, msg: String = "") {
//        loaderLayout.visibility = if (show) View.VISIBLE else View.GONE
//        captureButton.isEnabled = !show
//        captureButton.alpha = if (show) 0.4f else 1.0f
//        if (msg.isNotEmpty()) loaderText.text = msg
//    }
//
//    // ── Camera release ───────────────────────────────────────────────────────
//
//    private fun releaseCamera() {
//        try {
//            captureSession?.close(); captureSession = null
//            cameraDevice?.close(); cameraDevice = null
//            imageReader?.close(); imageReader = null
//            cameraThread?.quitSafely(); cameraThread = null; cameraHandler = null
//        } catch (_: Exception) {}
//    }
//
//    override fun onBackPressed() { setResult(RESULT_CANCELED); finish() }
//    override fun onDestroy() { super.onDestroy(); releaseCamera() }
//
//    // ── Helpers ──────────────────────────────────────────────────────────────
//
//    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
//
//    private fun makeRoundedBg(colorHex: String) = android.graphics.drawable.GradientDrawable().apply {
//        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
//        cornerRadius = dp(12).toFloat()
//        setColor(Color.parseColor(colorHex))
//    }
//
//    private fun createRingDrawable(size: Int) = android.graphics.drawable.GradientDrawable().apply {
//        shape = android.graphics.drawable.GradientDrawable.OVAL
//        setColor(Color.TRANSPARENT); setStroke(dp(3), Color.WHITE); setSize(size, size)
//    }
//
//    private fun createCircleDrawable(color: Int, size: Int) = android.graphics.drawable.GradientDrawable().apply {
//        shape = android.graphics.drawable.GradientDrawable.OVAL
//        setColor(color); setSize(size, size)
//    }
//
//    // ── Face Guide Overlay ───────────────────────────────────────────────────
//
//    class FaceGuideOverlay(context: android.content.Context) : View(context) {
//        private val shadePaint = Paint().apply { color = Color.parseColor("#88000000"); style = Paint.Style.FILL }
//        private val borderPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3f * resources.displayMetrics.density; isAntiAlias = true }
//        private val gridPaint = Paint().apply { color = Color.parseColor("#40FFFFFF"); style = Paint.Style.STROKE; strokeWidth = 1f * resources.displayMetrics.density }
//        private val cornerPaint = Paint().apply { color = Color.parseColor("#00FF88"); style = Paint.Style.STROKE; strokeWidth = 4f * resources.displayMetrics.density; strokeCap = Paint.Cap.ROUND }
//        private val cornerLen = 30f * resources.displayMetrics.density
//
//        override fun onDraw(canvas: Canvas) {
//            super.onDraw(canvas)
//            val w = width.toFloat(); val h = height.toFloat()
//            val bw = w * 0.75f; val bh = h * 0.45f
//            val bl = (w - bw) / 2f; val bt = (h - bh) / 2f - 40 * resources.displayMetrics.density
//            val br = bl + bw; val bb = bt + bh; val r = 12f * resources.displayMetrics.density
//
//            // Shade
//            canvas.drawRect(0f, 0f, w, bt, shadePaint)
//            canvas.drawRect(0f, bb, w, h, shadePaint)
//            canvas.drawRect(0f, bt, bl, bb, shadePaint)
//            canvas.drawRect(br, bt, w, bb, shadePaint)
//
//            // Border
//            canvas.drawRoundRect(RectF(bl, bt, br, bb), r, r, borderPaint)
//
//            // Grid
//            val tw = bw / 3f; val th = bh / 3f
//            canvas.drawLine(bl + tw, bt, bl + tw, bb, gridPaint)
//            canvas.drawLine(bl + 2 * tw, bt, bl + 2 * tw, bb, gridPaint)
//            canvas.drawLine(bl, bt + th, br, bt + th, gridPaint)
//            canvas.drawLine(bl, bt + 2 * th, br, bt + 2 * th, gridPaint)
//
//            // Green corners
//            canvas.drawLine(bl, bt + cornerLen, bl, bt, cornerPaint); canvas.drawLine(bl, bt, bl + cornerLen, bt, cornerPaint)
//            canvas.drawLine(br - cornerLen, bt, br, bt, cornerPaint); canvas.drawLine(br, bt, br, bt + cornerLen, cornerPaint)
//            canvas.drawLine(bl, bb - cornerLen, bl, bb, cornerPaint); canvas.drawLine(bl, bb, bl + cornerLen, bb, cornerPaint)
//            canvas.drawLine(br - cornerLen, bb, br, bb, cornerPaint); canvas.drawLine(br, bb - cornerLen, br, bb, cornerPaint)
//        }
//    }
//}

package com.example.gyroscope.kyc

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
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

/**
 * FaceRecognitionActivity — SDK Reusable
 *
 * Only captures face image, saves to file, returns path to Flutter.
 * No API calls. Host app handles backend.
 *
 * Returns:
 *   success: true, imagePath: "/path/to/face.jpg"
 */
class FaceRecognitionActivity : Activity() {

    companion object {
        private const val TAG = "FaceRecognition"
        private const val CAMERA_PERMISSION_CODE = 3001
        private const val MAX_IMAGE_WIDTH = 480
        private const val JPEG_QUALITY = 40
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
    private lateinit var instructionText: TextView

    // ── State ────────────────────────────────────────────────────────────────
    private var isCapturing = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUI()

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

        checkCameraPermission()
    }

    // ── Build UI ─────────────────────────────────────────────────────────────

    private fun buildUI() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        // 1. Camera preview
        textureView = TextureView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(textureView)

        // 2. Face guide overlay
        overlayView = FaceGuideOverlay(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(overlayView)

        // 3. Bottom panel
        val bottomPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#CC000000"))
            setPadding(0, dp(20), 0, dp(20))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
            )
        }

        // Adjust for navigation bar
        root.setOnApplyWindowInsetsListener { _, insets ->
            @Suppress("DEPRECATION")
            val navHeight = insets.systemWindowInsetBottom
            bottomPanel.setPadding(0, dp(20), 0, navHeight + dp(12))
            insets
        }

        instructionText = TextView(this).apply {
            text = "Fit your face in the box"
            setTextColor(Color.WHITE); textSize = 18f; gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, 0, 0, dp(20))
        }
        bottomPanel.addView(instructionText)

        val btnSize = dp(72)
        captureButton = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply { gravity = Gravity.CENTER }
            addView(View(this@FaceRecognitionActivity).apply {
                layoutParams = FrameLayout.LayoutParams(btnSize, btnSize)
                background = createRingDrawable(btnSize)
            })
            val innerSize = dp(58)
            addView(View(this@FaceRecognitionActivity).apply {
                layoutParams = FrameLayout.LayoutParams(innerSize, innerSize, Gravity.CENTER)
                background = createCircleDrawable(Color.WHITE, innerSize)
            })
            setOnClickListener { if (!isCapturing) captureImage() }
        }
        bottomPanel.addView(captureButton)
        root.addView(bottomPanel)

        // 4. Back button
        root.addView(ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.WHITE)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = FrameLayout.LayoutParams(dp(56), dp(56)).apply {
                topMargin = dp(40); leftMargin = dp(8)
            }
            setOnClickListener { setResult(RESULT_CANCELED); finish() }
        })

        setContentView(root)
    }

    // ── Camera ───────────────────────────────────────────────────────────────

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
        } else {
            startCamera()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startCamera()
            else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) { openFrontCamera() }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean { releaseCamera(); return true }
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }
    }

    private fun openFrontCamera() {
        try {
            val cm = getSystemService(CAMERA_SERVICE) as CameraManager
            var frontId: String? = null
            for (id in cm.cameraIdList) {
                val chars = cm.getCameraCharacteristics(id)
                if (chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    frontId = id
                    val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    val sizes = map?.getOutputSizes(SurfaceTexture::class.java)
                    if (sizes != null && sizes.isNotEmpty()) {
                        previewSize = sizes.filter { it.width in 480..1280 }
                            .minByOrNull { Math.abs(it.width * it.height - 640 * 480) } ?: sizes[0]
                    }
                    break
                }
            }
            if (frontId == null) {
                Toast.makeText(this, "No front camera found", Toast.LENGTH_SHORT).show()
                finish(); return
            }

            cameraThread = HandlerThread("FaceCamThread").also { it.start() }
            cameraHandler = Handler(cameraThread!!.looper)
            imageReader = ImageReader.newInstance(previewSize.width, previewSize.height, ImageFormat.JPEG, 2)

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return

            cm.openCamera(frontId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) { cameraDevice = camera; createPreviewSession() }
                override fun onDisconnected(camera: CameraDevice) { camera.close(); cameraDevice = null }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close(); cameraDevice = null
                    mainHandler.post {
                        Toast.makeText(this@FaceRecognitionActivity, "Camera error", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }, cameraHandler)
        } catch (e: Exception) {
            Log.e(TAG, "openCamera error: ${e.message}")
            finish()
        }
    }

    private fun createPreviewSession() {
        val camera = cameraDevice ?: return
        val st = textureView.surfaceTexture ?: return
        val reader = imageReader ?: return
        try {
            st.setDefaultBufferSize(previewSize.width, previewSize.height)
            val surface = Surface(st)
            camera.createCaptureSession(listOf(surface, reader.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    try {
                        val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(surface)
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                        }
                        session.setRepeatingRequest(req.build(), null, cameraHandler)
                    } catch (_: Exception) {}
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    mainHandler.post { finish() }
                }
            }, cameraHandler)
        } catch (_: Exception) {}
    }

    // ── Capture → Save to File → Return Path ─────────────────────────────────

    private fun captureImage() {
        val camera = cameraDevice ?: return
        val session = captureSession ?: return
        val reader = imageReader ?: return

        isCapturing = true
        captureButton.isEnabled = false
        captureButton.alpha = 0.4f
        instructionText.text = "Capturing..."

        try {
            reader.setOnImageAvailableListener({ imgReader ->
                val image = imgReader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    Log.d(TAG, "Captured (${bytes.size} bytes)")
                    Thread { saveAndReturn(bytes) }.start()
                } finally { image.close() }
            }, cameraHandler)

            val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(reader.surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                set(CaptureRequest.JPEG_QUALITY, 85.toByte())
            }
            session.capture(req.build(), null, cameraHandler)
        } catch (e: Exception) {
            isCapturing = false
            mainHandler.post {
                captureButton.isEnabled = true
                captureButton.alpha = 1.0f
                instructionText.text = "Fit your face in the box"
                Toast.makeText(this, "Capture failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveAndReturn(jpegBytes: ByteArray) {
        try {
            val original = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            val scale = if (original.width > MAX_IMAGE_WIDTH) MAX_IMAGE_WIDTH.toFloat() / original.width else 1f
            val resized = Bitmap.createScaledBitmap(
                original, (original.width * scale).toInt(), (original.height * scale).toInt(), true
            )
            original.recycle()

            val dir = File(cacheDir, "kyc_faces")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "face_${System.currentTimeMillis()}.jpg")

            FileOutputStream(file).use { fos ->
                resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos)
            }
            resized.recycle()

            Log.d(TAG, "Face saved: ${file.absolutePath} (${file.length()} bytes)")

            mainHandler.post {
                setResult(RESULT_OK, Intent().apply {
                    putExtra("imagePath", file.absolutePath)
                })
                finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Save error: ${e.message}")
            mainHandler.post {
                isCapturing = false
                captureButton.isEnabled = true
                captureButton.alpha = 1.0f
                instructionText.text = "Fit your face in the box"
                Toast.makeText(this, "Save failed, try again", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Camera release ───────────────────────────────────────────────────────

    private fun releaseCamera() {
        try {
            captureSession?.close(); captureSession = null
            cameraDevice?.close(); cameraDevice = null
            imageReader?.close(); imageReader = null
            cameraThread?.quitSafely(); cameraThread = null; cameraHandler = null
        } catch (_: Exception) {}
    }

    override fun onBackPressed() { setResult(RESULT_CANCELED); finish() }
    override fun onDestroy() { super.onDestroy(); releaseCamera() }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun createRingDrawable(size: Int) = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.OVAL
        setColor(Color.TRANSPARENT); setStroke(dp(3), Color.WHITE); setSize(size, size)
    }

    private fun createCircleDrawable(color: Int, size: Int) = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.OVAL
        setColor(color); setSize(size, size)
    }

    // ── Face Guide Overlay ───────────────────────────────────────────────────

    class FaceGuideOverlay(context: android.content.Context) : View(context) {
        private val shadePaint = Paint().apply { color = Color.parseColor("#88000000"); style = Paint.Style.FILL }
        private val borderPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3f * resources.displayMetrics.density; isAntiAlias = true }
        private val gridPaint = Paint().apply { color = Color.parseColor("#40FFFFFF"); style = Paint.Style.STROKE; strokeWidth = 1f * resources.displayMetrics.density }
        private val cornerPaint = Paint().apply { color = Color.parseColor("#00FF88"); style = Paint.Style.STROKE; strokeWidth = 4f * resources.displayMetrics.density; strokeCap = Paint.Cap.ROUND }
        private val cornerLen = 30f * resources.displayMetrics.density

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            val bw = w * 0.75f; val bh = h * 0.45f
            val bl = (w - bw) / 2f; val bt = (h - bh) / 2f - 40 * resources.displayMetrics.density
            val br = bl + bw; val bb = bt + bh; val r = 12f * resources.displayMetrics.density

            canvas.drawRect(0f, 0f, w, bt, shadePaint)
            canvas.drawRect(0f, bb, w, h, shadePaint)
            canvas.drawRect(0f, bt, bl, bb, shadePaint)
            canvas.drawRect(br, bt, w, bb, shadePaint)

            canvas.drawRoundRect(RectF(bl, bt, br, bb), r, r, borderPaint)

            val tw = bw / 3f; val th = bh / 3f
            canvas.drawLine(bl + tw, bt, bl + tw, bb, gridPaint)
            canvas.drawLine(bl + 2 * tw, bt, bl + 2 * tw, bb, gridPaint)
            canvas.drawLine(bl, bt + th, br, bt + th, gridPaint)
            canvas.drawLine(bl, bt + 2 * th, br, bt + 2 * th, gridPaint)

            canvas.drawLine(bl, bt + cornerLen, bl, bt, cornerPaint); canvas.drawLine(bl, bt, bl + cornerLen, bt, cornerPaint)
            canvas.drawLine(br - cornerLen, bt, br, bt, cornerPaint); canvas.drawLine(br, bt, br, bt + cornerLen, cornerPaint)
            canvas.drawLine(bl, bb - cornerLen, bl, bb, cornerPaint); canvas.drawLine(bl, bb, bl + cornerLen, bb, cornerPaint)
            canvas.drawLine(br - cornerLen, bb, br, bb, cornerPaint); canvas.drawLine(br, bb - cornerLen, br, bb, cornerPaint)
        }
    }
}