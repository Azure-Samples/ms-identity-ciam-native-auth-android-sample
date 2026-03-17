package com.example.myapplication.data

import com.example.myapplication.helpers.ApiEndpoints
import com.example.myapplication.helpers.HttpClientHelper
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit
import com.humansecurity.mobile_sdk.main.HSInterceptor

class AuthRepository(private val httpClient: OkHttpClient = HttpClientHelper.client) {

    // --- Sign-in Endpoints ---

    suspend fun initiateSignin(username: String): String? {
        val formBody = FormBody.Builder()
            .add("username", username)
            .add("client_id", ApiEndpoints.CLIENT_ID)
            .add("challenge_type", "password redirect")
            .add("capabilities", "mfa_required")
            .build()
        
        val request = HttpClientHelper.createRequest(ApiEndpoints.AUTH_API_BASE_URL_SIGNIN + ApiEndpoints.SIGNIN_INITIATE, formBody)
        return HttpClientHelper.executeRequest(request, httpClient)
    }

    suspend fun requestSigninChallenge(token: String): String? {
        val formBody = FormBody.Builder()
            .add("continuation_token", token)
            .add("challenge_type", "password redirect")
            .add("client_id", ApiEndpoints.CLIENT_ID)
            .build()
        
        val request = HttpClientHelper.createRequest(ApiEndpoints.AUTH_API_BASE_URL_SIGNIN + ApiEndpoints.SIGNIN_CHALLENGE, formBody)
        return HttpClientHelper.executeRequest(request, httpClient)
    }

    suspend fun submitSigninPassword(username: String, password: String, continuationToken: String, sessionId: String): Response? {
        val formBody = FormBody.Builder()
            .add("continuation_token", continuationToken)
            .add("grant_type", "password")
            .add("scope", "openid offline_access")
            .add("password", password)
            .add("client_id", ApiEndpoints.CLIENT_ID)
            .build()
        
        val headers = mapOf("email_id" to username, "session_id" to sessionId)
        return HttpClientHelper.executeFullRequest(
            HttpClientHelper.createRequest(ApiEndpoints.AUTH_API_BASE_URL_SIGNIN + ApiEndpoints.SIGNIN_TOKEN, formBody, headers),
            httpClient
        )
    }

    suspend fun initiateMFA(username: String, password: String, continuationToken: String, sessionId: String): Response? {
        val body = FormBody.Builder()
            .add("continuation_token", continuationToken)
            .add("grant_type", "password")
            .add("scope", "offline_access openid api://${ApiEndpoints.CLIENT_ID}/App.Read")
            .add("password", password)
            .add("client_id", ApiEndpoints.CLIENT_ID)
            .add("claims", "{\"access_token\":{\"acrs\":{\"essential\":true,\"value\":\"c3\"}}}")
            .build()
        
        val headers = mapOf("email_id" to username, "session_id" to sessionId)
        return HttpClientHelper.executeFullRequest(
            HttpClientHelper.createRequest(ApiEndpoints.AUTH_API_BASE_URL_SIGNIN + ApiEndpoints.SIGNIN_TOKEN, body, headers),
            httpClient
        )
    }

    suspend fun handleIntrospect(continuationToken: String): Response? {
        val body = FormBody.Builder()
            .add("continuation_token", continuationToken)
            .add("client_id", ApiEndpoints.CLIENT_ID)
            .add("challenge_type", "password oob redirect")
            .build()
        
        return HttpClientHelper.executeFullRequest(
            HttpClientHelper.createRequest(ApiEndpoints.AUTH_API_BASE_URL_SIGNIN + ApiEndpoints.SIGNIN_INTROSPECT, body),
            httpClient
        )
    }

