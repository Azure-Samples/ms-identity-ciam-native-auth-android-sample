package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.os.Parcelable
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentMfaChallengeBinding
import com.microsoft.identity.nativeauth.AuthMethod
import com.microsoft.identity.nativeauth.statemachine.errors.MFARequestChallengeError
import com.microsoft.identity.nativeauth.statemachine.errors.MFASubmitChallengeError
import com.microsoft.identity.nativeauth.statemachine.errors.NativeAuthErrorV2
import com.microsoft.identity.nativeauth.statemachine.results.MFARequiredResult
import com.microsoft.identity.nativeauth.statemachine.results.NativeAuthResultV2
import com.microsoft.identity.nativeauth.statemachine.results.SignInResult
import com.microsoft.identity.nativeauth.statemachine.states.MFARequiredState
import com.microsoft.identity.nativeauth.statemachine.states.MFAVerificationRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.StrongAuthVerificationRequiredStateV2
import kotlinx.coroutines.launch

class MFAVerificationFragment : Fragment() {
    // The challenge (verify) state: V1 MFARequiredState, or V2 MFAVerificationRequiredStateV2 /
    // StrongAuthVerificationRequiredStateV2. Reassigned when the user resends the challenge.
    private lateinit var currentState: Parcelable
    private var authMethod: AuthMethod? = null
    private lateinit var sentTo: String
    private lateinit var channel: String
    private var _binding: FragmentMfaChallengeBinding? = null
    private val binding get() = _binding!!

    companion object {
        private val TAG = MFAVerificationFragment::class.java.simpleName
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMfaChallengeBinding.inflate(inflater, container, false)

        val bundle = this.arguments
        currentState = (bundle?.getParcelable(Constants.STATE) as? Parcelable)!!
        authMethod = bundle.getParcelable(Constants.AUTH_METHOD)
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
        binding.hintText.text = getString(R.string.mfa_challenge_hint_text_value, channel, sentTo)
        val codeLength = arguments?.getInt(Constants.CODE_LENGTH) ?: 0
        if (codeLength > 0) {
            binding.hintText.append("\n" + getString(R.string.verification_code_length_hint, codeLength))
        }
        binding.resendChallengeText.visibility =
            if (currentState is StrongAuthVerificationRequiredStateV2) View.GONE else View.VISIBLE
        if (currentState is StrongAuthVerificationRequiredStateV2) {
            // Code length is a hint only; the service validates the activation challenge.
            binding.challengeText.inputType = InputType.TYPE_CLASS_TEXT
        }
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
        val verificationCode = binding.challengeText.text.toString()

        when (val state = currentState) {
            is MFARequiredState -> verifyChallengeV1(state, verificationCode)
            is MFAVerificationRequiredStateV2 -> verifyChallengeV2 {
                state.submitChallenge(verificationCode)
            }
            is StrongAuthVerificationRequiredStateV2 -> verifyChallengeV2 {
                state.submitChallenge(verificationCode)
            }
            else -> displayDialog(getString(R.string.unexpected_sdk_result_title), state.toString())
        }
    }

    private fun verifyChallengeV1(state: MFARequiredState, verificationCode: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val actionResult = state.submitChallenge(verificationCode)) {
                is SignInResult.Complete -> {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.sign_in_successful_message),
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
                is MFASubmitChallengeError -> {
                    handleMFASubmitChallengeError(actionResult)
                }
                else -> {
                    displayDialog(getString(R.string.unexpected_sdk_result_title), actionResult.toString())
                }
            }
        }
    }

    private inline fun verifyChallengeV2(crossinline call: suspend () -> NativeAuthResultV2) {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.verifyChallenge.isEnabled = false
            try {
                routeNativeAuthResultV2(call())
            } finally {
                _binding?.verifyChallenge?.isEnabled = true
            }
        }
    }

    private fun resendChallenge() {
        clearChallengeText()

        when (val state = currentState) {
            is MFARequiredState -> resendChallengeV1(state)
            is MFAVerificationRequiredStateV2 -> resendMFAChallengeV2(state)
            is StrongAuthVerificationRequiredStateV2 -> return
            else -> displayDialog(getString(R.string.unexpected_sdk_result_title), state.toString())
        }
    }

    private fun resendChallengeV1(state: MFARequiredState) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val actionResult = state.requestChallenge(requireNotNull(authMethod))) {
                is MFARequiredResult.VerificationRequired -> {
                    currentState = actionResult.nextState
                    Toast.makeText(requireContext(), getString(R.string.resend_challenge_message), Toast.LENGTH_LONG).show()
                }
                is MFARequestChallengeError -> {
                    handleMFARequestChallengeError(actionResult)
                }
                else -> {
                    displayDialog(getString(R.string.unexpected_sdk_result_title), actionResult.toString())
                }
            }
        }
    }

    private fun resendMFAChallengeV2(state: MFAVerificationRequiredStateV2) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = state.resendChallenge()) {
                is NativeAuthResultV2.MFAVerificationRequired -> {
                    requireArguments().putInt(Constants.CODE_LENGTH, result.codeLength)
                    updateVerificationState(
                        state = result.nextState,
                        newSentTo = result.sentTo,
                        newChannel = result.channel
                    )
                    Toast.makeText(requireContext(), getString(R.string.resend_challenge_message), Toast.LENGTH_LONG).show()
                }
                is NativeAuthResultV2.Complete -> {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.sign_in_successful_message),
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
                is NativeAuthErrorV2 -> {
                    displayDialog(result.error ?: getString(R.string.unexpected_sdk_error_title), result.errorMessage)
                }
                else -> {
                    routeNativeAuthResultV2(result)
                }
            }
        }
    }

    private fun updateVerificationState(state: Parcelable, newSentTo: String, newChannel: String) {
        currentState = state
        sentTo = newSentTo
        channel = newChannel
        requireArguments().putParcelable(Constants.STATE, state)
        requireArguments().putString(Constants.SENT_TO, newSentTo)
        requireArguments().putString(Constants.CHANNEL, newChannel)
        initializeLabels()
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

    private fun handleMFASubmitChallengeError(error: MFASubmitChallengeError) {
        when {
            error.isInvalidChallenge() -> {
                displayDialog(error.error, error.errorMessage)
            }
            else -> {
                displayDialog(getString(R.string.unexpected_sdk_error_title), error.exception?.message ?: error.errorMessage)
            }
        }
    }

    private fun handleMFARequestChallengeError(error: MFARequestChallengeError) {
        when {
            error.isBrowserRequired() -> {
                displayDialog(error.error, error.errorMessage)
            }
            else -> {
                displayDialog(getString(R.string.unexpected_sdk_error_title), error.exception?.message ?: error.errorMessage)
            }
        }
    }

    private fun finish() {
        // Pop the whole MFA sub-flow (always entered via PickAuthMethodFragment) and return to
        // whichever fragment launched it (e.g. MFAFragment or EmailSignInSignUpFragment).
        requireActivity().supportFragmentManager
            .popBackStack(PickAuthMethodFragment::class.java.name, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
