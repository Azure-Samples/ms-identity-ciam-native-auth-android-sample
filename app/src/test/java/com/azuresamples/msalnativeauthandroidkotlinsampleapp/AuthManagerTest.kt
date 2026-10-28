package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import com.microsoft.identity.common.java.nativeauth.providers.responses.v2.NativeAuthV2ContinuationState
import com.microsoft.identity.client.claims.ClaimsRequest
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication
import com.microsoft.identity.nativeauth.NativeAuthPublicClientApplicationConfiguration
import com.microsoft.identity.nativeauth.parameters.NativeAuthResetPasswordParameters
import com.microsoft.identity.nativeauth.parameters.NativeAuthSignInParameters
import com.microsoft.identity.nativeauth.parameters.NativeAuthSignUpParameters
import com.microsoft.identity.nativeauth.statemachine.NativeAuthFlowScenarioV2
import com.microsoft.identity.nativeauth.statemachine.errors.NativeAuthErrorV2
import com.microsoft.identity.nativeauth.statemachine.results.NativeAuthResultV2
import com.microsoft.identity.nativeauth.statemachine.states.CodeRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.ExistingAccountCodeRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.ExistingAccountPasswordRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.NativeAuthBaseStateV2
import com.microsoft.identity.nativeauth.statemachine.states.PasswordRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.SignInAfterResetPasswordStateV2
import com.microsoft.identity.nativeauth.statemachine.states.SignInAfterSignUpStateV2
import com.microsoft.identity.nativeauth.statemachine.states.StrongAuthVerificationRequiredStateV2
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.ArrayDeque

class AuthManagerTest {

    @Test
    fun signInForwardsClaimsWithoutAddingCustomScopes() = runBlocking {
        for (password in listOf(null, "password".toCharArray())) {
            var parameters: NativeAuthSignInParameters? = null
            val claims = authenticationContextClaims()
            val manager = AuthManager(applicationReturning(
                error(), "signInV2"
            ) { parameters = it as NativeAuthSignInParameters })

            manager.signIn("user@example.com", password, claimsRequest = claims)

            assertSame(claims, parameters!!.claimsRequest)
            assertSame(password, parameters!!.password)
            assertNull(parameters!!.scopes)
        }
    }

    @Test
    fun signUpForwardsClaimsWithoutAddingCustomScopes() = runBlocking {
        for (password in listOf(null, "password".toCharArray())) {
            var parameters: NativeAuthSignUpParameters? = null
            val claims = authenticationContextClaims()
            val manager = AuthManager(applicationReturning(
                error(), "signUpV2"
            ) { parameters = it as NativeAuthSignUpParameters })

            manager.signUp("user@example.com", password, claimsRequest = claims)

            assertSame(claims, parameters!!.claimsRequest)
            assertSame(password, parameters!!.password)
            assertNull(parameters!!.scopes)
        }
    }

    @Test
    fun resetPasswordForwardsClaimsWithoutAddingCustomScopes() = runBlocking {
        var parameters: NativeAuthResetPasswordParameters? = null
        val claims = authenticationContextClaims()
        val manager = AuthManager(applicationReturning(
            error(), "resetPasswordV2"
        ) { parameters = it as NativeAuthResetPasswordParameters })

        manager.resetPassword("user@example.com", claimsRequest = claims)

        assertSame(claims, parameters!!.claimsRequest)
        assertNull(parameters!!.scopes)
    }

    @Test
    fun entryPointsDoNotAddClaimsWhenNoneAreProvided() = runBlocking {
        var signIn: NativeAuthSignInParameters? = null
        AuthManager(applicationReturning(error(), "signInV2") {
            signIn = it as NativeAuthSignInParameters
        }).signIn("user@example.com")
        assertNull(signIn!!.claimsRequest)

        var signUp: NativeAuthSignUpParameters? = null
        AuthManager(applicationReturning(error(), "signUpV2") {
            signUp = it as NativeAuthSignUpParameters
        }).signUp("user@example.com")
        assertNull(signUp!!.claimsRequest)

        var reset: NativeAuthResetPasswordParameters? = null
        AuthManager(applicationReturning(error()) {
            reset = it as NativeAuthResetPasswordParameters
        }).resetPassword("user@example.com")
        assertNull(reset!!.claimsRequest)
    }

