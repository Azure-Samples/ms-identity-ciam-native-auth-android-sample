package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentSignUpAttributesV2Binding
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.microsoft.identity.nativeauth.UserAttributes
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class SignUpAttributesFragment : Fragment() {
    private var _binding: FragmentSignUpAttributesV2Binding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: NativeAuthViewModel
    private val attributeInputs = linkedMapOf<String, TextInputEditText>()
    private var renderedAttributes: List<String> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignUpAttributesV2Binding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(requireActivity())[NativeAuthViewModel::class.java]
        binding.submitAttributes.setOnClickListener { submitAttributes() }
        binding.cancelAttributes.setOnClickListener { viewModel.cancelFlow() }
        observeState()
        return binding.root
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                if (state.requiredAttributes != renderedAttributes) {
                    renderAttributes(state.requiredAttributes)
                }
                binding.submitAttributes.isEnabled = !state.busy
                binding.flowStatus.text = state.status
            }
        }
    }

    private fun renderAttributes(attributeNames: List<String>) {
        val previousValues = attributeInputs.mapValues { it.value.text?.toString().orEmpty() }
        attributeInputs.clear()
        binding.attributeFields.removeAllViews()
        renderedAttributes = attributeNames.distinct()

        renderedAttributes.forEach { attributeName ->
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
        val builder = UserAttributes.Builder()
        attributeInputs.forEach { (name, input) ->
            val value = input.text?.toString().orEmpty()
            if (value.isBlank()) {
                return@forEach
            }
            when {
                name.equals("city", ignoreCase = true) ->
                    builder.city(value)
                name.equals("country", ignoreCase = true) ->
                    builder.country(value)
                name.equals("givenName", ignoreCase = true) ->
                    builder.givenName(value)
                name.equals("surname", ignoreCase = true) ->
                    builder.surname(value)
                name.equals("flatusername", ignoreCase = true) ->
                    builder.flatUsername(value)
                else -> builder.customAttribute(name, value)
            }
        }
        viewModel.submitAttributes(builder.build())
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        attributeInputs.clear()
    }
}
