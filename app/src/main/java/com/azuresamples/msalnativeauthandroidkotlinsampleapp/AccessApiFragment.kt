package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentAccessApiBinding
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.common.java.util.StringUtil
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication
import com.microsoft.identity.nativeauth.statemachine.errors.SignInUsingPasswordError
import com.microsoft.identity.nativeauth.statemachine.results.GetAccessTokenResult
import com.microsoft.identity.nativeauth.statemachine.results.GetAccountResult
import com.microsoft.identity.nativeauth.statemachine.results.SignInResult
import com.microsoft.identity.nativeauth.statemachine.results.SignInUsingPasswordResult
import com.microsoft.identity.nativeauth.statemachine.results.SignOutResult
import com.microsoft.identity.nativeauth.statemachine.states.AccountState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccessApiFragment : Fragment() {
    private lateinit var authClient: INativeAuthPublicClientApplication
    private var _binding: FragmentAccessApiBinding? = null
    private val binding get() = _binding!!

    companion object {
        private val TAG = AccessApiFragment::class.java.simpleName
        private enum class STATUS { SignedIn, SignedOut }
        private const val WEB_API_BASE_URL = "" // Developers should set the respective URL of their web API here
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAccessApiBinding.inflate(inflater, container, false)

        (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.title_access_web_api)

        authClient = AuthClient.getAuthClient()

        init()

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        getStateAndUpdateUI()
    }

    private fun init() {
        initializeButtonListeners()
    }

    private fun initializeButtonListeners() {
        binding.signIn.setOnClickListener {
            signIn()
        }

        binding.getApi.setOnClickListener {
            accessWebAPI()
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
            }
        }
    }

    private fun signIn() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val email = binding.emailText.text.toString()
                val password = CharArray(binding.passwordText.length())
                binding.passwordText.text?.getChars(0, binding.passwordText.length(), password, 0)

                val actionResult: SignInUsingPasswordResult
                try {
                    actionResult = authClient.signInUsingPassword(
                        username = email,
                        password = password,
                        scopes = listOf() // Developers should set the respective scopes of their web API here
                    )
                } finally {
                    binding.passwordText.text?.clear()
                    StringUtil.overwriteWithNull(password)
                }

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
                    is SignInUsingPasswordError -> {
                        handleSignInError(actionResult)
                    }
                }
            } catch (exception: MsalException) {
                displayDialog(getString(R.string.msal_exception_title), exception.message.toString())
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
                    displayDialog(getString(R.string.unexpected_sdk_result_title), signOutResult.toString())
                }
            }
        }
    }

    private fun accessWebAPI() {
        CoroutineScope(Dispatchers.Main).launch {
            val accountResult = authClient.getCurrentAccount()
            when (accountResult) {
                is GetAccountResult.AccountFound -> {
                    val accessTokenState = accountResult.resultValue.getAccessToken()
                    if (accessTokenState is GetAccessTokenResult.Complete) {
                        val accessToken = accessTokenState.resultValue.accessToken
                        try {
                            if (WEB_API_BASE_URL.isBlank()) {
                                displayDialog(getString(R.string.invalid_web_url_title), getString(R.string.invalid_web_url_message))
                                return@launch
                            }
                            val apiResponseCode = withContext(Dispatchers.IO) {
                                ApiClient.performGetApiRequest(WEB_API_BASE_URL, accessToken)
                            }
                            binding.requestResponse.text = getString(R.string.response_code) + apiResponseCode
                        } catch (e: Exception) {
                            displayDialog(getString(R.string.network_request_exception_titile), e.message ?: getString(R.string.unknown_error_message))
                        }
                    }
                }
                is GetAccountResult.NoAccountFound -> {
                    displaySignedOutState()
                }
            }
        }
    }

    private fun handleSignInError(error: SignInUsingPasswordError) {
        when {
            error.isInvalidCredentials() || error.isBrowserRequired() || error.isUserNotFound() -> {
                displayDialog(error.error, error.errorMessage)
            }
            else -> {
                // Unexpected error
                displayDialog(getString(R.string.unexpected_sdk_error_title), error.toString())
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
                binding.getApi.isEnabled = true
            }
            STATUS.SignedOut -> {
                binding.signIn.isEnabled = true
                binding.signOut.isEnabled = false
                binding.getApi.isEnabled = false
            }
        }
    }

    private fun emptyResults() {
        binding.resultAccessToken.text = ""
        binding.requestResponse.text = ""
    }

    private fun displayAccount(accountState: AccountState) {
        CoroutineScope(Dispatchers.Main).launch {
            val accessTokenState = accountState.getAccessToken()
            if (accessTokenState is GetAccessTokenResult.Complete) {
                val accessToken = accessTokenState.resultValue.accessToken
                binding.resultAccessToken.text = accessToken
            }
        }
    }

    private fun displayDialog(error: String? = null, message: String?) {
        Log.w(TAG, "$message")
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(error)
            .setMessage(message)
        val alertDialog = builder.create()
        alertDialog.show()
    }
}