    @Test
    fun resetPasswordTracksCodeRequiredState() = runBlocking {
        val nextState = createState(CodeRequiredStateV2::class.java)
        val result = NativeAuthResultV2.CodeRequired(
            nextState = nextState,
            scenario = NativeAuthFlowScenarioV2.RESET_PASSWORD,
            codeLength = 6,
            sentTo = "user@example.com",
            channel = "email"
        )
        val authManager = AuthManager(applicationReturning(result))

        authManager.resetPassword("user@example.com")

        assertSame(nextState, authManager.currentState)
    }

    @Test
    fun resetPasswordTracksSignInAfterResetPasswordState() = runBlocking {
        val nextState = createState(SignInAfterResetPasswordStateV2::class.java)
        val result = NativeAuthResultV2.SignInAfterResetPasswordRequired(
            nextState = nextState,
            scenario = NativeAuthFlowScenarioV2.RESET_PASSWORD
        )
        val authManager = AuthManager(applicationReturning(result))

        authManager.resetPassword("user@example.com")

        assertSame(nextState, authManager.currentState)
    }

    @Test
    fun signUpTracksSignInAfterSignUpState() = runBlocking {
        val nextState = createState(SignInAfterSignUpStateV2::class.java)
        val result = NativeAuthResultV2.SignInAfterSignUpRequired(
            nextState = nextState,
            scenario = NativeAuthFlowScenarioV2.SIGN_UP
        )
        val authManager = AuthManager(applicationReturning(result, "signUpV2"))

        authManager.signUp("user@example.com")

        assertSame(nextState, authManager.currentState)
    }

    @Test
    fun recoverableErrorPreservesCurrentContinuation() = runBlocking {
        val nextState = createState(CodeRequiredStateV2::class.java)
        val codeRequired = NativeAuthResultV2.CodeRequired(
            nextState = nextState,
            scenario = NativeAuthFlowScenarioV2.RESET_PASSWORD,
            codeLength = 6,
            sentTo = "user@example.com",
            channel = "email"
        )
        val error = NativeAuthErrorV2(
            errorMessage = "Invalid code",
            correlationId = "correlation-id",
            scenario = NativeAuthFlowScenarioV2.RESET_PASSWORD
        )
        val authManager = AuthManager(
            applicationReturningSequence(codeRequired, error)
        )

        authManager.resetPassword("user@example.com")
        authManager.resetPassword("user@example.com")

        assertSame(nextState, authManager.currentState)
    }

    @Test
    fun staleResultDoesNotTrackContinuation() = runBlocking {
        val nextState = createState(CodeRequiredStateV2::class.java)
        val result = NativeAuthResultV2.CodeRequired(
            nextState = nextState,
            scenario = NativeAuthFlowScenarioV2.RESET_PASSWORD,
            codeLength = 6,
            sentTo = "user@example.com",
            channel = "email"
        )
        val authManager = AuthManager(applicationReturning(result))

        authManager.resetPassword("user@example.com") { false }

        assertNull(authManager.currentState)
    }

    @Test
    fun existingAccountCodeContinuationSupportsSubmitAndResend() = runBlocking {
        val state = createState(ExistingAccountCodeRequiredStateV2::class.java)
        val manager = AuthManager(applicationReturning(
            NativeAuthResultV2.ExistingAccountCodeRequired(
                state, 6, "user@example.com", "email"
            ),
            "signUpV2"
        ))

        manager.signUp("user@example.com")

        assertSame(state, manager.currentState)
        assertNotNull(manager.submitCode("123456") as? NativeAuthErrorV2)
        assertSame(state, manager.currentState)
        assertNotNull(manager.resendCode() as? NativeAuthErrorV2)
        assertSame(state, manager.currentState)
    }

