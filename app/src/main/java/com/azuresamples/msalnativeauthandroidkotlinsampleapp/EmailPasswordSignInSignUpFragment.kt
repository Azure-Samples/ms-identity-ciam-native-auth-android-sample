package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.text.set
import androidx.fragment.app.Fragment
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentEmailPasswordBinding
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.common.java.util.StringUtil
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication
import com.microsoft.identity.nativeauth.statemachine.results.Result
import com.microsoft.identity.nativeauth.statemachine.results.SignInResult
import com.microsoft.identity.nativeauth.statemachine.results.SignInUsingPasswordResult
import com.microsoft.identity.nativeauth.statemachine.results.SignOutResult
import com.microsoft.identity.nativeauth.statemachine.results.SignUpResult
import com.microsoft.identity.nativeauth.statemachine.results.SignUpUsingPasswordResult
import com.microsoft.identity.nativeauth.statemachine.states.AccountResult
import com.microsoft.identity.nativeauth.statemachine.states.SignInAfterSignUpState
import com.microsoft.identity.nativeauth.statemachine.states.SignUpCodeRequiredState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EmailPasswordSignInSignUpFragment : Fragment() {

    private lateinit var authClient: INativeAuthPublicClientApplication
    private var _binding: FragmentEmailPasswordBinding? = null
    private val binding get() = _binding!!

    companion object {
        private val TAG = EmailPasswordSignInSignUpFragment::class.java.simpleName
        private enum class STATUS { SignedIn, SignedOut }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEmailPasswordBinding.inflate(inflater, container, false)
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
                val password = CharArray(binding.passwordText.length());
                binding.passwordText.text?.getChars(0, binding.passwordText.length(), password, 0);

                val actionResult: SignInUsingPasswordResult
                try {
                    actionResult = authClient.signInUsingPassword(
                        username = email,
                        password = password
                    )
                } finally {
                    binding.passwordText.text?.clear();
                    StringUtil.overwriteWithNull(password);
                }

                when (actionResult) {
                    is SignInResult.Complete -> {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.sign_in_successful_message),
                            Toast.LENGTH_SHORT
                        ).show()
                        displaySignedInState(accountResult = actionResult.resultValue)
                    }
                    is SignInResult.CodeRequired -> {
                        displayDialog(getString(R.string.sign_in_switch_to_otp))
                    }
                    is SignInResult.InvalidCredentials,
                    is SignInResult.UserNotFound,
                    is SignInResult.UnexpectedError,
                    is SignInResult.BrowserRequired -> {
                        displayDialog((actionResult as Result.ErrorResult).error.errorMessage)
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
                val password = CharArray(binding.passwordText.length());
                binding.passwordText.text?.getChars(0, binding.passwordText.length(), password, 0);

                val actionResult: SignUpUsingPasswordResult

                try {
                    actionResult = authClient.signUpUsingPassword(
                        username = email,
                        password = password
                    )
                } finally {
                    binding.passwordText.text?.set(0, binding.passwordText.text?.length?.minus(1) ?: 0, 0);
                    StringUtil.overwriteWithNull(password);
                }

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
                    is SignUpUsingPasswordResult.AuthNotSupported,
                    is SignUpResult.InvalidEmail,
                    is SignUpResult.InvalidPassword,
                    is SignUpResult.UnexpectedError,
                    is SignUpResult.BrowserRequired -> {
                        displayDialog((actionResult as Result.ErrorResult).error.errorMessage)
                    }
                    is SignUpResult.AttributesRequired,
                    is SignUpResult.InvalidAttributes -> {
                        displayDialog("Unexpected result: $actionResult")
                    }
                }
            } catch (exception: MsalException) {
                displayDialog(exception.message.toString())
            }
        }
    }

    private suspend fun signInAfterSignUp(nextState: SignInAfterSignUpState) {
        val currentState = nextState
        val actionResult = currentState.signIn()
        when (actionResult) {
            is SignInResult.Complete -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.sign_in_successful_message),
                    Toast.LENGTH_SHORT
                ).show()
                displaySignedInState(accountResult = actionResult.resultValue)
            }
            is SignInResult.BrowserRequired,
            is SignInResult.CodeRequired,
            is SignInResult.PasswordRequired,
            is SignInResult.UserNotFound,
            is SignInResult.UnexpectedError -> {
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
        binding.emailText.setText("")
        binding.passwordText.setText("")
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
