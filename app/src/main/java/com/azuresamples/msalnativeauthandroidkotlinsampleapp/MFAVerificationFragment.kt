package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentMfaChallengeBinding
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MFAVerificationFragment : NativeAuthFlowDialogFragment() {
    private var _binding: FragmentMfaChallengeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMfaChallengeBinding.inflate(inflater, container, false)
        binding.verifyChallenge.setOnClickListener {
            viewModel.submitChallenge(binding.challengeText.text.toString())
        }
        binding.resendChallengeText.setOnClickListener {
            binding.challengeText.text?.clear()
            viewModel.resendChallenge()
        }
        binding.cancelFlow.setOnClickListener { viewModel.cancelFlow() }
        observeState()
        return binding.root
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.verifyChallenge.isEnabled = !state.busy
                binding.resendChallengeText.isVisible = state.canResendChallenge
                binding.resendChallengeText.isEnabled =
                    state.canResendChallenge && !state.busy
                binding.flowStatus.text = state.status
                if (!state.channel.isNullOrBlank() && !state.sentTo.isNullOrBlank()) {
                    binding.hintText.text = getString(
                        R.string.mfa_challenge_hint_text_value,
                        state.channel,
                        state.sentTo
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
