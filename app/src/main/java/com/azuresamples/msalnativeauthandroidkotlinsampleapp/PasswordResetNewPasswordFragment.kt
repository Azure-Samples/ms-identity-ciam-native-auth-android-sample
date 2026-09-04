package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentPasswordBinding
import com.microsoft.identity.nativeauth.parameters.NativeAuthSignInContinuationParameters
import com.microsoft.identity.nativeauth.statemachine.errors.ResetPasswordSubmitPasswordError
import com.microsoft.identity.nativeauth.statemachine.errors.SignInContinuationError
import com.microsoft.identity.nativeauth.statemachine.results.ResetPasswordResult
import com.microsoft.identity.nativeauth.statemachine.results.ResetPasswordSubmitPasswordResult
import com.microsoft.identity.nativeauth.statemachine.results.SignInResult
import com.microsoft.identity.nativeauth.statemachine.states.ResetPasswordPasswordRequiredState
import com.microsoft.identity.nativeauth.statemachine.states.SignInContinuationState
import com.microsoft.identity.nativeauth.statemachine.errors.NativeAuthErrorV2
import com.microsoft.identity.nativeauth.statemachine.errors.SubmitNewPasswordErrorV2
import com.microsoft.identity.nativeauth.statemachine.results.NativeAuthResultV2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PasswordResetNewPasswordFragment : Fragment() {
    private var currentState: ResetPasswordPasswordRequiredState? = null
    private lateinit var authManager: AuthManager
    private var _binding: FragmentPasswordBinding? = null
    private val binding get() = _binding!!

    companion object {
        private val TAG = PasswordResetNewPasswordFragment::class.java.simpleName
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPasswordBinding.inflate(inflater, container, false)
        val view = binding.root

        val bundle = this.arguments
        if (Configuration.useNativeAuthV2) {
            authManager = AuthClient.getAuthManager()
        } else {
            currentState = (bundle?.getParcelable(Constants.STATE) as? ResetPasswordPasswordRequiredState)!!
        }

        init()

        return view
    }

    private fun init() {
        initializeButtonListener()
    }

    private fun initializeButtonListener() {
        binding.create.setOnClickListener {
            resetPassword()
        }
    }

    private fun resetPassword() {
        CoroutineScope(Dispatchers.Main).launch {
            val password = CharArray(binding.passwordText.length())
            binding.passwordText.text?.getChars(0, binding.passwordText.length(), password, 0)

            if (Configuration.useNativeAuthV2) {
                resetPasswordV2(password)
                binding.passwordText.text?.clear()
                password.fill('\u0000')
                return@launch
            }

            val actionResult: ResetPasswordSubmitPasswordResult = currentState!!.submitPassword(password)
            binding.passwordText.text?.clear()
            password.fill('\u0000')

            when (actionResult) {
                is ResetPasswordResult.Complete -> {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.password_reset_success_message),
                        Toast.LENGTH_LONG
                    ).show()
                    signInAfterPasswordReset(
                        nextState = actionResult.nextState
                    )
                }
                is ResetPasswordSubmitPasswordError -> {
                    handleError(actionResult)
                }
            }
        }
    }

    private suspend fun resetPasswordV2(password: CharArray) {
        when (val result = authManager.submitNewPassword(password)) {
            is NativeAuthResultV2.Complete -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.password_reset_success_message),
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
            is NativeAuthResultV2.SignInAfterResetPasswordRequired -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.password_reset_success_message),
                    Toast.LENGTH_LONG
                ).show()
                signInAfterPasswordResetV2()
            }
            is SubmitNewPasswordErrorV2 -> {
                handleSubmitNewPasswordErrorV2(result)
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

    private fun handleSubmitNewPasswordErrorV2(error: SubmitNewPasswordErrorV2) {
        when {
            error.isInvalidPassword() || error.isPasswordResetFailed() || error.isBrowserRequired() -> {
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

    private suspend fun signInAfterPasswordResetV2() {
        when (val result = authManager.signInAfterPasswordReset()) {
            is NativeAuthResultV2.Complete -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.sign_in_successful_message),
                    Toast.LENGTH_LONG
                ).show()
                finish()
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

    private suspend fun signInAfterPasswordReset(nextState: SignInContinuationState) {
        val parameters = NativeAuthSignInContinuationParameters()
        val actionResult = nextState.signIn(parameters)

        when (actionResult) {
            is SignInResult.Complete -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.sign_in_successful_message),
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
            is SignInContinuationError -> {
                displayDialog(getString(R.string.msal_exception_title), actionResult.exception?.message ?: actionResult.errorMessage)
            }
        }
    }

    private fun handleError(error: ResetPasswordSubmitPasswordError) {
        when {
            error.isInvalidPassword() || error.isPasswordResetFailed() -> {
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

    private fun finish() {
        // Pop back to PasswordResetFragment fragment
        val fragmentManager = requireActivity().supportFragmentManager
        val name: String = PasswordResetFragment::class.java.name
        fragmentManager.popBackStack(name, 0)
    }
}
