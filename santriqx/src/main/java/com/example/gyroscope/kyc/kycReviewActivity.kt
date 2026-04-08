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
 * KycDocReviewActivity — Review captured images (NO upload)
 *
 * Shows front (and optionally back) image
 * Retake → go back to camera
 * Confirm → return file paths to parent
 */
class KycDocReviewActivity : Activity() {

    companion object {
        const val EXTRA_DOC_TYPE = "kycDocType"
        const val EXTRA_FRONT_IMAGE = "kycFrontImage"
        const val EXTRA_BACK_IMAGE = "kycBackImage"
    }

    private var docType = ""
    private var frontImagePath: String? = null
    private var backImagePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        docType = intent.getStringExtra(EXTRA_DOC_TYPE) ?: ""
        frontImagePath = intent.getStringExtra(EXTRA_FRONT_IMAGE)
        backImagePath = intent.getStringExtra(EXTRA_BACK_IMAGE)

        @Suppress("DEPRECATION")
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        buildUI()
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
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(10) }
        })

        // Front Image
        val frontLabel = if (needsFrontBack()) "Front Side"
        else docType.replaceFirstChar { it.uppercase() }
        frontImagePath?.let { content.addView(buildImageSection(frontLabel, it)) }

        // Back Image
        backImagePath?.let { content.addView(buildImageSection("Back Side", it)) }

        content.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(30))
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
            borderView.addView(ImageView(this).apply {
                setImageBitmap(bitmap)
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                layoutParams = FrameLayout.LayoutParams(MATCH, WRAP)
            })
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

    // ── Confirm → return file paths (NO upload) ─────────────────────────────

    private fun confirmAndReturn() {
        setResult(RESULT_OK, Intent().apply {
            putExtra("docType", docType)
            putExtra("frontPhoto", frontImagePath)
            putExtra("backPhoto", backImagePath)
        })
        finish()
    }

    override fun onBackPressed() {
        // Force retake or confirm
    }

    private fun needsFrontBack() = docType == "ID Card" || docType == "driving license"

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    private val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
}