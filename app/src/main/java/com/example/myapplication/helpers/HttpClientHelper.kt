package com.example.myapplication.helpers

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

object HttpClientHelper {
    private const val TAG = "SAMPLE_CODE"
    val client = OkHttpClient()

    fun createRequest(url: String, formBody: FormBody, headers: Map<String, String> = emptyMap()): Request {
        val requestBuilder = Request.Builder()
            .url(url)
            .post(formBody)
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
        
        headers.forEach { (name, value) ->
            requestBuilder.addHeader(name, value)
        }
        
        return requestBuilder.build()
    }

    suspend fun executeRequest(request: Request, httpClient: OkHttpClient = client): String? {
        val response = executeFullRequest(request, httpClient)
        return response?.use {
            val body = it.body?.string() ?: ""
            if (!it.isSuccessful) {
                Log.e(TAG, "Request failed for ${request.url} with code ${it.code}")
            }
            body
        }
    }

    suspend fun executeFullRequest(request: Request, httpClient: OkHttpClient = client): Response? {
        return withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(request).execute()
            } catch (e: IOException) {
                Log.e(TAG, "Request failed for ${request.url}", e)
                null
            }
        }
    }
}
