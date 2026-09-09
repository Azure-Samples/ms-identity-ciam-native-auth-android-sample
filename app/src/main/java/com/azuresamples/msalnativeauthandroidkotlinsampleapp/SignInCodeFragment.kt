package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.os.Parcelable
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentCodeBinding
import com.microsoft.identity.nativeauth.AuthMethod
import com.microsoft.identity.nativeauth.statemachine.errors.NativeAuthErrorV2
import com.microsoft.identity.nativeauth.statemachine.errors.ResendCodeError
import com.microsoft.identity.nativeauth.statemachine.errors.SubmitCodeError
import com.microsoft.identity.nativeauth.statemachine.errors.SubmitCodeErrorV2
import com.microsoft.identity.nativeauth.statemachine.results.NativeAuthResultV2
import com.microsoft.identity.nativeauth.statemachine.results.SignInResendCodeResult
import com.microsoft.identity.nativeauth.statemachine.results.SignInResult
import com.microsoft.identity.nativeauth.statemachine.states.CodeRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.PasswordRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.SignInCodeRequiredState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SignInCodeFragment : Fragment() {
    private lateinit var currentState: Parcelable
    private var _binding: FragmentCodeBinding? = null
    private val binding get() = _binding!!

    companion object {
        private val TAG = SignInCodeFragment::class.java.simpleName
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCodeBinding.inflate(inflater, container, false)

        val bundle = this.arguments
        currentState = requireNotNull(bundle?.getParcelable(Constants.STATE))

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

            when (val state = currentState) {
                is SignInCodeRequiredState -> submitCodeV1(state, emailCode)
                is CodeRequiredStateV2 -> handleResultV2(state.submitCode(emailCode))
                else -> displayUnexpectedState()
            }
        }
    }

    private suspend fun submitCodeV1(state: SignInCodeRequiredState, code: String) {
        when (val actionResult = state.submitCode(code)) {
            is SignInResult.Complete -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.sign_in_successful_message),
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
            is SubmitCodeError -> {
                handleSubmitCodeError(actionResult)
            }
        }
    }

    private fun resendCode() {
        clearCode()

        CoroutineScope(Dispatchers.Main).launch {
            when (val state = currentState) {
                is SignInCodeRequiredState -> resendCodeV1(state)
                is CodeRequiredStateV2 -> resendCodeV2(state)
                else -> displayUnexpectedState()
            }
        }
    }

    private suspend fun resendCodeV1(state: SignInCodeRequiredState) {
        when (val actionResult = state.resendCode()) {
            is SignInResendCodeResult.Success -> {
                updateCurrentState(actionResult.nextState)
                showCodeResentMessage()
            }
            is ResendCodeError -> {
                displayDialog(
                    getString(R.string.unexpected_sdk_error_title),
                    actionResult.exception?.message ?: actionResult.errorMessage
                )
            }
        }
    }

    private suspend fun resendCodeV2(state: CodeRequiredStateV2) {
        when (val result = state.resendCode()) {
            is NativeAuthResultV2.CodeRequired -> {
                updateCurrentState(result.nextState)
                showCodeResentMessage()
            }
            is NativeAuthErrorV2 -> {
                handleErrorV2(result)
            }
            else -> {
                displayDialog(
                    getString(R.string.unexpected_sdk_result_title),
                    result.toString()
                )
            }
        }
    }

    private fun updateCurrentState(state: Parcelable) {
        currentState = state
        requireArguments().putParcelable(Constants.STATE, state)
    }

    private fun showCodeResentMessage() {
        Toast.makeText(
            requireContext(),
            getString(R.string.resend_code_message),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun clearCode() {
        binding.codeText.text?.clear()
    }

    private fun handleSubmitCodeError(error: SubmitCodeError) {
        when {
            error.isInvalidCode() || error.isBrowserRequired() -> {
                displayDialog(error.error, error.errorMessage)
            }
            else -> {
                // Unexpected error
                displayDialog(getString(R.string.unexpected_sdk_error_title), error.exception?.message ?: error.errorMessage)
            }
        }
    }

    private suspend fun handleResultV2(result: NativeAuthResultV2) {
        when (result) {
            is NativeAuthResultV2.Complete -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.sign_in_successful_message),
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
            is NativeAuthResultV2.CodeRequired -> {
                updateCurrentState(result.nextState)
            }
            is NativeAuthResultV2.PasswordRequired -> {
                promptForPassword { password ->
                    submitPasswordV2(result.nextState, password)
                }
            }
            is NativeAuthResultV2.AttributesRequired -> {
                navigateToAttributesV2(
                    result.nextState,
                    result.requiredAttributes.mapNotNull { it.attributeName }
                )
            }
            is NativeAuthResultV2.SignInAfterSignUpRequired -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.sign_up_successful_message),
                    Toast.LENGTH_SHORT
                ).show()
                handleResultV2(result.nextState.signIn())
            }
            is NativeAuthResultV2.MFARequired -> {
                displayMFARequiredDialog(result.nextState, result.authMethods)
            }
            is NativeAuthResultV2.StrongAuthRegistrationRequired -> {
                displayStrongAuthRequiredDialog(result.nextState, result.authMethods)
            }
            is SubmitCodeErrorV2 -> {
                handleSubmitCodeErrorV2(result)
            }
            is NativeAuthErrorV2 -> {
                handleErrorV2(result)
            }
            else -> {
                displayDialog(
                    getString(R.string.unexpected_sdk_result_title),
                    result.toString()
                )
            }
        }
    }

    private fun submitPasswordV2(state: PasswordRequiredStateV2, password: CharArray) {
        CoroutineScope(Dispatchers.Main).launch {
            val result = try {
                state.submitPassword(password)
            } finally {
                password.fill('\u0000')
            }
            handleResultV2(result)
        }
    }

    private fun handleSubmitCodeErrorV2(error: SubmitCodeErrorV2) {
        when {
            error.isInvalidCode() || error.isBrowserRequired() -> {
                displayDialog(error.error, error.errorMessage)
            }
            else -> {
                handleErrorV2(error)
            }
        }
    }

    private fun handleErrorV2(error: NativeAuthErrorV2) {
        displayDialog(
            error.error ?: getString(R.string.unexpected_sdk_error_title),
            error.exception?.message ?: error.errorMessage
        )
    }

    private fun promptForPassword(onPasswordEntered: (CharArray) -> Unit) {
        val context = requireContext()
        val passwordInput = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = getString(R.string.password_required_hint)
        }
        val container = FrameLayout(context).apply {
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
            addView(passwordInput)
        }

        AlertDialog.Builder(context)
            .setTitle(getString(R.string.password_required_dialog_title))
            .setMessage(getString(R.string.password_required_dialog_message))
            .setView(container)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.submit_password)) { _, _ ->
                val length = passwordInput.text.length
                val password = CharArray(length)
                passwordInput.text.getChars(0, length, password, 0)
                passwordInput.text.clear()
                onPasswordEntered(password)
            }
            .setNegativeButton(getString(R.string.cancel_message)) { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    private fun displayMFARequiredDialog(state: Parcelable, authMethods: List<AuthMethod>) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.mfa_required_notice)
            .setPositiveButton(getString(R.string.yes_message)) { _, _ ->
                navigateToPickAuthMethod(state, authMethods)
            }
            .setNegativeButton(getString(R.string.cancel_message)) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun displayStrongAuthRequiredDialog(
        state: Parcelable,
        authMethods: List<AuthMethod>
    ) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.strong_auth_method_title)
            .setMessage(R.string.strong_auth_method_message)
            .setPositiveButton(getString(R.string.yes_message)) { _, _ ->
                navigateToPickAuthMethod(state, authMethods)
            }
            .setNegativeButton(getString(R.string.cancel_message)) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun navigateToPickAuthMethod(state: Parcelable, authMethods: List<AuthMethod>) {
        val fragment = PickAuthMethodFragment().apply {
            arguments = Bundle().apply {
                putParcelable(Constants.STATE, state)
                putSerializable(Constants.AUTH_METHOD_LIST, ArrayList(authMethods))
            }
        }
        val fragmentManager = requireActivity().supportFragmentManager

        // Remove this code step so successful MFA returns to the Email + OTP screen.
        fragmentManager.popBackStackImmediate()
        fragmentManager
            .beginTransaction()
            .setReorderingAllowed(true)
            .addToBackStack(fragment::class.java.name)
            .replace(R.id.scenario_fragment, fragment)
            .commit()
    }

    private fun navigateToAttributesV2(state: Parcelable, attributeNames: List<String>) {
        val fragment = SignUpAttributesFragmentV2().apply {
            arguments = Bundle().apply {
                putParcelable(Constants.STATE, state)
                putStringArrayList(Constants.REQUIRED_ATTRIBUTES, ArrayList(attributeNames))
            }
        }
        val fragmentManager = requireActivity().supportFragmentManager

        // Replace the completed code step so attributes completion returns to Email + OTP.
        fragmentManager.popBackStackImmediate()
        fragmentManager
            .beginTransaction()
            .setReorderingAllowed(true)
            .addToBackStack(fragment::class.java.name)
            .replace(R.id.scenario_fragment, fragment)
            .commit()
    }

    private fun displayUnexpectedState() {
        displayDialog(
            getString(R.string.unexpected_sdk_result_title),
            currentState.toString()
        )
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
