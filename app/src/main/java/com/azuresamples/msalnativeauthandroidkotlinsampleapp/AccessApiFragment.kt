package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.FragmentAccessApiBinding
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication
import com.microsoft.identity.nativeauth.statemachine.results.GetAccessTokenResult
import com.microsoft.identity.nativeauth.statemachine.results.GetAccountResult
import com.microsoft.identity.nativeauth.statemachine.states.AccountState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class AccessApiFragment : Fragment() {
    private lateinit var authClient: INativeAuthPublicClientApplication
    private lateinit var apiClient: ApiClient
    private var _binding: FragmentAccessApiBinding? = null
    private val binding get() = _binding!!

    companion object {
        private val TAG = AccessApiFragment::class.java.simpleName
        private enum class STATUS { SignedIn, SignedOut }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAccessApiBinding.inflate(inflater, container, false)

        (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.title_access_protected_api)

        authClient = AuthClient.getAuthClient()

        apiClient = ApiClient(requireContext())

        init()

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        getStateAndUpdateUI()
    }

    private fun init() {
        initializeButtonListeners()
    }

    private fun initializeButtonListeners() {
        binding.postTodo.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                val accountResult = authClient.getCurrentAccount()
                when (accountResult) {
                    is GetAccountResult.AccountFound -> {
                        postData(accountResult.resultValue)
                    }
                    is GetAccountResult.NoAccountFound -> {
                        displaySignedOutState()
                    }
                }
            }
        }

        binding.getTodo.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                val accountResult = authClient.getCurrentAccount()
                when (accountResult) {
                    is GetAccountResult.AccountFound -> {
                        getData(accountResult.resultValue)
                    }
                    is GetAccountResult.NoAccountFound -> {
                        displaySignedOutState()
                    }
                }
            }
        }
    }

    private fun getStateAndUpdateUI() {
        CoroutineScope(Dispatchers.Main).launch {
            val accountResult = authClient.getCurrentAccount()
            when (accountResult) {
                is GetAccountResult.AccountFound -> {
                    displaySignedInState(accountResult.resultValue)
                }
                is GetAccountResult.NoAccountFound -> {
                    displaySignedOutState()
                }
            }
        }
    }

    private fun postData(accountState: AccountState) {
        CoroutineScope(Dispatchers.Main).launch {
            val accessTokenState = accountState.getAccessToken()
            if (accessTokenState is GetAccessTokenResult.Complete) {
                val accessToken = accessTokenState.resultValue.accessToken
                try {
                    val apiResponse = withContext(Dispatchers.IO) {
                        apiClient.postTodoListItem(accessToken)
                    }
                    binding.requestResponse.text = apiResponse.message
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun getData(accountState: AccountState) {
        CoroutineScope(Dispatchers.Main).launch {
            val accessTokenState = accountState.getAccessToken()
            if (accessTokenState is GetAccessTokenResult.Complete) {
                val accessToken = accessTokenState.resultValue.accessToken
                try {
                    val apiResponse = withContext(Dispatchers.IO) {
                        apiClient.getAllToDoListItems(accessToken)
                    }
                    binding.requestResponse.text = apiResponse.message
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun displaySignedInState(accountState: AccountState) {
        updateUI(STATUS.SignedIn)
        displayAccount(accountState)
    }

    private fun displaySignedOutState() {
        updateUI(STATUS.SignedOut)
        emptyResults()
    }

    private fun updateUI(status: STATUS) {
        when (status) {
            STATUS.SignedIn -> {
                binding.getTodo.isEnabled = true
                binding.postTodo.isEnabled = true
            }
            STATUS.SignedOut -> {
                binding.getTodo.isEnabled = false
                binding.postTodo.isEnabled = false
            }
        }
    }

    private fun emptyResults() {
        binding.resultAccessToken.text = ""
        binding.requestResponse.text = ""
    }

    private fun displayAccount(accountState: AccountState) {
        CoroutineScope(Dispatchers.Main).launch {
            val accessTokenState = accountState.getAccessToken()
            if (accessTokenState is GetAccessTokenResult.Complete) {
                val accessToken = accessTokenState.resultValue.accessToken
                binding.resultAccessToken.text = accessToken
                Log.d(TAG, "Access token: $accessToken")
            }
        }
    }
}
