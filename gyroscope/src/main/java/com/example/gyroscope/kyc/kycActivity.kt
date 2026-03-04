package com.example.gyroscope.kyc

import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*

/**
 * KycActivity — Main KYC Home Screen (Reusable SDK — NO API calls)
 *
 * SDK only handles:
 *   - Document selection + capture (photo)
 *   - Selfie video recording
 *   - Returns captured file paths to host app
 *
 * Host app is responsible for uploading to their own backend.
 *
 * Returns to host:
 *   { success, docType, frontPhoto, backPhoto, selfieVideo }
 */
class KycActivity : Activity() {

    companion object {
        private const val TAG = "KycActivity"

        // Result keys
        const val RESULT_SUCCESS = "kycSuccess"
        const val RESULT_DOC_TYPE = "kycDocType"
        const val RESULT_FRONT_PHOTO = "kycFrontPhoto"
        const val RESULT_BACK_PHOTO = "kycBackPhoto"
        const val RESULT_SELFIE_VIDEO = "kycSelfieVideo"

        private const val RC_DOC = 5001
        private const val RC_VIDEO = 5002
    }

    // Captured file paths
    private var docType: String? = null
    private var frontPhotoPath: String? = null
    private var backPhotoPath: String? = null
    private var selfieVideoPath: String? = null

