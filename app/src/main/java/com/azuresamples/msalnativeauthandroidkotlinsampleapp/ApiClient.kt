package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.lang.Exception

object ApiClient {
    private val client = OkHttpClient()
    private const val WEB_API_BASE_URL = "" // Developers should set the respective URL of their web API here

    init {
        check(WEB_API_BASE_URL.isNotBlank()) { "WEB_API_BASE_URL is not set." }
    }

    fun performGetApiRequest(accessToken: String): Int {
        val fullUrl = "$WEB_API_BASE_URL/api/todolist"

        val requestBuilder = Request.Builder()
                .url(fullUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()

        val request = requestBuilder.build()

        client.newCall(request).execute().use { response -> return response.code }
    }
}
