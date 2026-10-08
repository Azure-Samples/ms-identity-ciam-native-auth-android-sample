package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.os.Parcelable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.microsoft.identity.nativeauth.AuthMethod
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication
import com.microsoft.identity.nativeauth.UserAttributes
import com.microsoft.identity.nativeauth.parameters.NativeAuthChallengeAuthMethodParameters
import com.microsoft.identity.nativeauth.parameters.NativeAuthGetAccessTokenParameters
import com.microsoft.identity.nativeauth.parameters.NativeAuthResetPasswordParameters
import com.microsoft.identity.nativeauth.parameters.NativeAuthSignInContinuationParameters
import com.microsoft.identity.nativeauth.parameters.NativeAuthSignInParameters
import com.microsoft.identity.nativeauth.parameters.NativeAuthSignUpParameters
import com.microsoft.identity.nativeauth.statemachine.NativeAuthFlowScenarioV2
import com.microsoft.identity.nativeauth.statemachine.errors.BrowserRequiredError
import com.microsoft.identity.nativeauth.statemachine.errors.Error as NativeAuthError
import com.microsoft.identity.nativeauth.statemachine.errors.GetAccessTokenError
import com.microsoft.identity.nativeauth.statemachine.errors.MFARequestChallengeError
import com.microsoft.identity.nativeauth.statemachine.errors.MFARequestChallengeErrorV2
import com.microsoft.identity.nativeauth.statemachine.errors.MFASubmitChallengeError
import com.microsoft.identity.nativeauth.statemachine.errors.MFASubmitChallengeErrorV2
import com.microsoft.identity.nativeauth.statemachine.errors.NativeAuthErrorV2
import com.microsoft.identity.nativeauth.statemachine.errors.RegisterStrongAuthChallengeError
import com.microsoft.identity.nativeauth.statemachine.errors.RegisterStrongAuthChallengeErrorV2
import com.microsoft.identity.nativeauth.statemachine.errors.RegisterStrongAuthSubmitChallengeError
import com.microsoft.identity.nativeauth.statemachine.errors.RegisterStrongAuthSubmitChallengeErrorV2
import com.microsoft.identity.nativeauth.statemachine.errors.ResetPasswordError
import com.microsoft.identity.nativeauth.statemachine.errors.ResetPasswordErrorV2
import com.microsoft.identity.nativeauth.statemachine.errors.ResetPasswordSubmitPasswordError
import com.microsoft.identity.nativeauth.statemachine.errors.ResendCodeError
import com.microsoft.identity.nativeauth.statemachine.errors.SignInContinuationError
import com.microsoft.identity.nativeauth.statemachine.errors.SignInError
import com.microsoft.identity.nativeauth.statemachine.errors.SignInErrorV2
import com.microsoft.identity.nativeauth.statemachine.errors.SignUpError
import com.microsoft.identity.nativeauth.statemachine.errors.SignUpErrorV2
import com.microsoft.identity.nativeauth.statemachine.errors.SignUpSubmitAttributesError
import com.microsoft.identity.nativeauth.statemachine.errors.SignUpSubmitPasswordError
import com.microsoft.identity.nativeauth.statemachine.errors.SubmitAttributesErrorV2
import com.microsoft.identity.nativeauth.statemachine.errors.SubmitCodeError
import com.microsoft.identity.nativeauth.statemachine.errors.SubmitCodeErrorV2
import com.microsoft.identity.nativeauth.statemachine.errors.SubmitNewPasswordErrorV2
import com.microsoft.identity.nativeauth.statemachine.errors.SubmitPasswordErrorV2
import com.microsoft.identity.nativeauth.statemachine.results.GetAccountResult
import com.microsoft.identity.nativeauth.statemachine.results.GetAccessTokenResult
import com.microsoft.identity.nativeauth.statemachine.results.MFARequiredResult
import com.microsoft.identity.nativeauth.statemachine.results.NativeAuthResultV2
import com.microsoft.identity.nativeauth.statemachine.results.RegisterStrongAuthChallengeResult
import com.microsoft.identity.nativeauth.statemachine.results.ResetPasswordResendCodeResult
import com.microsoft.identity.nativeauth.statemachine.results.ResetPasswordResult
import com.microsoft.identity.nativeauth.statemachine.results.ResetPasswordStartResult
import com.microsoft.identity.nativeauth.statemachine.results.ResetPasswordSubmitCodeResult
import com.microsoft.identity.nativeauth.statemachine.results.SignInResendCodeResult
import com.microsoft.identity.nativeauth.statemachine.results.SignInResult
import com.microsoft.identity.nativeauth.statemachine.results.SignOutResult
import com.microsoft.identity.nativeauth.statemachine.results.SignUpResendCodeResult
import com.microsoft.identity.nativeauth.statemachine.results.SignUpResult
import com.microsoft.identity.nativeauth.statemachine.states.AccountState
import com.microsoft.identity.nativeauth.statemachine.states.AwaitingMFAState
import com.microsoft.identity.nativeauth.statemachine.states.MFARequiredState
import com.microsoft.identity.nativeauth.statemachine.states.RegisterStrongAuthState
import com.microsoft.identity.nativeauth.statemachine.states.RegisterStrongAuthVerificationRequiredState
import com.microsoft.identity.nativeauth.statemachine.states.ResetPasswordCodeRequiredState
import com.microsoft.identity.nativeauth.statemachine.states.ResetPasswordMethodRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.ResetPasswordPasswordRequiredState
import com.microsoft.identity.nativeauth.statemachine.states.SignInCodeRequiredState
import com.microsoft.identity.nativeauth.statemachine.states.SignInContinuationState
import com.microsoft.identity.nativeauth.statemachine.states.SignInPasswordRequiredState
import com.microsoft.identity.nativeauth.statemachine.states.SignUpAttributesRequiredState
import com.microsoft.identity.nativeauth.statemachine.states.SignUpCodeRequiredState
import com.microsoft.identity.nativeauth.statemachine.states.SignUpPasswordRequiredState
import com.microsoft.identity.nativeauth.statemachine.states.StrongAuthRegistrationRequiredStateV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class NativeAuthApi {
    V1,
    V2
}

