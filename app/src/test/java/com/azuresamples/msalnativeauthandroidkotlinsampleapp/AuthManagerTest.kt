package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication
import com.microsoft.identity.nativeauth.NativeAuthPublicClientApplicationConfiguration
import com.microsoft.identity.nativeauth.statemachine.errors.NativeAuthErrorV2
import com.microsoft.identity.nativeauth.statemachine.NativeAuthFlowScenarioV2
import com.microsoft.identity.nativeauth.statemachine.results.NativeAuthResultV2
import com.microsoft.identity.nativeauth.statemachine.states.CodeRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.AttributesRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.AttributesInvalidStateV2
import com.microsoft.identity.nativeauth.statemachine.states.MFARequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.MFAVerificationRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.NativeAuthBaseStateV2
import com.microsoft.identity.nativeauth.statemachine.states.NewPasswordRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.PasswordRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.ResetPasswordMethodRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.SignInAfterResetPasswordStateV2
import com.microsoft.identity.nativeauth.statemachine.states.SignInAfterSignUpStateV2
import com.microsoft.identity.nativeauth.statemachine.states.StrongAuthRegistrationRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.StrongAuthVerificationRequiredStateV2
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.reflect.Proxy

class AuthManagerTest {

    @Test
    fun recoverableErrorRetainsCurrentCodeState() {
        val nextState = createState(CodeRequiredStateV2::class.java)
        val authManager = AuthManager(applicationReturning(error()))
        authManager.track(NativeAuthResultV2.CodeRequired(
            nextState, NativeAuthFlowScenarioV2.RESET_PASSWORD, 6, "email", "email"
        ))

        authManager.track(error())

        assertSame(nextState, authManager.currentState)
    }

    @Test
    fun enrollmentErrorsRetainRegistrationState() {
        val nextState = createState(StrongAuthRegistrationRequiredStateV2::class.java)
        val authManager = AuthManager(applicationReturning(error()))
        authManager.track(NativeAuthResultV2.StrongAuthRegistrationRequired(
            nextState, NativeAuthFlowScenarioV2.RESET_PASSWORD, emptyList()
        ))

        authManager.track(error("invalid_input"))

        assertSame(nextState, authManager.currentState)
    }

    @Test
    fun activationErrorsRetainVerificationStateForRepeatedRetries() {
        val nextState = createState(StrongAuthVerificationRequiredStateV2::class.java)
        val authManager = AuthManager(applicationReturning(error()))
        authManager.track(NativeAuthResultV2.StrongAuthVerificationRequired(
            nextState, NativeAuthFlowScenarioV2.RESET_PASSWORD, 6, "email", "email"
        ))

        repeat(2) { authManager.track(error("invalid_code")) }

        assertSame(nextState, authManager.currentState)
    }

    @Test
    fun newEntryFailureDoesNotRetainPreviousFlowState() = runBlocking {
        val previous = createState(CodeRequiredStateV2::class.java)
        val authManager = AuthManager(applicationReturning(error()))
        authManager.track(NativeAuthResultV2.CodeRequired(
            previous, NativeAuthFlowScenarioV2.RESET_PASSWORD, 6, "email", "email"
        ))

        authManager.resetPassword("user@example.com")

        assertNull(authManager.currentState)
    }

    @Test
    fun signUpContinuationIsRetainedForRetry() {
        val nextState = createState(SignInAfterSignUpStateV2::class.java)
        val authManager = AuthManager(applicationReturning(error()))
        authManager.track(NativeAuthResultV2.SignInAfterSignUpRequired(
            nextState, NativeAuthFlowScenarioV2.SIGN_UP
        ))
        authManager.track(error())

        assertSame(nextState, authManager.currentState)
    }

    @Test
    fun activationSuccessorsReplaceTheVerificationRetryState() {
        val scenario = NativeAuthFlowScenarioV2.RESET_PASSWORD
        val successors = listOf(
            createState(AttributesRequiredStateV2::class.java).let {
                it to NativeAuthResultV2.AttributesRequired(it, scenario, emptyList())
            },
            createState(AttributesInvalidStateV2::class.java).let {
                it to NativeAuthResultV2.AttributesInvalid(it, scenario, listOf("city"))
            },
            createState(PasswordRequiredStateV2::class.java).let {
                it to NativeAuthResultV2.PasswordRequired(it, scenario)
            },
            createState(NewPasswordRequiredStateV2::class.java).let {
                it to NativeAuthResultV2.NewPasswordRequired(it, scenario)
            },
            createState(MFARequiredStateV2::class.java).let {
                it to NativeAuthResultV2.MFARequired(it, scenario)
            },
            createState(MFAVerificationRequiredStateV2::class.java).let {
                it to NativeAuthResultV2.MFAVerificationRequired(it, scenario, 6, "email", "email")
            },
            createState(ResetPasswordMethodRequiredStateV2::class.java).let {
                it to NativeAuthResultV2.ResetPasswordMethodRequired(it, scenario)
            }
        )
        val authManager = AuthManager(applicationReturning(error()))
        successors.forEach { (state, result) ->
            val verification = createState(StrongAuthVerificationRequiredStateV2::class.java)
            authManager.track(NativeAuthResultV2.StrongAuthVerificationRequired(
                verification, scenario, 6, "email", "email"
            ))
            authManager.track(result)
            authManager.track(error())

            assertSame(state, authManager.currentState)
        }
    }

    private fun error(errorType: String? = null) = NativeAuthErrorV2(
            errorType = errorType,
            errorMessage = "Invalid code",
            correlationId = "correlation-id",
            scenario = NativeAuthFlowScenarioV2.RESET_PASSWORD
        )

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

    private fun applicationReturning(result: NativeAuthResultV2): INativeAuthPublicClientApplication {
        return Proxy.newProxyInstance(
            INativeAuthPublicClientApplication::class.java.classLoader,
            arrayOf(INativeAuthPublicClientApplication::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "resetPasswordV2" -> result
                else -> throw UnsupportedOperationException(method.name)
            }
        } as INativeAuthPublicClientApplication
    }

    private fun <T : NativeAuthBaseStateV2> createState(stateClass: Class<T>): T {
        val constructor = stateClass.declaredConstructors.single {
            !it.isSynthetic && it.parameterTypes.firstOrNull() == String::class.java
        }
        constructor.isAccessible = true
        val args = mutableListOf<Any?>(
            "correlation-id",
            NativeAuthFlowScenarioV2.RESET_PASSWORD,
            NativeAuthPublicClientApplicationConfiguration(),
            null
        )
        constructor.parameterTypes.drop(4).forEach {
            args += if (it == List::class.java) emptyList<Any>() else null
        }
        return stateClass.cast(constructor.newInstance(*args.toTypedArray()))
    }
}
