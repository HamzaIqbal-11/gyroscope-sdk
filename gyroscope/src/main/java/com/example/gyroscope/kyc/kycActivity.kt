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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * KycActivity — Main KYC Home Screen
 *
 * Shows:
 *   - Header card with lock icon
 *   - "Upload ID" card (with status: not_submitted / pending / verified / rejected)
 *   - "Selfie Video" card (with status)
 *   - Submit button
 *
 * Launched from Flutter:
 *   await _overlayChannel.invokeMethod('openKyc', {
 *     'apiUrl': 'https://api.earnscape.com',
 *     'playerId': '14086',
 *     'headers': { 'Authorization': 'Bearer ...' },
 *   });
 */
class KycActivity : Activity() {

    companion object {
        private const val TAG = "KycActivity"

        // Intent extras
        const val EXTRA_API_URL = "kycApiUrl"
        const val EXTRA_PLAYER_ID = "kycPlayerId"
        const val EXTRA_HEADERS = "kycHeaders"

        // Result keys
        const val RESULT_SUCCESS = "kycSuccess"
        const val RESULT_MESSAGE = "kycMessage"
        const val RESULT_ERROR = "kycError"

        // Request codes
        private const val RC_DOC = 5001
        private const val RC_VIDEO = 5002
    }

    // Config from Flutter
    private var apiUrl = ""
    private var playerId = ""
    private var headers = mutableMapOf<String, String>()

    // KYC status
    private var docStatus = "not_submitted"
    private var videoStatus = "not_submitted"

    // Views
    private lateinit var rootLayout: FrameLayout
    private lateinit var docStatusView: TextView
    private lateinit var videoStatusView: TextView
    private lateinit var loaderOverlay: FrameLayout
    private lateinit var loaderText: TextView

    // ── onCreate ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Read config from intent
        apiUrl = intent.getStringExtra(EXTRA_API_URL) ?: ""
        playerId = intent.getStringExtra(EXTRA_PLAYER_ID) ?: ""

        val hBundle = intent.getBundleExtra(EXTRA_HEADERS)
        if (hBundle != null) {
            for (key in hBundle.keySet()) {
                headers[key] = hBundle.getString(key, "")
            }
        }

        // Fullscreen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }

