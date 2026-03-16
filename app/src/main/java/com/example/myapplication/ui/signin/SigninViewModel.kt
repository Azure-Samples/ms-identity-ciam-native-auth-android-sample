package com.example.myapplication.ui.signin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.AuthRepository
import kotlinx.coroutines.launch
import org.json.JSONObject

class SigninViewModel(private val repository: AuthRepository = AuthRepository()) : ViewModel() {

    private val _uiState = MutableLiveData<SigninUiState>(SigninUiState.Idle)
    val uiState: LiveData<SigninUiState> = _uiState

    var continuationToken: String? = null
    var sessionId: String? = null
    var mfaMethodId: String? = null

    fun initiateSignin(username: String) {
        _uiState.value = SigninUiState.Loading("Starting sign-in...")
        viewModelScope.launch {
            val result = repository.initiateSignin(username)
            handleInitiateResponse(result)
        }
    }

    private fun handleInitiateResponse(result: String?) {
        if (result == null) {
            _uiState.value = SigninUiState.Error("Initiate call failed.")
            return
        }
        val json = try { JSONObject(result) } catch (_: Exception) { null }
        val token = json?.optString("continuation_token", null)

        if (token != null) {
            continuationToken = token
            requestChallenge()
        } else {
            _uiState.value = SigninUiState.Error(json?.optString("error_description", "Unknown error") ?: "Unknown error")
        }
    }

    private fun requestChallenge() {
        viewModelScope.launch {
            val result = repository.requestSigninChallenge(continuationToken!!)
            val json = try { JSONObject(result ?: "") } catch (_: Exception) { null }
            continuationToken = json?.optString("continuation_token", null)

            if (continuationToken != null) {
                _uiState.value = SigninUiState.ReadyForPassword
            } else {
                _uiState.value = SigninUiState.Error("Challenge failed.")
            }
        }
    }

    fun submitPassword(username: String, pwd: String) {
        _uiState.value = SigninUiState.Loading("Verifying password...")
        viewModelScope.launch {
            val response = repository.submitSigninPassword(username, pwd, continuationToken!!, sessionId!!)
            val body = response?.body?.string() ?: ""
            handleTokenResponse(username, pwd, response?.code ?: 0, body)
        }
    }

    private fun handleTokenResponse(username: String, pwd: String, code: Int, body: String) {
        // TODO: Report authentication status for each scenario to  to LNR (Update API)
        when (code) {
            200 -> {
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                _uiState.value = SigninUiState.Success(json?.optString("access_token") ?: "")
            }
            202 -> initiateMFA(username, pwd)
            else -> {
                val error = try { JSONObject(body).optString("error_description") } catch (_: Exception) { null } ?: "Sign in failed."
                _uiState.value = SigninUiState.Error(error)
            }
        }
    }

    private fun initiateMFA(username: String, pwd: String) {
        viewModelScope.launch {
            val response = repository.initiateMFA(username, pwd, continuationToken!!, sessionId!!)
            val body = response?.body?.string() ?: ""
            val json = try { JSONObject(body) } catch (_: Exception) { null }
            continuationToken = json?.optString("continuation_token", null)

            if (continuationToken != null) {
                handleIntrospect()
            } else {
                _uiState.value = SigninUiState.Error("MFA Step failed.")
            }
        }
    }

    private fun handleIntrospect() {
        viewModelScope.launch {
            val response = repository.handleIntrospect(continuationToken!!)
            val body = response?.body?.string() ?: ""
            val json = try { JSONObject(body) } catch (_: Exception) { null }
            continuationToken = json?.optString("continuation_token", continuationToken)
            
            val methods = json?.optJSONArray("methods")
            if (methods != null && methods.length() > 0) {
                mfaMethodId = methods.getJSONObject(0).optString("id")
                requestMFAChallenge()
            } else {
                _uiState.value = SigninUiState.Error("No MFA methods found.")
            }
        }
    }

    private fun requestMFAChallenge() {
        viewModelScope.launch {
            val response = repository.requestMFAChallenge(mfaMethodId!!, continuationToken!!)
            val body = response?.body?.string() ?: ""
            val json = try { JSONObject(body) } catch (_: Exception) { null }
            continuationToken = json?.optString("continuation_token", null)

            if (continuationToken != null) {
                _uiState.value = SigninUiState.MfaOtpSent
            } else {
                _uiState.value = SigninUiState.Error("MFA Challenge failed.")
            }
        }
    }

    fun verifyMFAOTP(username: String, otp: String) {
        _uiState.value = SigninUiState.Loading("Finalizing sign-in...")
        // TODO: Report MFA verification status for each scenario to LNR (Update API)

        viewModelScope.launch {
            val response = repository.getFinalAccessTokenUsingOTP(otp, username, continuationToken!!, sessionId!!)
            val body = response?.body?.string() ?: ""
            if (response != null && response.isSuccessful) {
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                _uiState.value = SigninUiState.Success(json?.optString("access_token") ?: "")
            } else {
                _uiState.value = SigninUiState.Error("Final verification failed.")
            }
        }
    }
}

sealed class SigninUiState {
    object Idle : SigninUiState()
    data class Loading(val message: String) : SigninUiState()
    object ReadyForPassword : SigninUiState()
    object MfaOtpSent : SigninUiState()
    data class Success(val accessToken: String) : SigninUiState()
    data class Error(val message: String) : SigninUiState()
}
