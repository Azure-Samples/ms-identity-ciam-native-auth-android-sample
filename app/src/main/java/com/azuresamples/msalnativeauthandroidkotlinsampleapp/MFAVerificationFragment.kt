package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentMfaChallengeBinding
import com.microsoft.identity.nativeauth.statemachine.errors.MFARequestChallengeError
import com.microsoft.identity.nativeauth.statemachine.errors.MFASubmitChallengeError
import com.microsoft.identity.nativeauth.statemachine.results.MFARequiredResult
import com.microsoft.identity.nativeauth.statemachine.results.SignInResult
import com.microsoft.identity.nativeauth.statemachine.states.MFARequiredState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MFAVerificationFragment : Fragment() {
    private lateinit var currentState: MFARequiredState
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
        currentState = (bundle?.getParcelable(Constants.STATE) as? MFARequiredState)!!
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
                is MFASubmitChallengeError -> {
                    handleMFASubmitChallengeError(actionResult)
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
            val actionResult = currentState.requestChallenge()

            when (actionResult) {
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
            error.isError() || error.isInvalidChallenge() -> {
                displayDialog(error.error, error.errorMessage)
            }
            else -> {
                displayDialog(getString(R.string.unexpected_sdk_result_title), error.exception?.message)
            }
        }
    }

    private fun handleMFARequestChallengeError(error: MFARequestChallengeError) {
        when {
            error.isError() || error.isBrowserRequired() -> {
                displayDialog(error.error, error.errorMessage)
            }
            else -> {
                displayDialog(getString(R.string.unexpected_sdk_result_title), error.exception?.message)
            }
        }
    }

    private fun finish() {
        requireActivity().supportFragmentManager.popBackStackImmediate()
    }
}
