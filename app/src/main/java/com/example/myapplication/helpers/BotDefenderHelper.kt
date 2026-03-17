package com.example.myapplication.helpers

import android.util.Log
import com.humansecurity.mobile_sdk.HumanSecurity
import com.humansecurity.mobile_sdk.main.HSBotDefenderErrorType
import okhttp3.Response

object BotDefenderHelper {
    private const val TAG = "SANJIB_NATIVE"

    fun handleApiError(response: Response, responseBody: String) {
        when (HumanSecurity.BD.errorType(responseBody)) {
            HSBotDefenderErrorType.REQUEST_WAS_BLOCKED -> {
                Log.d(TAG, "request was blocked by HUMAN")
            }
            HSBotDefenderErrorType.CHALLENGE_WAS_SOLVED -> {
                Log.d(TAG, "request was blocked by HUMAN and user solved the challenge")
            }
            HSBotDefenderErrorType.CHALLENGE_WAS_CANCELLED -> {
                Log.d(TAG, "request was blocked by HUMAN and challenge was cancelled")
            }
            else -> {
                Log.e(TAG, "API error: ${response.code}")
            }
        }
    }
}
