package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentEmailPasswordBinding
import com.microsoft.identity.client.claims.ClaimsRequest
import com.microsoft.identity.nativeauth.AuthMethod
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication
import com.microsoft.identity.nativeauth.parameters.NativeAuthGetAccessTokenParameters
import com.microsoft.identity.nativeauth.parameters.NativeAuthSignInParameters
import com.microsoft.identity.nativeauth.statemachine.errors.GetAccessTokenError
import com.microsoft.identity.nativeauth.statemachine.errors.GetAccountError
import com.microsoft.identity.nativeauth.statemachine.errors.NativeAuthErrorV2
import com.microsoft.identity.nativeauth.statemachine.errors.SignInError
import com.microsoft.identity.nativeauth.statemachine.results.GetAccessTokenResult
import com.microsoft.identity.nativeauth.statemachine.results.GetAccountResult
import com.microsoft.identity.nativeauth.statemachine.results.NativeAuthResultV2
import com.microsoft.identity.nativeauth.statemachine.results.SignInResult
import com.microsoft.identity.nativeauth.statemachine.results.SignOutResult
import com.microsoft.identity.nativeauth.statemachine.states.AccountState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MFAFragment : Fragment() {

    private lateinit var authClient: INativeAuthPublicClientApplication
    private lateinit var authManager: AuthManager
    private var _binding: FragmentEmailPasswordBinding? = null
    private val binding get() = _binding!!

    companion object {
        private val TAG = MFAFragment::class.java.simpleName
        private enum class STATUS { SignedIn, SignedOut }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEmailPasswordBinding.inflate(inflater, container, false)
        val view = binding.root

        (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.title_mfa)

        authClient = AuthClient.getAuthClient()
        authManager = AuthClient.getAuthManager()

        init()

        return view
    }
    override fun onResume() {
        super.onResume()
        getStateAndUpdateUI()
    }

    private fun init() {
        binding.signUp.visibility = View.GONE

        initializeButtonListeners()
    }

    private fun initializeButtonListeners() {
        binding.signIn.setOnClickListener {
            signIn()
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
                    displayDialog(getString(R.string.msal_exception_title), accountResult.exception?.message ?: accountResult.errorMessage)
                }
            }
        }
    }

    private fun signIn() {
        CoroutineScope(Dispatchers.Main).launch {
            val email = binding.emailText.text.toString()
            val password = CharArray(binding.passwordText.length())
            binding.passwordText.text?.getChars(0, binding.passwordText.length(), password, 0)

            if (authManager.useV2APIs) {
                val result = authManager.signIn(email, password)
                binding.passwordText.text?.clear()
                password.fill('\u0000')
                handleSignInResultV2(result)
                return@launch
            }

            val parameters = NativeAuthSignInParameters(username = email)
            parameters.password = password
            val actionResult: SignInResult = authClient.signIn(parameters)
            binding.passwordText.text?.clear()
            password.fill('\u0000')

            when (actionResult) {
                is SignInResult.Complete -> {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.sign_in_successful_message),
                        Toast.LENGTH_SHORT
                    ).show()
                    displaySignedInState(accountState = actionResult.resultValue)
                }
                is SignInResult.MFARequired -> {
                    displayMFARequiredDialog(actionResult.nextState, actionResult.authMethods)
                }
                is SignInResult.StrongAuthMethodRegistrationRequired -> {
                    displayStrongAuthRequiredDialog(actionResult.nextState, actionResult.authMethods)
                }
                is SignInError -> {
                    handleSignInError(actionResult)
                }
                else -> {
                    displayDialog(getString(R.string.unexpected_sdk_result_title), actionResult.toString())
                }
            }
        }
    }

    private fun handleSignInResultV2(result: NativeAuthResultV2) {
        when (result) {
            is NativeAuthResultV2.Complete -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.sign_in_successful_message),
                    Toast.LENGTH_SHORT
                ).show()
                displaySignedInState(accountState = result.resultValue)
            }
            is NativeAuthResultV2.MFARequired -> {
                displayMFARequiredDialog(result.nextState, result.authMethods)
            }
            is NativeAuthResultV2.StrongAuthRegistrationRequired -> {
                displayStrongAuthRequiredDialog(result.nextState, result.authMethods)
            }
            is NativeAuthErrorV2 -> {
                displayDialog(result.error ?: getString(R.string.unexpected_sdk_error_title), result.errorMessage)
            }
            else -> {
                displayDialog(getString(R.string.unexpected_sdk_result_title), result.toString())
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
        binding.resultAccessToken.text = ""
        binding.resultIdToken.text = ""
    }

    private fun displayAccount(accountState: AccountState) {
        CoroutineScope(Dispatchers.Main).launch {
            val parameters = NativeAuthGetAccessTokenParameters()
            val accessTokenResult = accountState.getAccessToken(parameters)

            when (accessTokenResult) {
                is GetAccessTokenResult.Complete -> {
                    val accessToken = accessTokenResult.resultValue.accessToken
                    binding.resultAccessToken.text = getString(R.string.result_access_token_text) + accessToken

                    val idToken = accountState.getIdToken()
                    binding.resultIdToken.text = getString(R.string.result_id_token_text) + idToken
                }
                is GetAccessTokenError -> {
                    displayDialog(getString(R.string.msal_exception_title), accessTokenResult.exception?.message ?: accessTokenResult.errorMessage)
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
                displayDialog(getString(R.string.unexpected_sdk_error_title), error.exception?.message ?: error.errorMessage)
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

    private fun displayMFARequiredDialog(state: Parcelable, authMethods: List<AuthMethod>) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(R.string.mfa_required_notice)

        // If proceed, let the user pick which authentication method to challenge.
        builder.setPositiveButton(getString(R.string.yes_message)) { _, _ ->
            navigateToPickAuthMethod(state, ArrayList(authMethods))
        }

        // If not proceed
        builder.setNegativeButton(getString(R.string.cancel_message)) { dialog, _ ->
           dialog.dismiss()
        }

        builder.setCancelable(false)

        val dialog = builder.create()
        dialog.show()
    }

    private fun displayStrongAuthRequiredDialog(state: Parcelable, authMethods: List<AuthMethod>) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(R.string.strong_auth_method_title)
        builder.setMessage(R.string.strong_auth_method_message)

        // If proceed
        builder.setPositiveButton(getString(R.string.yes_message)) { _, _ ->
            navigateToPickAuthMethod(state, ArrayList(authMethods))
        }

        // If not proceed
        builder.setNegativeButton(getString(R.string.cancel_message)) { dialog, _ ->
            dialog.dismiss()
        }

        builder.setCancelable(false)

        val dialog = builder.create()
        dialog.show()
    }

    private fun navigateToPickAuthMethod(state: Parcelable, authMethods: ArrayList<AuthMethod>) {
        val bundle = Bundle()
        bundle.putParcelable(Constants.STATE, state)
        bundle.putSerializable(Constants.AUTH_METHOD_LIST, authMethods)

        val fragment = PickAuthMethodFragment()
        fragment.arguments = bundle

        requireActivity().supportFragmentManager
            .beginTransaction()
            .setReorderingAllowed(true)
            .addToBackStack(fragment::class.java.name)
            .replace(R.id.scenario_fragment, fragment)
            .commit()
    }
}
