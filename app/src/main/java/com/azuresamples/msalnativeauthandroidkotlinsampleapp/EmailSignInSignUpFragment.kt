package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentEmailSisuBinding
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication
import com.microsoft.identity.nativeauth.statemachine.errors.GetAccessTokenError
import com.microsoft.identity.nativeauth.statemachine.errors.GetAccountError
import com.microsoft.identity.nativeauth.statemachine.errors.SignInContinuationError
import com.microsoft.identity.nativeauth.statemachine.errors.SignInError
import com.microsoft.identity.nativeauth.statemachine.errors.SignUpError
import com.microsoft.identity.nativeauth.statemachine.results.GetAccessTokenResult
import com.microsoft.identity.nativeauth.statemachine.results.GetAccountResult
import com.microsoft.identity.nativeauth.statemachine.results.SignInResult
import com.microsoft.identity.nativeauth.statemachine.results.SignOutResult
import com.microsoft.identity.nativeauth.statemachine.results.SignUpResult
import com.microsoft.identity.nativeauth.statemachine.states.AccountState
import com.microsoft.identity.nativeauth.statemachine.states.SignInContinuationState
import com.microsoft.identity.nativeauth.statemachine.states.SignInCodeRequiredState
import com.microsoft.identity.nativeauth.statemachine.states.SignUpCodeRequiredState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EmailSignInSignUpFragment : Fragment() {
    private lateinit var authClient: INativeAuthPublicClientApplication
    private var _binding: FragmentEmailSisuBinding? = null
    private val binding get() = _binding!!

    companion object {
        private val TAG = EmailSignInSignUpFragment::class.java.simpleName
        private enum class STATUS { SignedIn, SignedOut }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEmailSisuBinding.inflate(inflater, container, false)
        val view = binding.root

        authClient = AuthClient.getAuthClient()

        init()

        return view
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

        binding.signUp.setOnClickListener {
            signUp()
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

            val actionResult = authClient.signIn(
                username = email
            )

            when (actionResult) {
                is SignInResult.CodeRequired -> {
                    navigateToSignIn(
                        signInstate = actionResult.nextState
                    )
                }
                is SignInResult.PasswordRequired -> {
                    displayDialog(getString(R.string.unexpected_sdk_result_title), actionResult.toString())
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

    private fun signUp() {
        CoroutineScope(Dispatchers.Main).launch {
            val email = binding.emailText.text.toString()

            val actionResult = authClient.signUp(
                username = email
            )

            when (actionResult) {
                is SignUpResult.CodeRequired -> {
                    navigateToSignUp(
                        nextState = actionResult.nextState
                    )
                }
                is SignUpResult.Complete -> {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.sign_up_successful_message),
                        Toast.LENGTH_SHORT
                    ).show()
                    signInAfterSignUp(
                        nextState = actionResult.nextState
                    )
                }
                is SignUpResult.AttributesRequired,
                is SignUpResult.PasswordRequired -> {
                    displayDialog(getString(R.string.unexpected_sdk_result_title), actionResult.toString())
                }
                is SignUpError -> {
                    handleSignUpError(actionResult)
                }
            }
        }
    }


    private suspend fun signInAfterSignUp(nextState: SignInContinuationState) {
        val actionResult = nextState.signIn()
        when (actionResult) {
            is SignInResult.Complete -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.sign_in_successful_message),
                    Toast.LENGTH_SHORT
                ).show()
                displaySignedInState(accountState = actionResult.resultValue)
            }
            is SignInContinuationError -> {
                displayDialog(getString(R.string.msal_exception_title), actionResult.exception?.message)
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

    private fun displaySignedInState(accountState: AccountState) {
        emptyFields()
        updateUI(STATUS.SignedIn)
        displayAccount(accountState)
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
                binding.signUp.isEnabled = false
                binding.signOut.isEnabled = true
            }
            STATUS.SignedOut -> {
                binding.signIn.isEnabled = true
                binding.signUp.isEnabled = true
                binding.signOut.isEnabled = false
            }
        }
    }

    private fun emptyFields() {
        binding.emailText.setText(/* text = */ "")
    }

    private fun emptyResults() {
        binding.resultAccessToken.text = ""
        binding.resultIdToken.text = ""
    }

    private fun displayAccount(accountState: AccountState) {
        CoroutineScope(Dispatchers.Main).launch {
            val accessTokenResult = accountState.getAccessToken()
            when (accessTokenResult) {
                is GetAccessTokenResult.Complete -> {
                    val accessToken = accessTokenResult.resultValue.accessToken
                    binding.resultAccessToken.text = getString(R.string.result_access_token_text) + accessToken

                    val idToken = accountState.getIdToken()
                    binding.resultIdToken.text = getString(R.string.result_id_token_text) + idToken
                }
                is GetAccessTokenError -> {
                    displayDialog(getString(R.string.msal_exception_title), accessTokenResult.exception?.message)
                }
            }
        }
    }

    private fun handleSignInError(error: SignInError) {
        when {
            error.isUserNotFound() || error.isBrowserRequired() -> {
                displayDialog(error.error, error.errorMessage)
            }
            else -> {
                // Unexpected error
                displayDialog(getString(R.string.unexpected_sdk_result_title), error.exception?.message)
            }
        }
    }

    private fun handleSignUpError(error: SignUpError) {
        when {
            error.isUserAlreadyExists() || error.isInvalidUsername() || error.isInvalidAttributes()
                    || error.isBrowserRequired() -> {
                displayDialog(error.error, error.errorMessage)
            }
            else -> {
                // Unexpected error
                displayDialog(getString(R.string.unexpected_sdk_error_title), error.exception?.message)
            }
        }
    }

    private fun displayDialog(error: String? = null, message: String?) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(error)
            .setMessage(message)
        val alertDialog = builder.create()
        alertDialog.show()
    }

    private fun navigateToSignIn(signInstate: SignInCodeRequiredState) {
        val bundle = Bundle()
        bundle.putParcelable(Constants.STATE, signInstate)
        val fragment = SignInCodeFragment()
        fragment.arguments = bundle

        requireActivity().supportFragmentManager
            .beginTransaction()
            .setReorderingAllowed(true)
            .addToBackStack(fragment::class.java.name)
            .replace(R.id.scenario_fragment, fragment)
            .commit()
    }

    private fun navigateToSignUp(nextState: SignUpCodeRequiredState) {
        val bundle = Bundle()
        bundle.putParcelable(Constants.STATE, nextState)
        val fragment = SignUpCodeFragment()
        fragment.arguments = bundle

        requireActivity().supportFragmentManager
            .beginTransaction()
            .setReorderingAllowed(true)
            .addToBackStack(fragment::class.java.name)
            .replace(R.id.scenario_fragment, fragment)
            .commit()
    }
}
