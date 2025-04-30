package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentVerificationChallengeBinding
import com.microsoft.identity.nativeauth.parameters.NativeAuthChallengeAuthMethodParameters
import com.microsoft.identity.nativeauth.statemachine.errors.RegisterStrongAuthChallengeError
import com.microsoft.identity.nativeauth.statemachine.errors.RegisterStrongAuthSubmitChallengeError
import com.microsoft.identity.nativeauth.statemachine.results.RegisterStrongAuthChallengeResult
import com.microsoft.identity.nativeauth.statemachine.results.SignInResult
import com.microsoft.identity.nativeauth.statemachine.states.RegisterStrongAuthVerificationRequiredState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StrongAuthVerificationChallengeFragment : Fragment() {
    private lateinit var currentState: RegisterStrongAuthVerificationRequiredState
    private lateinit var sentTo: String
    private lateinit var channel: String
    private var _binding: FragmentVerificationChallengeBinding? = null
    private val binding get() = _binding!!

    companion object {
        private val TAG = StrongAuthVerificationChallengeFragment::class.java.simpleName
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVerificationChallengeBinding.inflate(inflater, container, false)

        val bundle = this.arguments
        currentState = (bundle?.getParcelable(Constants.STATE) as? RegisterStrongAuthVerificationRequiredState)!!
        sentTo = bundle.getString(Constants.SENT_TO)!!
        channel = bundle.getString(Constants.CHANNEL)!!

        init()

        return binding.root
    }

    private fun init() {
        initializeLabels()
        initializeButtonListeners()
    }

    private fun initializeLabels() {
        binding.hintText.text = getString(R.string.jit_challenge_hint_text_value, channel, sentTo)
    }

    private fun initializeButtonListeners() {
        binding.verifyChallenge.setOnClickListener {
            verifyChallenge()
        }

        binding.resendChallengeText.setOnClickListener {
            resendChallenge()
        }
    }

    private fun verifyChallenge() {
        CoroutineScope(Dispatchers.Main).launch {
            val emailCode = binding.challengeText.text.toString()

            val actionResult = currentState.submitChallenge(emailCode)

            when (actionResult) {
                is SignInResult.Complete -> {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.sign_in_successful_message),
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
                is RegisterStrongAuthSubmitChallengeError -> {
                    handleSubmitChallengeError(actionResult)
                }
                else -> {
                    displayDialog(getString(R.string.unexpected_sdk_result_title), actionResult.toString())
                }
            }
        }
    }

    private fun resendChallenge() {
        clearChallengeText()

        CoroutineScope(Dispatchers.Main).launch {
            val params = NativeAuthChallengeAuthMethodParameters() // TODO: SDK return auth methods
            params.verificationContact = sentTo
            val actionResult = currentState.challengeAuthMethod(params)

            when (actionResult) {
                is RegisterStrongAuthChallengeResult.VerificationRequired -> {
                    currentState = actionResult.result.getNextState()
                    Toast.makeText(requireContext(), getString(R.string.resend_challenge_message), Toast.LENGTH_LONG).show()
                }
                is RegisterStrongAuthChallengeError -> {
                    handleRequestChallengeError(actionResult)
                }
                else -> {
                    displayDialog(getString(R.string.unexpected_sdk_result_title), actionResult.toString())
                }
            }
        }
    }

    private fun clearChallengeText() {
        binding.challengeText.text?.clear()
    }

    fun displayDialog(error: String? = null, message: String?) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle(error)
            .setMessage(message)
        val alertDialog = builder.create()
        alertDialog.show()
    }

    private fun handleSubmitChallengeError(error: RegisterStrongAuthSubmitChallengeError) {
        when {
            error.isInvalidChallenge() -> {
                displayDialog(error.error, error.errorMessage)
            }
            else -> {
                displayDialog(getString(R.string.unexpected_sdk_error_title), error.exception?.message ?: error.errorMessage)
            }
        }
    }

    private fun handleRequestChallengeError(error: RegisterStrongAuthChallengeError) {
        when {
            error.isInvalidInput() -> {
                displayDialog(error.error, error.errorMessage)
            }
            else -> {
                displayDialog(getString(R.string.unexpected_sdk_error_title), error.exception?.message ?: error.errorMessage)
            }
        }
    }

    private fun finish() {
        // Pop back to MFAFragment fragment
        val fragmentManager = requireActivity().supportFragmentManager
        val name: String = MFAFragment::class.java.name
        fragmentManager.popBackStack(name, 0)
    }
}