enum class AccessTokenStatus {
    NONE,
    LOADING,
    AVAILABLE,
    ERROR
}

data class BrowserSignInRequest(
    val domainHint: String?,
    val displayName: String
)

enum class NativeAuthDestination {
    HOME,
    SIGN_IN_CODE,
    SIGN_UP_CODE,
    PASSWORD,
    RESET_PASSWORD_CODE,
    NEW_PASSWORD,
    ATTRIBUTES,
    PICK_AUTH_METHOD,
    MFA_VERIFICATION,
    STRONG_AUTH_CONTACT
}

data class NativeAuthUiState(
    val useNativeAuthV2: Boolean = true,
    val flowActive: Boolean = false,
    val busy: Boolean = false,
    val activeApi: NativeAuthApi? = null,
    val destination: NativeAuthDestination = NativeAuthDestination.HOME,
    val accountState: AccountState? = null,
    val accountName: String? = null,
    val accessTokenStatus: AccessTokenStatus = AccessTokenStatus.NONE,
    val status: String = "",
    val requiredAttributes: List<String> = emptyList(),
    val authMethods: List<AuthMethod> = emptyList(),
    val selectedAuthMethod: AuthMethod? = null,
    val sentTo: String? = null,
    val channel: String? = null,
    val canResendChallenge: Boolean = false
)

