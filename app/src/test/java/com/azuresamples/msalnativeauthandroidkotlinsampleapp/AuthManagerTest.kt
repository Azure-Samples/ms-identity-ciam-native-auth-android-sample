package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import com.microsoft.identity.common.java.nativeauth.providers.responses.v2.NativeAuthV2ContinuationState
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication
import com.microsoft.identity.nativeauth.NativeAuthPublicClientApplicationConfiguration
import com.microsoft.identity.nativeauth.statemachine.NativeAuthFlowScenarioV2
import com.microsoft.identity.nativeauth.statemachine.errors.NativeAuthErrorV2
import com.microsoft.identity.nativeauth.statemachine.results.NativeAuthResultV2
import com.microsoft.identity.nativeauth.statemachine.states.CodeRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.NativeAuthBaseStateV2
import com.microsoft.identity.nativeauth.statemachine.states.SignInAfterResetPasswordStateV2
import com.microsoft.identity.nativeauth.statemachine.states.SignInAfterSignUpStateV2
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.ArrayDeque

class AuthManagerTest {

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

    private fun applicationReturning(
        result: NativeAuthResultV2,
        methodName: String = "resetPasswordV2"
    ): INativeAuthPublicClientApplication {
        return Proxy.newProxyInstance(
            INativeAuthPublicClientApplication::class.java.classLoader,
            arrayOf(INativeAuthPublicClientApplication::class.java)
        ) { _, method, _ ->
            when (method.name) {
                methodName -> result
                else -> throw UnsupportedOperationException(method.name)
            }
        } as INativeAuthPublicClientApplication
    }

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
            NativeAuthFlowScenarioV2.RESET_PASSWORD,
            NativeAuthPublicClientApplicationConfiguration(),
            null
        )
    }
}
