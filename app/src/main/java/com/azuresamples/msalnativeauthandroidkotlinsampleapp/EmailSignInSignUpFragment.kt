package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentEmailSisuBinding
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.nativeauth.statemachine.results.Result
import com.microsoft.identity.nativeauth.statemachine.results.SignInResult
import com.microsoft.identity.nativeauth.statemachine.results.SignOutResult
import com.microsoft.identity.nativeauth.statemachine.results.SignUpResult
import com.microsoft.identity.nativeauth.statemachine.states.AccountResult
import com.microsoft.identity.nativeauth.statemachine.states.SignInAfterSignUpState
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
            if (accountResult == null) {
                displaySignedOutState()
            } else {
                displaySignedInState(accountResult)
            }
        }
    }

    private fun signIn() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
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
                    is SignInResult.UserNotFound,
                    is SignInResult.UnexpectedError,
                    is SignInResult.BrowserRequired -> {
                        displayDialog((actionResult as Result.ErrorResult).error.errorMessage)
                    }
                    is SignInResult.Complete,
                    is SignInResult.PasswordRequired -> {
                        displayDialog("Unexpected result: $actionResult")
                    }
                }
            } catch (exception: MsalException) {
                displayDialog(exception.message.toString())
            }
        }
    }

    private fun signUp() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
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
                    is SignUpResult.UserAlreadyExists,
                    is SignUpResult.InvalidEmail,
                    is SignUpResult.UnexpectedError,
                    is SignUpResult.BrowserRequired,
                    is SignUpResult.AttributesRequired,
                    is SignUpResult.InvalidAttributes,
                    is SignUpResult.InvalidPassword,
                    is SignUpResult.PasswordRequired -> {
                        displayDialog((actionResult as Result.ErrorResult).error.errorMessage)
                    }
                }
            } catch (exception: MsalException) {
                displayDialog(exception.message.toString())
            }
        }
    }

    private suspend fun signInAfterSignUp(nextState: SignInAfterSignUpState) {
        val actionResult = nextState.signIn()
        when (actionResult) {
            is SignInResult.Complete -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.sign_in_successful_message),
                    Toast.LENGTH_SHORT
                ).show()
                displaySignedInState(accountResult = actionResult.resultValue)
            }
            else -> {
                displayDialog("Unexpected result: $actionResult")
            }
        }
    }

    private fun signOut() {
        CoroutineScope(Dispatchers.Main).launch {
            val signOutResult = authClient.getCurrentAccount()?.signOut()
            if (signOutResult is SignOutResult.Complete) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.sign_out_successful_message),
                    Toast.LENGTH_SHORT
                ).show()
                displaySignedOutState()
            } else {
                displayDialog("Unexpected result: $signOutResult")
            }
        }
    }

    private fun displaySignedInState(accountResult: AccountResult) {
        emptyFields()
        updateUI(STATUS.SignedIn)
        displayAccount(accountResult)
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

    private fun displayAccount(accountResult: AccountResult) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val accessToken = accountResult.getAccessToken()?.accessToken
                binding.resultAccessToken.text =
                    getString(R.string.result_access_token_text) + accessToken

                val idToken = accountResult.getIdToken()
                binding.resultIdToken.text = getString(R.string.result_id_token_text) + idToken
            } catch (exception: Exception) {
                displayDialog(exception.message.toString())
            }
        }
    }

    private fun displayDialog(message: String?) {
        Log.w(TAG, "$message")
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(getString(R.string.msal_exception_title))
            .setMessage(message)
        val alertDialog = builder.create()
        alertDialog.show()
    }

    private fun navigateToSignIn(signInstate: SignInCodeRequiredState) {
        val bundle = Bundle()
        bundle.putSerializable(Constants.STATE, signInstate)
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
        bundle.putSerializable(Constants.STATE, nextState)
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
