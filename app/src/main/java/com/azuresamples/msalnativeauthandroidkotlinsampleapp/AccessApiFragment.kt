package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentAccessApiBinding
import com.microsoft.identity.common.java.util.StringUtil
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication
import com.microsoft.identity.nativeauth.statemachine.errors.GetAccountError
import com.microsoft.identity.nativeauth.statemachine.errors.SignInError
import com.microsoft.identity.nativeauth.statemachine.results.GetAccessTokenResult
import com.microsoft.identity.nativeauth.statemachine.results.GetAccountResult
import com.microsoft.identity.nativeauth.statemachine.results.SignInResult
import com.microsoft.identity.nativeauth.statemachine.results.SignOutResult
import com.microsoft.identity.nativeauth.statemachine.states.AccountState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Response

/**
 * AccessApiFragment class implements samples for accessing custom web APIs using Entra External ID identity tokens.
 * Learn documentation: https://learn.microsoft.com/en-us/entra/external-id/customers/sample-native-authentication-android-sample-app-call-web-api
 */
class AccessApiFragment : Fragment() {
    private lateinit var authClient: INativeAuthPublicClientApplication
    private var _binding: FragmentAccessApiBinding? = null
    private val binding get() = _binding!!

    companion object {
        private val TAG = AccessApiFragment::class.java.simpleName
        private enum class STATUS { SignedIn, SignedOut }
        private const val WEB_API_URL_1 = "" // Developers should set the URL of their first web API resource here
        private const val WEB_API_URL_2 = "" // Developers should set the URL of their second web API resource here
        // Developers should set the respective scopes for their web API resources here, for example: ["api://<Resource_App_ID>/ToDoList.Read", "api://<Resource_App_ID>/ToDoList.ReadWrite"]
        private val scopesForAPI1 = listOf<String>()
        private val scopesForAPI2 = listOf<String>()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAccessApiBinding.inflate(inflater, container, false)

        (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.title_access_web_api)

        authClient = AuthClient.getAuthClient()

        init()

        getStateAndUpdateUI()

        return binding.root
    }

    private fun init() {
        initializeButtonListeners()
    }

    private fun initializeButtonListeners() {
        binding.signIn.setOnClickListener {
            signIn()
        }

        binding.getApi1.setOnClickListener {
            accessWebAPIAndUpdateUI(WEB_API_URL_1, scopesForAPI1)
        }

        binding.getApi2.setOnClickListener {
            accessWebAPIAndUpdateUI(WEB_API_URL_2, scopesForAPI2)
        }

        binding.signOut.setOnClickListener {
            signOut()
        }
    }

    private fun getStateAndUpdateUI() {
        CoroutineScope(Dispatchers.Main).launch {
            val accountResult = authClient.getCurrentAccount()
            when (accountResult) {
                is GetAccountResult.AccountFound -> {
                    displaySignedInState(accountResult.resultValue)
                }
                is GetAccountResult.NoAccountFound -> {
                    displaySignedOutState()
                }
                is GetAccountError -> {
                    displayDialog(getString(R.string.msal_exception_title), accountResult.exception?.message)
                }
            }
        }
    }

