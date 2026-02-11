package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Helper class for integrating Prove Bot Detection into the sign-in/sign-up flows.
 *
 * Flow overview (per https://developer.prove.com/docs/check-for-prove-key#prompt-for-phone-number-3):
 * 1. Initialize the Prove SDK on app start to obtain a device-level "Prove Key".
 * 2. Before sending an OTP / completing sign-in, call YOUR backend with the phone number + Prove Key.
 * 3. Your backend calls Prove's /v3/verify API with verificationType="bot".
 * 4. The backend returns the bot-detection result to the app.
 * 5. The app decides whether to proceed with authentication or block the attempt.
 */
object ProveBotDetectionHelper {

    private const val TAG = "ProveBotDetection"

    // IMPORTANT: Replace with your own backend endpoint that proxies to Prove's API.
    // NEVER call Prove's API directly from the client — your access token must stay server-side.
    private const val BOT_DETECTION_BACKEND_URL = "https://your-backend.example.com/api/prove/bot-detect"

    private val httpClient = OkHttpClient()
    private var proveKey: String? = null

    /**
     * Initialize the Prove SDK and retrieve the Prove Key (device fingerprint).
     * Call this once during app startup (e.g., in AuthClient.initialize).
     */
    fun initialize(context: Context) {
        try {
            // Initialize the Prove SDK — the exact API depends on the SDK version.
            // Refer to: https://developer.prove.com/reference/unify-android-sdk
            //
            // Example (pseudocode — adapt to actual SDK):
            // ProveAuth.initialize(context, "YOUR_PROVE_CLIENT_ID")
            // proveKey = ProveAuth.getDeviceId()

            Log.i(TAG, "Prove SDK initialized. Prove Key obtained.")

            // TODO: Replace the line below with real SDK call
            // proveKey = ProveAuth.getDeviceId()
            proveKey = "PLACEHOLDER_PROVE_KEY" // Remove this after real integration
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Prove SDK", e)
        }
    }

    /**
     * Check whether the Prove Key is available.
     */
    fun hasProveKey(): Boolean {
        return !proveKey.isNullOrBlank()
    }

    /**
     * Perform bot detection by calling YOUR backend with the phone number and Prove Key.
     *
     * Your backend should:
     *   1. Obtain a Prove access token (OAuth2 client_credentials grant).
     *   2. POST to https://platform.prove.com/v3/verify with:
     *      {
     *        "verificationType": "bot",
     *        "phoneNumber": "<phone>",
     *        "deviceId": "<proveKey>",
     *        "clientRequestId": "<unique-session-id>"
     *      }
     *   3. Return the result to the mobile app.
     *
     * @param phoneNumber The phone number entered by the user (E.164 format recommended)
     * @return BotDetectionResult indicating whether the user passed or failed bot detection
     */
    suspend fun performBotDetection(phoneNumber: String): BotDetectionResult {
        if (!hasProveKey()) {
            Log.w(TAG, "Prove Key not available. Cannot perform bot detection.")
            return BotDetectionResult.Error("Prove Key not available. Please restart the app.")
        }

        return withContext(Dispatchers.IO) {
            try {
                val requestBody = JSONObject().apply {
                    put("phoneNumber", phoneNumber)
                    put("deviceId", proveKey)
                    put("clientRequestId", java.util.UUID.randomUUID().toString())
                }.toString()

                val request = Request.Builder()
                    .url(BOT_DETECTION_BACKEND_URL)
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: "{}"
                    val json = JSONObject(responseBody)

                    val isHuman = json.optBoolean("success", false)
                    val score = json.optDouble("score", 0.0)

                    if (isHuman) {
                        Log.i(TAG, "Bot detection passed. Score: $score")
                        BotDetectionResult.Passed(score)
                    } else {
                        Log.w(TAG, "Bot detection failed. Score: $score")
                        BotDetectionResult.Failed(score, "Phone number flagged as potential fraud.")
                    }
                } else {
                    Log.e(TAG, "Bot detection API returned ${response.code}")
                    BotDetectionResult.Error("Bot detection service unavailable (HTTP ${response.code}).")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Bot detection request failed", e)
                BotDetectionResult.Error("Bot detection failed: ${e.message}")
            }
        }
    }

    /**
     * Sealed class representing the result of a bot detection check.
     */
    sealed class BotDetectionResult {
        /** The phone number passed bot detection (appears to be a real human). */
        data class Passed(val score: Double) : BotDetectionResult()

        /** The phone number failed bot detection (potential bot/fraud). */
        data class Failed(val score: Double, val reason: String) : BotDetectionResult()

        /** An error occurred during bot detection. */
        data class Error(val message: String) : BotDetectionResult()
    }
}
