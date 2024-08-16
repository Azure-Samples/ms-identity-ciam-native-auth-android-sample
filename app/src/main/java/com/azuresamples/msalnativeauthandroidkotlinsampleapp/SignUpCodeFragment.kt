package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentCodeBinding
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.utils.AppUtil
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.utils.NavigationUtil
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
    private lateinit var appUtil: AppUtil
    private var _binding: FragmentCodeBinding? = null
    private val binding get() = _binding!!

    companion object {
        private val TAG = SignUpCodeFragment::class.java.simpleName
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCodeBinding.inflate(inflater, container, false)

        val bundle = this.arguments
        currentState = (bundle?.getParcelable(NavigationUtil.STATE) as? SignUpCodeRequiredState)!!

        appUtil = AppUtil(requireContext(), requireActivity())

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
                    appUtil.errorHandler.handleUnexpectedError(actionResult.toString())
                }
                is SubmitCodeError -> {
                    appUtil.errorHandler.handleSubmitCodeError(actionResult)
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
                appUtil.navigation.finish()
            }
            is SignInContinuationError -> {
                appUtil.errorHandler.handleSignInContinuationError(actionResult)
            }
            is SignInResult.CodeRequired,
            is SignInResult.PasswordRequired -> {
                appUtil.errorHandler.handleUnexpectedError(actionResult.toString())
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
                    appUtil.errorHandler.handleResendCodeError(actionResult)
                }
            }
        }
    }

    private fun clearCode() {
        binding.codeText.text?.clear()
    }
}
