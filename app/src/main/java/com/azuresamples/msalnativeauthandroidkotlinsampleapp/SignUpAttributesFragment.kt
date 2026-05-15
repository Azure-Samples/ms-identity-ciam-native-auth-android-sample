package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentSignUpAttributesBinding
import com.microsoft.identity.nativeauth.UserAttributes
import com.microsoft.identity.nativeauth.parameters.NativeAuthSignInContinuationParameters
import com.microsoft.identity.nativeauth.statemachine.errors.SignInContinuationError
import com.microsoft.identity.nativeauth.statemachine.errors.SignUpSubmitAttributesError
import com.microsoft.identity.nativeauth.statemachine.results.SignInResult
import com.microsoft.identity.nativeauth.statemachine.results.SignUpResult
import com.microsoft.identity.nativeauth.statemachine.states.SignInContinuationState
import com.microsoft.identity.nativeauth.statemachine.states.SignUpAttributesRequiredState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SignUpAttributesFragment : Fragment() {
    private lateinit var currentState: SignUpAttributesRequiredState
    private var _binding: FragmentSignUpAttributesBinding? = null
    private val binding get() = _binding!!

    companion object {
        private val TAG = SignUpAttributesFragment::class.java.simpleName
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSignUpAttributesBinding.inflate(inflater, container, false)

        val bundle = this.arguments
        currentState = (bundle?.getParcelable(Constants.STATE) as? SignUpAttributesRequiredState)!!

        init()

        return binding.root
    }

    private fun init() {
        initializeButtonListeners()
    }

    private fun initializeButtonListeners() {
        binding.submitAttributes.setOnClickListener {
            submitAttributes()
        }

        binding.cancelAttributes.setOnClickListener {
            finish()
        }
    }

    private fun submitAttributes() {
        CoroutineScope(Dispatchers.Main).launch {
            val username = binding.usernameText.text.toString()

            val attributes = UserAttributes.Builder()
                .flatUsername(username)
                .build()

            val actionResult = currentState.submitAttributes(attributes)

            when (actionResult) {
                is SignUpResult.Complete -> {
                    Toast.makeText(requireContext(), getString(R.string.sign_up_successful_message), Toast.LENGTH_SHORT).show()
                    signInAfterSignUp(
                        nextState = actionResult.nextState
                    )
                }
                is SignUpResult.AttributesRequired -> {
                    displayDialog(getString(R.string.unexpected_sdk_result_title), actionResult.toString())
                }
                is SignUpSubmitAttributesError -> {
                    displayDialog(getString(R.string.unexpected_sdk_error_title), actionResult.exception?.message ?: actionResult.errorMessage)
                }
            }
        }
    }

    private suspend fun signInAfterSignUp(nextState: SignInContinuationState) {
        val parameters = NativeAuthSignInContinuationParameters()
        val actionResult = nextState.signIn(parameters)

        when (actionResult) {
            is SignInResult.Complete -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.sign_in_successful_message),
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
            is SignInContinuationError -> {
                displayDialog(getString(R.string.msal_exception_title), actionResult.exception?.message ?: actionResult.errorMessage)
            }
            is SignInResult.CodeRequired,
            is SignInResult.PasswordRequired -> {
                displayDialog(getString(R.string.unexpected_sdk_result_title), actionResult.toString())
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

    private fun finish() {
        requireActivity().supportFragmentManager.popBackStackImmediate()
    }
}
