package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody


object ApiClient {
    private lateinit var apiClient: ApiClient
    private val client = OkHttpClient()
    private const val BASE_URL = "https://todolistapi20231027124634.azurewebsites.net/api/"

    data class ApiResponse(val message: String)

    private fun performApiRequest(url: String, accessToken: String, method: String, requestBody: RequestBody? = null): ApiResponse {
        val requestBuilder = Request.Builder()
            .url("$BASE_URL$url")
            .addHeader("Authorization", "Bearer $accessToken")

        when (method) {
            "GET" -> requestBuilder.get()
            "POST" -> requestBuilder.post(requestBody!!)
        }

        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Network request failed with code: ${response.code}")
            }

            val responseBody = response.body?.string() ?: throw IllegalStateException("Empty response body")
            return ApiResponse(responseBody)
        }
    }

    fun getToDoListItem(accessToken: String, id: Int): ApiResponse {
        return performApiRequest("/todolist/$id", accessToken, "GET")
    }

    fun postTodoListItem(accessToken: String, description: String): ApiResponse {
        val jsonData = "{\"Description\": \"$description\"}"
        val requestBody: RequestBody = jsonData.toRequestBody("application/json".toMediaTypeOrNull())

        return performApiRequest("/todolist", accessToken, "POST", requestBody)
    }
}
