package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentVerificationContactBinding
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class StrongAuthVerificationContactFragment : NativeAuthFlowDialogFragment() {
    private var _binding: FragmentVerificationContactBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVerificationContactBinding.inflate(inflater, container, false)
        val authMethod = viewModel.uiState.value.selectedAuthMethod
        if (authMethod?.challengeChannel?.uppercase() == "SMS") {
            binding.hintText.text = getString(R.string.strong_auth_sms_hint_text_value)
            binding.contactTypeHint.text = getString(R.string.attribute_phone_number)
        } else {
            binding.hintText.text = getString(R.string.strong_auth_email_hint_text_value)
            binding.contactTypeHint.text = getString(R.string.attribute_email)
        }
        binding.verifyCode.setOnClickListener {
            val contact = binding.emailText.text.toString().ifBlank {
                authMethod?.loginHint.orEmpty()
            }
            viewModel.submitVerificationContact(contact)
        }
        binding.cancelFlow.setOnClickListener { viewModel.cancelFlow() }
        observeState()
        return binding.root
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.verifyCode.isEnabled = !state.busy
                binding.flowStatus.text = state.status
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
