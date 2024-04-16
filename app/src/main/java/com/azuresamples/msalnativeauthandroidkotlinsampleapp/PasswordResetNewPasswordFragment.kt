package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.text.set
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentPasswordBinding
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.common.java.util.StringUtil
import com.microsoft.identity.nativeauth.statemachine.errors.ClientExceptionError
import com.microsoft.identity.nativeauth.statemachine.errors.ResetPasswordSubmitPasswordError
import com.microsoft.identity.nativeauth.statemachine.errors.SignInContinuationError
import com.microsoft.identity.nativeauth.statemachine.results.ResetPasswordResult
import com.microsoft.identity.nativeauth.statemachine.results.ResetPasswordSubmitPasswordResult
import com.microsoft.identity.nativeauth.statemachine.results.SignInResult
import com.microsoft.identity.nativeauth.statemachine.states.ResetPasswordPasswordRequiredState
import com.microsoft.identity.nativeauth.statemachine.states.SignInContinuationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PasswordResetNewPasswordFragment : Fragment() {
    private lateinit var currentState: ResetPasswordPasswordRequiredState
    private var _binding: FragmentPasswordBinding? = null
    private val binding get() = _binding!!

    companion object {
        private val TAG = PasswordResetNewPasswordFragment::class.java.simpleName
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPasswordBinding.inflate(inflater, container, false)
        val view = binding.root

        val bundle = this.arguments
        currentState = (bundle?.getParcelable(Constants.STATE) as? ResetPasswordPasswordRequiredState)!!

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

            val actionResult: ResetPasswordSubmitPasswordResult
            try {
                actionResult = currentState.submitPassword(password)
            } finally {
                binding.passwordText.text?.set(0, binding.passwordText.text?.length?.minus(1) ?: 0, 0)
                StringUtil.overwriteWithNull(password)
            }

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
                is ClientExceptionError -> {
                    displayDialog(getString(R.string.msal_exception_title), actionResult.exception?.message.toString())
                }
            }
        }
    }

    private suspend fun signInAfterPasswordReset(nextState: SignInContinuationState) {
        val currentState = nextState
        val actionResult = currentState.signIn()
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
                displayDialog(getString(R.string.unexpected_sdk_error_title), actionResult.toString())
            }
            is ClientExceptionError -> {
                displayDialog(getString(R.string.msal_exception_title), actionResult.exception?.message.toString())
            }
            else -> {
                displayDialog(getString(R.string.unexpected_sdk_result_title), actionResult.toString())
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
                displayDialog(getString(R.string.unexpected_sdk_error_title), error.toString())
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
        val fragmentManager = requireActivity().supportFragmentManager
        val name: String? = fragmentManager.getBackStackEntryAt(0).name
        fragmentManager.popBackStack(name, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }
}
