package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.clearFragmentResult
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentCodeBinding
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.utils.AppUtil
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.utils.NavigationUtil
import com.microsoft.identity.nativeauth.statemachine.errors.ResendCodeError
import com.microsoft.identity.nativeauth.statemachine.errors.SubmitCodeError
import com.microsoft.identity.nativeauth.statemachine.results.SignInResendCodeResult
import com.microsoft.identity.nativeauth.statemachine.results.SignInResult
import com.microsoft.identity.nativeauth.statemachine.states.SignInCodeRequiredState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SignInCodeFragment : Fragment() {
    private lateinit var currentState: SignInCodeRequiredState
    private lateinit var appUtil: AppUtil
    private var _binding: FragmentCodeBinding? = null
    private val binding get() = _binding!!

    companion object {
        private val TAG = SignInCodeFragment::class.java.simpleName
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCodeBinding.inflate(inflater, container, false)

        val bundle = this.arguments
        currentState = (bundle?.getParcelable(NavigationUtil.STATE) as? SignInCodeRequiredState)!!

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
            val emailCode = binding.codeText.text.toString()

            val actionResult = currentState.submitCode(emailCode)
//            val actionResult = currentState.submitChallenge(emailCode)

            when (actionResult) {
                is SignInResult.Complete -> {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.sign_in_successful_message),
                        Toast.LENGTH_SHORT
                    ).show()
                    appUtil.navigation.finish()
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
                is SignInResendCodeResult.Success -> {
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
