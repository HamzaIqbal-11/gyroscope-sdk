package com.example.gyroscope.kyc



import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.*
import android.widget.*
import java.io.File
import java.io.FileOutputStream

/**
 * KycDocCaptureActivity — Camera for document photos
 *
 * - ID Card / driving license → capture front + back
 * - Passport → capture single photo
 * - Back camera with flash toggle
 * - Dotted frame overlay for ID/License
 */
class KycDocCaptureActivity : Activity() {

    companion object {
        private const val TAG = "KycDocCapture"

        const val EXTRA_API_URL = "kycApiUrl"
        const val EXTRA_PLAYER_ID = "kycPlayerId"
        const val EXTRA_HEADERS = "kycHeaders"
        const val EXTRA_DOC_TYPE = "kycDocType"

        private const val RC_REVIEW = 5020
    }

    // Config
    private var apiUrl = ""
    private var playerId = ""
    private var headersBundle: Bundle? = null
    private var docType = ""

    // Camera2
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var textureView: TextureView? = null
    private var cameraId: String? = null

    // State
    private var isFrontSide = true
    private var isFlashOn = false
    private var isCapturing = false
    private var frontImagePath: String? = null
    private var backImagePath: String? = null

    // Views
    private lateinit var labelText: TextView
    private lateinit var instructionText: TextView
    private lateinit var flashBtn: TextView

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        apiUrl = intent.getStringExtra(EXTRA_API_URL) ?: ""
        playerId = intent.getStringExtra(EXTRA_PLAYER_ID) ?: ""
        headersBundle = intent.getBundleExtra(EXTRA_HEADERS)
        docType = intent.getStringExtra(EXTRA_DOC_TYPE) ?: ""

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }

        buildUI()
        startCameraThread()
    }

    override fun onResume() {
        super.onResume()
        if (textureView?.isAvailable == true) {
            openCamera()
        }
    }

    override fun onPause() {
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

        // Camera preview
        textureView = TextureView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) { openCamera() }
                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
            }
        }
        root.addView(textureView)

        // ── Top label ──
        val topLabel = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, WRAP).apply {
                gravity = Gravity.TOP
                topMargin = dp(80)
                leftMargin = dp(20)
                rightMargin = dp(20)
            }
        }
        labelText = TextView(this).apply {
            text = getLabelText()
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#4DFFFFFF"))
                setStroke(dp(1), Color.WHITE)
            }
        }
        topLabel.addView(labelText)
        root.addView(topLabel)

        // ── Dotted frame (for ID/License) ──
        if (needsFrontBack()) {
            root.addView(DottedFrameView(this).apply {
                layoutParams = FrameLayout.LayoutParams(MATCH, dp(250)).apply {
                    gravity = Gravity.CENTER
                    leftMargin = dp(20)
                    rightMargin = dp(20)
                }
            })
        }

        // ── Bottom controls ──
        val bottomPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(16), dp(16), dp(30))
            layoutParams = FrameLayout.LayoutParams(MATCH, WRAP).apply {
                gravity = Gravity.BOTTOM
            }
        }

        bottomPanel.addView(TextView(this).apply {
            text = "Center Properly"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })

        instructionText = TextView(this).apply {
            text = if (needsFrontBack())
                "Place ID on a Plain Surface\nCenter ID within the Frame."
            else
                "Place Document on a Plain Surface"
            setTextColor(Color.parseColor("#B0B0B0"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(16))
        }
        bottomPanel.addView(instructionText)

        // Capture + Flash row
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        btnRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(50), dp(1)) })

        // Capture button
        val captureOuter = FrameLayout(this).apply {
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(3), Color.WHITE)
                setColor(Color.TRANSPARENT)
            }
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply {
                gravity = Gravity.CENTER
            }
        }
        val captureInner = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            layoutParams = FrameLayout.LayoutParams(dp(56), dp(56)).apply {
                gravity = Gravity.CENTER
            }
            setOnClickListener { captureImage() }
        }
        captureOuter.addView(captureInner)
        btnRow.addView(captureOuter)

        // Flash button
        flashBtn = TextView(this).apply {
            text = "⚡"
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(8), dp(8))
            setOnClickListener { toggleFlash() }
            alpha = 0.7f
        }
        btnRow.addView(flashBtn)

        bottomPanel.addView(btnRow)
        root.addView(bottomPanel)

        setContentView(root)
    }

    // ── Camera2 ──────────────────────────────────────────────────────────────

    private fun startCameraThread() {
        cameraThread = HandlerThread("KycDocCamera").also { it.start() }
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

            // Find back camera
            for (id in mgr.cameraIdList) {
                val chars = mgr.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id
                    break
                }
            }

            if (cameraId == null) {
                Log.e(TAG, "No back camera found")
                Toast.makeText(this, "No camera found", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 2)
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                image.close()

                saveImage(bytes)
            }, cameraHandler)

            mgr.openCamera(cameraId!!, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createPreviewSession()
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    Log.e(TAG, "Camera error: $error")
                }
            }, cameraHandler)

        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission not granted: ${e.message}")
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
            texture.setDefaultBufferSize(1920, 1080)
            val surface = Surface(texture)

            val previewRequest = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)?.apply {
                addTarget(surface)
            } ?: return

            cameraDevice?.createCaptureSession(
                listOf(surface, imageReader!!.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            session.setRepeatingRequest(previewRequest.build(), null, cameraHandler)
                        } catch (e: Exception) {
                            Log.e(TAG, "Preview failed: ${e.message}")
                        }
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
            imageReader?.close(); imageReader = null
        } catch (_: Exception) {}
    }

    // ── Capture ──────────────────────────────────────────────────────────────

    private fun captureImage() {
        if (isCapturing || cameraDevice == null || captureSession == null) return
        isCapturing = true

        try {
            val captureBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(imageReader!!.surface)
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                set(CaptureRequest.JPEG_ORIENTATION, getJpegOrientation())
                if (isFlashOn) {
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE)
                }
            }

            captureSession?.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    Log.d(TAG, "Capture completed")
                }
                override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                    Log.e(TAG, "Capture failed")
                    isCapturing = false
                }
            }, cameraHandler)

        } catch (e: Exception) {
            Log.e(TAG, "Capture error: ${e.message}")
            isCapturing = false
        }
    }

    private fun saveImage(bytes: ByteArray) {
        try {
            val dir = File(cacheDir, "kyc_docs")
            if (!dir.exists()) dir.mkdirs()

            val fileName = "kyc_doc_${System.currentTimeMillis()}.jpg"
            val file = File(dir, fileName)
            FileOutputStream(file).use { it.write(bytes) }

            Log.d(TAG, "Image saved: ${file.absolutePath}")

            if (needsFrontBack()) {
                if (isFrontSide) {
                    frontImagePath = file.absolutePath
                    isFrontSide = false
                    isCapturing = false
                    runOnUiThread { labelText.text = "Back Side" }
                } else {
                    backImagePath = file.absolutePath
                    runOnUiThread { navigateToReview() }
                }
            } else {
                frontImagePath = file.absolutePath
                runOnUiThread { navigateToReview() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Save image failed: ${e.message}")
            isCapturing = false
        }
    }

    private fun navigateToReview() {
        val intent = Intent(this, KycDocReviewActivity::class.java).apply {
            putExtra(KycDocReviewActivity.EXTRA_API_URL, apiUrl)
            putExtra(KycDocReviewActivity.EXTRA_PLAYER_ID, playerId)
            putExtra(KycDocReviewActivity.EXTRA_DOC_TYPE, docType)
            putExtra(KycDocReviewActivity.EXTRA_HEADERS, headersBundle)
            putExtra(KycDocReviewActivity.EXTRA_FRONT_IMAGE, frontImagePath)
            if (backImagePath != null) {
                putExtra(KycDocReviewActivity.EXTRA_BACK_IMAGE, backImagePath)
            }
        }
        startActivityForResult(intent, RC_REVIEW)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_REVIEW) {
            if (resultCode == RESULT_OK) {
                // Upload success → pass back
                setResult(RESULT_OK, data)
                finish()
            } else {
                // Retake → reset
                frontImagePath = null
                backImagePath = null
                isFrontSide = true
                isCapturing = false
                labelText.text = getLabelText()
            }
        }
    }

    // ── Flash ────────────────────────────────────────────────────────────────

    private fun toggleFlash() {
        isFlashOn = !isFlashOn
        flashBtn.alpha = if (isFlashOn) 1f else 0.5f

        try {
            val previewRequest = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)?.apply {
                addTarget(Surface(textureView?.surfaceTexture!!))
                if (isFlashOn) {
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                } else {
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }
            }
            captureSession?.setRepeatingRequest(previewRequest!!.build(), null, cameraHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Flash toggle failed: ${e.message}")
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun needsFrontBack() = docType == "ID Card" || docType == "driving license"

    private fun getLabelText(): String {
        return if (needsFrontBack()) "Front Side"
        else "Take a photo of a $docType"
    }

    private fun getJpegOrientation(): Int {
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
        return when (rotation) {
            Surface.ROTATION_0 -> 90
            Surface.ROTATION_90 -> 0
            Surface.ROTATION_180 -> 270
            Surface.ROTATION_270 -> 180
            else -> 90
        }
    }

    override fun onBackPressed() { setResult(RESULT_CANCELED); finish() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val MATCH = FrameLayout.LayoutParams.MATCH_PARENT
    private val WRAP = FrameLayout.LayoutParams.WRAP_CONTENT

    // ── Dotted Frame Custom View ─────────────────────────────────────────────

    class DottedFrameView(context: Context) : View(context) {
        private val paint = Paint().apply {
            color = Color.YELLOW
            style = Paint.Style.STROKE
            strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val rect = RectF(8f, 8f, width.toFloat() - 8f, height.toFloat() - 8f)
            canvas.drawRoundRect(rect, 32f, 32f, paint)
        }
    }
}