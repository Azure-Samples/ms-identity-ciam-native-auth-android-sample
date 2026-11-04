package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.PersistableBundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentHomeBinding
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    companion object {
        private const val WEB_API_URL = ""
        private const val CLIPBOARD_SENSITIVE_EXTRA = "android.content.extra.IS_SENSITIVE"
        private val WEB_API_SCOPES = emptyList<String>()
    }

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: NativeAuthViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(requireActivity())[NativeAuthViewModel::class.java]
        initializeListeners()
        observeState()
        return binding.root
    }

    private fun initializeListeners() {
        binding.useV2Checkbox.setOnCheckedChangeListener { _, checked ->
            viewModel.setUseNativeAuthV2(checked)
        }
        binding.signIn.setOnClickListener {
            viewModel.startSignIn(
                binding.emailText.text.toString(),
                readPassword().takeIf { it.isNotEmpty() }
            )
            binding.passwordText.text?.clear()
        }
        binding.signUp.setOnClickListener {
            viewModel.startSignUp(
                binding.emailText.text.toString(),
                readPassword().takeIf { it.isNotEmpty() },
                binding.givenNameText.text.toString(),
                binding.lastNameText.text.toString()
            )
            binding.passwordText.text?.clear()
        }
        binding.resetPassword.setOnClickListener {
            viewModel.startResetPassword(binding.emailText.text.toString())
        }
        binding.signInWithBrowser.setOnClickListener {
            viewModel.requestBrowserSignIn()
        }
        binding.signInWithGoogle.setOnClickListener {
            viewModel.requestBrowserSignIn("Google", getString(R.string.google))
        }
        binding.signInWithFacebook.setOnClickListener {
            viewModel.requestBrowserSignIn("Facebook", getString(R.string.facebook))
        }
        binding.signInWithLinkedin.setOnClickListener {
            viewModel.requestBrowserSignIn(
                "www.linkedin.com",
                getString(R.string.linkedin)
            )
        }
        binding.callProtectedApi.setOnClickListener {
            viewModel.callProtectedApi(WEB_API_URL, WEB_API_SCOPES)
        }
        binding.signOut.setOnClickListener {
            viewModel.signOut()
        }
        binding.accessTokenText.setOnClickListener {
            val token = viewModel.accessTokenForCopy() ?: return@setOnClickListener
            val clip = ClipData.newPlainText(getString(R.string.access_token_clip_label), token)
            clip.description.extras = PersistableBundle().apply {
                putBoolean(CLIPBOARD_SENSITIVE_EXTRA, true)
            }
            val clipboard = requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(clip)
            Toast.makeText(
                requireContext(),
                R.string.access_token_copied,
                Toast.LENGTH_SHORT
            ).show()
        }
        binding.emailText.doAfterTextChanged {
            updateActionAvailability(viewModel.uiState.value)
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                if (binding.useV2Checkbox.isChecked != state.useNativeAuthV2) {
                    binding.useV2Checkbox.isChecked = state.useNativeAuthV2
                }
                binding.useV2Checkbox.isEnabled = !state.flowActive && !state.busy
                binding.progress.visibility = if (state.busy) View.VISIBLE else View.GONE

                val signedIn = state.accountState != null
                binding.signedOutContent.visibility =
                    if (signedIn) View.GONE else View.VISIBLE
                binding.signedInContent.visibility =
                    if (signedIn) View.VISIBLE else View.GONE
                binding.signedOutStatusText.text = state.status
                binding.signedOutStatusText.visibility =
                    if (!signedIn && state.status.isNotBlank()) View.VISIBLE else View.GONE
                binding.signedInStatusText.text = state.status
                binding.signedInStatusText.visibility =
                    if (signedIn && state.status.isNotBlank()) View.VISIBLE else View.GONE

                if (signedIn) {
                    binding.accountText.text = state.accountName?.let {
                        getString(R.string.signed_in_as, it)
                    }.orEmpty()
                    val token = viewModel.accessTokenForCopy()
                    binding.accessTokenText.text = when (state.accessTokenStatus) {
                        AccessTokenStatus.LOADING -> getString(R.string.access_token_loading)
                        AccessTokenStatus.AVAILABLE -> {
                            if (token == null) {
                                getString(R.string.access_token_unavailable)
                            } else {
                                getString(
                                    R.string.access_token_masked,
                                    token.take(8),
                                    token.takeLast(6)
                                )
                            }
                        }
                        AccessTokenStatus.ERROR,
                        AccessTokenStatus.NONE -> getString(R.string.access_token_unavailable)
                    }
                    binding.accessTokenText.isEnabled = token != null
                    binding.accessTokenText.contentDescription = binding.accessTokenText.text
                }

                updateActionAvailability(state)
                binding.callProtectedApi.isEnabled = !state.busy && signedIn
                binding.signOut.isEnabled = !state.busy && signedIn
            }
        }
    }

    private fun updateActionAvailability(state: NativeAuthUiState) {
        val signedIn = state.accountState != null
        val canStart = !state.busy && !state.flowActive && !signedIn
        val hasUsername = binding.emailText.text?.isNotBlank() == true
        binding.signIn.isEnabled = canStart && hasUsername
        binding.signUp.isEnabled = canStart && hasUsername
        binding.resetPassword.isEnabled = canStart && hasUsername
        binding.signInWithBrowser.isEnabled = canStart
        binding.signInWithGoogle.isEnabled = canStart
        binding.signInWithFacebook.isEnabled = canStart
        binding.signInWithLinkedin.isEnabled = canStart
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
