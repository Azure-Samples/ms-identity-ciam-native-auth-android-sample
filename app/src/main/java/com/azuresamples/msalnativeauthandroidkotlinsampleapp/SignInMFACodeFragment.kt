package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentMfaCodeBinding
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.utils.AppUtil
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.utils.NavigationUtil
import com.microsoft.identity.nativeauth.AuthMethod
import com.microsoft.identity.nativeauth.statemachine.errors.MFAError
import com.microsoft.identity.nativeauth.statemachine.errors.SubmitChallengeError
import com.microsoft.identity.nativeauth.statemachine.results.SignInResult
import com.microsoft.identity.nativeauth.statemachine.states.MFARequiredState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SignInMFACodeFragment : Fragment() {
    private lateinit var currentState: MFARequiredState
    private lateinit var authMethod: AuthMethod
    private lateinit var appUtil: AppUtil
    private var _binding: FragmentMfaCodeBinding? = null
    private val binding get() = _binding!!

    companion object {
        private val TAG = SignInMFACodeFragment::class.java.simpleName
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMfaCodeBinding.inflate(inflater, container, false)

        val bundle = this.arguments
        currentState = (bundle?.getParcelable(NavigationUtil.STATE) as? MFARequiredState)!!
//        authMethod = (bundle?.getParcelable(NavigationUtil.STATE) as? AuthMethod)!!
        authMethod = AuthMethod(
            id = "id",
            challengeType = "oob",
            challengeChannel = "email",
            loginHint = "user@contoso.com"
        )
        appUtil = AppUtil(requireContext(), requireActivity())

        init()

        return binding.root
    }

    private fun init() {
        initializeLabels()
        initializeButtonListeners()
    }

    private fun initializeLabels() {
        binding.hintText.text = getString(R.string.oob_hint_text_value).replace("challengeChannel", authMethod.challengeChannel).replace("loginHint", authMethod.loginHint)
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

            val actionResult = currentState.submitChallenge(emailCode)

            when (actionResult) {
                is SignInResult.Complete -> {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.sign_in_successful_message),
                        Toast.LENGTH_SHORT
                    ).show()
                    appUtil.navigation.finish()
                }
                is SubmitChallengeError -> {
//                    appUtil.errorHandler.handleMFAError(actionResult)
                }
            }
        }
    }

    private fun resendCode() {
        clearCode()
        CoroutineScope(Dispatchers.Main).launch {
            val actionResult = currentState.requestChallenge()
//            val actionResult = currentState.sendChallenge(authMethodId = authMethod.id)

            when (actionResult) {
                // None
            }
        }
    }

    private fun clearCode() {
        binding.codeText.text?.clear()
    }
}