    @Test
    fun existingAccountPasswordContinuationSupportsSubmit() = runBlocking {
        val state = createState(ExistingAccountPasswordRequiredStateV2::class.java)
        val manager = AuthManager(applicationReturning(
            NativeAuthResultV2.ExistingAccountPasswordRequired(state),
            "signUpV2"
        ))

        manager.signUp("user@example.com")

        assertSame(state, manager.currentState)
        assertNotNull(manager.submitPassword("password".toCharArray()) as? NativeAuthErrorV2)
        assertSame(state, manager.currentState)
    }

    @Test
    fun strongAuthResendReturnsSdkErrorAndPreservesCurrentState() = runBlocking {
        val initialState = createStrongAuthVerificationState()
        val refreshedState = createStrongAuthVerificationState()
        val authManager = AuthManager(
            applicationReturningSequence(
                strongAuthResult(initialState),
                strongAuthResult(refreshedState)
            )
        )

        authManager.resetPassword("user@example.com")
        assertSame(initialState, authManager.currentState)
        authManager.resetPassword("user@example.com")
        assertSame(refreshedState, authManager.currentState)

        val result = authManager.resendChallenge() as? NativeAuthErrorV2

        assertNotNull(result)
        assertSame(refreshedState, authManager.currentState)
    }

    private fun strongAuthResult(state: StrongAuthVerificationRequiredStateV2) =
        NativeAuthResultV2.StrongAuthVerificationRequired(
            nextState = state,
            scenario = NativeAuthFlowScenarioV2.RESET_PASSWORD,
            codeLength = 6,
            sentTo = "user@example.com",
            channel = "email"
        )

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
            NativeAuthFlowScenarioV2.RESET_PASSWORD,
            NativeAuthPublicClientApplicationConfiguration(),
            null,
            "email"
        )
    }

    private fun applicationReturning(
        result: NativeAuthResultV2,
        methodName: String = "resetPasswordV2",
        captureParameters: ((Any) -> Unit)? = null
    ): INativeAuthPublicClientApplication {
        return Proxy.newProxyInstance(
            INativeAuthPublicClientApplication::class.java.classLoader,
            arrayOf(INativeAuthPublicClientApplication::class.java)
        ) { _, method, args ->
            when (method.name) {
                methodName -> {
                    captureParameters?.invoke(args[0])
                    result
                }
                else -> throw UnsupportedOperationException(method.name)
            }
        } as INativeAuthPublicClientApplication
    }

    private fun authenticationContextClaims(): ClaimsRequest =
        ClaimsRequest.getClaimsRequestFromJsonString(
            "{\"access_token\":{\"acrs\":{\"essential\":true,\"value\":\"c4\"}}}"
        )

    private fun error() = NativeAuthErrorV2(
        errorMessage = "Invalid input",
        correlationId = "correlation-id",
        scenario = NativeAuthFlowScenarioV2.SIGN_IN
    )

    private fun applicationReturningSequence(
        vararg results: NativeAuthResultV2
    ): INativeAuthPublicClientApplication {
        val queue = ArrayDeque(results.toList())
        return Proxy.newProxyInstance(
            INativeAuthPublicClientApplication::class.java.classLoader,
            arrayOf(INativeAuthPublicClientApplication::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "resetPasswordV2" -> queue.removeFirst()
                else -> throw UnsupportedOperationException(method.name)
            }
        } as INativeAuthPublicClientApplication
    }

    private fun <T : NativeAuthBaseStateV2> createState(
        stateClass: Class<T>,
        scenario: NativeAuthFlowScenarioV2 = NativeAuthFlowScenarioV2.RESET_PASSWORD
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
}