    // Views
    private lateinit var rootLayout: FrameLayout
    private lateinit var docStatusView: TextView
    private lateinit var videoStatusView: TextView
    private lateinit var submitBtn: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        buildUI()
    }

    private fun buildUI() {
        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0D1117"))
        }

        val scroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            isVerticalScrollBarEnabled = false
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(48), dp(20), dp(40))
        }

        // ── Back + Title ──
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = lp(MATCH, WRAP).apply { bottomMargin = dp(16) }
        }
        topBar.addView(TextView(this).apply {
            text = "‹"
            setTextColor(Color.WHITE)
            textSize = 28f
            setPadding(dp(4), 0, dp(16), 0)
            setOnClickListener { onBackPressed() }
        })
        topBar.addView(TextView(this).apply {
            text = "Verify"
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            gravity = Gravity.CENTER
        })
        topBar.addView(View(this).apply { layoutParams = lp(dp(32), dp(1)) })
        content.addView(topBar)

        // ── Header Card ──
        val headerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(24), dp(16), dp(24))
            background = cardBg()
            layoutParams = lp(MATCH, WRAP).apply { bottomMargin = dp(12) }
        }
        val iconBox = FrameLayout(this).apply {
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.parseColor("#1E2530"))
                setStroke(dp(1), Color.parseColor("#2A2F3B"))
            }
            layoutParams = lp(WRAP, WRAP).apply { gravity = Gravity.CENTER }
        }
        iconBox.addView(TextView(this).apply {
            text = "\uD83D\uDD12"
            textSize = 48f
            gravity = Gravity.CENTER
        })
        headerCard.addView(iconBox)
        headerCard.addView(TextView(this).apply {
            text = "Let's verify your identity\nwith KYC"
            setTextColor(Color.WHITE)
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(8))
        })
        headerCard.addView(TextView(this).apply {
            text = "You will need the following to\nverify your profile"
            setTextColor(Color.parseColor("#9EA3AD"))
            textSize = 13f
            gravity = Gravity.CENTER
        })
        content.addView(headerCard)
        content.addView(spacer(dp(8)))

        // ── Doc Card ──
        val docCard = buildVerifyCard(
            "\uD83D\uDCC4",
            "Take a picture of your valid ID",
            "To check that the personal information you have provided us is correct"
        ) {
            if (frontPhotoPath == null) {
                startActivityForResult(Intent(this, KycDocSelectionActivity::class.java), RC_DOC)
            }
        }
        docStatusView = TextView(this).apply {
            text = "›"
            textSize = 18f
            setPadding(dp(8), 0, 0, 0)
        }
        replaceLastChild(docCard, docStatusView)
        content.addView(docCard)

        // ── Video Card ──
        val videoCard = buildVerifyCard(
            "\uD83E\uDD33",
            "Take a selfie video",
            "To verify it's really you on your photo ID"
        ) {
            if (selfieVideoPath == null) {
                startActivityForResult(Intent(this, KycVideoCaptureActivity::class.java), RC_VIDEO)
            }
        }
        videoStatusView = TextView(this).apply {
            text = "›"
            textSize = 18f
            setPadding(dp(8), 0, 0, 0)
        }
        replaceLastChild(videoCard, videoStatusView)
        content.addView(videoCard)

        content.addView(spacer(dp(32)))

        // ── Submit Button ──
        submitBtn = TextView(this).apply {
            text = "SUBMIT"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#333333"))
            }
            layoutParams = lp(MATCH, WRAP)
            isEnabled = false
            alpha = 0.4f
            setOnClickListener { returnResults() }
        }
        content.addView(submitBtn)

        scroll.addView(content)
        rootLayout.addView(scroll)
        setContentView(rootLayout)
    }

    private fun buildVerifyCard(icon: String, title: String, subtitle: String, onClick: () -> Unit): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(16), dp(12), dp(16))
            background = cardBg()
            layoutParams = lp(MATCH, WRAP).apply { topMargin = dp(8) }
            setOnClickListener { onClick() }
        }
        card.addView(TextView(this).apply {
            text = icon; textSize = 28f; setPadding(dp(4), 0, dp(12), 0)
        })
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        textCol.addView(TextView(this).apply {
            text = title; setTextColor(Color.WHITE); textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })
        textCol.addView(TextView(this).apply {
            text = subtitle; setTextColor(Color.parseColor("#9EA3AD")); textSize = 12f
            setPadding(0, dp(4), 0, 0)
        })
        card.addView(textCol)
        card.addView(TextView(this).apply {
            text = "›"; setTextColor(Color.WHITE); textSize = 22f; setPadding(dp(8), 0, 0, 0)
        })
        return card
    }

    private fun replaceLastChild(parent: LinearLayout, newView: View) {
        if (parent.childCount > 0) parent.removeViewAt(parent.childCount - 1)
        parent.addView(newView)
    }

    // ── Activity Result ──────────────────────────────────────────────────────

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return

        when (requestCode) {
            RC_DOC -> {
                docType = data.getStringExtra("docType")
                frontPhotoPath = data.getStringExtra("frontPhoto")
                backPhotoPath = data.getStringExtra("backPhoto") // null for passport
                Log.d(TAG, "Doc captured: type=$docType front=$frontPhotoPath back=$backPhotoPath")
                docStatusView.text = "✅"
            }
            RC_VIDEO -> {
                selfieVideoPath = data.getStringExtra("selfieVideo")
                Log.d(TAG, "Video captured: path=$selfieVideoPath")
                videoStatusView.text = "✅"
            }
        }
        updateSubmitButton()
    }

    private fun updateSubmitButton() {
        if (frontPhotoPath != null && selfieVideoPath != null) {
            submitBtn.isEnabled = true
            submitBtn.alpha = 1.0f
            submitBtn.background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#667EEA"))
            }
        }
    }

    private fun returnResults() {
        Log.d(TAG, "Returning: docType=$docType front=$frontPhotoPath back=$backPhotoPath video=$selfieVideoPath")
        setResult(RESULT_OK, Intent().apply {
            putExtra(RESULT_SUCCESS, true)
            putExtra(RESULT_DOC_TYPE, docType ?: "")
            putExtra(RESULT_FRONT_PHOTO, frontPhotoPath ?: "")
            putExtra(RESULT_BACK_PHOTO, backPhotoPath ?: "")
            putExtra(RESULT_SELFIE_VIDEO, selfieVideoPath ?: "")
        })
        finish()
    }

    override fun onBackPressed() { setResult(RESULT_CANCELED); finish() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun cardBg() = GradientDrawable().apply {
        cornerRadius = dp(20).toFloat()
        setColor(Color.parseColor("#1A1F2B"))
        setStroke(dp(1), Color.parseColor("#2A2F3B"))
    }
    private fun spacer(h: Int) = View(this).apply { layoutParams = lp(MATCH, h) }
    private fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)
    private val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    private val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
}