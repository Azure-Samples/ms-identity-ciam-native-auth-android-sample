package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentCodeBinding
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class SignUpCodeFragment : Fragment() {
    private var _binding: FragmentCodeBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: NativeAuthViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCodeBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(requireActivity())[NativeAuthViewModel::class.java]
        binding.verifyCode.setOnClickListener {
            viewModel.submitCode(binding.codeText.text.toString())
        }
        binding.resendCodeText.setOnClickListener {
            binding.codeText.text?.clear()
            viewModel.resendCode()
        }
        binding.cancelFlow.setOnClickListener { viewModel.cancelFlow() }
        observeState()
        return binding.root
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.verifyCode.isEnabled = !state.busy
                binding.resendCodeText.isEnabled = !state.busy
                binding.flowStatus.text = state.status
                if (!state.channel.isNullOrBlank() && !state.sentTo.isNullOrBlank()) {
                    binding.hintText.text = getString(
                        R.string.verification_code_hint_text_value,
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