    private fun signIn() {
        CoroutineScope(Dispatchers.Main).launch {
            val email = binding.emailText.text.toString()
            val password = CharArray(binding.passwordText.length())
            binding.passwordText.text?.getChars(0, binding.passwordText.length(), password, 0)

            val actionResult: SignInResult = authClient.signIn(
                username = email,
                password = password
            )
            binding.passwordText.text?.clear()
            StringUtil.overwriteWithNull(password)

            when (actionResult) {
                is SignInResult.Complete -> {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.sign_in_successful_message),
                        Toast.LENGTH_SHORT
                    ).show()
                    displaySignedInState(accountState = actionResult.resultValue)
                }
                is SignInResult.CodeRequired -> {
                    displayDialog(message = getString(R.string.sign_in_switch_to_otp_message))
                }
                is SignInResult.MFARequired -> {
                    // Please refer to the MFA Fragment for handling MFA branches if conditional access - MFA is enabled.
                    displayDialog(getString(R.string.unexpected_sdk_result_title), actionResult.toString())
                }
                is SignInError -> {
                    handleSignInError(actionResult)
                }
            }
        }
    }

    private fun signOut() {
        CoroutineScope(Dispatchers.Main).launch {
            val getAccountResult = authClient.getCurrentAccount()
            if (getAccountResult is GetAccountResult.AccountFound) {
                val signOutResult = getAccountResult.resultValue.signOut()
                if (signOutResult is SignOutResult.Complete) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.sign_out_successful_message),
                        Toast.LENGTH_SHORT
                    ).show()
                    displaySignedOutState()
                } else {
                    // Unexpected result
                    displayDialog(getString(R.string.unexpected_sdk_result_title), signOutResult.toString())
                }
            }
        }
    }

    private suspend fun getAccessToken(accountState: AccountState, scopes: List<String>): String {
        val accessTokenState = accountState.getAccessToken(false, scopes)
        return if (accessTokenState is GetAccessTokenResult.Complete) {
            accessTokenState.resultValue.accessToken
        } else {
            throw Exception("Failed to get access token")
        }
    }

    private suspend fun useAccessToken(WEB_API_URL: String, accessToken: String): Response {
        return withContext(Dispatchers.IO) {
            ApiClient.performGetApiRequest(WEB_API_URL, accessToken)
        }
    }

    private fun accessWebAPIAndUpdateUI(webApiUrl: String, scopes: List<String>) {
        if (webApiUrl.isBlank()) {
            displayDialog(getString(R.string.invalid_web_url_title), getString(R.string.invalid_web_url_message))
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            val accountResult = authClient.getCurrentAccount()
            when (accountResult) {
                is GetAccountResult.AccountFound -> {
                    try {
                        val accessToken = getAccessToken(accountResult.resultValue, scopes)
                        val apiResponse = useAccessToken(webApiUrl, accessToken)
                        binding.result.text = getString(R.string.result_access_token_of_scopes_text)  + scopes.toString()
                        binding.resultText.text = getString(R.string.response_api) + apiResponse.toString()
                    } catch (e: Exception) {
                        displayDialog(getString(R.string.network_request_exception_titile), e.message ?: getString(R.string.unknown_error_message))
                    }
                }
                is GetAccountResult.NoAccountFound -> {
                    displaySignedOutState()
                }
                is GetAccountError -> {
                    displayDialog(getString(R.string.msal_exception_title), accountResult.exception?.message ?: accountResult.errorMessage)
                }
            }
        }
    }

    private fun handleSignInError(error: SignInError) {
        when {
            error.isInvalidCredentials() || error.isBrowserRequired() || error.isUserNotFound() -> {
                displayDialog(error.error, error.errorMessage)
            }
            else -> {
                // Unexpected error
                displayDialog(getString(R.string.unexpected_sdk_error_title), error.errorMessage)
            }
        }
    }

    private fun displaySignedInState(accountState: AccountState) {
        updateUI(STATUS.SignedIn)
        displayAccount(accountState)
    }

    private fun displaySignedOutState() {
        updateUI(STATUS.SignedOut)
        emptyResults()
    }

    private fun updateUI(status: STATUS) {
        when (status) {
            STATUS.SignedIn -> {
                binding.signIn.isEnabled = false
                binding.signOut.isEnabled = true
                binding.getApi1.isEnabled = true
                binding.getApi2.isEnabled = true
            }
            STATUS.SignedOut -> {
                binding.signIn.isEnabled = true
                binding.signOut.isEnabled = false
                binding.getApi1.isEnabled = false
                binding.getApi2.isEnabled = false
            }
        }
    }

    private fun emptyResults() {
        binding.result.text = ""
        binding.resultText.text = ""
    }

    private fun displayAccount(accountState: AccountState) {
        CoroutineScope(Dispatchers.Main).launch {
            val username = accountState.getAccount().username
            binding.result.text = getString(R.string.result_account_text)
            binding.resultText.text = username
        }
    }

    private fun displayDialog(error: String? = null, message: String?) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(error)
            .setMessage(message)
        val alertDialog = builder.create()
        alertDialog.show()
    }
}
