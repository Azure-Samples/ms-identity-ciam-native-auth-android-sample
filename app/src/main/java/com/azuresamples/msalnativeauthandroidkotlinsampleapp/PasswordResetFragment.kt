package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentEmailSsprBinding
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.nativeauth.statemachine.results.ResetPasswordResult
import com.microsoft.identity.nativeauth.statemachine.results.ResetPasswordStartResult
import com.microsoft.identity.nativeauth.statemachine.results.Result
import com.microsoft.identity.nativeauth.statemachine.results.SignOutResult
import com.microsoft.identity.nativeauth.statemachine.states.AccountResult
import com.microsoft.identity.nativeauth.statemachine.states.ResetPasswordCodeRequiredState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PasswordResetFragment : Fragment() {
    private lateinit var authClient: INativeAuthPublicClientApplication
    private var _binding: FragmentEmailSsprBinding? = null
    private val binding get() = _binding!!

    companion object {
        private val TAG = PasswordResetFragment::class.java.simpleName
        private enum class STATUS { SignedIn, SignedOut }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEmailSsprBinding.inflate(inflater, container, false)
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
        binding.forgetPassword.setOnClickListener {
            forgetPassword()
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

    private fun forgetPassword() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val email = binding.emailText.text.toString()

                val actionResult = authClient.resetPassword(
                    username = email
                )
                when (actionResult) {
                    is ResetPasswordStartResult.CodeRequired -> {
                        navigateToResetPasswordCodeFragment(
                            nextState = actionResult.nextState
                        )
                    }
                    is ResetPasswordStartResult.UserNotFound,
                    is ResetPasswordResult.UnexpectedError, is ResetPasswordResult.BrowserRequired -> {
                        displayDialog((actionResult as Result.ErrorResult).error.errorMessage)
                    }
                }
            } catch (exception: MsalException) {
                displayDialog(exception.message.toString())
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
                binding.forgetPassword.isEnabled = false
                binding.signOut.isEnabled = true
            }
            STATUS.SignedOut -> {
                binding.forgetPassword.isEnabled = true
                binding.signOut.isEnabled = false
            }
        }
    }

    private fun emptyFields() {
        binding.emailText.setText("")
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

    private fun navigateToResetPasswordCodeFragment(nextState: ResetPasswordCodeRequiredState) {
        val bundle = Bundle()
        bundle.putSerializable(Constants.STATE, nextState)
        val fragment = PasswordResetCodeFragment()
        fragment.arguments = bundle

        requireActivity().supportFragmentManager
            .beginTransaction()
            .setReorderingAllowed(true)
            .addToBackStack(fragment::class.java.name)
            .replace(R.id.scenario_fragment, fragment)
            .commit()
    }
}
