package com.example.gyroscope.kyc

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.widget.*

/**
 * KycDocSelectionActivity — Select document type
 * Options: Driver's License, Passport, Photo ID Card
 * After selection → opens KycDocCaptureActivity
 */
class KycDocSelectionActivity : Activity() {

    companion object {
        private const val RC_CAPTURE = 5010
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        buildUI()
    }

    private fun buildUI() {
        val root = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0D1117"))
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
            text = "‹"; setTextColor(Color.WHITE); textSize = 28f
            setPadding(dp(4), 0, dp(16), 0)
            setOnClickListener { onBackPressed() }
        })
        topBar.addView(TextView(this).apply {
            text = "Verify"; setTextColor(Color.WHITE); textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        })
        topBar.addView(View(this).apply { layoutParams = lp(dp(32), dp(1)) })
        content.addView(topBar)

        content.addView(TextView(this).apply {
            text = "\uD83D\uDCCB"; textSize = 64f; gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(20)); layoutParams = lp(MATCH, WRAP)
        })
        content.addView(TextView(this).apply {
            text = "Upload proof of identity"; setTextColor(Color.WHITE); textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER; layoutParams = lp(MATCH, WRAP)
        })
        content.addView(TextView(this).apply {
            text = "Select the type of document\nyou are using below"
            setTextColor(Color.parseColor("#9EA3AD")); textSize = 14f; gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(28)); layoutParams = lp(MATCH, WRAP)
        })

        content.addView(buildOption("\uD83E\uDEAA", "Driver's License") { openCapture("driving license") })
        content.addView(buildOption("\uD83D\uDCD5", "Passport") { openCapture("passport") })
        content.addView(buildOption("\uD83C\uDD94", "Photo ID Card") { openCapture("ID Card") })

        root.addView(content)
        setContentView(root)
    }

    private fun buildOption(icon: String, title: String, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(16), dp(14), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.parseColor("#1A1F2B"))
                setStroke(dp(1), Color.parseColor("#2A2F3B"))
            }
            layoutParams = lp(MATCH, WRAP).apply { topMargin = dp(8) }
            setOnClickListener { onClick() }

            addView(TextView(this@KycDocSelectionActivity).apply {
                text = icon; textSize = 28f; setPadding(dp(4), 0, dp(16), 0)
            })
            addView(TextView(this@KycDocSelectionActivity).apply {
                text = title; setTextColor(Color.WHITE); textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            })
            addView(TextView(this@KycDocSelectionActivity).apply {
                text = "›"; setTextColor(Color.WHITE); textSize = 22f
            })
        }
    }

    private fun openCapture(docType: String) {
        val intent = Intent(this, KycDocCaptureActivity::class.java).apply {
            putExtra(KycDocCaptureActivity.EXTRA_DOC_TYPE, docType)
        }
        startActivityForResult(intent, RC_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_CAPTURE && resultCode == RESULT_OK) {
            setResult(RESULT_OK, data)
            finish()
        }
    }

    override fun onBackPressed() { setResult(RESULT_CANCELED); finish() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)
    private val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    private val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
}