    suspend fun requestMFAChallenge(methodId: String, continuationToken: String): Response? {
        val body = FormBody.Builder()
            .add("continuation_token", continuationToken)
            .add("client_id", ApiEndpoints.CLIENT_ID)
            .add("id", methodId)
            .build()
        
        return HttpClientHelper.executeFullRequest(
            HttpClientHelper.createRequest(ApiEndpoints.AUTH_API_BASE_URL_SIGNIN + ApiEndpoints.SIGNIN_CHALLENGE, body),
            httpClient
        )
    }

    suspend fun getFinalAccessTokenUsingOTP(otp: String, username: String, continuationToken: String, sessionId: String): Response? {
        val body = FormBody.Builder()
            .add("continuation_token", continuationToken)
            .add("grant_type", "mfa_oob")
            .add("scope", "offline_access openid api://${ApiEndpoints.CLIENT_ID}/App.Read")
            .add("oob", otp)
            .add("client_id", ApiEndpoints.CLIENT_ID)
            .add("claims", "{\"access_token\":{\"acrs\":{\"essential\":true,\"value\":\"c3\"}}}")
            .build()
        
        val headers = mapOf("email_id" to username, "session_id" to sessionId)
        return HttpClientHelper.executeFullRequest(
            HttpClientHelper.createRequest(ApiEndpoints.AUTH_API_BASE_URL_SIGNIN + ApiEndpoints.SIGNIN_TOKEN, body, headers),
            httpClient
        )
    }

    // --- Sign-up Endpoints ---

    suspend fun signupStart(username: String, password: String): String? {
        val formBody = FormBody.Builder()
            .add("username", username)
            .add("client_id", ApiEndpoints.CLIENT_ID)
            .add("password", password)
            .add("challenge_type", "oob password redirect")
            .build()
        
        val request = HttpClientHelper.createRequest(ApiEndpoints.AUTH_API_BASE_URL_SIGNUP + ApiEndpoints.SIGNUP_START, formBody)
        return HttpClientHelper.executeRequest(request, httpClient)
    }

    suspend fun signupChallenge(continuationToken: String): String? {
        val formBody = FormBody.Builder()
            .add("continuation_token", continuationToken)
            .add("challenge_type", "oob password redirect")
            .add("client_id", ApiEndpoints.CLIENT_ID)
            .build()
        
        val request = HttpClientHelper.createRequest(ApiEndpoints.AUTH_API_BASE_URL_SIGNUP + ApiEndpoints.SIGNUP_CHALLENGE, formBody)
        return HttpClientHelper.executeRequest(request, httpClient)
    }

    suspend fun signupContinue(otp: String, continuationToken: String): String? {
        val humanEmbeddedClient = OkHttpClient.Builder()
            .callTimeout(0, TimeUnit.SECONDS)
            .addInterceptor(HSInterceptor())
            .build()

        val formBody = FormBody.Builder()
            .add("continuation_token", continuationToken)
            .add("grant_type", "oob")
            .add("oob", otp)
            .add("client_id", ApiEndpoints.CLIENT_ID)
            .build()
        
        val request = HttpClientHelper.createRequest(ApiEndpoints.AUTH_API_BASE_URL_SIGNUP + ApiEndpoints.SIGNUP_CONTINUE, formBody)
        return HttpClientHelper.executeRequest(request, humanEmbeddedClient)
    }

    suspend fun getSignupFinalToken(username: String, continuationToken: String, sessionId: String): String? {
        val formBody = FormBody.Builder()
            .add("grant_type", "continuation_token")
            .add("continuation_token", continuationToken)
            .add("client_id", ApiEndpoints.CLIENT_ID)
            .add("username", username)
            .add("scope", "openid profile")
            .build()
        
        val headers = mapOf("email_id" to username, "session_id" to sessionId)
        val request = HttpClientHelper.createRequest(ApiEndpoints.AUTH_API_BASE_URL_SIGNUP + ApiEndpoints.SIGNUP_TOKEN, formBody, headers)
        return HttpClientHelper.executeRequest(request, httpClient)
    }
}
