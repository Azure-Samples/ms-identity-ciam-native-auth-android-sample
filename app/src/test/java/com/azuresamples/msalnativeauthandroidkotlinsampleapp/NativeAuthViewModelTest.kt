package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import com.microsoft.identity.common.java.nativeauth.providers.responses.v2.NativeAuthV2ContinuationState
import com.microsoft.identity.nativeauth.AuthMethod
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication
import com.microsoft.identity.nativeauth.NativeAuthPublicClientApplicationConfiguration
import com.microsoft.identity.nativeauth.statemachine.NativeAuthFlowScenarioV2
import com.microsoft.identity.nativeauth.statemachine.errors.SignInErrorV2
import com.microsoft.identity.nativeauth.statemachine.errors.SubmitCodeError
import com.microsoft.identity.nativeauth.statemachine.results.MFARequiredResult
import com.microsoft.identity.nativeauth.statemachine.results.NativeAuthResultV2
import com.microsoft.identity.nativeauth.statemachine.results.SignInResult
import com.microsoft.identity.nativeauth.statemachine.states.AttributesInvalidStateV2
import com.microsoft.identity.nativeauth.statemachine.states.AwaitingMFAState
import com.microsoft.identity.nativeauth.statemachine.states.CodeRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.MFAVerificationRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.MFARequiredState
import com.microsoft.identity.nativeauth.statemachine.states.NativeAuthBaseStateV2
import com.microsoft.identity.nativeauth.statemachine.states.StrongAuthVerificationRequiredStateV2
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class NativeAuthViewModelTest {

    @Test
    fun v2IsSelectedByDefaultAndCanChangeBeforeFlow() {
        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.value.useNativeAuthV2)

        viewModel.setUseNativeAuthV2(false)

        assertFalse(viewModel.uiState.value.useNativeAuthV2)
    }

    @Test
    fun apiSelectionIsCapturedAndLockedDuringFlow() {
        val viewModel = createViewModel()
        viewModel.setUseNativeAuthV2(false)

        assertNotNull(viewModel.beginFlow())
        viewModel.setUseNativeAuthV2(true)

        assertEquals(NativeAuthApi.V1, viewModel.uiState.value.activeApi)
        assertFalse(viewModel.uiState.value.useNativeAuthV2)
        assertTrue(viewModel.uiState.value.flowActive)
    }

    @Test
    fun cancellingFlowReturnsHomeAndUnlocksSelection() {
        val viewModel = createViewModel()
        viewModel.beginFlow(NativeAuthApi.V2)

        viewModel.cancelFlow()

        assertFalse(viewModel.uiState.value.flowActive)
        assertEquals(NativeAuthDestination.HOME, viewModel.uiState.value.destination)
    }

    @Test
    fun v2CodeRequiredRoutesByScenario() = runBlocking {
        val viewModel = createViewModel()
        viewModel.beginFlow(NativeAuthApi.V2)
        val nextState = createCodeState()

        viewModel.handleV2Result(
            NativeAuthResultV2.CodeRequired(
                nextState = nextState,
                scenario = NativeAuthFlowScenarioV2.SIGN_UP,
                codeLength = 6,
                sentTo = "user@example.com",
                channel = "email"
            )
        )

        assertEquals(
            NativeAuthDestination.SIGN_UP_CODE,
            viewModel.uiState.value.destination
        )
        assertEquals("user@example.com", viewModel.uiState.value.sentTo)
        assertTrue(viewModel.uiState.value.flowActive)
    }

    @Test
    fun onlyMfaVerificationEnablesChallengeResend() = runBlocking {
        val viewModel = createViewModel()
        viewModel.beginFlow(NativeAuthApi.V2)

        viewModel.handleV2Result(
            NativeAuthResultV2.MFAVerificationRequired(
                nextState = createState(MFAVerificationRequiredStateV2::class.java),
                scenario = NativeAuthFlowScenarioV2.SIGN_IN,
                codeLength = 6,
                sentTo = "user@example.com",
                channel = "email"
            )
        )

        assertTrue(viewModel.uiState.value.canResendChallenge)

        viewModel.handleV2Result(
            NativeAuthResultV2.StrongAuthVerificationRequired(
                nextState = createStrongAuthVerificationState(),
                scenario = NativeAuthFlowScenarioV2.SIGN_IN,
                codeLength = 6,
                sentTo = "user@example.com",
                channel = "email"
            )
        )

        assertFalse(viewModel.uiState.value.canResendChallenge)
    }

    @Test
    fun v1MfaSelectionUsesAwaitingStateAndRetainsVerificationState() = runBlocking {
        val authMethod = AuthMethod(
            id = "email",
            challengeType = "oob",
            loginHint = "user@example.com",
            challengeChannel = "email"
        )
        val config = NativeAuthPublicClientApplicationConfiguration()
        val awaitingState = AwaitingMFAState(
            "awaiting-token",
            "correlation-id",
            null,
            config
        )
        val verificationState = MFARequiredState(
            "verification-token",
            "correlation-id",
            null,
            config
        )
        val viewModel = createViewModel(
            initialMfaRequester = { state, selectedMethod ->
                assertSame(awaitingState, state)
                assertEquals(authMethod, selectedMethod)
                MFARequiredResult.VerificationRequired(
                    nextState = verificationState,
                    codeLength = 6,
                    sentTo = "user@example.com",
                    channel = "email"
                )
            },
            repeatedMfaRequester = { state, selectedMethod ->
                assertSame(verificationState, state)
                assertEquals(authMethod, selectedMethod)
                MFARequiredResult.VerificationRequired(
                    nextState = verificationState,
                    codeLength = 6,
                    sentTo = "user@example.com",
                    channel = "email"
                )
            }
        )
        val generation = requireNotNull(viewModel.beginFlow(NativeAuthApi.V1))

        viewModel.handleV1SignInResult(
            SignInResult.MFARequired(awaitingState, listOf(authMethod)),
            generation
        )
        viewModel.selectAuthMethodInternal(authMethod, generation)
        viewModel.resendChallengeInternal(authMethod, generation)

        assertEquals(
            NativeAuthDestination.MFA_VERIFICATION,
            viewModel.uiState.value.destination
        )
        assertTrue(viewModel.uiState.value.canResendChallenge)
    }

    @Test
    fun staleV2ResultCannotOverwriteNewV1FlowAfterCancel() = runBlocking {
        val viewModel = createViewModel()
        val staleGeneration = requireNotNull(viewModel.beginFlow(NativeAuthApi.V2))
        viewModel.cancelFlow()
        viewModel.beginFlow(NativeAuthApi.V1)

        viewModel.handleV2Result(
            NativeAuthResultV2.CodeRequired(
                nextState = createCodeState(),
                scenario = NativeAuthFlowScenarioV2.SIGN_IN,
                codeLength = 6,
                sentTo = "user@example.com",
                channel = "email"
            ),
            staleGeneration
        )

        assertTrue(viewModel.uiState.value.flowActive)
        assertEquals(NativeAuthApi.V1, viewModel.uiState.value.activeApi)
        assertEquals(NativeAuthDestination.HOME, viewModel.uiState.value.destination)
    }

    @Test
    fun invalidAttributesRenderOnlyAsInputFields() = runBlocking {
        val viewModel = createViewModel()
        val generation = requireNotNull(viewModel.beginFlow(NativeAuthApi.V2))

        viewModel.handleV2Result(
            NativeAuthResultV2.AttributesInvalid(
                nextState = createState(AttributesInvalidStateV2::class.java),
                scenario = NativeAuthFlowScenarioV2.SIGN_UP,
                invalidAttributes = listOf("password", "email", "city")
            ),
            generation
        )

        assertTrue(viewModel.uiState.value.flowActive)
        assertEquals(NativeAuthDestination.ATTRIBUTES, viewModel.uiState.value.destination)
        assertEquals(
            listOf("password", "email", "city"),
            viewModel.uiState.value.requiredAttributes
        )
        assertTrue(viewModel.uiState.value.status.isEmpty())
    }

    @Test
    fun browserRequiredV2ErrorReturnsHomeAndRequestsInteractiveSignIn() = runBlocking {
        val viewModel = createViewModel()
        val request = async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.browserSignInRequests.first()
        }
        val generation = requireNotNull(viewModel.beginFlow(NativeAuthApi.V2))

        viewModel.handleV2Result(
            SignInErrorV2(
                errorType = "browser_required",
                errorMessage = "Continue in a browser.",
                correlationId = "correlation-id",
                scenario = NativeAuthFlowScenarioV2.SIGN_IN
            ),
            generation
        )

        assertFalse(viewModel.uiState.value.flowActive)
        assertTrue(viewModel.uiState.value.busy)
        assertEquals(NativeAuthDestination.HOME, viewModel.uiState.value.destination)
        assertEquals(BrowserSignInRequest(null, "browser"), request.await())
    }

    @Test
    fun socialSignInRequestPreservesProviderDomainHint() = runBlocking {
        val viewModel = createViewModel()
        val request = async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.browserSignInRequests.first()
        }

        viewModel.requestBrowserSignIn("www.linkedin.com", "LinkedIn")

        assertTrue(viewModel.uiState.value.busy)
        assertEquals(
            BrowserSignInRequest("www.linkedin.com", "LinkedIn"),
            request.await()
        )
    }

    @Test
    fun browserRequiredV1ContinuationErrorRequestsInteractiveSignIn() = runBlocking {
        val viewModel = createViewModel()
        val request = async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.browserSignInRequests.first()
        }
        val generation = requireNotNull(viewModel.beginFlow(NativeAuthApi.V1))

        viewModel.handleV1SignInResult(
            SubmitCodeError(
                errorType = "browser_required",
                errorMessage = "Continue in a browser.",
                correlationId = "correlation-id"
            ),
            generation
        )

        assertFalse(viewModel.uiState.value.flowActive)
        assertEquals(NativeAuthDestination.HOME, viewModel.uiState.value.destination)
        assertEquals(BrowserSignInRequest(null, "browser"), request.await())
    }

    private fun createViewModel(
        initialMfaRequester: suspend (AwaitingMFAState, AuthMethod) -> MFARequiredResult =
            { state, method -> state.requestChallenge(method) },
        repeatedMfaRequester: suspend (MFARequiredState, AuthMethod) -> MFARequiredResult =
            { state, method -> state.requestChallenge(method) }
    ): NativeAuthViewModel {
        val application = Proxy.newProxyInstance(
            INativeAuthPublicClientApplication::class.java.classLoader,
            arrayOf(INativeAuthPublicClientApplication::class.java)
        ) { _, method, _ ->
            throw UnsupportedOperationException(method.name)
        } as INativeAuthPublicClientApplication
        return NativeAuthViewModel(
            application,
            AuthManager(application),
            autoRestoreAccount = false,
            initialV1MfaRequester = initialMfaRequester,
            repeatedV1MfaRequester = repeatedMfaRequester
        )
    }

    private fun createCodeState(): CodeRequiredStateV2 {
        return createState(CodeRequiredStateV2::class.java)
    }

    private fun <T : NativeAuthBaseStateV2> createState(stateClass: Class<T>): T {
        val constructor = stateClass.getDeclaredConstructor(
            String::class.java,
            String::class.java,
            NativeAuthFlowScenarioV2::class.java,
            NativeAuthPublicClientApplicationConfiguration::class.java,
            NativeAuthV2ContinuationState::class.java
        )
        constructor.isAccessible = true
        return constructor.newInstance(
            "continuation-token",
            "correlation-id",
            NativeAuthFlowScenarioV2.SIGN_UP,
            NativeAuthPublicClientApplicationConfiguration(),
            null
        )
    }

    private fun createStrongAuthVerificationState(): StrongAuthVerificationRequiredStateV2 {
        val constructor = StrongAuthVerificationRequiredStateV2::class.java.getDeclaredConstructor(
            String::class.java,
            String::class.java,
            NativeAuthFlowScenarioV2::class.java,
            NativeAuthPublicClientApplicationConfiguration::class.java
        )
        constructor.isAccessible = true
        return constructor.newInstance(
            "continuation-token",
            "correlation-id",
            NativeAuthFlowScenarioV2.SIGN_IN,
            NativeAuthPublicClientApplicationConfiguration()
        )
    }
}
