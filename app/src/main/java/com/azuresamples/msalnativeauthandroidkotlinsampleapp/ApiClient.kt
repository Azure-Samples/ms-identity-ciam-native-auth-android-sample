package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import okhttp3.OkHttpClient
import okhttp3.Request

object ApiClient {
    private val client = OkHttpClient()

    data class ProtectedApiResponse(
        val statusCode: Int,
        val body: String
    )

    fun performGetApiRequest(
        webApiUrl: String,
        accessToken: String
    ): ProtectedApiResponse {
        val fullUrl = "${webApiUrl.trimEnd('/')}/api/todolist"
        val request = Request.Builder()
            .url(fullUrl)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            return ProtectedApiResponse(
                statusCode = response.code,
                body = response.body?.string().orEmpty()
            )
        }
    }
}
