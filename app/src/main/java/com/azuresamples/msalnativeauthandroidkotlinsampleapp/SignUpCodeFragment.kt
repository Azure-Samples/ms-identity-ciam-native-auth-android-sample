package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentCodeBinding
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.nativeauth.statemachine.results.Result
import com.microsoft.identity.nativeauth.statemachine.results.SignInResult
import com.microsoft.identity.nativeauth.statemachine.results.SignUpResendCodeResult
import com.microsoft.identity.nativeauth.statemachine.results.SignUpResult
import com.microsoft.identity.nativeauth.statemachine.results.SignUpSubmitCodeResult
import com.microsoft.identity.nativeauth.statemachine.states.SignInAfterSignUpState
import com.microsoft.identity.nativeauth.statemachine.states.SignUpCodeRequiredState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SignUpCodeFragment : Fragment() {
    private lateinit var currentState: SignUpCodeRequiredState
    private var _binding: FragmentCodeBinding? = null
    private val binding get() = _binding!!

    companion object {
        private val TAG = SignUpCodeFragment::class.java.simpleName
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCodeBinding.inflate(inflater, container, false)

        val bundle = this.arguments
        currentState = bundle!!.getSerializable(Constants.STATE) as SignUpCodeRequiredState

        init()

        return binding.root
    }

    private fun init() {
        initializeButtonListeners()
    }

    private fun initializeButtonListeners() {
        binding.verifyCode.setOnClickListener {
            verifyCode()
        }

        binding.resendCodeText.setOnClickListener {
            resendCode()
        }
    }

    private fun verifyCode() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val oobCode = binding.codeText.text.toString()

                val actionResult = currentState.submitCode(oobCode)

                when (actionResult) {
                    is SignUpResult.Complete -> {
                        Toast.makeText(requireContext(), getString(R.string.sign_up_successful_message), Toast.LENGTH_SHORT).show()
                        signInAfterSignUp(
                            nextState = actionResult.nextState
                        )
                    }
                    is SignUpSubmitCodeResult.CodeIncorrect,
                    is SignUpResult.BrowserRequired -> {
                        displayDialog((actionResult as Result.ErrorResult).error.errorMessage)
                        clearCode()
                    }
                    is SignUpResult.AttributesRequired,
                    is SignUpResult.PasswordRequired,
                    is SignUpResult.UnexpectedError -> {
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
        val actionResult = currentState.signIn(null)
        when (actionResult) {
            is SignInResult.Complete -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.sign_in_successful_message),
                    Toast.LENGTH_SHORT
                ).show()
                finish()
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

    private fun resendCode() {
        clearCode()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val actionResult = currentState.resendCode()

                when (actionResult) {
                    is SignUpResendCodeResult.Success -> {
                        currentState = actionResult.nextState
                        Toast.makeText(requireContext(), getString(R.string.resend_code_message), Toast.LENGTH_LONG).show()
                    }
                    is SignUpResult.BrowserRequired, is SignUpResult.UnexpectedError -> {
                        displayDialog((actionResult as Result.ErrorResult).error.errorMessage)
                    }
                }
            } catch (exception: MsalException) {
                displayDialog(exception.message.toString())
            }
        }
    }

    private fun clearCode() {
        binding.codeText.text?.clear()
    }

    private fun displayDialog(message: String?) {
        Log.w(TAG, "$message")

        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(getString(R.string.msal_exception_title))
            .setMessage(message)
        val alertDialog = builder.create()
        alertDialog.show()
    }

    private fun finish() {
        requireActivity().supportFragmentManager.popBackStackImmediate()
    }
}
