package com.example.gyroscope.kyc



import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import java.io.File

/**
 * KycDocReviewActivity — Review captured images + Upload
 *
 * Shows front (and optionally back) image
 * Retake → go back to camera
 * Confirm → multipart upload to backend
 *
 * Upload endpoint: POST /player/uploadPlayerKycDocument/{playerId}
 * Fields: identityDocumentType, images (multipart files)
 */
class KycDocReviewActivity : Activity() {

    companion object {
        private const val TAG = "KycDocReview"

        const val EXTRA_API_URL = "kycApiUrl"
        const val EXTRA_PLAYER_ID = "kycPlayerId"
        const val EXTRA_HEADERS = "kycHeaders"
        const val EXTRA_DOC_TYPE = "kycDocType"
        const val EXTRA_FRONT_IMAGE = "kycFrontImage"
        const val EXTRA_BACK_IMAGE = "kycBackImage"
    }

    private var apiUrl = ""
    private var playerId = ""
    private var headers = mutableMapOf<String, String>()
    private var docType = ""
    private var frontImagePath: String? = null
    private var backImagePath: String? = null

    // Views
    private lateinit var rootLayout: FrameLayout
    private lateinit var retakeBtn: TextView
    private lateinit var confirmBtn: TextView
    private lateinit var loaderOverlay: FrameLayout
    private lateinit var loaderText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        apiUrl = intent.getStringExtra(EXTRA_API_URL) ?: ""
        playerId = intent.getStringExtra(EXTRA_PLAYER_ID) ?: ""
        docType = intent.getStringExtra(EXTRA_DOC_TYPE) ?: ""
        frontImagePath = intent.getStringExtra(EXTRA_FRONT_IMAGE)
        backImagePath = intent.getStringExtra(EXTRA_BACK_IMAGE)

        val hBundle = intent.getBundleExtra(EXTRA_HEADERS)
        if (hBundle != null) {
            for (key in hBundle.keySet()) headers[key] = hBundle.getString(key, "")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }

        buildUI()
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
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(10) }
        })

        // ── Front Image ──
        val frontLabel = if (needsFrontBack()) "Front Side"
        else docType.replaceFirstChar { it.uppercase() }

        frontImagePath?.let { path ->
            content.addView(buildImageSection(frontLabel, path))
        }

        // ── Back Image ──
        backImagePath?.let { path ->
            content.addView(buildImageSection("Back Side", path))
        }

        content.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(30))
        })

        // ── Buttons Row ──
        val btnRow = LinearLayout(this).apply {
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
                setResult(RESULT_CANCELED) // triggers retake in capture activity
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
            setOnClickListener { uploadDocs() }
        }
        btnRow.addView(confirmBtn)

        content.addView(btnRow)

        scroll.addView(content)
        rootLayout.addView(scroll)

        // ── Loader ──
        loaderOverlay = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#AA000000"))
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }
        val loaderBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(24), dp(28), dp(24))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.parseColor("#2A2F3B"))
            }
            layoutParams = FrameLayout.LayoutParams(dp(200), WRAP, Gravity.CENTER)
        }
        loaderBox.addView(ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { gravity = Gravity.CENTER }
        })
        loaderText = TextView(this).apply {
            text = "Uploading..."
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }
        loaderBox.addView(loaderText)
        loaderOverlay.addView(loaderBox)
        rootLayout.addView(loaderOverlay)

        setContentView(rootLayout)
    }

    // ── Image Section ────────────────────────────────────────────────────────

    private fun buildImageSection(label: String, imagePath: String): LinearLayout {
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16) }
        }

        section.addView(TextView(this).apply {
            text = label
            setTextColor(Color.parseColor("#B0B0B0"))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        })

        // Dotted border container
        val borderView = FrameLayout(this).apply {
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setStroke(dp(2), Color.YELLOW)
                setColor(Color.TRANSPARENT)
            }
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        val bitmap = BitmapFactory.decodeFile(imagePath)
        if (bitmap != null) {
            val imageView = ImageView(this).apply {
                setImageBitmap(bitmap)
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                layoutParams = FrameLayout.LayoutParams(MATCH, WRAP)
            }
            borderView.addView(imageView)
        } else {
            borderView.addView(TextView(this).apply {
                text = "Image not found"
                setTextColor(Color.RED)
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(40), dp(16), dp(40))
            })
        }

        section.addView(borderView)
        return section
    }

    // ── Upload ───────────────────────────────────────────────────────────────

    private fun uploadDocs() {
        showLoader(true)
        retakeBtn.isEnabled = false
        confirmBtn.isEnabled = false

        Thread {
            try {
                val files = mutableListOf<Pair<String, File>>()

                frontImagePath?.let { path ->
                    val file = File(path)
                    if (file.exists()) files.add("images" to file)
                }
                backImagePath?.let { path ->
                    val file = File(path)
                    if (file.exists()) files.add("images" to file)
                }

                if (files.isEmpty()) {
                    runOnUiThread {
                        showLoader(false)
                        Toast.makeText(this, "No images to upload", Toast.LENGTH_SHORT).show()
                        retakeBtn.isEnabled = true
                        confirmBtn.isEnabled = true
                    }
                    return@Thread
                }

                val fields = mutableMapOf<String, String>()
                fields["identityDocumentType"] = docType

                val url = "$apiUrl/player/uploadPlayerKycDocument/$playerId"

                val result = KycUploadHelper.upload(
                    url = url,
                    headers = headers,
                    fields = fields,
                    files = files,
                    contentType = "image/jpeg"
                )

                runOnUiThread {
                    showLoader(false)
                    if (result.success) {
                        Toast.makeText(this, result.message.ifEmpty { "Document uploaded!" }, Toast.LENGTH_LONG).show()
                        setResult(RESULT_OK, Intent().apply {
                            putExtra("uploadSuccess", true)
                            putExtra("uploadMessage", result.message)
                        })
                        finish()
                    } else {
                        Toast.makeText(this, result.message.ifEmpty { "Upload failed" }, Toast.LENGTH_LONG).show()
                        retakeBtn.isEnabled = true
                        confirmBtn.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Upload error: ${e.message}")
                runOnUiThread {
                    showLoader(false)
                    Toast.makeText(this, "Something went wrong. Please try again.", Toast.LENGTH_LONG).show()
                    retakeBtn.isEnabled = true
                    confirmBtn.isEnabled = true
                }
            }
        }.start()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun showLoader(show: Boolean) {
        loaderOverlay.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun needsFrontBack() = docType == "ID Card" || docType == "driving license"

    override fun onBackPressed() {
        // Don't allow back without explicit retake
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    private val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
}