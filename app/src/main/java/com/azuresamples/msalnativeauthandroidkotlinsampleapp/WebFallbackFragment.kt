package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.set
import androidx.fragment.app.Fragment
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentWebFallbackBinding
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.nativeauth.statemachine.errors.SignInError
import com.microsoft.identity.nativeauth.statemachine.results.GetAccountResult
import com.microsoft.identity.nativeauth.statemachine.results.SignInUsingPasswordResult
import com.microsoft.identity.nativeauth.statemachine.results.SignOutResult
import com.microsoft.identity.common.java.util.StringUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WebFallbackFragment : Fragment() {
    private lateinit var authClient: INativeAuthPublicClientApplication
    private var accountResult: IAccount? = null
    private var _binding: FragmentWebFallbackBinding? = null
    private val binding get() = _binding!!

    companion object {
        private val TAG = WebFallbackFragment::class.java.simpleName
        private enum class STATUS { SignedIn, SignedOut }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWebFallbackBinding.inflate(inflater, container, false)

        (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.title_web_fallback)

        authClient = AuthClient.getAuthClient()

        initializeButtonListeners()

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        getStateAndUpdateUI()
    }

    private fun initializeButtonListeners() {
        binding.signIn.setOnClickListener {
            signIn()
        }

        binding.signUp.visibility = View.GONE

        binding.signOut.setOnClickListener {
            signOut()
        }
    }

    private fun getStateAndUpdateUI() {
        CoroutineScope(Dispatchers.Main).launch {
            val accountResult = authClient.getCurrentAccount()
            when (accountResult) {
                is GetAccountResult.AccountFound -> {
                    displaySignedInState()
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
                        password = password
                    )
                } finally {
                    binding.passwordText.text?.set(
                        0,
                        binding.passwordText.text?.length?.minus(1) ?: 0,
                        0
                    )
                    StringUtil.overwriteWithNull(password)
                }

                if (actionResult is SignInError && actionResult.isBrowserRequired()) {
                    Toast.makeText(requireContext(), actionResult.errorMessage, Toast.LENGTH_SHORT)
                        .show()

                    authClient.acquireToken(
                        AcquireTokenParameters(
                            AcquireTokenParameters.Builder()
                                .startAuthorizationFromActivity(requireActivity())
                                .withScopes(mutableListOf("profile", "openid", "email"))
                                .withCallback(getAuthInteractiveCallback())
                        )
                    )
                } else {
                    displayDialog("Unexpected result", actionResult.toString())
                }

            } catch (exception: MsalException) {
                displayDialog(getString(R.string.msal_exception_title), exception.message.toString())
            }
        }
    }

    /**
     * Callback used for interactive request.
     * If succeeds we use the access token to call the Microsoft Graph.
     * Does not check cache.
     */
    private fun getAuthInteractiveCallback(): AuthenticationCallback {
        return object : AuthenticationCallback {

            override fun onSuccess(authenticationResult: IAuthenticationResult) {
                /* Successfully got a token, use it to call a protected resource - MSGraph */
                Log.d(TAG, "Successfully authenticated")
                Log.d(TAG, "ID Token: " + authenticationResult.account.claims?.get("id_token"))

                accountResult = authenticationResult.account

                /* Update account */
                displayAccount()
                displaySignedInState()

                Toast.makeText(requireContext(), getString(R.string.sign_in_successful_message), Toast.LENGTH_SHORT).show()
            }

            override fun onError(exception: MsalException) {
                /* Failed to acquireToken */
                displayDialog(getString(R.string.msal_exception_title),"Authentication failed: $exception")
            }

            override fun onCancel() {
                // Do nothing
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
                    displayDialog("Unexpected result", signOutResult.toString())
                }
            }
        }
    }

    private fun displaySignedInState() {
        emptyFields()
        updateUI(STATUS.SignedIn)
        displayAccount()
    }

    private fun displaySignedOutState() {
        emptyFields()
        updateUI(STATUS.SignedOut)
        emptyResults()
    }

    private fun updateUI(status: STATUS) {
        when (status) {
            STATUS.SignedIn -> {
                binding.signIn.isEnabled = false
                binding.signOut.isEnabled = true
            }
            STATUS.SignedOut -> {
                binding.signIn.isEnabled = true
                binding.signOut.isEnabled = false
            }
        }
    }

    private fun emptyFields() {
        binding.emailText.setText("")
        binding.passwordText.setText("")
    }

    private fun emptyResults() {
        binding.resultAccessToken.setText("")
        binding.resultIdToken.setText("")
    }

    private fun displayAccount() {
        binding.resultIdToken.text = getString(R.string.result_id_token_text) + accountResult?.idToken
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
