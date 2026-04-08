package com.example.gyroscope.kyc


import android.util.Log
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * KycUploadHelper — Shared multipart upload utility
 *
 * Used by KycDocReviewActivity and KycVideoReviewActivity
 * Handles: multipart/form-data upload with headers, fields, files
 */
object KycUploadHelper {

    private const val TAG = "KycUploadHelper"
    private const val BOUNDARY = "----KycBoundary${Long.MAX_VALUE}"
    private const val LINE_END = "\r\n"
    private const val TWO_HYPHENS = "--"

    data class UploadResult(
        val success: Boolean,
        val statusCode: Int,
        val body: String,
        val message: String
    )

    /**
     * Upload files with multipart/form-data
     *
     * @param url           Full API URL
     * @param headers       HTTP headers (Authorization etc.)
     * @param fields        Text fields (e.g. identityDocumentType)
     * @param files         List of Pair(fieldName, File)
     * @param contentType   MIME type for files (e.g. "image/jpeg", "video/mp4")
     * @param onProgress    Progress callback (0.0 to 1.0)
     */
    fun upload(
        url: String,
        headers: Map<String, String>,
        fields: Map<String, String> = emptyMap(),
        files: List<Pair<String, File>>,
        contentType: String = "image/jpeg",
        onProgress: ((Float) -> Unit)? = null
    ): UploadResult {
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                useCaches = false
                connectTimeout = 60_000
                readTimeout = 120_000
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$BOUNDARY")
                for ((k, v) in headers) {
                    if (k.lowercase() != "content-type") {
                        setRequestProperty(k, v)
                    }
                }
            }

            // Calculate total size for progress
            var totalSize = 0L
            for ((_, file) in files) {
                totalSize += file.length()
            }

            val outputStream = DataOutputStream(connection.outputStream)
            var bytesWritten = 0L

            // ── Write text fields ──
            for ((key, value) in fields) {
                outputStream.writeBytes("$TWO_HYPHENS$BOUNDARY$LINE_END")
                outputStream.writeBytes("Content-Disposition: form-data; name=\"$key\"$LINE_END")
                outputStream.writeBytes(LINE_END)
                outputStream.writeBytes(value)
                outputStream.writeBytes(LINE_END)
            }

            // ── Write files ──
            for ((fieldName, file) in files) {
                if (!file.exists()) {
                    Log.w(TAG, "File not found: ${file.absolutePath}")
                    continue
                }

                outputStream.writeBytes("$TWO_HYPHENS$BOUNDARY$LINE_END")
                outputStream.writeBytes(
                    "Content-Disposition: form-data; name=\"$fieldName\"; filename=\"${file.name}\"$LINE_END"
                )
                outputStream.writeBytes("Content-Type: $contentType$LINE_END")
                outputStream.writeBytes(LINE_END)

                val fileInputStream = file.inputStream()
                val buffer = ByteArray(4096)
                var bytesRead: Int

                while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    bytesWritten += bytesRead
                    if (totalSize > 0) {
                        onProgress?.invoke(bytesWritten.toFloat() / totalSize.toFloat())
                    }
                }

                fileInputStream.close()
                outputStream.writeBytes(LINE_END)
            }

            // ── End boundary ──
            outputStream.writeBytes("$TWO_HYPHENS$BOUNDARY$TWO_HYPHENS$LINE_END")
            outputStream.flush()
            outputStream.close()

            // ── Read response ──
            val responseCode = connection.responseCode
            val responseBody = try {
                connection.inputStream.bufferedReader().readText()
            } catch (_: Exception) {
                connection.errorStream?.bufferedReader()?.readText() ?: ""
            }
            connection.disconnect()

            Log.d(TAG, "Upload response: HTTP $responseCode — $responseBody")

            val message = try {
                JSONObject(responseBody).optString("message", "")
            } catch (_: Exception) { "" }

            return UploadResult(
                success = responseCode in 200..299,
                statusCode = responseCode,
                body = responseBody,
                message = message
            )

        } catch (e: Exception) {
            Log.e(TAG, "Upload failed: ${e.message}", e)
            return UploadResult(
                success = false,
                statusCode = -1,
                body = "",
                message = e.message ?: "Upload failed"
            )
        }
    }
}