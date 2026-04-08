//package com.example.gyroscope

package com.earnscape.gyroscopesdk

import android.util.Log

/**
 * TransactionService — SDK Reusable
 *
 * Only validates and structures transaction data.
 * API call is handled by host app (Flutter/Dart side).
 */
object TransactionService {

    private const val TAG = "TransactionService"

    private val REQUIRED_FIELDS = listOf(
        "transaction_hash",
        "receiver_wallet",
        "sender_wallet",
        "amount",
        "chain",
        "device_id"
    )

    /**
     * Validate and structure transaction data
     *
     * @param fields  Raw transaction fields from host app
     * @return Map with validated: true/false, structured data or error
     */
    fun validateTransaction(fields: Map<String, String>): Map<String, Any> {
        // Check required fields
        val missing = mutableListOf<String>()
        for (field in REQUIRED_FIELDS) {
            if (fields[field].isNullOrBlank()) {
                missing.add(field)
            }
        }

        if (missing.isNotEmpty()) {
            Log.w(TAG, "❌ Missing fields: $missing")
            return mapOf(
                "validated" to false,
                "error" to "Missing required fields: ${missing.joinToString(", ")}"
            )
        }

        // Validate transaction hash format (basic check)
        val hash = fields["transaction_hash"]!!
        if (hash.length < 10) {
            return mapOf("validated" to false, "error" to "Invalid transaction hash")
        }

        // Validate amount is numeric
        val amount = fields["amount"]!!
        try {
            amount.toDouble()
        } catch (_: Exception) {
            return mapOf("validated" to false, "error" to "Amount must be numeric")
        }

        // Build structured data
        val structured = mutableMapOf<String, Any>(
            "validated" to true,
            "transaction_hash" to hash.trim(),
            "receiver_wallet" to fields["receiver_wallet"]!!.trim(),
            "sender_wallet" to fields["sender_wallet"]!!.trim(),
            "amount" to amount.trim(),
            "chain" to fields["chain"]!!.trim().lowercase(),
            "device_id" to fields["device_id"]!!.trim(),
            "timestamp" to System.currentTimeMillis()
        )

        // Pass through any extra fields
        for ((key, value) in fields) {
            if (key !in REQUIRED_FIELDS) {
                structured[key] = value
            }
        }

        Log.d(TAG, "✅ Transaction validated: $structured")
        return structured
    }
}