        buildUI()
        fetchKycStatus()
    }

    // ── Build UI ─────────────────────────────────────────────────────────────

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

        // Icon box
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
            text = "\uD83D\uDD12" // lock emoji
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
            "\uD83D\uDCC4", // document emoji
            "Take a picture of your valid ID",
            "To check that the personal information you have provided us is correct"
        ) {
            if (docStatus != "pending" && docStatus != "verified") {
                val intent = Intent(this, KycDocSelectionActivity::class.java).apply {
                    putExtra(KycDocSelectionActivity.EXTRA_API_URL, apiUrl)
                    putExtra(KycDocSelectionActivity.EXTRA_PLAYER_ID, playerId)
                    putExtra(KycDocSelectionActivity.EXTRA_HEADERS, bundleFromHeaders())
                }
                startActivityForResult(intent, RC_DOC)
            }
        }
        // Find and replace the arrow with status
        docStatusView = TextView(this).apply {
            text = statusIcon("not_submitted")
            textSize = 18f
            setPadding(dp(8), 0, 0, 0)
        }
        replaceLastChild(docCard, docStatusView)
        content.addView(docCard)

        // ── Video Card ──
        val videoCard = buildVerifyCard(
            "\uD83E\uDD33", // selfie emoji
            "Take a selfie video",
            "To verify it's really you on your photo ID"
        ) {
            if (videoStatus != "pending" && videoStatus != "verified") {
                val intent = Intent(this, KycVideoCaptureActivity::class.java).apply {
                    putExtra(KycVideoCaptureActivity.EXTRA_API_URL, apiUrl)
                    putExtra(KycVideoCaptureActivity.EXTRA_PLAYER_ID, playerId)
                    putExtra(KycVideoCaptureActivity.EXTRA_HEADERS, bundleFromHeaders())
                }
                startActivityForResult(intent, RC_VIDEO)
            }
        }
        videoStatusView = TextView(this).apply {
            text = statusIcon("not_submitted")
            textSize = 18f
            setPadding(dp(8), 0, 0, 0)
        }
        replaceLastChild(videoCard, videoStatusView)
        content.addView(videoCard)

        content.addView(spacer(dp(60)))

        // ── Submit Button ──
        content.addView(TextView(this).apply {
            text = "Submit"
            setTextColor(Color.BLACK)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#4CAF50"))
            }
            layoutParams = lp(MATCH, WRAP)
            setOnClickListener { submitKyc() }
        })

        scroll.addView(content)
        rootLayout.addView(scroll)

        // ── Loader Overlay ──
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
            text = "Loading..."
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

    // ── Build Verify Card ────────────────────────────────────────────────────

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
            text = icon
            textSize = 28f
            setPadding(dp(4), 0, dp(12), 0)
        })

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        textCol.addView(TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })
        textCol.addView(TextView(this).apply {
            text = subtitle
            setTextColor(Color.parseColor("#9EA3AD"))
            textSize = 12f
            setPadding(0, dp(4), 0, 0)
        })
        card.addView(textCol)

        // Arrow (will be replaced with status icon)
        card.addView(TextView(this).apply {
            text = "›"
            setTextColor(Color.WHITE)
            textSize = 22f
            setPadding(dp(8), 0, 0, 0)
        })

        return card
    }

    private fun replaceLastChild(parent: LinearLayout, newView: View) {
        if (parent.childCount > 0) {
            parent.removeViewAt(parent.childCount - 1)
        }
        parent.addView(newView)
    }

    // ── Fetch KYC Status ─────────────────────────────────────────────────────

    private fun fetchKycStatus() {
        showLoader(true, "Loading KYC status...")
        Thread {
            try {
                val url = URL("$apiUrl/player/getPlayerKycDetails/$playerId")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 30_000
                    readTimeout = 30_000
                    for ((k, v) in headers) setRequestProperty(k, v)
                }

                val code = conn.responseCode
                val body = try { conn.inputStream.bufferedReader().readText() }
                catch (_: Exception) { conn.errorStream?.bufferedReader()?.readText() ?: "" }
                conn.disconnect()

                Log.d(TAG, "KYC Status: HTTP $code — $body")

                if (code in 200..299) {
                    try {
                        val json = JSONObject(body)
                        val data = json.optJSONObject("data")
                        val playerKyc = data?.optJSONObject("playerKyc")
                        if (playerKyc != null) {
                            docStatus = playerKyc.optString("documentStatus", "not_submitted")
                            videoStatus = playerKyc.optString("videoStatus", "not_submitted")
                        }
                    } catch (_: Exception) {}
                }

                runOnUiThread {
                    showLoader(false)
                    updateStatusIcons()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fetch KYC failed: ${e.message}")
                runOnUiThread { showLoader(false) }
            }
        }.start()
    }

    private fun updateStatusIcons() {
        docStatusView.text = statusIcon(docStatus)
        videoStatusView.text = statusIcon(videoStatus)
    }

    private fun statusIcon(status: String) = when (status) {
        "pending" -> "⏳"
        "verified" -> "✅"
        "rejected" -> "❌"
        else -> "›"
    }

    // ── Submit KYC ───────────────────────────────────────────────────────────

    private fun submitKyc() {
        showLoader(true, "Submitting...")
        Thread {
            try {
                val url = URL("$apiUrl/player/submitPlayerKyc")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 30_000
                    readTimeout = 30_000
                    doOutput = true
                    for ((k, v) in headers) setRequestProperty(k, v)
                }

                val payload = """{"pid":"$playerId"}"""
                conn.outputStream.use { it.write(payload.toByteArray()) }

                val code = conn.responseCode
                val body = try { conn.inputStream.bufferedReader().readText() }
                catch (_: Exception) { conn.errorStream?.bufferedReader()?.readText() ?: "" }
                conn.disconnect()

                Log.d(TAG, "Submit KYC: HTTP $code")

                runOnUiThread {
                    showLoader(false)
                    if (code in 200..299) {
                        val msg = try { JSONObject(body).optString("message", "KYC submitted!") }
                        catch (_: Exception) { "KYC submitted!" }

                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                        setResult(RESULT_OK, Intent().apply {
                            putExtra(RESULT_SUCCESS, true)
                            putExtra(RESULT_MESSAGE, msg)
                        })
                        finish()
                    } else {
                        val msg = try { JSONObject(body).optString("message", "Something went wrong") }
                        catch (_: Exception) { "Something went wrong" }
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Submit failed: ${e.message}")
                runOnUiThread {
                    showLoader(false)
                    Toast.makeText(this, "Something went wrong. Please try again.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    // ── Activity Result ──────────────────────────────────────────────────────

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            // Refresh status after doc/video uploaded
            fetchKycStatus()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun showLoader(show: Boolean, msg: String = "") {
        loaderOverlay.visibility = if (show) View.VISIBLE else View.GONE
        if (msg.isNotEmpty()) loaderText.text = msg
    }

    private fun bundleFromHeaders(): Bundle {
        return Bundle().apply { for ((k, v) in headers) putString(k, v) }
    }

    override fun onBackPressed() {
        setResult(RESULT_CANCELED)
        finish()
    }

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