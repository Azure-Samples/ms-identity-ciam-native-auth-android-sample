package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
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
import com.microsoft.identity.nativeauth.statemachine.states.StrongAuthRegistrationRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.StrongAuthVerificationRequiredStateV2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MFAVerificationFragment : Fragment() {
    // The challenge (verify) state: V1 MFARequiredState, or V2 MFAVerificationRequiredStateV2 /
    // StrongAuthVerificationRequiredStateV2. Reassigned when the user resends the challenge.
    private lateinit var currentState: Parcelable
    // Strong-auth V2 resends by re-selecting its method. MFA V2 resends directly from its
    // verification state so the latest opaque continuation is preserved.
    private var selectionState: Parcelable? = null
    private lateinit var authMethod: AuthMethod
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
        selectionState = bundle.getParcelable(Constants.SELECTION_STATE) as? Parcelable
        authMethod = (bundle.getParcelable(Constants.AUTH_METHOD) as? AuthMethod)!!
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
        binding.hintText.text = getString(R.string.mfa_challenge_hint_text_value)
            .replace("challengeChannel", channel)
            .replace("loginHint", sentTo)
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
        val emailCode = binding.challengeText.text.toString()

        when (val state = currentState) {
            is MFARequiredState -> verifyChallengeV1(state, emailCode)
            is MFAVerificationRequiredStateV2 -> verifyChallengeV2 { state.submitChallenge(emailCode) }
            is StrongAuthVerificationRequiredStateV2 -> verifyChallengeV2 { state.submitChallenge(emailCode) }
            else -> displayDialog(getString(R.string.unexpected_sdk_result_title), state.toString())
        }
    }

    private fun verifyChallengeV1(state: MFARequiredState, emailCode: String) {
        CoroutineScope(Dispatchers.Main).launch {
            when (val actionResult = state.submitChallenge(emailCode)) {
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
        CoroutineScope(Dispatchers.Main).launch {
            when (val result = call()) {
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
                    displayDialog(getString(R.string.unexpected_sdk_result_title), result.toString())
                }
            }
        }
    }

    private fun resendChallenge() {
        clearChallengeText()

        when (val state = currentState) {
            is MFARequiredState -> resendChallengeV1(state)
            is MFAVerificationRequiredStateV2 -> resendMFAChallengeV2(state)
            is StrongAuthVerificationRequiredStateV2 -> resendStrongAuthChallengeV2()
            else -> displayDialog(getString(R.string.unexpected_sdk_result_title), state.toString())
        }
    }

    private fun resendChallengeV1(state: MFARequiredState) {
        CoroutineScope(Dispatchers.Main).launch {
            when (val actionResult = state.requestChallenge(authMethod)) {
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
        CoroutineScope(Dispatchers.Main).launch {
            when (val result = state.resendChallenge()) {
                is NativeAuthResultV2.MFAVerificationRequired -> {
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
                    displayDialog(getString(R.string.unexpected_sdk_result_title), result.toString())
                }
            }
        }
    }

    private fun resendStrongAuthChallengeV2() {
        val selection = selectionState
        val call: (suspend () -> NativeAuthResultV2)? = when (selection) {
            is StrongAuthRegistrationRequiredStateV2 -> { { selection.selectAuthMethod(authMethod) } }
            else -> null
        }

        if (call == null) {
            displayDialog(getString(R.string.unexpected_sdk_result_title), getString(R.string.unknown_error_message))
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            when (val result = call()) {
                is NativeAuthResultV2.StrongAuthVerificationRequired -> {
                    updateVerificationState(
                        state = result.nextState,
                        newSentTo = result.sentTo,
                        newChannel = result.channel
                    )
                    Toast.makeText(requireContext(), getString(R.string.resend_challenge_message), Toast.LENGTH_LONG).show()
                }
                is NativeAuthErrorV2 -> {
                    displayDialog(result.error ?: getString(R.string.unexpected_sdk_error_title), result.errorMessage)
                }
                else -> {
                    displayDialog(getString(R.string.unexpected_sdk_result_title), result.toString())
                }
            }
        }
    }

    private fun updateVerificationState(state: Parcelable, newSentTo: String, newChannel: String) {
        currentState = state
        sentTo = newSentTo
        channel = newChannel
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
}
