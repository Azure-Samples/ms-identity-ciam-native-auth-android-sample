package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.databinding.ActivityMainBinding
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.Prompt
import com.microsoft.identity.client.exception.MsalException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        private const val FLOW_DIALOG_TAG = "native_auth_flow_dialog"
        private val WEB_SCOPES = mutableListOf("openid", "profile", "email")
    }

    private lateinit var viewModel: NativeAuthViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        AuthClient.initialize(
            this,
            intent.getStringExtra(AuthClient.EXTRA_CLIENT_ID),
            intent.getStringExtra(AuthClient.EXTRA_AUTHORITY_URL)
        )
        viewModel = ViewModelProvider(
            this,
            NativeAuthViewModelFactory(
                AuthClient.getAuthClient(),
                AuthClient.getAuthManager()
            )
        )[NativeAuthViewModel::class.java]

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val current =
                        supportFragmentManager.findFragmentById(R.id.scenario_fragment)
                    if (current is HomeFragment) {
                        finish()
                    } else {
                        viewModel.returnHome()
                    }
                }
            }
        )

        showDestination(viewModel.uiState.value.destination)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.navigation.collect { destination ->
                        showDestination(destination)
                    }
                }
                launch {
                    viewModel.browserSignInRequests.collect { request ->
                        startBrowserSignIn(request)
                    }
                }
            }
        }
    }

    private fun showDestination(destination: NativeAuthDestination) {
        when (destination) {
            NativeAuthDestination.PICK_AUTH_METHOD -> {
                showFlowDialog(PickAuthMethodFragment())
                return
            }
            NativeAuthDestination.MFA_VERIFICATION -> {
                showFlowDialog(MFAVerificationFragment())
                return
            }
            NativeAuthDestination.STRONG_AUTH_CONTACT -> {
                showFlowDialog(StrongAuthVerificationContactFragment())
                return
            }
            else -> dismissFlowDialog()
        }

        val fragment = when (destination) {
            NativeAuthDestination.HOME -> HomeFragment()
            NativeAuthDestination.SIGN_IN_CODE -> SignInCodeFragment()
            NativeAuthDestination.SIGN_UP_CODE -> SignUpCodeFragment()
            NativeAuthDestination.PASSWORD -> PasswordRequiredFragment()
            NativeAuthDestination.RESET_PASSWORD_CODE -> PasswordResetCodeFragment()
            NativeAuthDestination.NEW_PASSWORD -> PasswordResetNewPasswordFragment()
            NativeAuthDestination.ATTRIBUTES -> SignUpAttributesFragment()
            NativeAuthDestination.PICK_AUTH_METHOD,
            NativeAuthDestination.MFA_VERIFICATION,
            NativeAuthDestination.STRONG_AUTH_CONTACT -> return
        }
        replaceFragment(fragment)
    }

    private fun showFlowDialog(dialog: DialogFragment) {
        if (supportFragmentManager.findFragmentById(R.id.scenario_fragment) == null) {
            replaceFragment(HomeFragment())
            supportFragmentManager.executePendingTransactions()
        }
        val current = supportFragmentManager.findFragmentByTag(FLOW_DIALOG_TAG)
        if (current?.javaClass == dialog.javaClass) {
            return
        }
        (current as? DialogFragment)?.dismissAllowingStateLoss()
        supportFragmentManager.executePendingTransactions()
        dialog.showNow(supportFragmentManager, FLOW_DIALOG_TAG)
    }

    private fun dismissFlowDialog() {
        (supportFragmentManager.findFragmentByTag(FLOW_DIALOG_TAG) as? DialogFragment)
            ?.dismissAllowingStateLoss()
    }

    private fun replaceFragment(fragment: Fragment) {
        val current = supportFragmentManager.findFragmentById(R.id.scenario_fragment)
        if (current?.javaClass == fragment.javaClass) {
            return
        }
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.scenario_fragment, fragment)
            .commit()
    }

    private fun startBrowserSignIn(request: BrowserSignInRequest) {
        val builder = AcquireTokenParameters.Builder()
            .startAuthorizationFromActivity(this)
            .withScopes(WEB_SCOPES)
            .withPrompt(Prompt.LOGIN)
            .withCallback(object : AuthenticationCallback {
                override fun onSuccess(authenticationResult: IAuthenticationResult) {
                    viewModel.completeBrowserSignIn(request.displayName)
                }

                override fun onError(exception: MsalException) {
                    viewModel.failBrowserSignIn(
                        exception.message ?: exception.errorCode
                    )
                }

                override fun onCancel() {
                    viewModel.failBrowserSignIn(
                        getString(R.string.browser_sign_in_cancelled)
                    )
                }
            })
        request.domainHint?.let(builder::withDomainHint)
        AuthClient.getAuthClient().acquireToken(AcquireTokenParameters(builder))
    }
}
