package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentMfaBinding
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.utils.AppUtil
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.utils.NavigationUtil
import com.microsoft.identity.nativeauth.AuthMethod
import com.microsoft.identity.nativeauth.statemachine.results.MFARequiredResult
import com.microsoft.identity.nativeauth.statemachine.states.MFARequiredState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SignInMFAFragment : Fragment(){
    private lateinit var currentState: MFARequiredState
    private lateinit var appUtil: AppUtil
    private var _binding: FragmentMfaBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: AuthMethodAdapter

    companion object {
        private val TAG = SignInMFAFragment::class.java.simpleName
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMfaBinding.inflate(inflater, container, false)

        val bundle = this.arguments
        currentState = (bundle?.getParcelable(NavigationUtil.STATE) as? MFARequiredState)!!

        appUtil = AppUtil(requireContext(), requireActivity())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        loadMethods()
    }

    private fun setupRecyclerView() {
        binding.authMethodRecyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun loadMethods() {
        viewLifecycleOwner.lifecycleScope.launch {
            val methods = getAuthMethodList()
            adapter = AuthMethodAdapter(methods) { authMethod ->
                handleAuthMethodClick(authMethod)
            }
            binding.authMethodRecyclerView.adapter = adapter
        }
    }

    private suspend fun getAuthMethodList(): List<AuthMethod> = withContext(Dispatchers.IO) {
        var authMethodList = listOf<AuthMethod>()
        val getAuthActionResult = currentState.getAuthMethods()
        if (getAuthActionResult is MFARequiredResult.SelectionRequired) {
                authMethodList = getAuthActionResult.authMethods
        } else {
            appUtil.errorHandler.handleUnexpectedError(getAuthActionResult.toString())
        }
        return@withContext authMethodList
    }

    private fun handleAuthMethodClick(method: AuthMethod) {
        CoroutineScope(Dispatchers.IO).launch {
            val sendChallengeResult = currentState.sendChallenge(method.id)
            if (sendChallengeResult is MFARequiredResult.VerificationRequired) {
//                appUtil.navigation.navigateToSignInCode(
//                    nextState = sendChallengeResult.nextState
//                )
                appUtil.navigation.navigateToSignInMFACode(
                    nextState = sendChallengeResult.nextState,
                )
            } else {
                appUtil.errorHandler.handleUnexpectedError(sendChallengeResult.toString())
            }
        }
    }
}