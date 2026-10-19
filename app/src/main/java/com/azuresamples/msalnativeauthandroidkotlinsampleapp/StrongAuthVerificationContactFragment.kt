package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentVerificationContactBinding
import com.microsoft.identity.nativeauth.AuthMethod
import com.microsoft.identity.nativeauth.parameters.NativeAuthChallengeAuthMethodParameters
import com.microsoft.identity.nativeauth.statemachine.errors.RegisterStrongAuthChallengeError
import com.microsoft.identity.nativeauth.statemachine.results.RegisterStrongAuthChallengeResult
import com.microsoft.identity.nativeauth.statemachine.states.RegisterStrongAuthState
import com.microsoft.identity.nativeauth.statemachine.states.RegisterStrongAuthVerificationRequiredState
import com.microsoft.identity.nativeauth.statemachine.states.StrongAuthRegistrationRequiredStateV2
import kotlinx.coroutines.launch

class StrongAuthVerificationContactFragment : Fragment() {
    private lateinit var currentState: Parcelable
    private lateinit var authMethod: AuthMethod
    private var _binding: FragmentVerificationContactBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVerificationContactBinding.inflate(inflater, container, false)

        val bundle = this.arguments
        currentState = requireNotNull(bundle?.getParcelable(Constants.STATE))
        authMethod = requireNotNull(bundle?.getParcelable(Constants.AUTH_METHOD))

        if (authMethod.challengeChannel.uppercase() == "SMS") {
            binding.hintText.text = getString(R.string.strong_auth_sms_hint_text_value)
            binding.contactTypeHint.text = getString(R.string.attribute_phone_number)
        } else {
            binding.hintText.text = getString(R.string.strong_auth_email_hint_text_value)
            binding.contactTypeHint.text = getString(R.string.attribute_email)
        }

        init()

        return binding.root
    }

    private fun init() {
        initializeButtonListeners()
    }

    private fun initializeButtonListeners() {
        binding.verifyCode.setOnClickListener {
            verifyContact()
        }
    }

    private fun verifyContact() {
        viewLifecycleOwner.lifecycleScope.launch {
            val state = currentState
            if (state is StrongAuthRegistrationRequiredStateV2) {
                // V2 enrollment requires the explicit contact, never the method's login hint.
                binding.verifyCode.isEnabled = false
                try {
                    routeNativeAuthResultV2(
                        state.selectAuthMethod(authMethod, binding.emailText.text.toString())
                    )
                } finally {
                    _binding?.verifyCode?.isEnabled = true
                }
                return@launch
            }
            var optionalEmail = binding.emailText.text.toString()
            if (optionalEmail.isBlank()) {
                optionalEmail = authMethod.loginHint.orEmpty()
            }
            val params = NativeAuthChallengeAuthMethodParameters(authMethod, optionalEmail)
            val actionResult = (state as RegisterStrongAuthState).challengeAuthMethod(params)

            when (actionResult) {
                is RegisterStrongAuthChallengeResult.VerificationRequired -> {
                    navigateToStrongAuthChallengeFragment(actionResult.result.getNextState(), actionResult.result.getChannel(), actionResult.result.getSentTo())
                }
                is RegisterStrongAuthChallengeError -> {
                    handleRegisterStrongAuthChallengeError(actionResult)
                }
            }
        }
    }

    private fun handleRegisterStrongAuthChallengeError(error: RegisterStrongAuthChallengeError) {
        when {
            error.isInvalidInput() -> {
                displayDialog(error.error, error.errorMessage)
            }
            else -> {
                // Unexpected error
                displayDialog(getString(R.string.unexpected_sdk_error_title), error.exception?.message ?: error.errorMessage)
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

    private fun navigateToStrongAuthChallengeFragment(nextState: RegisterStrongAuthVerificationRequiredState, channel: String, sentTo: String) {
        val bundle = Bundle()
        bundle.putParcelable(Constants.STATE, nextState)
        bundle.putString(Constants.CHANNEL, channel)
        bundle.putString(Constants.SENT_TO, sentTo)
        bundle.putParcelable(Constants.AUTH_METHOD, authMethod)

        val fragment = StrongAuthVerificationChallengeFragment()
        fragment.arguments = bundle

        requireActivity().supportFragmentManager
            .beginTransaction()
            .setReorderingAllowed(true)
            .addToBackStack(fragment::class.java.name)
            .replace(R.id.scenario_fragment, fragment)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
