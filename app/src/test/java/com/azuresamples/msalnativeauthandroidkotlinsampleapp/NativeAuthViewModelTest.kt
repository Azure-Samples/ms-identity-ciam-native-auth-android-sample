package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import com.microsoft.identity.common.java.nativeauth.providers.responses.v2.NativeAuthV2ContinuationState
import com.microsoft.identity.nativeauth.AuthMethod
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication
import com.microsoft.identity.nativeauth.NativeAuthPublicClientApplicationConfiguration
import com.microsoft.identity.nativeauth.RequiredUserAttribute
import com.microsoft.identity.nativeauth.statemachine.NativeAuthFlowScenarioV2
import com.microsoft.identity.nativeauth.statemachine.errors.NativeAuthErrorV2
import com.microsoft.identity.nativeauth.statemachine.errors.SubmitCodeError
import com.microsoft.identity.nativeauth.statemachine.results.MFARequiredResult
import com.microsoft.identity.nativeauth.statemachine.results.NativeAuthResultV2
import com.microsoft.identity.nativeauth.statemachine.results.SignInResult
import com.microsoft.identity.nativeauth.statemachine.states.AttributesRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.AuthMethodSelectionRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.AwaitingMFAState
import com.microsoft.identity.nativeauth.statemachine.states.CodeRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.ExistingAccountCodeRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.ExistingAccountPasswordRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.MFAVerificationRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.MFARequiredState
import com.microsoft.identity.nativeauth.statemachine.states.NativeAuthBaseStateV2
import com.microsoft.identity.nativeauth.statemachine.states.NewPasswordRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.PasswordRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.StrongAuthVerificationRequiredStateV2
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun existingAccountCodeRoutesToSignInWithRefreshedState() = runBlocking {
        val manager = AuthManager(applicationWithoutCalls())
        val viewModel = createViewModel(authManager = manager)
        viewModel.beginFlow(NativeAuthApi.V2)

        for (sentTo in listOf("old@example.com", "new@example.com")) {
            val state = createState(ExistingAccountCodeRequiredStateV2::class.java)
            viewModel.handleV2Result(
                NativeAuthResultV2.ExistingAccountCodeRequired(state, 6, sentTo, "email")
            )

            assertEquals(NativeAuthDestination.SIGN_IN_CODE, viewModel.uiState.value.destination)
            assertEquals(sentTo, viewModel.uiState.value.sentTo)
            assertEquals("email", viewModel.uiState.value.channel)
            assertEquals(
                "An account already exists. Enter the code to sign in.",
                viewModel.uiState.value.status
            )
            assertTrue(viewModel.uiState.value.flowActive)
            assertFalse(viewModel.uiState.value.busy)
            assertSame(state, manager.currentState)
        }
    }

    @Test
    fun existingAccountPasswordRoutesToCurrentPassword() = runBlocking {
        val manager = AuthManager(applicationWithoutCalls())
        val viewModel = createViewModel(authManager = manager)
        viewModel.beginFlow(NativeAuthApi.V2)
        val state = createState(ExistingAccountPasswordRequiredStateV2::class.java)

        viewModel.handleV2Result(NativeAuthResultV2.ExistingAccountPasswordRequired(state))

        assertEquals(NativeAuthDestination.PASSWORD, viewModel.uiState.value.destination)
        assertEquals(
            "An account already exists. Enter your current password to sign in.",
            viewModel.uiState.value.status
        )
        assertTrue(viewModel.uiState.value.flowActive)
        assertFalse(viewModel.uiState.value.busy)
        assertSame(state, manager.currentState)
    }

    @Test
    fun initialPasswordIsSubmittedOnceThroughRequestedV2StateAndErased() = runBlocking {
        for (scenario in listOf(NativeAuthFlowScenarioV2.SIGN_IN, NativeAuthFlowScenarioV2.SIGN_UP)) {
            val requestedState = createState(PasswordRequiredStateV2::class.java, scenario)
            var submittedPassword: CharArray? = null
            var submissions = 0
            val manager = AuthManager(applicationWithoutCalls())
            val viewModel = createViewModel(
                authManager = manager,
                initialPasswordSubmitter = { state, password ->
                    assertSame(requestedState, state)
                    assertEquals("initial-password", String(password))
                    submittedPassword = password
                    submissions++
                    NativeAuthErrorV2(
                        errorType = "invalid_password",
                        errorMessage = "Try another password",
                        correlationId = "correlation-id",
                        scenario = scenario
                    )
                }
            )
            val input = "initial-password".toCharArray()
            viewModel.beginFlow(NativeAuthApi.V2, password = input)
            input.fill('\u0000')
            if (scenario == NativeAuthFlowScenarioV2.SIGN_UP) {
                viewModel.handleV2Result(NativeAuthResultV2.CodeRequired(
                    createCodeState(), scenario, 6, "user@example.com", "email"
                ))
                viewModel.handleV2Result(NativeAuthResultV2.AttributesRequired(
                    createState(AttributesRequiredStateV2::class.java), scenario,
                    listOf(RequiredUserAttribute("city", "string", true, null))
                ))
                assertEquals(0, submissions)
            }

            viewModel.handleV2Result(NativeAuthResultV2.PasswordRequired(requestedState, scenario))

            assertEquals(1, submissions)
            assertTrue(submittedPassword!!.all { it == '\u0000' })
            assertTrue(viewModel.uiState.value.flowActive)
            assertFalse(viewModel.uiState.value.busy)
            assertEquals(NativeAuthDestination.PASSWORD, viewModel.uiState.value.destination)
            assertEquals("Try another password", viewModel.uiState.value.status)
            assertSame(requestedState, manager.currentState)

            viewModel.handleV2Result(NativeAuthResultV2.PasswordRequired(requestedState, scenario))

            assertEquals(1, submissions)
        }
    }

    @Test
    fun omittedInitialPasswordStillPromptsWhenRequested() = runBlocking {
        for (password in listOf(null, charArrayOf())) {
            val viewModel = createViewModel(initialPasswordSubmitter = { _, _ ->
                throw AssertionError("An omitted password must not be submitted")
            })
            viewModel.beginFlow(NativeAuthApi.V2, password = password)

            viewModel.handleV2Result(NativeAuthResultV2.PasswordRequired(
                createState(PasswordRequiredStateV2::class.java), NativeAuthFlowScenarioV2.SIGN_IN
            ))

            assertTrue(viewModel.uiState.value.flowActive)
            assertFalse(viewModel.uiState.value.busy)
            assertEquals(NativeAuthDestination.PASSWORD, viewModel.uiState.value.destination)
        }
    }

    @Test
    fun existingAccountDiscoveryDiscardsInitialSignUpPassword() = runBlocking {
        val viewModel = createViewModel(initialPasswordSubmitter = { _, _ ->
            throw AssertionError("A sign-up password must not be used for an existing account")
        })
        viewModel.beginFlow(NativeAuthApi.V2, password = "new-password".toCharArray())

        viewModel.handleV2Result(NativeAuthResultV2.ExistingAccountPasswordRequired(
            createState(ExistingAccountPasswordRequiredStateV2::class.java)
        ))

        assertEquals(NativeAuthDestination.PASSWORD, viewModel.uiState.value.destination)
        assertEquals(
            "An account already exists. Enter your current password to sign in.",
            viewModel.uiState.value.status
        )
        viewModel.handleV2Result(NativeAuthResultV2.PasswordRequired(
            createState(PasswordRequiredStateV2::class.java), NativeAuthFlowScenarioV2.SIGN_IN
        ))
        assertTrue(viewModel.uiState.value.flowActive)
    }

    @Test
    fun cancelledFlowCannotReuseItsInitialPassword() = runBlocking {
        val viewModel = createViewModel(initialPasswordSubmitter = { _, _ ->
            throw AssertionError("A cancelled flow's password must not be submitted")
        })
        val staleGeneration = requireNotNull(viewModel.beginFlow(
            NativeAuthApi.V2, password = "password".toCharArray()
        ))
        viewModel.cancelFlow()
        viewModel.beginFlow(NativeAuthApi.V2)
        val result = NativeAuthResultV2.PasswordRequired(
            createState(PasswordRequiredStateV2::class.java), NativeAuthFlowScenarioV2.SIGN_IN
        )

        viewModel.handleV2Result(result, staleGeneration)
        viewModel.handleV2Result(result)

        assertTrue(viewModel.uiState.value.flowActive)
        assertEquals(NativeAuthDestination.PASSWORD, viewModel.uiState.value.destination)
    }

    @Test
    fun lateInitialPasswordSubmissionCannotOverwriteNewFlowAndErasesBuffer() = runBlocking {
        val response = CompletableDeferred<NativeAuthResultV2>()
        var submittedPassword: CharArray? = null
        val viewModel = createViewModel(initialPasswordSubmitter = { _, password ->
            submittedPassword = password
            response.await()
        })
        viewModel.beginFlow(NativeAuthApi.V2, password = "password".toCharArray())
        val submission = async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.handleV2Result(NativeAuthResultV2.PasswordRequired(
                createState(PasswordRequiredStateV2::class.java), NativeAuthFlowScenarioV2.SIGN_IN
            ))
        }
        assertNotNull(submittedPassword)
        viewModel.cancelFlow()
        viewModel.beginFlow(NativeAuthApi.V1)

        response.complete(NativeAuthErrorV2(
            errorType = "invalid_password",
            errorMessage = "Old response",
            correlationId = "correlation-id",
            scenario = NativeAuthFlowScenarioV2.SIGN_IN
        ))
        submission.await()

        assertTrue(submittedPassword!!.all { it == '\u0000' })
        assertEquals(NativeAuthApi.V1, viewModel.uiState.value.activeApi)
        assertEquals(NativeAuthDestination.HOME, viewModel.uiState.value.destination)
        assertEquals("", viewModel.uiState.value.status)
    }

    @Test
    fun passwordResetRetainsItsExistingMethodSelection() = runBlocking {
        val methods = listOf(
            AuthMethod("email", "oob", "user@example.com", "email"),
            AuthMethod("sms", "oob", null, "sms")
        )
        val manager = AuthManager(applicationWithoutCalls())
        val viewModel = createViewModel(authManager = manager)
        viewModel.beginFlow(NativeAuthApi.V2)
        val state = createMethodSelectionState(methods, NativeAuthFlowScenarioV2.RESET_PASSWORD)

        viewModel.handleV2Result(NativeAuthResultV2.AuthMethodSelectionRequired(state))

        assertEquals(NativeAuthDestination.PICK_AUTH_METHOD, viewModel.uiState.value.destination)
        assertEquals(methods, viewModel.uiState.value.authMethods)
        assertEquals(R.string.pick_password_reset_method_text_value, viewModel.authMethodPrompt())
        assertTrue(viewModel.uiState.value.flowActive)
        assertFalse(viewModel.uiState.value.busy)
        assertSame(state, manager.currentState)
    }

    @Test
    fun signInMethodSelectionDoesNotOpenPickerOrSubmitPassword() = runBlocking {
        for (password in listOf(null, "password".toCharArray())) {
            val manager = AuthManager(applicationWithoutCalls())
            val viewModel = createViewModel(
                authManager = manager,
                initialPasswordSubmitter = { _, _ ->
                    throw AssertionError("Only PasswordRequired may submit the entered password")
                }
            )
            viewModel.beginFlow(NativeAuthApi.V2, password = password)
            val methods = listOf(
                AuthMethod("email", "oob", "user@example.com", "email"),
                AuthMethod("password", "password", null, "password")
            )

            viewModel.handleV2Result(NativeAuthResultV2.AuthMethodSelectionRequired(
                createMethodSelectionState(methods)
            ))

            assertEquals(NativeAuthDestination.HOME, viewModel.uiState.value.destination)
            assertFalse(viewModel.uiState.value.flowActive)
            assertFalse(viewModel.uiState.value.busy)
            assertTrue(viewModel.uiState.value.authMethods.isEmpty())
            assertTrue(viewModel.uiState.value.status.contains("does not support for sign-in"))
            assertNull(manager.currentState)
        }
    }

    @Test
    fun mfaAndJitVerificationEnableResend() = runBlocking {
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

        assertTrue(viewModel.uiState.value.canResendChallenge)
    }

    @Test
    fun activationSuccessorsReplaceTheRetainedContinuation() = runBlocking {
        val manager = AuthManager(applicationWithoutCalls())
        val viewModel = createViewModel(authManager = manager)
        viewModel.beginFlow(NativeAuthApi.V2)
        val activation = createStrongAuthVerificationState()
        viewModel.handleV2Result(
            NativeAuthResultV2.StrongAuthVerificationRequired(
                activation, NativeAuthFlowScenarioV2.SIGN_UP, 6, "user@example.com", "email"
            )
        )
        assertSame(activation, manager.currentState)

        val successors = listOf(
            createState(AttributesRequiredStateV2::class.java).let {
                Triple(
                    NativeAuthResultV2.AttributesRequired(
                        it, NativeAuthFlowScenarioV2.SIGN_UP,
                        listOf(RequiredUserAttribute("city", "string", true, null))
                    ),
                    it,
                    NativeAuthDestination.ATTRIBUTES
                )
            },
            createState(NewPasswordRequiredStateV2::class.java).let {
                Triple(
                    NativeAuthResultV2.NewPasswordRequired(
                        it, NativeAuthFlowScenarioV2.RESET_PASSWORD
                    ),
                    it,
                    NativeAuthDestination.NEW_PASSWORD
                )
            },
            createState(MFAVerificationRequiredStateV2::class.java).let {
                Triple(
                    NativeAuthResultV2.MFAVerificationRequired(
                        it, NativeAuthFlowScenarioV2.SIGN_IN, 6, "user@example.com", "email"
                    ),
                    it,
                    NativeAuthDestination.MFA_VERIFICATION
                )
            }
        )
        for ((result, nextState, destination) in successors) {
            viewModel.handleV2Result(result)
            assertSame(nextState, manager.currentState)
            assertEquals(destination, viewModel.uiState.value.destination)
        }
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
        val manager = AuthManager(applicationWithoutCalls())
        val viewModel = createViewModel(authManager = manager)
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
        assertNull(manager.currentState)
    }

    @Test
    fun staleExistingAccountResultsCannotOverwriteNewFlow() = runBlocking {
        val results = listOf(
            NativeAuthResultV2.ExistingAccountCodeRequired(
                createState(ExistingAccountCodeRequiredStateV2::class.java),
                6, "user@example.com", "email"
            ),
            NativeAuthResultV2.ExistingAccountPasswordRequired(
                createState(ExistingAccountPasswordRequiredStateV2::class.java)
            )
        )
        for (result in results) {
            val manager = AuthManager(applicationWithoutCalls())
            val viewModel = createViewModel(authManager = manager)
            val staleGeneration = requireNotNull(viewModel.beginFlow(NativeAuthApi.V2))
            viewModel.cancelFlow()
            viewModel.beginFlow(NativeAuthApi.V1)

            viewModel.handleV2Result(result, staleGeneration)

            assertTrue(viewModel.uiState.value.flowActive)
            assertEquals(NativeAuthApi.V1, viewModel.uiState.value.activeApi)
            assertEquals(NativeAuthDestination.HOME, viewModel.uiState.value.destination)
            assertNull(manager.currentState)
        }
    }

    @Test
    fun requiredAttributesRenderAsInputFields() = runBlocking {
        val viewModel = createViewModel()
        val generation = requireNotNull(viewModel.beginFlow(NativeAuthApi.V2))

        viewModel.handleV2Result(
            NativeAuthResultV2.AttributesRequired(
                nextState = createState(AttributesRequiredStateV2::class.java),
                scenario = NativeAuthFlowScenarioV2.SIGN_UP,
                requiredAttributes = listOf(
                    RequiredUserAttribute("city", "string", true, null),
                    RequiredUserAttribute("country", "string", true, null)
                )
            ),
            generation
        )

        assertTrue(viewModel.uiState.value.flowActive)
        assertEquals(NativeAuthDestination.ATTRIBUTES, viewModel.uiState.value.destination)
        assertEquals(
            listOf("city", "country"),
            viewModel.uiState.value.requiredAttributes
        )
        assertTrue(viewModel.uiState.value.status.isEmpty())
    }

    @Test
    fun correctableV2ErrorsRetainTheirContinuationScreen() = runBlocking {
        val scenario = NativeAuthFlowScenarioV2.SIGN_IN
        val cases = listOf(
            NativeAuthResultV2.CodeRequired(
                createCodeState(), scenario, 6, "user@example.com", "email"
            ) to NativeAuthErrorV2(
                error = "invalidGrant",
                subError = "invalidOneTimeCode",
                errorMessage = "Invalid code",
                correlationId = "correlation-id",
                scenario = scenario
            ),
            NativeAuthResultV2.PasswordRequired(
                createState(PasswordRequiredStateV2::class.java), scenario
            ) to NativeAuthErrorV2(
                errorType = "invalid_password",
                error = "invalidGrant",
                subError = "invalidUserNameOrPassword",
                errorMessage = "Invalid credentials",
                correlationId = "correlation-id",
                scenario = scenario
            ),
            NativeAuthResultV2.PasswordRequired(
                createState(PasswordRequiredStateV2::class.java, NativeAuthFlowScenarioV2.SIGN_UP),
                NativeAuthFlowScenarioV2.SIGN_UP
            ) to NativeAuthErrorV2(
                error = "invalidRequest",
                subError = "attributeValidationError",
                errorMessage = "Password does not meet policy",
                correlationId = "correlation-id",
                scenario = NativeAuthFlowScenarioV2.SIGN_UP,
                attributeValidationDetails = listOf(
                    mapOf("code" to "attributeValidationFailed", "attributeIds" to listOf("password"))
                )
            ),
            NativeAuthResultV2.ExistingAccountCodeRequired(
                createState(ExistingAccountCodeRequiredStateV2::class.java),
                6, "user@example.com", "email"
            ) to NativeAuthErrorV2(
                error = "invalidGrant",
                subError = "invalidOneTimeCode",
                errorMessage = "Invalid sign-in code",
                correlationId = "correlation-id",
                scenario = scenario
            ),
            NativeAuthResultV2.ExistingAccountPasswordRequired(
                createState(ExistingAccountPasswordRequiredStateV2::class.java)
            ) to NativeAuthErrorV2(
                errorType = "invalid_password",
                error = "invalidGrant",
                subError = "invalidUserNameOrPassword",
                errorMessage = "Invalid current password",
                correlationId = "correlation-id",
                scenario = scenario
            ),
            NativeAuthResultV2.NewPasswordRequired(
                createState(NewPasswordRequiredStateV2::class.java),
                NativeAuthFlowScenarioV2.RESET_PASSWORD
            ) to NativeAuthErrorV2(
                error = "invalidRequest",
                subError = "passwordTooWeak",
                errorMessage = "Choose a stronger password",
                correlationId = "correlation-id",
                scenario = NativeAuthFlowScenarioV2.RESET_PASSWORD
            ),
            NativeAuthResultV2.StrongAuthVerificationRequired(
                createStrongAuthVerificationState(), scenario, 6, "user@example.com", "email"
            ) to NativeAuthErrorV2(
                errorType = "invalid_challenge",
                error = "invalidGrant",
                subError = "invalidOneTimeCode",
                errorMessage = "Invalid activation code",
                correlationId = "correlation-id",
                scenario = scenario
            ),
            NativeAuthResultV2.MFAVerificationRequired(
                createState(MFAVerificationRequiredStateV2::class.java), scenario,
                6, "user@example.com", "email"
            ) to NativeAuthErrorV2(
                errorType = "invalid_challenge",
                error = "invalidGrant",
                subError = "invalidOneTimeCode",
                errorMessage = "Invalid MFA code",
                correlationId = "correlation-id",
                scenario = scenario
            )
        )

        for ((result, error) in cases) {
            val manager = AuthManager(applicationWithoutCalls())
            val viewModel = createViewModel(authManager = manager)
            viewModel.beginFlow(NativeAuthApi.V2)
            viewModel.handleV2Result(result)
            val destination = viewModel.uiState.value.destination
            val state = manager.currentState

            viewModel.handleV2Result(error)

            assertTrue(viewModel.uiState.value.flowActive)
            assertFalse(viewModel.uiState.value.busy)
            assertEquals(destination, viewModel.uiState.value.destination)
            assertEquals(error.errorMessage, viewModel.uiState.value.status)
            assertSame(state, manager.currentState)
        }
    }

    @Test
    fun invalidAttributesShowSharedDiagnosticsAndRetainFieldsAndState() = runBlocking {
        val manager = AuthManager(applicationWithoutCalls())
        val viewModel = createViewModel(authManager = manager)
        viewModel.beginFlow(NativeAuthApi.V2)
        val state = createState(AttributesRequiredStateV2::class.java)

        viewModel.handleV2Result(
            NativeAuthResultV2.AttributesRequired(
                nextState = state,
                scenario = NativeAuthFlowScenarioV2.SIGN_UP,
                requiredAttributes = listOf(
                    RequiredUserAttribute("city", "string", true, null)
                )
            )
        )
        viewModel.handleV2Result(
            NativeAuthErrorV2(
                error = "invalidRequest",
                subError = "attributeValidationError",
                errorMessage = "City is required",
                correlationId = "correlation-id",
                scenario = NativeAuthFlowScenarioV2.SIGN_UP,
                attributeValidationDetails = listOf(
                    mapOf("code" to "attributeRequired", "attributeIds" to listOf("city"))
                )
            )
        )

        assertTrue(viewModel.uiState.value.flowActive)
        assertEquals(NativeAuthDestination.ATTRIBUTES, viewModel.uiState.value.destination)
        assertEquals(listOf("city"), viewModel.uiState.value.requiredAttributes)
        assertEquals("City is required", viewModel.uiState.value.status)
        assertSame(state, manager.currentState)
    }

    @Test
    fun generalAttributeErrorsDoNotOfferUnsupportedRetries() = runBlocking {
        for (subError in listOf("userAlreadyExists", "authMethodBlocked", "verificationContactBlocked")) {
            val manager = AuthManager(applicationWithoutCalls())
            val viewModel = createViewModel(authManager = manager)
            viewModel.beginFlow(NativeAuthApi.V2)
            viewModel.handleV2Result(NativeAuthResultV2.AttributesRequired(
                createState(AttributesRequiredStateV2::class.java),
                NativeAuthFlowScenarioV2.SIGN_UP,
                listOf(RequiredUserAttribute("city", "string", true, null))
            ))

            viewModel.handleV2Result(NativeAuthErrorV2(
                error = "invalidRequest",
                subError = subError,
                errorMessage = "Service rejected the operation: $subError",
                correlationId = "correlation-id",
                scenario = NativeAuthFlowScenarioV2.SIGN_UP
            ))

            assertFalse(viewModel.uiState.value.flowActive)
            assertEquals(NativeAuthDestination.HOME, viewModel.uiState.value.destination)
            assertEquals("Service rejected the operation: $subError", viewModel.uiState.value.status)
            assertNull(manager.currentState)
        }
    }

    @Test
    fun entryPointInputErrorsReturnHome() = runBlocking {
        val viewModel = createViewModel()
        viewModel.beginFlow(NativeAuthApi.V2)

        viewModel.handleV2Result(
            NativeAuthErrorV2(
                errorType = "invalid_input",
                errorMessage = "Enter a valid username",
                correlationId = "correlation-id",
                scenario = NativeAuthFlowScenarioV2.SIGN_IN
            )
        )

        assertFalse(viewModel.uiState.value.flowActive)
        assertEquals(NativeAuthDestination.HOME, viewModel.uiState.value.destination)
        assertEquals("Enter a valid username", viewModel.uiState.value.status)
    }

    @Test
    fun terminalAndUnknownErrorsEndV2Continuations() = runBlocking {
        for (errorType in listOf("invalid_state", "not_implemented", "future_error")) {
            val viewModel = createViewModel()
            viewModel.beginFlow(NativeAuthApi.V2)
            viewModel.handleV2Result(
                NativeAuthResultV2.CodeRequired(
                    createCodeState(), NativeAuthFlowScenarioV2.SIGN_IN,
                    6, "user@example.com", "email"
                )
            )

            viewModel.handleV2Result(
                NativeAuthErrorV2(
                    errorType = errorType,
                    errorMessage = "Restart the flow",
                    correlationId = "correlation-id",
                    scenario = NativeAuthFlowScenarioV2.SIGN_IN
                )
            )

            assertFalse(viewModel.uiState.value.flowActive)
            assertEquals(NativeAuthDestination.HOME, viewModel.uiState.value.destination)
            assertEquals("Restart the flow", viewModel.uiState.value.status)
        }
    }

    @Test
    fun strongAuthVerificationRefreshesContactAndStateWithResendEnabled() = runBlocking {
        val manager = AuthManager(applicationWithoutCalls())
        val viewModel = createViewModel(authManager = manager)
        viewModel.beginFlow(NativeAuthApi.V2)
        for (sentTo in listOf("old@example.com", "new@example.com", "")) {
            val nextState = createStrongAuthVerificationState()
            viewModel.handleV2Result(
                NativeAuthResultV2.StrongAuthVerificationRequired(
                    nextState, NativeAuthFlowScenarioV2.SIGN_UP,
                    6, sentTo, "email"
                )
            )

            assertSame(nextState, manager.currentState)
            assertEquals(sentTo, viewModel.uiState.value.sentTo)
            assertEquals(NativeAuthDestination.MFA_VERIFICATION, viewModel.uiState.value.destination)
            assertTrue(viewModel.uiState.value.canResendChallenge)
        }

        viewModel.handleV2Result(
            NativeAuthResultV2.NewPasswordRequired(
                createState(NewPasswordRequiredStateV2::class.java),
                NativeAuthFlowScenarioV2.RESET_PASSWORD
            )
        )

        assertFalse(viewModel.uiState.value.canResendChallenge)
    }

    @Test
    fun browserRequiredV2ErrorReturnsHomeAndRequestsInteractiveSignIn() = runBlocking {
        val viewModel = createViewModel()
        val request = async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.browserSignInRequests.first()
        }
        val generation = requireNotNull(viewModel.beginFlow(NativeAuthApi.V2))

        viewModel.handleV2Result(
            NativeAuthErrorV2(
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
    fun exactOAuthRedirectPairRequestsBrowserAndClearsContinuation() = runBlocking {
        val manager = AuthManager(applicationWithoutCalls())
        val viewModel = createViewModel(authManager = manager)
        val request = async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.browserSignInRequests.first()
        }
        viewModel.beginFlow(NativeAuthApi.V2)
        viewModel.handleV2Result(NativeAuthResultV2.PasswordRequired(
            createState(PasswordRequiredStateV2::class.java), NativeAuthFlowScenarioV2.SIGN_IN
        ))

        viewModel.handleV2Result(NativeAuthErrorV2(
            error = "invalid_grant",
            subError = "web_redirect_required",
            errorMessage = "Continue in a browser.",
            correlationId = "correlation-id",
            scenario = NativeAuthFlowScenarioV2.SIGN_IN
        ))

        assertFalse(viewModel.uiState.value.flowActive)
        assertTrue(viewModel.uiState.value.busy)
        assertNull(manager.currentState)
        assertEquals(BrowserSignInRequest(null, "browser"), request.await())
    }

    @Test
    fun nearMatchRedirectErrorsDoNotStartBrowserSignIn() = runBlocking {
        for ((error, subError) in listOf(
            "invalidGrant" to "web_redirect_required",
            "invalid_grant" to "webRedirectRequired",
            "invalid_request" to "web_redirect_required"
        )) {
            val viewModel = createViewModel()
            viewModel.beginFlow(NativeAuthApi.V2)

            viewModel.handleV2Result(NativeAuthErrorV2(
                error = error,
                subError = subError,
                errorMessage = "Service error",
                correlationId = "correlation-id",
                scenario = NativeAuthFlowScenarioV2.SIGN_IN
            ))

            assertFalse(viewModel.uiState.value.flowActive)
            assertFalse(viewModel.uiState.value.busy)
            assertEquals("Service error", viewModel.uiState.value.status)
        }
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
            { state, method -> state.requestChallenge(method) },
        authManager: AuthManager? = null,
        initialPasswordSubmitter: suspend (PasswordRequiredStateV2, CharArray) -> NativeAuthResultV2 =
            { state, password -> state.submitPassword(password) }
    ): NativeAuthViewModel {
        val application = applicationWithoutCalls()
        return NativeAuthViewModel(
            application,
            authManager ?: AuthManager(application),
            autoRestoreAccount = false,
            initialV1MfaRequester = initialMfaRequester,
            repeatedV1MfaRequester = repeatedMfaRequester,
            initialV2PasswordSubmitter = initialPasswordSubmitter
        )
    }

    private fun applicationWithoutCalls(): INativeAuthPublicClientApplication =
        Proxy.newProxyInstance(
            INativeAuthPublicClientApplication::class.java.classLoader,
            arrayOf(INativeAuthPublicClientApplication::class.java)
        ) { _, method, _ ->
            throw UnsupportedOperationException(method.name)
        } as INativeAuthPublicClientApplication

    private fun createCodeState(): CodeRequiredStateV2 {
        return createState(CodeRequiredStateV2::class.java)
    }

    private fun createMethodSelectionState(
        methods: List<AuthMethod>,
        scenario: NativeAuthFlowScenarioV2 = NativeAuthFlowScenarioV2.SIGN_IN,
        existingAccount: Boolean = false
    ): AuthMethodSelectionRequiredStateV2 {
        val constructor = AuthMethodSelectionRequiredStateV2::class.java.getDeclaredConstructor(
            String::class.java,
            NativeAuthFlowScenarioV2::class.java,
            NativeAuthPublicClientApplicationConfiguration::class.java,
            NativeAuthV2ContinuationState::class.java,
            List::class.java,
            Boolean::class.javaPrimitiveType
        )
        constructor.isAccessible = true
        return constructor.newInstance(
            "correlation-id", scenario, NativeAuthPublicClientApplicationConfiguration(),
            null, methods, existingAccount
        )
    }

    private fun <T : NativeAuthBaseStateV2> createState(
        stateClass: Class<T>,
        scenario: NativeAuthFlowScenarioV2 = NativeAuthFlowScenarioV2.SIGN_UP
    ): T {
        val signInState = when (stateClass) {
            ExistingAccountCodeRequiredStateV2::class.java ->
                createState(CodeRequiredStateV2::class.java, NativeAuthFlowScenarioV2.SIGN_IN)
            ExistingAccountPasswordRequiredStateV2::class.java ->
                createState(PasswordRequiredStateV2::class.java, NativeAuthFlowScenarioV2.SIGN_IN)
            else -> null
        }
        if (signInState != null) {
            val constructor = stateClass.getDeclaredConstructor(signInState.javaClass)
            constructor.isAccessible = true
            return constructor.newInstance(signInState)
        }
        val constructor = stateClass.getDeclaredConstructor(
            String::class.java,
            NativeAuthFlowScenarioV2::class.java,
            NativeAuthPublicClientApplicationConfiguration::class.java,
            NativeAuthV2ContinuationState::class.java
        )
        constructor.isAccessible = true
        return constructor.newInstance(
            "correlation-id",
            scenario,
            NativeAuthPublicClientApplicationConfiguration(),
            null
        )
    }

    private fun createStrongAuthVerificationState(): StrongAuthVerificationRequiredStateV2 {
        val constructor = StrongAuthVerificationRequiredStateV2::class.java.getDeclaredConstructor(
            String::class.java,
            NativeAuthFlowScenarioV2::class.java,
            NativeAuthPublicClientApplicationConfiguration::class.java,
            NativeAuthV2ContinuationState::class.java,
            String::class.java
        )
        constructor.isAccessible = true
        return constructor.newInstance(
            "correlation-id",
            NativeAuthFlowScenarioV2.SIGN_IN,
            NativeAuthPublicClientApplicationConfiguration(),
            null,
            "email"
        )
    }
}
