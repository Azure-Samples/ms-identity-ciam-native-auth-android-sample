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
import com.microsoft.identity.nativeauth.statemachine.states.ResetPasswordCodeRequiredState
import com.microsoft.identity.nativeauth.statemachine.errors.ResendCodeError
import com.microsoft.identity.nativeauth.statemachine.errors.SubmitCodeError
import com.microsoft.identity.nativeauth.statemachine.results.ResetPasswordResendCodeResult
import com.microsoft.identity.nativeauth.statemachine.results.ResetPasswordSubmitCodeResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PasswordResetCodeFragment : Fragment() {
    private lateinit var currentState: ResetPasswordCodeRequiredState
    private lateinit var appUtil: AppUtil
    private var _binding: FragmentCodeBinding? = null
    private val binding get() = _binding!!

    companion object {
        private val TAG = PasswordResetCodeFragment::class.java.simpleName
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCodeBinding.inflate(inflater, container, false)
        val view = binding.root

        val bundle = this.arguments
        currentState = (bundle?.getParcelable(NavigationUtil.STATE) as? ResetPasswordCodeRequiredState)!!

        appUtil = AppUtil(requireContext(), requireActivity())

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

            val actionResult = currentState.submitCode(code)

            when (actionResult) {
                is ResetPasswordSubmitCodeResult.PasswordRequired -> {
                    appUtil.navigation.navigateToResetPasswordPasswordFragment(
                        nextState = actionResult.nextState
                    )
                }
                is SubmitCodeError -> {
                    appUtil.errorHandler.handleSubmitCodeError(actionResult)
                }
            }
        }
    }

    private fun resendCode() {
        clearCode()

        CoroutineScope(Dispatchers.Main).launch {
            val actionResult = currentState.resendCode()

            when (actionResult) {
                is ResetPasswordResendCodeResult.Success -> {
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
