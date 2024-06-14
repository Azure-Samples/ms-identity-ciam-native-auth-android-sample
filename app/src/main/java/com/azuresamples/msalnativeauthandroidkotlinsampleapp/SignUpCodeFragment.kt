package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentCodeBinding
import com.microsoft.identity.nativeauth.statemachine.errors.ResendCodeError
import com.microsoft.identity.nativeauth.statemachine.errors.SignInContinuationError
import com.microsoft.identity.nativeauth.statemachine.errors.SubmitCodeError
import com.microsoft.identity.nativeauth.statemachine.results.SignInResult
import com.microsoft.identity.nativeauth.statemachine.results.SignUpResendCodeResult
import com.microsoft.identity.nativeauth.statemachine.results.SignUpResult
import com.microsoft.identity.nativeauth.statemachine.states.SignInContinuationState
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
        currentState = (bundle?.getParcelable(Constants.STATE) as? SignUpCodeRequiredState)!!

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
            val oobCode = binding.codeText.text.toString()

            val actionResult = currentState.submitCode(oobCode)

            when (actionResult) {
                is SignUpResult.Complete -> {
                    Toast.makeText(requireContext(), getString(R.string.sign_up_successful_message), Toast.LENGTH_SHORT).show()
                    signInAfterSignUp(
                        nextState = actionResult.nextState
                    )
                }
                is SignUpResult.AttributesRequired,
                is SignUpResult.PasswordRequired -> {
                    displayDialog(getString(R.string.unexpected_sdk_result_title), actionResult.toString())
                }
                is SubmitCodeError -> {
                    handleSubmitError(actionResult)
                }
            }
        }
    }

    private suspend fun signInAfterSignUp(nextState: SignInContinuationState) {
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
            is SignInContinuationError -> {
                displayDialog(getString(R.string.msal_exception_title), actionResult.errorMessage)
            }
            is SignInResult.CodeRequired,
            is SignInResult.PasswordRequired -> {
                displayDialog(getString(R.string.unexpected_sdk_result_title), actionResult.toString())
            }
        }
    }

    private fun resendCode() {
        clearCode()

        CoroutineScope(Dispatchers.Main).launch {
            val actionResult = currentState.resendCode()

            when (actionResult) {
                is SignUpResendCodeResult.Success -> {
                    currentState = actionResult.nextState
                    Toast.makeText(requireContext(), getString(R.string.resend_code_message), Toast.LENGTH_LONG).show()
                }
                is ResendCodeError -> {
                    displayDialog(getString(R.string.unexpected_sdk_error_title), actionResult.errorMessage)
                }
            }
        }
    }

    private fun clearCode() {
        binding.codeText.text?.clear()
    }

    private fun handleSubmitError(error: SubmitCodeError) {
        when {
            error.isBrowserRequired() || error.isInvalidCode() -> {
                displayDialog(error.error, error.errorMessage)
            }
            else -> {
                // Unexpected error
                displayDialog(getString(R.string.unexpected_sdk_error_title), error.errorMessage)
            }
        }
    }

    private fun displayDialog(error: String?, message: String?) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(error)
            .setMessage(message)
        val alertDialog = builder.create()
        alertDialog.show()
    }

    private fun finish() {
        requireActivity().supportFragmentManager.popBackStackImmediate()
    }
}
