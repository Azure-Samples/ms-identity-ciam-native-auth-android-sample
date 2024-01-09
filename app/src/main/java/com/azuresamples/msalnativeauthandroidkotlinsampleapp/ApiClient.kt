package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request


class ApiClient(context: Context) {
    private val client = OkHttpClient()
    private val site = context.readSiteFromRawJsonFile(R.raw.protected_api_config)
    companion object {
        private val TAG = ApiClient::class.java.simpleName
    }

    fun performGetApiRequest(accessToken: String): Int {
        val fullUrl = "$site/api/todolist"
        Log.d(TAG, "Requesting $fullUrl")

        val requestBuilder = Request.Builder()
            .url(fullUrl)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()

        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(TAG, "Request failed with code: ${response.code}")
                throw IllegalStateException("Network request failed with code: ${response.code}")
            }

            return response.code
        }
    }
}
