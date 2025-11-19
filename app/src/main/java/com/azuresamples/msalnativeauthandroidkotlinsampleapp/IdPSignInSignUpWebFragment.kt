package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentIdpSignInSignUpWebBinding
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.Prompt
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication
import com.microsoft.identity.nativeauth.statemachine.errors.GetAccountError
import com.microsoft.identity.nativeauth.statemachine.results.GetAccountResult
import com.microsoft.identity.nativeauth.statemachine.results.SignOutResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class IdPSignInSignUpWebFragment : Fragment() {
    private lateinit var authClient: INativeAuthPublicClientApplication
    private var accountResult: IAccount? = null
    private var _binding: FragmentIdpSignInSignUpWebBinding? = null
    private val binding get() = _binding!!

    companion object {
        private val TAG = IdPSignInSignUpWebFragment::class.java.simpleName

        private enum class STATUS { SignedIn, SignedOut }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIdpSignInSignUpWebBinding.inflate(inflater, container, false)

        (activity as? AppCompatActivity)?.supportActionBar?.title =
            getString(R.string.title_idp_web_flow)

        authClient = AuthClient.getAuthClient()

        setupIdpDropdown()
        initializeButtonListeners()

        return binding.root
    }

    private fun setupIdpDropdown() {
        val domainHints = listOf(
            "Google",
            "Apple",
            "Facebook",
            "www.linkedin.com" // Custom OIDC
        )
        val adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, domainHints)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.idpDropdown.adapter = adapter
    }

    private fun initializeButtonListeners() {
        binding.signIn.setOnClickListener {
            val domainHint = binding.idpDropdown.selectedItem.toString()
            acquireTokenInteractively(domainHint, Prompt.LOGIN)
        }

        binding.signUp.setOnClickListener {
            val domainHint = binding.idpDropdown.selectedItem.toString()
            acquireTokenInteractively(domainHint, Prompt.CREATE)
        }

        binding.signOut.setOnClickListener {
            signOut()
        }
    }

    private fun acquireTokenInteractively(domainHint: String, prompt: Prompt) {
        val acquireTokenParametersBuilder = AcquireTokenParameters.Builder()
            .startAuthorizationFromActivity(requireActivity())
            .withScopes(mutableListOf("openid", "profile", "email"))
            .withDomainHint(domainHint)
            .withPrompt(prompt)
            .withCallback(getAuthInteractiveCallback())
        authClient.acquireToken(AcquireTokenParameters(acquireTokenParametersBuilder))
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

                displayAccount()
                displaySignedInState()

                Toast.makeText(
                    requireContext(),
                    getString(R.string.sign_in_successful_message),
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onError(exception: MsalException) {
                Log.e(TAG, "Authentication failed: $exception")
                displayDialog(
                    getString(R.string.msal_exception_title),
                    "Authentication failed: $exception"
                )
            }

            override fun onCancel() {
                Log.d(TAG, "User cancelled authentication")
                Toast.makeText(requireContext(), "Authentication cancelled", Toast.LENGTH_SHORT)
                    .show()
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
                    displayDialog(
                        getString(R.string.unexpected_sdk_result_title),
                        signOutResult.toString()
                    )
                }
            }
        }
    }

    private fun displaySignedInState() {
        updateUI(STATUS.SignedIn)
        displayAccount()
    }

    private fun displaySignedOutState() {
        updateUI(STATUS.SignedOut)
        emptyResults()
    }

    private fun updateUI(status: STATUS) {
        when (status) {
            STATUS.SignedIn -> {
                binding.idpDropdown.isEnabled = false
                binding.signIn.isEnabled = false
                binding.signUp.isEnabled = false
                binding.signOut.isEnabled = true
            }

            STATUS.SignedOut -> {
                binding.idpDropdown.isEnabled = true
                binding.signIn.isEnabled = true
                binding.signUp.isEnabled = true
                binding.signOut.isEnabled = false
            }
        }
    }

    private fun emptyResults() {
        binding.resultAccessToken.text = ""
        binding.resultIdToken.text = ""
    }

    private fun displayAccount() {
        binding.resultIdToken.text =
            getString(R.string.result_id_token_text) + accountResult?.idToken
    }

    private fun displayDialog(error: String? = null, message: String?) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(error)
            .setMessage(message)
        val alertDialog = builder.create()
        alertDialog.show()
    }

    override fun onResume() {
        super.onResume()
        getStateAndUpdateUI()
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

                is GetAccountError -> {
                    displayDialog(
                        getString(R.string.msal_exception_title),
                        accountResult.exception?.message ?: accountResult.errorMessage
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

