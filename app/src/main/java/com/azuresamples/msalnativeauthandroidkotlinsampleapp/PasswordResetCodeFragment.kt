package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentCodeBinding
import com.microsoft.identity.nativeauth.statemachine.states.ResetPasswordCodeRequiredState
import com.microsoft.identity.nativeauth.statemachine.errors.ResendCodeError
import com.microsoft.identity.nativeauth.statemachine.errors.SubmitCodeError
import com.microsoft.identity.nativeauth.statemachine.errors.NativeAuthErrorV2
import com.microsoft.identity.nativeauth.statemachine.errors.SubmitCodeErrorV2
import com.microsoft.identity.nativeauth.statemachine.results.NativeAuthResultV2
import com.microsoft.identity.nativeauth.statemachine.results.ResetPasswordResendCodeResult
import com.microsoft.identity.nativeauth.statemachine.results.ResetPasswordSubmitCodeResult
import com.microsoft.identity.nativeauth.statemachine.states.ResetPasswordPasswordRequiredState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PasswordResetCodeFragment : Fragment() {
    private var currentState: ResetPasswordCodeRequiredState? = null
    private lateinit var authManager: AuthManager
    private var _binding: FragmentCodeBinding? = null
    private val binding get() = _binding!!

    companion object {
        private val TAG = PasswordResetCodeFragment::class.java.simpleName
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCodeBinding.inflate(inflater, container, false)
        val view = binding.root

        val bundle = this.arguments
        if (Configuration.useNativeAuthV2) {
            authManager = AuthClient.getAuthManager()
        } else {
            currentState = (bundle?.getParcelable(Constants.STATE) as? ResetPasswordCodeRequiredState)!!
        }

        init()

        return view
    }

    private fun init() {
        initializeButtonListeners()
    }

    private fun initializeButtonListeners() {
        binding.verifyCode.setOnClickListener {
            submitCode()
        }

        binding.resendCodeText.setOnClickListener {
            resendCode()
        }
    }

    private fun submitCode() {
        CoroutineScope(Dispatchers.Main).launch {
            val code = binding.codeText.text.toString()

            if (Configuration.useNativeAuthV2) {
                submitCodeV2(code)
                return@launch
            }

            val actionResult = currentState!!.submitCode(code)

            when (actionResult) {
                is ResetPasswordSubmitCodeResult.PasswordRequired -> {
                    navigateToResetPasswordPasswordFragment(
                        nextState = actionResult.nextState
                    )
                }
                is SubmitCodeError -> {
                    handleError(actionResult)
                }
            }
        }
    }

    private suspend fun submitCodeV2(code: String) {
        when (val result = authManager.submitCode(code)) {
            is NativeAuthResultV2.NewPasswordRequired -> {
                navigateToNewPasswordFragmentV2()
            }
            is SubmitCodeErrorV2 -> {
                handleSubmitCodeErrorV2(result)
            }
            is NativeAuthErrorV2 -> {
                handleGenericErrorV2(result)
            }
            null -> {
                displayDialog(getString(R.string.unexpected_sdk_result_title), result.toString())
            }
            else -> {
                displayDialog(getString(R.string.unexpected_sdk_result_title), result.toString())
            }
        }
    }

    private fun handleSubmitCodeErrorV2(error: SubmitCodeErrorV2) {
        when {
            error.isBrowserRequired() || error.isInvalidCode() -> {
                displayDialog(error.error, error.errorMessage)
            }
            else -> {
                // Unexpected error
                displayDialog(getString(R.string.unexpected_sdk_error_title), error.exception?.message ?: error.errorMessage)
            }
        }
    }

    private fun handleGenericErrorV2(error: NativeAuthErrorV2) {
        when {
            error.isNotImplemented() || error.isBrowserRequired() -> {
                displayDialog(error.error ?: getString(R.string.unexpected_sdk_error_title), error.errorMessage)
            }
            else -> {
                // Unexpected error
                displayDialog(getString(R.string.unexpected_sdk_error_title), error.exception?.message ?: error.errorMessage)
            }
        }
    }

    private fun resendCode() {
        clearCode()

        CoroutineScope(Dispatchers.Main).launch {
            if (Configuration.useNativeAuthV2) {
                when (val result = authManager.resendCode()) {
                    is NativeAuthResultV2.CodeRequired -> {
                        Toast.makeText(requireContext(), getString(R.string.resend_code_message), Toast.LENGTH_LONG).show()
                    }
                    is NativeAuthErrorV2 -> {
                        displayDialog(getString(R.string.msal_exception_title), result.errorMessage)
                    }
                    null -> {
                        displayDialog(getString(R.string.unexpected_sdk_result_title), result.toString())
                    }
                    else -> {
                        displayDialog(getString(R.string.unexpected_sdk_result_title), result.toString())
                    }
                }
                return@launch
            }

            val actionResult = currentState!!.resendCode()

            when (actionResult) {
                is ResetPasswordResendCodeResult.Success -> {
                    currentState = actionResult.nextState
                    Toast.makeText(requireContext(), getString(R.string.resend_code_message), Toast.LENGTH_LONG).show()
                }
                is ResendCodeError -> {
                    displayDialog(getString(R.string.msal_exception_title), actionResult.exception?.message ?: actionResult.errorMessage)
                }
            }
        }
    }

    private fun clearCode() {
        binding.codeText.text?.clear()
    }

    private fun handleError(error: SubmitCodeError) {
        when {
            error.isBrowserRequired() || error.isInvalidCode() -> {
                displayDialog(error.error, error.errorMessage)
            }
            else -> {
                // Unexpected error
                displayDialog(getString(R.string.unexpected_sdk_error_title), error.exception?.message ?: error.errorMessage)
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

    private fun navigateToResetPasswordPasswordFragment(nextState: ResetPasswordPasswordRequiredState) {
        val bundle = Bundle()
        bundle.putParcelable(Constants.STATE, nextState)
        val fragment = PasswordResetNewPasswordFragment()
        fragment.arguments = bundle

        requireActivity().supportFragmentManager
            .beginTransaction()
            .setReorderingAllowed(true)
            .addToBackStack(fragment::class.java.name)
            .replace(R.id.scenario_fragment, fragment)
            .commit()
    }

    private fun navigateToNewPasswordFragmentV2() {
        val bundle = Bundle()
        val fragment = PasswordResetNewPasswordFragment()
        fragment.arguments = bundle

        requireActivity().supportFragmentManager
            .beginTransaction()
            .setReorderingAllowed(true)
            .addToBackStack(fragment::class.java.name)
            .replace(R.id.scenario_fragment, fragment)
            .commit()
    }
}
