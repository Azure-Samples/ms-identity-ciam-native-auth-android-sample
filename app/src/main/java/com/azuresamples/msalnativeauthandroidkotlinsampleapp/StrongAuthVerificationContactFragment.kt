package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentVerificationContactBinding
import com.microsoft.identity.nativeauth.AuthMethod
import com.microsoft.identity.nativeauth.parameters.NativeAuthChallengeAuthMethodParameters
import com.microsoft.identity.nativeauth.statemachine.errors.RegisterStrongAuthChallengeError
import com.microsoft.identity.nativeauth.statemachine.results.RegisterStrongAuthChallengeResult
import com.microsoft.identity.nativeauth.statemachine.states.RegisterStrongAuthState
import com.microsoft.identity.nativeauth.statemachine.states.RegisterStrongAuthVerificationRequiredState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StrongAuthVerificationContactFragment : Fragment() {
    private lateinit var currentState: RegisterStrongAuthState
    private lateinit var authMethod: AuthMethod
    private var _binding: FragmentVerificationContactBinding? = null
    private val binding get() = _binding!!

    companion object {
        private val TAG = StrongAuthVerificationContactFragment::class.java.simpleName
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVerificationContactBinding.inflate(inflater, container, false)

        val bundle = this.arguments
        currentState = (bundle?.getParcelable(Constants.STATE) as? RegisterStrongAuthState)!!
        authMethod = bundle.getParcelable(Constants.AUTH_METHOD)!!

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
        CoroutineScope(Dispatchers.Main).launch {
            val optionalEmail = binding.emailText.text.toString()

            val params = NativeAuthChallengeAuthMethodParameters(authMethod)
            params.verificationContact = optionalEmail
            val actionResult = currentState.challengeAuthMethod(params)

            when (actionResult) {
                is RegisterStrongAuthChallengeResult.VerificationRequired -> {
                    navigateToJITChallengeFragment(actionResult.result.getNextState(), actionResult.result.getChannel(), actionResult.result.getSentTo())
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

    private fun navigateToJITChallengeFragment(nextState: RegisterStrongAuthVerificationRequiredState, channel: String, sentTo: String) {
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

}
