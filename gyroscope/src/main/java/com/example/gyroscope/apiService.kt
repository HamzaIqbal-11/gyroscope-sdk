package com.earnscape.gyroscopesdk

import android.util.Log
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

/**
 * ApiService — SDK Internal HTTP Client
 *
 * Handles GET, POST (JSON), and Multipart POST.
 * No external dependencies — uses HttpURLConnection.
 */
object ApiService {

    private const val TAG = "ApiService"
    private const val TIMEOUT = 30000

    // ── Global headers (set once, sent with every request) ──
    private var globalHeaders: Map<String, String> = emptyMap()

    fun setGlobalHeaders(headers: Map<String, String>) {
        globalHeaders = headers
        Log.d(TAG, "✅ Global headers set: ${headers.keys}")
    }
    /**
     * GET request
     */
    fun get(url: String, headers: Map<String, String> = emptyMap()): Map<String, Any>  {
        return try {
            Log.d(TAG, "📡 GET $url")
            val allHeaders = globalHeaders + headers  // ← global + per-request
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Content-Type", "application/json")
                allHeaders.forEach { (key, value) -> setRequestProperty(key, value) }
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
            }

            val code = conn.responseCode
            val body = readResponse(conn)
            conn.disconnect()

            Log.d(TAG, "📡 Response: $code")
            parseResponse(code, body)
        } catch (e: Exception) {
            Log.e(TAG, "❌ GET error: ${e.message}")
            mapOf("success" to false, "error" to (e.message ?: "Network error"))
        }
    }

    /**
     * POST request with JSON body
     */
    fun post(url: String, data: Map<String, Any>, headers: Map<String, String> = emptyMap()): Map<String, Any> {
        return try {
            Log.d(TAG, "📡 POST $url")
            val json = JSONObject(data).toString()
            val allHeaders = globalHeaders + headers  // ← global + per-request
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                allHeaders.forEach { (key, value) -> setRequestProperty(key, value) }
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
                doOutput = true
            }

            OutputStreamWriter(conn.outputStream).use { it.write(json); it.flush() }

            val code = conn.responseCode
            val body = readResponse(conn)
            conn.disconnect()

            Log.d(TAG, "📡 Response: $code")
            parseResponse(code, body)
        } catch (e: Exception) {
            Log.e(TAG, "❌ POST error: ${e.message}")
            mapOf("success" to false, "error" to (e.message ?: "Network error"))
        }
    }

    /**
     * Multipart POST (for file uploads — face image etc)
     */
    fun postMultipart(
        url: String,
        fields: Map<String, String>,
        filePath: String,
        fileFieldName: String = "image",
        fileName: String = "file.png"
    ): Map<String, Any> {
        return try {
            val boundary = "----SantriqxSDK${System.currentTimeMillis()}"
            val file = File(filePath)
            if (!file.exists()) return mapOf("success" to false, "error" to "File not found")

            val allHeaders = globalHeaders  // ← global headers
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                allHeaders.forEach { (key, value) -> setRequestProperty(key, value) }
                connectTimeout = 120000
                readTimeout = 120000
                doOutput = true
            }

            DataOutputStream(conn.outputStream).use { out ->
                // Text fields
                for ((key, value) in fields) {
                    out.writeBytes("--$boundary\r\n")
                    out.writeBytes("Content-Disposition: form-data; name=\"$key\"\r\n\r\n")
                    out.writeBytes("$value\r\n")
                }

                // File
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"$fileFieldName\"; filename=\"$fileName\"\r\n")
                out.writeBytes("Content-Type: image/png\r\n\r\n")

                FileInputStream(file).use { fis ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                    }
                }

                out.writeBytes("\r\n--$boundary--\r\n")
                out.flush()
            }

            val code = conn.responseCode
            val body = readResponse(conn)
            conn.disconnect()

            Log.d(TAG, "📡 Response: $code")
            parseResponse(code, body)
        } catch (e: Exception) {
            Log.e(TAG, "❌ MULTIPART error: ${e.message}")
            mapOf("success" to false, "error" to (e.message ?: "Upload error"))
        }
    }

    private fun readResponse(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        return BufferedReader(InputStreamReader(stream ?: return "")).use { it.readText() }
    }

    private fun parseResponse(code: Int, body: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>(
            "httpCode" to code,
            "success" to (code in 200..299)
        )
        try {
            val json = JSONObject(body)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = json.get(key).toString()
            }
        } catch (_: Exception) {
            result["body"] = body
        }
        return result
    }
}