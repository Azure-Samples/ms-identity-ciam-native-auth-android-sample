package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentPasswordBinding
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class PasswordRequiredFragment : Fragment() {
    private var _binding: FragmentPasswordBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: NativeAuthViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPasswordBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(requireActivity())[NativeAuthViewModel::class.java]
        binding.hintText.setText(R.string.password_required_hint_text_value)
        binding.create.setOnClickListener {
            viewModel.submitPassword(readPassword())
            binding.passwordText.text?.clear()
        }
        binding.cancelFlow.setOnClickListener { viewModel.cancelFlow() }
        observeState()
        return binding.root
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.create.isEnabled = !state.busy
                binding.flowStatus.text = state.status
            }
        }
    }

    private fun readPassword(): CharArray {
        val password = CharArray(binding.passwordText.length())
        binding.passwordText.text?.getChars(0, binding.passwordText.length(), password, 0)
        return password
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