class NativeAuthViewModel(
    private val authClient: INativeAuthPublicClientApplication,
    private val authManager: AuthManager,
    autoRestoreAccount: Boolean = true,
    private val initialV1MfaRequester:
        suspend (AwaitingMFAState, AuthMethod) -> MFARequiredResult =
        { state, method -> state.requestChallenge(method) },
    private val repeatedV1MfaRequester:
        suspend (MFARequiredState, AuthMethod) -> MFARequiredResult =
        { state, method -> state.requestChallenge(method) }
) : ViewModel() {

    private val _uiState = MutableStateFlow(NativeAuthUiState())
    val uiState: StateFlow<NativeAuthUiState> = _uiState.asStateFlow()

    private val _navigation = MutableSharedFlow<NativeAuthDestination>(
        replay = 1,
        extraBufferCapacity = 1
    )
    val navigation: SharedFlow<NativeAuthDestination> = _navigation.asSharedFlow()

    private val _browserSignInRequests = MutableSharedFlow<BrowserSignInRequest>(
        extraBufferCapacity = 1
    )
    val browserSignInRequests: SharedFlow<BrowserSignInRequest> =
        _browserSignInRequests.asSharedFlow()

    private var v1State: Parcelable? = null
    private var v1SelectionState: Parcelable? = null
    private var flowGeneration = 0L
    private var accessToken: String? = null

    init {
        if (autoRestoreAccount) {
            refreshAccount()
        }
    }

    fun setUseNativeAuthV2(enabled: Boolean) {
        if (!_uiState.value.flowActive) {
            _uiState.value = _uiState.value.copy(useNativeAuthV2 = enabled)
        }
    }

    fun refreshAccount() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true)
            when (val result = authClient.getCurrentAccount()) {
                is GetAccountResult.AccountFound -> updateAccount(result.resultValue)
                is GetAccountResult.NoAccountFound -> {
                    accessToken = null
                    _uiState.value = _uiState.value.copy(
                        busy = false,
                        accountState = null,
                        accountName = null,
                        accessTokenStatus = AccessTokenStatus.NONE
                    )
                }
                else -> {
                    _uiState.value = _uiState.value.copy(
                        busy = false,
                        status = errorMessage(result)
                    )
                }
            }
        }
    }

    fun startSignIn(email: String, password: CharArray?) {
        val generation = beginFlow()
        if (generation == null) {
            password?.fill('\u0000')
            return
        }
        launchFlowOperation(generation) {
            try {
                if (_uiState.value.activeApi == NativeAuthApi.V2) {
                    handleV2Result(
                        authManager.signIn(email, password) {
                            isCurrentFlow(generation)
                        },
                        generation
                    )
                } else {
                    val parameters = NativeAuthSignInParameters(username = email)
                    parameters.password = password
                    handleV1SignInResult(authClient.signIn(parameters), generation)
                }
            } catch (exception: Exception) {
                failFlow(exceptionMessage(exception), generation)
            } finally {
                password?.fill('\u0000')
            }
        }
    }

    fun startSignUp(
        email: String,
        password: CharArray?,
        givenName: String,
        lastName: String
    ) {
        val generation = beginFlow()
        if (generation == null) {
            password?.fill('\u0000')
            return
        }
        launchFlowOperation(generation) {
            try {
                val attributes = buildOptionalAttributes(givenName, lastName)
                if (_uiState.value.activeApi == NativeAuthApi.V2) {
                    handleV2Result(
                        authManager.signUp(email, password, attributes) {
                            isCurrentFlow(generation)
                        },
                        generation
                    )
                } else {
                    val parameters = NativeAuthSignUpParameters(username = email)
                    parameters.password = password
                    parameters.attributes = attributes
                    handleV1SignUpResult(authClient.signUp(parameters), generation)
                }
            } catch (exception: Exception) {
                failFlow(exceptionMessage(exception), generation)
            } finally {
                password?.fill('\u0000')
            }
        }
    }

    fun startResetPassword(email: String) {
        val generation = beginFlow()
        if (generation == null) {
            return
        }
        launchFlowOperation(generation) {
            try {
                if (_uiState.value.activeApi == NativeAuthApi.V2) {
                    handleV2Result(
                        authManager.resetPassword(email) {
                            isCurrentFlow(generation)
                        },
                        generation
                    )
                } else {
                    when (val result = authClient.resetPassword(
                        NativeAuthResetPasswordParameters(username = email)
                    )) {
                        is ResetPasswordStartResult.CodeRequired -> {
                            continueV1(
                                result.nextState,
                                NativeAuthDestination.RESET_PASSWORD_CODE,
                                sentTo = result.sentTo,
                                channel = result.channel,
                                generation = generation
                            )
                        }
                        is ResetPasswordError -> handleV1Error(result, generation)
                    }
                }
            } catch (exception: Exception) {
                failFlow(exceptionMessage(exception), generation)
            }
        }
    }

    fun submitCode(code: String) {
        val generation = flowGeneration
        launchFlowOperation(generation) {
            _uiState.value = _uiState.value.copy(busy = true)
            try {
                if (_uiState.value.activeApi == NativeAuthApi.V2) {
                    handleV2ResultOrInvalid(
                        authManager.submitCode(code) { isCurrentFlow(generation) },
                        generation
                    )
                    return@launchFlowOperation
                }
                when (val state = v1State) {
                    is SignInCodeRequiredState ->
                        handleV1SignInResult(state.submitCode(code), generation)
                    is SignUpCodeRequiredState ->
                        handleV1SignUpResult(state.submitCode(code), generation)
                    is ResetPasswordCodeRequiredState -> {
                        when (val result = state.submitCode(code)) {
                            is ResetPasswordSubmitCodeResult.PasswordRequired -> {
                                continueV1(
                                    result.nextState,
                                    NativeAuthDestination.NEW_PASSWORD,
                                    generation = generation
                                )
                            }
                            is SubmitCodeError ->
                                handleRecoverableV1Error(result, generation)
                        }
                    }
                    else -> invalidContinuation(generation)
                }
            } catch (exception: Exception) {
                failFlow(exceptionMessage(exception), generation)
            }
        }
    }

    fun resendCode() {
        val generation = flowGeneration
        launchFlowOperation(generation) {
            _uiState.value = _uiState.value.copy(busy = true)
            try {
                if (_uiState.value.activeApi == NativeAuthApi.V2) {
                    handleV2ResultOrInvalid(
                        authManager.resendCode { isCurrentFlow(generation) },
                        generation
                    )
                    return@launchFlowOperation
                }
                when (val state = v1State) {
                    is SignInCodeRequiredState -> when (val result = state.resendCode()) {
                        is SignInResendCodeResult.Success -> {
                            continueV1(
                                result.nextState,
                                NativeAuthDestination.SIGN_IN_CODE,
                                sentTo = _uiState.value.sentTo,
                                channel = _uiState.value.channel,
                                status = "A new code was sent.",
                                generation = generation
                            )
                        }
                        is ResendCodeError -> handleRecoverableV1Error(result, generation)
                    }
                    is SignUpCodeRequiredState -> when (val result = state.resendCode()) {
                        is SignUpResendCodeResult.Success -> {
                            continueV1(
                                result.nextState,
                                NativeAuthDestination.SIGN_UP_CODE,
                                sentTo = _uiState.value.sentTo,
                                channel = _uiState.value.channel,
                                status = "A new code was sent.",
                                generation = generation
                            )
                        }
                        is ResendCodeError -> handleRecoverableV1Error(result, generation)
                    }
                    is ResetPasswordCodeRequiredState -> when (val result = state.resendCode()) {
                        is ResetPasswordResendCodeResult.Success -> {
                            continueV1(
                                result.nextState,
                                NativeAuthDestination.RESET_PASSWORD_CODE,
                                sentTo = _uiState.value.sentTo,
                                channel = _uiState.value.channel,
                                status = "A new code was sent.",
                                generation = generation
                            )
                        }
                        is ResendCodeError -> handleRecoverableV1Error(result, generation)
                    }
                    else -> invalidContinuation(generation)
                }
            } catch (exception: Exception) {
                failFlow(exceptionMessage(exception), generation)
            }
        }
    }

    fun submitPassword(password: CharArray) {
        val generation = flowGeneration
        launchFlowOperation(generation) {
            _uiState.value = _uiState.value.copy(busy = true)
            try {
                if (_uiState.value.activeApi == NativeAuthApi.V2) {
                    handleV2ResultOrInvalid(
                        authManager.submitPassword(password) { isCurrentFlow(generation) },
                        generation
                    )
                    return@launchFlowOperation
                }
                when (val state = v1State) {
                    is SignInPasswordRequiredState ->
                        handleV1SignInResult(state.submitPassword(password), generation)
                    is SignUpPasswordRequiredState ->
                        handleV1SignUpResult(state.submitPassword(password), generation)
                    else -> invalidContinuation(generation)
                }
            } catch (exception: Exception) {
                failFlow(exceptionMessage(exception), generation)
            } finally {
                password.fill('\u0000')
            }
        }
    }

    fun submitNewPassword(password: CharArray) {
        val generation = flowGeneration
        launchFlowOperation(generation) {
            _uiState.value = _uiState.value.copy(busy = true)
            try {
                if (_uiState.value.activeApi == NativeAuthApi.V2) {
                    handleV2ResultOrInvalid(
                        authManager.submitNewPassword(password) {
                            isCurrentFlow(generation)
                        },
                        generation
                    )
                    return@launchFlowOperation
                }
                val state = v1State as? ResetPasswordPasswordRequiredState
                    ?: return@launchFlowOperation invalidContinuation(generation)
                when (val result = state.submitPassword(password)) {
                    is ResetPasswordResult.Complete ->
                        signInAfterV1(result.nextState, generation)
                    is ResetPasswordSubmitPasswordError ->
                        handleRecoverableV1Error(result, generation)
                }
            } catch (exception: Exception) {
                failFlow(exceptionMessage(exception), generation)
            } finally {
                password.fill('\u0000')
            }
        }
    }

    fun submitAttributes(attributes: UserAttributes) {
        val generation = flowGeneration
        launchFlowOperation(generation) {
            _uiState.value = _uiState.value.copy(busy = true)
            try {
                if (_uiState.value.activeApi == NativeAuthApi.V2) {
                    handleV2ResultOrInvalid(
                        authManager.submitAttributes(attributes) {
                            isCurrentFlow(generation)
                        },
                        generation
                    )
                    return@launchFlowOperation
                }
                val state = v1State as? SignUpAttributesRequiredState
                    ?: return@launchFlowOperation invalidContinuation(generation)
                handleV1SignUpResult(state.submitAttributes(attributes), generation)
            } catch (exception: Exception) {
                failFlow(exceptionMessage(exception), generation)
            }
        }
    }

    fun selectAuthMethod(authMethod: AuthMethod) {
        val state = _uiState.value
        val generation = flowGeneration
        if (!isCurrentFlow(generation)) {
            return
        }
        _uiState.value = state.copy(selectedAuthMethod = authMethod, busy = true)
        launchFlowOperation(generation) {
            selectAuthMethodInternal(authMethod, generation)
        }
    }

    internal suspend fun selectAuthMethodInternal(
        authMethod: AuthMethod,
        generation: Long = flowGeneration
    ) {
        try {
            if (_uiState.value.activeApi == NativeAuthApi.V2) {
                when (authManager.currentState) {
                    is StrongAuthRegistrationRequiredStateV2 -> {
                        navigate(NativeAuthDestination.STRONG_AUTH_CONTACT, generation)
                    }
                    else -> {
                        handleV2ResultOrInvalid(
                            authManager.selectAuthMethod(authMethod) {
                                isCurrentFlow(generation)
                            },
                            generation
                        )
                    }
                }
                return
            }
            when (val current = v1State) {
                is AwaitingMFAState ->
                    requestInitialV1MfaChallenge(current, authMethod, generation)
                is RegisterStrongAuthState -> {
                    v1SelectionState = current
                    navigate(NativeAuthDestination.STRONG_AUTH_CONTACT, generation)
                }
                else -> invalidContinuation(generation)
            }
        } catch (exception: Exception) {
            failFlow(exceptionMessage(exception), generation)
        }
    }

    fun submitVerificationContact(contact: String) {
        val generation = flowGeneration
        val authMethod = _uiState.value.selectedAuthMethod
            ?: return invalidContinuation(generation)
        launchFlowOperation(generation) {
            _uiState.value = _uiState.value.copy(busy = true)
            try {
                if (_uiState.value.activeApi == NativeAuthApi.V2) {
                    handleV2ResultOrInvalid(
                        authManager.selectAuthMethod(authMethod, contact) {
                            isCurrentFlow(generation)
                        },
                        generation
                    )
                    return@launchFlowOperation
                }
                val state = v1SelectionState as? RegisterStrongAuthState
                    ?: return@launchFlowOperation invalidContinuation(generation)
                val result = state.challengeAuthMethod(
                    NativeAuthChallengeAuthMethodParameters(authMethod, contact)
                )
                when (result) {
                    is RegisterStrongAuthChallengeResult.VerificationRequired -> {
                        continueV1(
                            result.result.getNextState(),
                            NativeAuthDestination.MFA_VERIFICATION,
                            sentTo = result.result.getSentTo(),
                            channel = result.result.getChannel(),
                            generation = generation
                        )
                    }
                    is RegisterStrongAuthChallengeError ->
                        handleRecoverableV1Error(result, generation)
                }
            } catch (exception: Exception) {
                failFlow(exceptionMessage(exception), generation)
            }
        }
    }

    fun submitChallenge(challenge: String) {
        val generation = flowGeneration
        launchFlowOperation(generation) {
            _uiState.value = _uiState.value.copy(busy = true)
            try {
                if (_uiState.value.activeApi == NativeAuthApi.V2) {
                    handleV2ResultOrInvalid(
                        authManager.submitChallenge(challenge) {
                            isCurrentFlow(generation)
                        },
                        generation
                    )
                    return@launchFlowOperation
                }
                when (val state = v1State) {
                    is MFARequiredState ->
                        handleV1SignInResult(state.submitChallenge(challenge), generation)
                    is RegisterStrongAuthVerificationRequiredState -> {
                        when (val result = state.submitChallenge(challenge)) {
                            is SignInResult.Complete -> finishFlow(
                                result.resultValue,
                                "Sign in successful.",
                                generation
                            )
                            is RegisterStrongAuthSubmitChallengeError ->
                                handleRecoverableV1Error(result, generation)
                            else -> invalidContinuation(generation)
                        }
                    }
                    else -> invalidContinuation(generation)
                }
            } catch (exception: Exception) {
                failFlow(exceptionMessage(exception), generation)
            }
        }
    }

    fun resendChallenge() {
        if (!_uiState.value.canResendChallenge) {
            return
        }
        val generation = flowGeneration
        val method = _uiState.value.selectedAuthMethod
            ?: return invalidContinuation(generation)
        launchFlowOperation(generation) {
            resendChallengeInternal(method, generation)
        }
    }

    internal suspend fun resendChallengeInternal(
        method: AuthMethod,
        generation: Long = flowGeneration
    ) {
        if (!isCurrentFlow(generation)) {
            return
        }
        try {
            _uiState.value = _uiState.value.copy(busy = true)
            if (_uiState.value.activeApi == NativeAuthApi.V2) {
                handleV2ResultOrInvalid(
                    authManager.resendChallenge { isCurrentFlow(generation) },
                    generation
                )
                return
            }
            when (val state = v1State) {
                is MFARequiredState ->
                    requestRepeatedV1MfaChallenge(state, method, generation)
                else -> invalidContinuation(generation)
            }
        } catch (exception: Exception) {
            failFlow(exceptionMessage(exception), generation)
        }
    }

    fun signOut() {
        val account = _uiState.value.accountState ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true)
            when (val result = account.signOut()) {
                is SignOutResult.Complete -> finishFlow(null, "Sign out successful.")
                else -> {
                    _uiState.value = _uiState.value.copy(
                        busy = false,
                        status = errorMessage(result)
                    )
                }
            }
        }
    }

    fun callProtectedApi(webApiUrl: String, scopes: List<String>) {
        val account = _uiState.value.accountState ?: return
        if (webApiUrl.isBlank() || scopes.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                status = "Protected API is not configured. Set its URL and scopes in HomeFragment."
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                busy = true,
                accessTokenStatus = AccessTokenStatus.LOADING,
                status = "Retrieving an access token for the protected API."
            )
            when (val result = getAccessToken(account, scopes)) {
                is GetAccessTokenResult.Complete -> {
                    if (_uiState.value.accountState !== account) {
                        return@launch
                    }
                    accessToken = result.resultValue.accessToken
                    _uiState.value = _uiState.value.copy(
                        accessTokenStatus = AccessTokenStatus.AVAILABLE,
                        status = "Calling the protected API."
                    )
                    try {
                        val response = withContext(Dispatchers.IO) {
                            ApiClient.performGetApiRequest(
                                webApiUrl,
                                result.resultValue.accessToken
                            )
                        }
                        if (_uiState.value.accountState === account) {
                            _uiState.value = _uiState.value.copy(
                                busy = false,
                                status = "Protected API response (${response.statusCode}):\n" +
                                    response.body
                            )
                        }
                    } catch (exception: Exception) {
                        if (_uiState.value.accountState === account) {
                            _uiState.value = _uiState.value.copy(
                                busy = false,
                                status = exceptionMessage(exception)
                            )
                        }
                    }
                }
                is GetAccessTokenError -> {
                    if (_uiState.value.accountState === account) {
                        accessToken = null
                        _uiState.value = _uiState.value.copy(
                            busy = false,
                            accessTokenStatus = AccessTokenStatus.ERROR,
                            status = errorMessage(result)
                        )
                    }
                }
                else -> {
                    if (_uiState.value.accountState === account) {
                        accessToken = null
                        _uiState.value = _uiState.value.copy(
                            busy = false,
                            accessTokenStatus = AccessTokenStatus.ERROR,
                            status = result.toString()
                        )
                    }
                }
            }
        }
    }

    internal fun accessTokenForCopy(): String? = accessToken

    fun requestBrowserSignIn(domainHint: String? = null, displayName: String = "browser") {
        if (_uiState.value.flowActive || _uiState.value.busy) {
            return
        }

        _uiState.value = _uiState.value.copy(
            busy = true,
            status = "Signing in with $displayName."
        )
        if (!_browserSignInRequests.tryEmit(BrowserSignInRequest(domainHint, displayName))) {
            _uiState.value = _uiState.value.copy(
                busy = false,
                status = "Unable to start browser sign-in."
            )
        }
    }

    fun completeBrowserSignIn(displayName: String) {
        _uiState.value = _uiState.value.copy(
            busy = false,
            status = "Signed in successfully with $displayName."
        )
        refreshAccount()
    }

    fun failBrowserSignIn(message: String) {
        _uiState.value = _uiState.value.copy(
            busy = false,
            status = message
        )
    }

    fun authMethodPrompt(): Int {
        return when {
            v1State is RegisterStrongAuthState ||
                authManager.currentState is StrongAuthRegistrationRequiredStateV2 ->
                R.string.pick_auth_method_registration_text_value
            authManager.currentState is ResetPasswordMethodRequiredStateV2 ->
                R.string.pick_password_reset_method_text_value
            else -> R.string.pick_auth_method_text_value
        }
    }

    fun cancelFlow() {
        flowGeneration++
        clearContinuation()
        _uiState.value = _uiState.value.copy(
            flowActive = false,
            busy = false,
            activeApi = null,
            destination = NativeAuthDestination.HOME,
            status = "Authentication flow cancelled.",
            requiredAttributes = emptyList(),
            authMethods = emptyList(),
            selectedAuthMethod = null,
            sentTo = null,
            channel = null,
            canResendChallenge = false
        )
        _navigation.tryEmit(NativeAuthDestination.HOME)
    }

    fun returnHome() {
        if (_uiState.value.flowActive) {
            cancelFlow()
            return
        }
        _uiState.value = _uiState.value.copy(destination = NativeAuthDestination.HOME)
        _navigation.tryEmit(NativeAuthDestination.HOME)
        refreshAccount()
    }

    internal fun beginFlow(api: NativeAuthApi? = null): Long? {
        if (_uiState.value.flowActive) {
            return null
        }
        val selectedApi = api ?: if (_uiState.value.useNativeAuthV2) {
            NativeAuthApi.V2
        } else {
            NativeAuthApi.V1
        }
        flowGeneration++
        val generation = flowGeneration
        clearContinuation()
        _uiState.value = _uiState.value.copy(
            flowActive = true,
            busy = true,
            activeApi = selectedApi,
            status = "",
            requiredAttributes = emptyList(),
            authMethods = emptyList(),
            selectedAuthMethod = null,
            sentTo = null,
            channel = null,
            canResendChallenge = false
        )
        return generation
    }

    internal suspend fun handleV2Result(
        result: NativeAuthResultV2,
        generation: Long = flowGeneration
    ) {
        if (!isCurrentFlow(generation)) {
            return
        }
        when (result) {
            is NativeAuthResultV2.Complete -> finishFlow(
                result.resultValue,
                "Sign in successful.",
                generation
            )
            is NativeAuthResultV2.CodeRequired -> {
                val destination = when (result.scenario) {
                    NativeAuthFlowScenarioV2.SIGN_UP -> NativeAuthDestination.SIGN_UP_CODE
                    NativeAuthFlowScenarioV2.RESET_PASSWORD ->
                        NativeAuthDestination.RESET_PASSWORD_CODE
                    else -> NativeAuthDestination.SIGN_IN_CODE
                }
                continueV2(
                    destination,
                    result.sentTo,
                    result.channel,
                    generation = generation
                )
            }
            is NativeAuthResultV2.PasswordRequired ->
                continueV2(NativeAuthDestination.PASSWORD, generation = generation)
            is NativeAuthResultV2.NewPasswordRequired ->
                continueV2(NativeAuthDestination.NEW_PASSWORD, generation = generation)
            is NativeAuthResultV2.AttributesRequired -> {
                val requiredAttributes =
                    result.requiredAttributes.mapNotNull { it.attributeName }
                continueV2(
                    NativeAuthDestination.ATTRIBUTES,
                    requiredAttributes = requiredAttributes,
                    generation = generation
                )
            }
            is NativeAuthResultV2.AttributesInvalid ->
                continueV2(
                    NativeAuthDestination.ATTRIBUTES,
                    requiredAttributes = result.invalidAttributes,
                    generation = generation
                )
            is NativeAuthResultV2.MFARequired -> {
                continueV2(
                    NativeAuthDestination.PICK_AUTH_METHOD,
                    authMethods = result.authMethods,
                    generation = generation
                )
            }
            is NativeAuthResultV2.ResetPasswordMethodRequired -> {
                continueV2(
                    NativeAuthDestination.PICK_AUTH_METHOD,
                    authMethods = result.authMethods,
                    generation = generation
                )
            }
            is NativeAuthResultV2.MFAVerificationRequired -> {
                continueV2(
                    NativeAuthDestination.MFA_VERIFICATION,
                    sentTo = result.sentTo,
                    channel = result.channel,
                    canResendChallenge = true,
                    generation = generation
                )
            }
            is NativeAuthResultV2.StrongAuthRegistrationRequired -> {
                continueV2(
                    NativeAuthDestination.PICK_AUTH_METHOD,
                    authMethods = result.authMethods,
                    generation = generation
                )
            }
            is NativeAuthResultV2.StrongAuthVerificationRequired -> {
                continueV2(
                    NativeAuthDestination.MFA_VERIFICATION,
                    sentTo = result.sentTo,
                    channel = result.channel,
                    canResendChallenge = false,
                    generation = generation
                )
            }
            is NativeAuthResultV2.SignInAfterSignUpRequired ->
                handleV2Result(result.nextState.signIn(), generation)
            is NativeAuthResultV2.SignInAfterResetPasswordRequired ->
                handleV2Result(result.nextState.signIn(), generation)
            is SignInErrorV2,
            is SignUpErrorV2,
            is ResetPasswordErrorV2,
            is SubmitCodeErrorV2,
            is SubmitPasswordErrorV2,
            is SubmitNewPasswordErrorV2,
            is SubmitAttributesErrorV2,
            is MFARequestChallengeErrorV2,
            is MFASubmitChallengeErrorV2,
            is RegisterStrongAuthChallengeErrorV2,
            is RegisterStrongAuthSubmitChallengeErrorV2,
            is NativeAuthErrorV2 -> handleV2Error(result as NativeAuthErrorV2, generation)
            else -> failFlow(result.toString(), generation)
        }
    }

    private suspend fun handleV2ResultOrInvalid(
        result: NativeAuthResultV2?,
        generation: Long
    ) {
        if (result == null) {
            invalidContinuation(generation)
        } else {
            handleV2Result(result, generation)
        }
    }

    private suspend fun handleV2Error(error: NativeAuthErrorV2, generation: Long) {
        if (!isCurrentFlow(generation)) {
            return
        }
        if (error.isBrowserRequired()) {
            startBrowserFallback(errorMessage(error))
            return
        }

        when (error) {
            is SubmitCodeErrorV2,
            is SubmitPasswordErrorV2,
            is SubmitNewPasswordErrorV2,
            is SubmitAttributesErrorV2,
            is MFARequestChallengeErrorV2,
            is MFASubmitChallengeErrorV2,
            is RegisterStrongAuthChallengeErrorV2,
            is RegisterStrongAuthSubmitChallengeErrorV2 ->
                keepFlow(errorMessage(error), generation)
            is SignInErrorV2,
            is SignUpErrorV2,
            is ResetPasswordErrorV2 -> failFlow(errorMessage(error), generation)
            else -> failFlow(errorMessage(error), generation)
        }
    }

    internal suspend fun handleV1SignInResult(
        result: Any,
        generation: Long = flowGeneration
    ) {
        if (!isCurrentFlow(generation)) {
            return
        }
        when (result) {
            is SignInResult.Complete ->
                finishFlow(result.resultValue, "Sign in successful.", generation)
            is SignInResult.CodeRequired ->
                continueV1(
                    result.nextState,
                    NativeAuthDestination.SIGN_IN_CODE,
                    generation = generation
                )
            is SignInResult.PasswordRequired ->
                continueV1(
                    result.nextState,
                    NativeAuthDestination.PASSWORD,
                    generation = generation
                )
            is SignInResult.MFARequired -> {
                continueV1(
                    result.nextState,
                    NativeAuthDestination.PICK_AUTH_METHOD,
                    authMethods = result.authMethods,
                    generation = generation
                )
            }
            is SignInResult.StrongAuthMethodRegistrationRequired -> {
                continueV1(
                    result.nextState,
                    NativeAuthDestination.PICK_AUTH_METHOD,
                    authMethods = result.authMethods,
                    generation = generation
                )
            }
            is SignInError -> handleV1Error(result, generation)
            is SignInContinuationError -> handleV1Error(result, generation)
            is SubmitCodeError -> handleRecoverableV1Error(result, generation)
            is MFASubmitChallengeError -> handleRecoverableV1Error(result, generation)
            else -> invalidContinuation(generation)
        }
    }

    internal suspend fun handleV1SignUpResult(result: Any, generation: Long) {
        if (!isCurrentFlow(generation)) {
            return
        }
        when (result) {
            is SignUpResult.Complete -> signInAfterV1(result.nextState, generation)
            is SignUpResult.CodeRequired ->
                continueV1(
                    result.nextState,
                    NativeAuthDestination.SIGN_UP_CODE,
                    sentTo = result.sentTo,
                    channel = result.channel,
                    generation = generation
                )
            is SignUpResult.PasswordRequired ->
                continueV1(
                    result.nextState,
                    NativeAuthDestination.PASSWORD,
                    generation = generation
                )
            is SignUpResult.AttributesRequired -> {
                continueV1(
                    result.nextState,
                    NativeAuthDestination.ATTRIBUTES,
                    requiredAttributes =
                        result.requiredAttributes.mapNotNull { it.attributeName },
                    generation = generation
                )
            }
            is SignUpError -> handleV1Error(result, generation)
            is SignUpSubmitPasswordError -> handleRecoverableV1Error(result, generation)
            is SignUpSubmitAttributesError -> handleRecoverableV1Error(result, generation)
            is SubmitCodeError -> handleRecoverableV1Error(result, generation)
            else -> invalidContinuation(generation)
        }
    }

    private suspend fun signInAfterV1(
        state: SignInContinuationState,
        generation: Long
    ) {
        when (val result = state.signIn(NativeAuthSignInContinuationParameters())) {
            is SignInResult.Complete ->
                finishFlow(result.resultValue, "Sign in successful.", generation)
            is SignInContinuationError -> handleV1Error(result, generation)
            else -> handleV1SignInResult(result, generation)
        }
    }

    private suspend fun requestInitialV1MfaChallenge(
        state: AwaitingMFAState,
        authMethod: AuthMethod,
        generation: Long
    ) {
        when (val result = initialV1MfaRequester(state, authMethod)) {
            is MFARequiredResult.VerificationRequired -> {
                continueV1(
                    result.nextState,
                    NativeAuthDestination.MFA_VERIFICATION,
                    sentTo = result.sentTo,
                    channel = result.channel,
                    canResendChallenge = true,
                    generation = generation
                )
            }
            is MFARequestChallengeError -> handleRecoverableV1Error(result, generation)
            else -> invalidContinuation(generation)
        }
    }

    private suspend fun requestRepeatedV1MfaChallenge(
        state: MFARequiredState,
        authMethod: AuthMethod,
        generation: Long
    ) {
        when (val result = repeatedV1MfaRequester(state, authMethod)) {
            is MFARequiredResult.VerificationRequired -> {
                continueV1(
                    result.nextState,
                    NativeAuthDestination.MFA_VERIFICATION,
                    sentTo = result.sentTo,
                    channel = result.channel,
                    canResendChallenge = true,
                    generation = generation
                )
            }
            is MFARequestChallengeError -> handleRecoverableV1Error(result, generation)
            else -> invalidContinuation(generation)
        }
    }

    private suspend fun handleV1Error(error: Any, generation: Long) {
        if (!isCurrentFlow(generation)) {
            return
        }
        if ((error as? BrowserRequiredError)?.isBrowserRequired() == true) {
            startBrowserFallback(errorMessage(error))
        } else {
            failFlow(errorMessage(error), generation)
        }
    }

    private suspend fun handleRecoverableV1Error(error: Any, generation: Long) {
        if (!isCurrentFlow(generation)) {
            return
        }
        if ((error as? BrowserRequiredError)?.isBrowserRequired() == true) {
            startBrowserFallback(errorMessage(error))
        } else {
            keepFlow(errorMessage(error), generation)
        }
    }

    private suspend fun startBrowserFallback(message: String) {
        flowGeneration++
        clearContinuation()
        _uiState.value = _uiState.value.copy(
            flowActive = false,
            busy = true,
            activeApi = null,
            destination = NativeAuthDestination.HOME,
            status = if (message.isBlank()) {
                "Native authentication requires browser sign-in."
            } else {
                "$message Continuing in the browser."
            },
            requiredAttributes = emptyList(),
            authMethods = emptyList(),
            selectedAuthMethod = null,
            sentTo = null,
            channel = null,
            canResendChallenge = false
        )
        _navigation.emit(NativeAuthDestination.HOME)
        _browserSignInRequests.emit(BrowserSignInRequest(null, "browser"))
    }

    private fun continueV1(
        state: Parcelable,
        destination: NativeAuthDestination,
        sentTo: String? = null,
        channel: String? = null,
        requiredAttributes: List<String> = emptyList(),
        authMethods: List<AuthMethod> = emptyList(),
        canResendChallenge: Boolean = false,
        status: String = "",
        generation: Long
    ) {
        if (!isCurrentFlow(generation)) {
            return
        }
        v1State = state
        updateContinuation(
            destination,
            sentTo,
            channel,
            requiredAttributes,
            authMethods,
            canResendChallenge,
            status,
            generation
        )
    }

    private fun continueV2(
        destination: NativeAuthDestination,
        sentTo: String? = null,
        channel: String? = null,
        requiredAttributes: List<String> = emptyList(),
        authMethods: List<AuthMethod> = emptyList(),
        canResendChallenge: Boolean = false,
        status: String = "",
        generation: Long
    ) {
        updateContinuation(
            destination,
            sentTo,
            channel,
            requiredAttributes,
            authMethods,
            canResendChallenge,
            status,
            generation
        )
    }

    private fun updateContinuation(
        destination: NativeAuthDestination,
        sentTo: String?,
        channel: String?,
        requiredAttributes: List<String>,
        authMethods: List<AuthMethod>,
        canResendChallenge: Boolean,
        status: String,
        generation: Long
    ) {
        if (!isCurrentFlow(generation)) {
            return
        }
        _uiState.value = _uiState.value.copy(
            flowActive = true,
            busy = false,
            destination = destination,
            sentTo = sentTo,
            channel = channel,
            requiredAttributes = requiredAttributes,
            authMethods = authMethods,
            canResendChallenge = canResendChallenge,
            status = status
        )
        _navigation.tryEmit(destination)
    }

    private fun navigate(destination: NativeAuthDestination, generation: Long) {
        if (!isCurrentFlow(generation)) {
            return
        }
        _uiState.value = _uiState.value.copy(
            busy = false,
            destination = destination
        )
        _navigation.tryEmit(destination)
    }

    private fun keepFlow(message: String, generation: Long) {
        if (!isCurrentFlow(generation)) {
            return
        }
        _uiState.value = _uiState.value.copy(busy = false, status = message)
    }

    private fun finishFlow(
        accountState: AccountState?,
        status: String,
        generation: Long? = null
    ) {
        if (generation != null && !isCurrentFlow(generation)) {
            return
        }
        flowGeneration++
        clearContinuation()
        accessToken = null
        _uiState.value = _uiState.value.copy(
            flowActive = false,
            busy = false,
            activeApi = null,
            destination = NativeAuthDestination.HOME,
            accountState = accountState,
            accountName = accountState?.getAccount()?.username,
            accessTokenStatus = if (accountState == null) {
                AccessTokenStatus.NONE
            } else {
                AccessTokenStatus.LOADING
            },
            status = status,
            requiredAttributes = emptyList(),
            authMethods = emptyList(),
            selectedAuthMethod = null,
            sentTo = null,
            channel = null,
            canResendChallenge = false
        )
        _navigation.tryEmit(NativeAuthDestination.HOME)
        if (accountState != null) {
            retrieveAccessToken(accountState)
        }
    }

    private fun failFlow(message: String, generation: Long) {
        if (!isCurrentFlow(generation)) {
            return
        }
        flowGeneration++
        clearContinuation()
        _uiState.value = _uiState.value.copy(
            flowActive = false,
            busy = false,
            activeApi = null,
            destination = NativeAuthDestination.HOME,
            status = message,
            requiredAttributes = emptyList(),
            authMethods = emptyList(),
            selectedAuthMethod = null,
            sentTo = null,
            channel = null,
            canResendChallenge = false
        )
        _navigation.tryEmit(NativeAuthDestination.HOME)
    }

    private fun invalidContinuation(generation: Long) {
        failFlow(
            "The active authentication continuation is no longer valid. Start again.",
            generation
        )
    }

    private fun updateAccount(accountState: AccountState) {
        accessToken = null
        _uiState.value = _uiState.value.copy(
            busy = false,
            accountState = accountState,
            accountName = accountState.getAccount().username,
            accessTokenStatus = AccessTokenStatus.LOADING
        )
        retrieveAccessToken(accountState)
    }

    private fun retrieveAccessToken(accountState: AccountState) {
        viewModelScope.launch {
            when (val result = getAccessToken(accountState, null)) {
                is GetAccessTokenResult.Complete -> {
                    if (_uiState.value.accountState === accountState) {
                        accessToken = result.resultValue.accessToken
                        _uiState.value = _uiState.value.copy(
                            accessTokenStatus = AccessTokenStatus.AVAILABLE
                        )
                    }
                }
                is GetAccessTokenError -> {
                    if (_uiState.value.accountState === accountState) {
                        accessToken = null
                        _uiState.value = _uiState.value.copy(
                            accessTokenStatus = AccessTokenStatus.ERROR,
                            status = errorMessage(result)
                        )
                    }
                }
                else -> {
                    if (_uiState.value.accountState === accountState) {
                        accessToken = null
                        _uiState.value = _uiState.value.copy(
                            accessTokenStatus = AccessTokenStatus.ERROR,
                            status = result.toString()
                        )
                    }
                }
            }
        }
    }

    private suspend fun getAccessToken(
        accountState: AccountState,
        scopes: List<String>?
    ): GetAccessTokenResult {
        val parameters = NativeAuthGetAccessTokenParameters()
        parameters.scopes = scopes
        return accountState.getAccessToken(parameters)
    }

    private fun clearContinuation() {
        v1State = null
        v1SelectionState = null
        authManager.clearState()
    }

    private fun launchFlowOperation(
        generation: Long,
        operation: suspend () -> Unit
    ) {
        if (!isCurrentFlow(generation)) {
            return
        }
        viewModelScope.launch {
            if (isCurrentFlow(generation)) {
                operation()
            }
        }
    }

    private fun isCurrentFlow(generation: Long): Boolean {
        return _uiState.value.flowActive && generation == flowGeneration
    }

    private fun buildOptionalAttributes(givenName: String, lastName: String): UserAttributes? {
        if (givenName.isBlank() && lastName.isBlank()) {
            return null
        }
        val builder = UserAttributes.Builder()
        if (givenName.isNotBlank()) {
            builder.givenName(givenName)
        }
        if (lastName.isNotBlank()) {
            builder.customAttribute("lastName", lastName)
        }
        return builder.build()
    }

    private fun errorMessage(error: Any): String {
        val nativeAuthError = error as? NativeAuthError
        val clientExceptionMessage =
            (nativeAuthError?.exception as? MsalClientException)?.message
                ?.takeIf { it.isNotBlank() }
        return clientExceptionMessage
            ?: nativeAuthError?.errorMessage
            ?: nativeAuthError?.error
            ?: nativeAuthError?.exception?.message
            ?: nativeAuthError?.errorCodes?.joinToString()
            ?: error.toString()
    }

    private fun exceptionMessage(exception: Exception): String {
        return exception.message ?: exception.toString()
    }
}
