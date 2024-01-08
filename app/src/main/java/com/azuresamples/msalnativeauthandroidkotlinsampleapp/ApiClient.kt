package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.DateFormat
import java.util.Date


object ApiClient {
    private val client = OkHttpClient()
    private const val BASE_URL = "https://todolistapi20231027124634.azurewebsites.net/api/"
    private val TAG = ApiClient::class.java.simpleName

    data class ApiResponse(val message: String)

    private fun performApiRequest(url: String, accessToken: String, method: String, requestBody: RequestBody? = null): ApiResponse {
        val fullUrl = "$BASE_URL$url"
        Log.d(TAG,"Requesting $fullUrl with method $method")

        val requestBuilder = Request.Builder()
            .url(fullUrl)
            .addHeader("Authorization", "Bearer $accessToken")

        when (method) {
            "GET" -> requestBuilder.get()
            "POST" -> requestBuilder.post(requestBody!!)
        }

        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(TAG, "Request failed with code: ${response.code}")
                throw IllegalStateException("Network request failed with code: ${response.code}")
            }

            val responseBody = response.body?.string() ?: throw IllegalStateException("Empty response body")
            Log.d(TAG, "Response: $responseBody")
            return ApiResponse(responseBody)
        }
    }

    fun getAllToDoListItems(accessToken: String): ApiResponse {
        return performApiRequest("/todolist", accessToken, "GET")
    }

    fun getToDoListItem(accessToken: String, id: Int): ApiResponse {
        return performApiRequest("/todolist/$id", accessToken, "GET")
    }

    fun postTodoListItem(accessToken: String): ApiResponse {
        val currentDate = getCurrentDate()
        val jsonData = "{\"Description\": \"$currentDate\"}"
        val requestBody: RequestBody = jsonData.toRequestBody("application/json".toMediaTypeOrNull())

        return performApiRequest("/todolist", accessToken, "POST", requestBody)
    }

    private fun getCurrentDate(): String {
        val dateFormat = DateFormat.getDateInstance()
        return dateFormat.format(Date())
    }
}
