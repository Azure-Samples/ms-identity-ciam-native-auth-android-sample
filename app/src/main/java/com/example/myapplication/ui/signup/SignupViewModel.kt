package com.example.myapplication.ui.signup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.AuthRepository
import kotlinx.coroutines.launch
import org.json.JSONObject

class SignupViewModel(private val repository: AuthRepository = AuthRepository()) : ViewModel() {

    private val _uiState = MutableLiveData<SignupUiState>(SignupUiState.Idle)
    val uiState: LiveData<SignupUiState> = _uiState

    var continuationToken: String? = null
    var sessionId: String? = null

    fun startSignup(username: String, password: String) {
        _uiState.value = SignupUiState.Loading("Starting sign-up...")
        viewModelScope.launch {
            val result = repository.signupStart(username, password)
            handleStartResult(result)
        }
    }

    private fun handleStartResult(result: String?) {
        if (result == null) {
            _uiState.value = SignupUiState.Error("Network request failed.")
            return
        }

        val json = try { JSONObject(result) } catch (_: Exception) { null }
        if (json == null) {
            _uiState.value = SignupUiState.Error("Invalid response from server.")
            return
        }

        val token = json.optString("continuation_token").ifEmpty { null }
        if (token != null) {
            continuationToken = token
            requestChallenge()
        } else {
            val error = json.optString("error_description", "Signup failed to start.")
            _uiState.value = SignupUiState.Error(error)
        }
    }

    private fun requestChallenge() {
        val token = continuationToken ?: return
        viewModelScope.launch {
            val result = repository.signupChallenge(token)
            handleChallengeResult(result)
        }
    }

    private fun handleChallengeResult(result: String?) {
        if (result == null) {
            _uiState.value = SignupUiState.Error("Challenge request failed.")
            return
        }

        val json = try { JSONObject(result) } catch (_: Exception) { null }
        val token = json?.optString("continuation_token")?.ifEmpty { null }

        if (token != null) {
            continuationToken = token
            _uiState.value = SignupUiState.OtpSent
        } else {
            val error = json?.optString("error_description", "Challenge failed.") ?: "Unknown error"
            _uiState.value = SignupUiState.Error(error)
        }
    }

    fun verifyOtp(username: String, otp: String) {
        val token = continuationToken ?: return
        _uiState.value = SignupUiState.Loading("Verifying OTP...")
        viewModelScope.launch {
            val result = repository.signupContinue(otp, token)
            handleContinueResult(username, result)
        }
    }

    private fun handleContinueResult(username: String, result: String?) {
        val json = try { JSONObject(result ?: "") } catch (_: Exception) { null }
        if (json == null || json.has("error")) {
            val error = json?.optString("error_description", "Invalid OTP.") ?: "Invalid OTP."
            _uiState.value = SignupUiState.Error(error)
            return
        }

        val token = json.optString("continuation_token").ifEmpty { null }
        if (token != null) {
            continuationToken = token
            fetchFinalToken(username)
        } else {
            _uiState.value = SignupUiState.Error("Failed to proceed after OTP.")
        }
    }

    private fun fetchFinalToken(username: String) {
        val token = continuationToken ?: return
        val sid = sessionId ?: return
        viewModelScope.launch {
            val result = repository.getSignupFinalToken(username, token, sid)
            val json = try { JSONObject(result ?: "") } catch (_: Exception) { null }
            val accessToken = json?.optString("access_token")

            if (accessToken != null) {
                _uiState.value = SignupUiState.Success(accessToken)
            } else {
                val error = json?.optString("error_description", "Failed to acquire token.") ?: "Failed to acquire token."
                _uiState.value = SignupUiState.Error(error)
            }
        }
    }
}

sealed class SignupUiState {
    object Idle : SignupUiState()
    data class Loading(val message: String) : SignupUiState()
    object OtpSent : SignupUiState()
    data class Success(val accessToken: String) : SignupUiState()
    data class Error(val message: String) : SignupUiState()
}
