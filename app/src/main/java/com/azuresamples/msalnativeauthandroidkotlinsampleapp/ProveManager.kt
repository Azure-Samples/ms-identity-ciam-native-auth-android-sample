package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit


/**
 * ProveManager handles Prove Identity verification for MFA with SMS
 * using customer-supplied possession flow.
 *
 * Uses OkHttp for REST calls because the Prove server SDK (com.prove:proveapi)
 * relies on java.net.http.HttpClient which is not available on Android.
 *
 * Documentation: https://developer.prove.com/docs/check-for-prove-key#prove-possession-mobile
 */
class ProveManager private constructor() {

    companion object {
        private const val TAG = "ProveManager"
        private const val BASE_URL = "https://platform.uat.proveapis.com"
        private const val TOKEN_URL = "$BASE_URL/token"
        private const val V3_UNIFY_URL = "$BASE_URL/v3/unify"
        private const val V3_VALIDATE_URL = "$BASE_URL/v3/validate"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        @Volatile
        private var instance: ProveManager? = null

        fun getInstance(): ProveManager {
            return instance ?: synchronized(this) {
                instance ?: ProveManager().also { instance = it }
            }
        }
    }

    // Store the correlation ID from the start response for subsequent calls
    private var correlationId: String? = null
    private var authToken: String? = null

    private var clientId: String? = null
    private var clientSecret: String? = null

    // Cached OAuth access token
    private var accessToken: String? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Result class for Prove verification operations
     */
    sealed class ProveResult {
        data class Success(
            val correlationId: String,
            val authToken: String? = null,
            val phoneVerified: Boolean = false,
            val possessionResult: String? = null
        ) : ProveResult()

        /**
         * The Prove call failed or possession could not be verified.
         *
         * Note: In this sample we fail closed (possession must verify) before proceeding to Microsoft SMS OTP.
         */
        data class Error(
            val message: String,
            val errorCode: String? = null
        ) : ProveResult()
    }

    /**
     * Initialize the Prove API credentials.
     * NOTE: In production, credentials should be fetched from your backend server,
     * not stored in the app.
     */
    fun initialize(clientId: String, clientSecret: String) {
        this.clientId = clientId
        this.clientSecret = clientSecret
        Log.d(TAG, "Prove API credentials stored")
    }

    /**
     * Obtain an OAuth2 access token using the client-credentials grant.
     * Caches the token for subsequent calls (no expiry handling in this sample).
     */
    private fun fetchAccessToken(): String {
        Log.d(TAG, "First touch of fetchAccessToken() - fetching new token")
        accessToken?.let { return it }

        val id = clientId
            ?: throw IllegalStateException("Prove clientId not set. Call initialize() first.")
        val secret = clientSecret
            ?: throw IllegalStateException("Prove clientSecret not set. Call initialize() 2.")
        Log.d(TAG, "2 touch of fetchAccessToken() - fetching new token")
        val formBody = FormBody.Builder()
            .add("grant_type", "client_credentials")
            .add("client_id", id)
            .add("client_secret", secret)
            .build()
        Log.d(TAG, "${formBody}")
        val request = Request.Builder()
            .url(TOKEN_URL)
            .post(formBody)
            .build()

        Log.d(TAG, "${request}")
        httpClient.newCall(request).execute().use { response ->

            if (!response.isSuccessful) {
                throw RuntimeException("Token request failed: ${response.code} ${response.body?.string()}")
            }

            val json = JSONObject(response.body!!.string())
            Log.d(TAG, "${json}")
            val token = json.getString("access_token")
            accessToken = token
            Log.d(TAG, "${token}")
            return token
        }

    }

    /**
     * Start a V3 Unify session and store authToken / correlationId.
     */
    suspend fun initializeWithAuthToken(phoneNumber: String?): ProveResult = withContext(Dispatchers.IO) {
        try {
            val token = fetchAccessToken()

            val payload = FormBody.Builder()
                .add("clientRequestId", UUID.randomUUID().toString())
                .add("possessionType", "none")
                .add("phoneNumber", phoneNumber ?: "")
                .build()

            val request = Request.Builder()
                .url(V3_UNIFY_URL)
                .addHeader("Authorization", "Bearer $token")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    return@withContext ProveResult.Error(
                        message = "V3 Unify failed: ${response.code} $responseBody",
                        errorCode = response.code.toString()
                    )
                }

                val json = JSONObject(responseBody!!)
                authToken = json.optString("authToken", null)
                correlationId = json.optString("correlationId", null)

                Log.d(TAG, "V3 Unify successful. CorrelationId: $correlationId")

                return@withContext ProveResult.Success(
                    correlationId = correlationId!!,
                    authToken = authToken
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "initializeWithAuthToken failed", e)
            ProveResult.Error(message = e.message ?: "Unknown error occurred")
        }
    }

    /**
     * Step 2: Validate/Check for Prove Key (Possession Check)
     *
     * This checks if the device has Prove Key installed and can verify possession.
     *
     * Policy for this sample:
     * - If Prove Key is available and possession verifies: proceed to Microsoft SMS OTP.
     * - If not available / not verified / API error: FAIL CLOSED and do not proceed.
     *
     * @param correlationId The correlation ID from initializeWithAuthToken
     */
    suspend fun checkPossession(correlationId: String? = null): ProveResult = withContext(Dispatchers.IO) {
        try {
            clearSession()

            val token = fetchAccessToken()

            val corrId = correlationId ?: this@ProveManager.correlationId
            ?: return@withContext ProveResult.Error(
                message = "No correlation ID available. Call initializeWithAuthToken() first."
            )

            Log.d(TAG, "Checking possession for correlationId: $corrId")

            val payload = JSONObject().apply {
                put("correlationId", corrId)
            }

            val request = Request.Builder()
                .url(V3_VALIDATE_URL)
                .addHeader("Authorization", "Bearer $token")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    return@withContext ProveResult.Error(
                        message = "V3 Validate failed: ${response.code} $responseBody",
                        errorCode = response.code.toString()
                    )
                }

                val json = JSONObject(responseBody!!)
                val verified = json.optBoolean("success", false)

                Log.d(TAG, "Possession check result: success=$verified")

                if (verified) {
                    return@withContext ProveResult.Success(
                        correlationId = corrId,
                        phoneVerified = true,
                        possessionResult = "verified"
                    )
                }

                val returnedPhone = json.optString("phoneNumber", "")
                Log.w(TAG, "Possession not verified. success=$verified, phoneNumber=${returnedPhone.takeLast(4)}")
                return@withContext ProveResult.Error(
                    message = "Prove possession could not be verified. Cannot proceed with SMS OTP."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Possession check exception", e)
            ProveResult.Error(message = e.message ?: "Unknown error occurred")
        }
    }

    /**
     * Clear the current session data.
     */
    private fun clearSession() {
        correlationId = null
        authToken = null
        Log.d(TAG, "Session cleared")
    }
}