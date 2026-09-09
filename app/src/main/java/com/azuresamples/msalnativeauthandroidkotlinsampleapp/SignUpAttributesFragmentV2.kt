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
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentSignUpAttributesV2Binding
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.microsoft.identity.nativeauth.AuthMethod
import com.microsoft.identity.nativeauth.UserAttributes
import com.microsoft.identity.nativeauth.statemachine.errors.NativeAuthErrorV2
import com.microsoft.identity.nativeauth.statemachine.errors.SubmitAttributesErrorV2
import com.microsoft.identity.nativeauth.statemachine.results.NativeAuthResultV2
import com.microsoft.identity.nativeauth.statemachine.states.AttributesInvalidStateV2
import com.microsoft.identity.nativeauth.statemachine.states.AttributesRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.PasswordRequiredStateV2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SignUpAttributesFragmentV2 : Fragment() {
    private lateinit var currentState: Parcelable
    private var _binding: FragmentSignUpAttributesV2Binding? = null
    private val binding get() = _binding!!
    private val attributeInputs = linkedMapOf<String, TextInputEditText>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignUpAttributesV2Binding.inflate(inflater, container, false)
        currentState = requireNotNull(arguments?.getParcelable(Constants.STATE))
        renderAttributes(requireNotNull(arguments?.getStringArrayList(Constants.REQUIRED_ATTRIBUTES)))
        initializeButtonListeners()
        return binding.root
    }

    private fun initializeButtonListeners() {
        binding.submitAttributes.setOnClickListener {
            submitAttributes()
        }
        binding.cancelAttributes.setOnClickListener {
            finish()
        }
    }

    private fun renderAttributes(attributeNames: List<String>) {
        val previousValues = attributeInputs.mapValues { it.value.text?.toString().orEmpty() }
        attributeInputs.clear()
        binding.attributeFields.removeAllViews()

        attributeNames.distinct().forEach { attributeName ->
            val inputLayout = TextInputLayout(requireContext()).apply {
                hint = attributeLabel(attributeName)
                setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    val horizontalMargin = resources.getDimensionPixelSize(R.dimen.dimens_15dp)
                    val verticalMargin = resources.getDimensionPixelSize(R.dimen.dimens_5dp)
                    setMargins(horizontalMargin, verticalMargin, horizontalMargin, verticalMargin)
                }
            }
            val input = TextInputEditText(inputLayout.context).apply {
                inputType = InputType.TYPE_CLASS_TEXT
                setText(previousValues[attributeName])
            }
            inputLayout.addView(input)
            binding.attributeFields.addView(inputLayout)
            attributeInputs[attributeName] = input
        }
    }

    private fun submitAttributes() {
        if (attributeInputs.isEmpty()) {
            displayDialog(
                getString(R.string.unexpected_sdk_result_title),
                getString(R.string.missing_required_attributes_message)
            )
            return
        }

        val emptyAttribute = attributeInputs.entries.firstOrNull { it.value.text.isNullOrBlank() }
        if (emptyAttribute != null) {
            emptyAttribute.value.error = getString(R.string.required_attribute_message)
            return
        }

        val builder = UserAttributes.Builder()
        attributeInputs.forEach { (name, input) ->
            builder.customAttribute(name, input.text.toString())
        }

        CoroutineScope(Dispatchers.Main).launch {
            val result = when (val state = currentState) {
                is AttributesRequiredStateV2 -> state.submitAttributes(builder.build())
                is AttributesInvalidStateV2 -> state.submitAttributes(builder.build())
                else -> {
                    displayDialog(
                        getString(R.string.unexpected_sdk_result_title),
                        state.toString()
                    )
                    return@launch
                }
            }
            handleResult(result)
        }
    }

    private suspend fun handleResult(result: NativeAuthResultV2) {
        when (result) {
            is NativeAuthResultV2.Complete -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.sign_in_successful_message),
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
            is NativeAuthResultV2.AttributesRequired -> {
                updateStateAndAttributes(
                    result.nextState,
                    result.requiredAttributes.mapNotNull { it.attributeName }
                )
            }
            is NativeAuthResultV2.AttributesInvalid -> {
                val invalidAttributeNames = result.invalidAttributes
                updateStateAndAttributes(result.nextState, invalidAttributeNames)
                displayDialog(
                    getString(R.string.invalid_attributes_title),
                    invalidAttributeNames.joinToString()
                )
            }
            is NativeAuthResultV2.SignInAfterSignUpRequired -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.sign_up_successful_message),
                    Toast.LENGTH_SHORT
                ).show()
                handleResult(result.nextState.signIn())
            }
            is NativeAuthResultV2.PasswordRequired -> {
                promptForPassword { password ->
                    submitPassword(result.nextState, password)
                }
            }
            is NativeAuthResultV2.CodeRequired -> {
                navigateToSignInCode(result.nextState)
            }
            is NativeAuthResultV2.MFARequired -> {
                displayMFARequiredDialog(result.nextState, result.authMethods)
            }
            is NativeAuthResultV2.StrongAuthRegistrationRequired -> {
                displayStrongAuthRequiredDialog(result.nextState, result.authMethods)
            }
            is SubmitAttributesErrorV2 -> {
                displayDialog(
                    result.error ?: getString(R.string.unexpected_sdk_error_title),
                    result.exception?.message ?: result.errorMessage
                )
            }
            is NativeAuthErrorV2 -> {
                displayDialog(
                    result.error ?: getString(R.string.unexpected_sdk_error_title),
                    result.exception?.message ?: result.errorMessage
                )
            }
            else -> {
                displayDialog(
                    getString(R.string.unexpected_sdk_result_title),
                    result.toString()
                )
            }
        }
    }

    private fun updateStateAndAttributes(state: Parcelable, attributeNames: List<String>) {
        currentState = state
        requireArguments().putParcelable(Constants.STATE, state)
        requireArguments().putStringArrayList(
            Constants.REQUIRED_ATTRIBUTES,
            ArrayList(attributeNames)
        )
        renderAttributes(attributeNames)
    }

    private fun submitPassword(state: PasswordRequiredStateV2, password: CharArray) {
        CoroutineScope(Dispatchers.Main).launch {
            val result = try {
                state.submitPassword(password)
            } finally {
                password.fill('\u0000')
            }
            handleResult(result)
        }
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
                val password = CharArray(passwordInput.length())
                passwordInput.text.getChars(0, passwordInput.length(), password, 0)
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
        replaceCompletedStep(fragment)
    }

    private fun navigateToSignInCode(state: Parcelable) {
        val fragment = SignInCodeFragment().apply {
            arguments = Bundle().apply {
                putParcelable(Constants.STATE, state)
            }
        }
        replaceCompletedStep(fragment)
    }

    private fun replaceCompletedStep(fragment: Fragment) {
        val fragmentManager = requireActivity().supportFragmentManager
        fragmentManager.popBackStackImmediate()
        fragmentManager
            .beginTransaction()
            .setReorderingAllowed(true)
            .addToBackStack(fragment::class.java.name)
            .replace(R.id.scenario_fragment, fragment)
            .commit()
    }

    private fun attributeLabel(attributeName: String): String {
        return when {
            attributeName.equals("city", ignoreCase = true) -> getString(R.string.attribute_city)
            attributeName.equals("country", ignoreCase = true) -> getString(R.string.attribute_country)
            attributeName.equals("givenName", ignoreCase = true) -> getString(R.string.attribute_given)
            attributeName.equals("surname", ignoreCase = true) -> getString(R.string.attribute_surname)
            attributeName.equals("flatusername", ignoreCase = true) -> getString(R.string.attribute_username)
            else -> attributeName
                .replace(Regex("([a-z])([A-Z])"), "$1 $2")
                .replace('_', ' ')
                .replaceFirstChar { it.uppercase() }
        }
    }

    private fun displayDialog(error: String?, message: String?) {
        AlertDialog.Builder(requireContext())
            .setTitle(error)
            .setMessage(message)
            .show()
    }

    private fun finish() {
        requireActivity().supportFragmentManager.popBackStackImmediate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        attributeInputs.clear()
    }
}
