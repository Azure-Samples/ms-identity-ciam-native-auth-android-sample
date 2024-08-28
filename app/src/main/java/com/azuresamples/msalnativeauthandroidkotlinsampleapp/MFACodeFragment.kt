package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentMfaCodeBinding
import com.microsoft.identity.nativeauth.AuthMethod
import com.microsoft.identity.nativeauth.statemachine.errors.MFAError
import com.microsoft.identity.nativeauth.statemachine.results.MFARequiredResult
import com.microsoft.identity.nativeauth.statemachine.results.SignInResult
import com.microsoft.identity.nativeauth.statemachine.states.MFARequiredState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MFACodeFragment : Fragment() {
    private lateinit var currentState: MFARequiredState
    private lateinit var authMethod: AuthMethod
    private var _binding: FragmentMfaCodeBinding? = null
    private val binding get() = _binding!!

    companion object {
        private val TAG = MFACodeFragment::class.java.simpleName
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMfaCodeBinding.inflate(inflater, container, false)

        val bundle = this.arguments
        currentState = (bundle?.getParcelable(Constants.STATE) as? MFARequiredState)!!
//        authMethod = (bundle?.getParcelable(NavigationUtil.STATE) as? AuthMethod)!!
        authMethod = AuthMethod(
            id = "id",
            challengeType = "oob",
            challengeChannel = "email",
            loginHint = "user@contoso.com"
        )

        init()

        return binding.root
    }

    private fun init() {
        initializeLabels()
        initializeButtonListeners()
    }

    private fun initializeLabels() {
        binding.hintText.text = getString(R.string.mfa_code_hint_text_value)
            .replace("challengeChannel", authMethod.challengeChannel)
            .replace("loginHint", authMethod.loginHint)
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

            val actionResult = currentState.submitChallenge(emailCode.toInt())

            when (actionResult) {
                is SignInResult.Complete -> {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.sign_in_successful_message),
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
                is MFAError -> {
                    handleMFAError(actionResult)
                }
            }
        }
    }

    private fun resendCode() {
        clearCode()

        CoroutineScope(Dispatchers.Main).launch {
            val actionResult = currentState.sendChallenge(authMethodId = authMethod.id)

            when (actionResult) {
                is MFARequiredResult.VerificationRequired -> {
                    currentState = actionResult.nextState
                    Toast.makeText(requireContext(), getString(R.string.resend_code_message), Toast.LENGTH_LONG).show()
                }
                is MFAError -> {
                    handleMFAError(actionResult)
                }
            }
        }
    }

    private fun clearCode() {
        binding.codeText.text?.clear()
    }

    fun displayDialog(error: String? = null, message: String?) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle(error)
            .setMessage(message)
        val alertDialog = builder.create()
        alertDialog.show()
    }

    private fun handleMFAError(error: MFAError) {
        when {
            error.isError()
            -> {
                displayDialog(error.error, error.errorMessage)
            }
            else -> {
                displayDialog(getString(R.string.unexpected_sdk_result_title), error.errorMessage)
            }
        }
    }

    private fun finish() {
        requireActivity().supportFragmentManager.popBackStackImmediate()
    }
}
