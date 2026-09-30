package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import com.microsoft.identity.nativeauth.AuthMethod
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication
import com.microsoft.identity.nativeauth.UserAttributes
import com.microsoft.identity.nativeauth.parameters.NativeAuthResetPasswordParameters
import com.microsoft.identity.nativeauth.parameters.NativeAuthSignInParameters
import com.microsoft.identity.nativeauth.parameters.NativeAuthSignUpParameters
import com.microsoft.identity.nativeauth.statemachine.errors.NativeAuthErrorV2
import com.microsoft.identity.nativeauth.statemachine.results.NativeAuthResultV2
import com.microsoft.identity.nativeauth.statemachine.states.AttributesInvalidStateV2
import com.microsoft.identity.nativeauth.statemachine.states.AttributesRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.CodeRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.MFARequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.MFAVerificationRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.NativeAuthBaseStateV2
import com.microsoft.identity.nativeauth.statemachine.states.NewPasswordRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.PasswordRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.ResetPasswordMethodRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.StrongAuthRegistrationRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.StrongAuthVerificationRequiredStateV2

/**
 * Facade over the Native Auth V2 SDK surface. Starts the V2 entry points, retains the state
 * handed back so a multi-step flow can be continued, and returns the unified [NativeAuthResultV2].
 */
class AuthManager(private val application: INativeAuthPublicClientApplication) {

    var currentState: NativeAuthBaseStateV2? = null
        private set

    suspend fun signIn(
        email: String,
        password: CharArray? = null,
        isCurrent: () -> Boolean = { true }
    ): NativeAuthResultV2 {
        val parameters = NativeAuthSignInParameters(username = email)
        parameters.password = password
        parameters.scopes =
            listOf("api://96e12db6-dcb2-47f2-b6fc-2e3c8d27e903/Custom.Scope")
        return trackIfCurrent(application.signInV2(parameters), isCurrent)
    }

    suspend fun signUp(
        email: String,
        password: CharArray? = null,
        attributes: UserAttributes? = null,
        isCurrent: () -> Boolean = { true }
    ): NativeAuthResultV2 {
        val parameters = NativeAuthSignUpParameters(username = email)
        parameters.password = password
        parameters.attributes = attributes
        return trackIfCurrent(application.signUpV2(parameters), isCurrent)
    }

    suspend fun resetPassword(
        email: String,
        isCurrent: () -> Boolean = { true }
    ): NativeAuthResultV2 {
        val parameters = NativeAuthResetPasswordParameters(username = email)
        return trackIfCurrent(application.resetPasswordV2(parameters), isCurrent)
    }

    suspend fun submitCode(
        code: String,
        isCurrent: () -> Boolean = { true }
    ): NativeAuthResultV2? =
        (currentState as? CodeRequiredStateV2)?.let {
            trackIfCurrent(it.submitCode(code), isCurrent)
        }

    suspend fun resendCode(isCurrent: () -> Boolean = { true }): NativeAuthResultV2? =
        (currentState as? CodeRequiredStateV2)?.let {
            trackIfCurrent(it.resendCode(), isCurrent)
        }

    suspend fun submitPassword(
        password: CharArray,
        isCurrent: () -> Boolean = { true }
    ): NativeAuthResultV2? =
        (currentState as? PasswordRequiredStateV2)?.let {
            trackIfCurrent(it.submitPassword(password), isCurrent)
        }

    suspend fun submitNewPassword(
        password: CharArray,
        isCurrent: () -> Boolean = { true }
    ): NativeAuthResultV2? =
        (currentState as? NewPasswordRequiredStateV2)?.let {
            trackIfCurrent(it.submitNewPassword(password), isCurrent)
        }

    suspend fun submitAttributes(
        attributes: UserAttributes,
        isCurrent: () -> Boolean = { true }
    ): NativeAuthResultV2? =
        when (val state = currentState) {
            is AttributesRequiredStateV2 ->
                trackIfCurrent(state.submitAttributes(attributes), isCurrent)
            is AttributesInvalidStateV2 ->
                trackIfCurrent(state.submitAttributes(attributes), isCurrent)
            else -> null
        }

    suspend fun selectAuthMethod(
        method: AuthMethod,
        verificationContact: String? = null,
        isCurrent: () -> Boolean = { true }
    ): NativeAuthResultV2? =
        when (val state = currentState) {
            is MFARequiredStateV2 ->
                trackIfCurrent(state.selectAuthMethod(method, verificationContact), isCurrent)
            is ResetPasswordMethodRequiredStateV2 ->
                trackIfCurrent(state.selectAuthMethod(method), isCurrent)
            is StrongAuthRegistrationRequiredStateV2 ->
                trackIfCurrent(state.selectAuthMethod(method, verificationContact), isCurrent)
            else -> null
        }

    suspend fun submitChallenge(
        challenge: String,
        isCurrent: () -> Boolean = { true }
    ): NativeAuthResultV2? =
        when (val state = currentState) {
            is MFAVerificationRequiredStateV2 ->
                trackIfCurrent(state.submitChallenge(challenge), isCurrent)
            is StrongAuthVerificationRequiredStateV2 ->
                trackIfCurrent(state.submitChallenge(challenge), isCurrent)
            else -> null
        }

    suspend fun resendChallenge(isCurrent: () -> Boolean = { true }): NativeAuthResultV2? =
        (currentState as? MFAVerificationRequiredStateV2)?.let {
            trackIfCurrent(it.resendChallenge(), isCurrent)
        }

    fun clearState() {
        currentState = null
    }

    private fun track(result: NativeAuthResultV2): NativeAuthResultV2 {
        val nextState = when (result) {
            is NativeAuthResultV2.CodeRequired -> result.nextState
            is NativeAuthResultV2.PasswordRequired -> result.nextState
            is NativeAuthResultV2.NewPasswordRequired -> result.nextState
            is NativeAuthResultV2.AttributesRequired -> result.nextState
            is NativeAuthResultV2.AttributesInvalid -> result.nextState
            is NativeAuthResultV2.MFARequired -> result.nextState
            is NativeAuthResultV2.ResetPasswordMethodRequired -> result.nextState
            is NativeAuthResultV2.MFAVerificationRequired -> result.nextState
            is NativeAuthResultV2.SignInAfterSignUpRequired -> result.nextState
            is NativeAuthResultV2.SignInAfterResetPasswordRequired -> result.nextState
            is NativeAuthResultV2.StrongAuthRegistrationRequired -> result.nextState
            is NativeAuthResultV2.StrongAuthVerificationRequired -> result.nextState
            else -> null
        }
        if (nextState != null) {
            currentState = nextState
        } else if (result !is NativeAuthErrorV2) {
            currentState = null
        }
        return result
    }

    private fun trackIfCurrent(
        result: NativeAuthResultV2,
        isCurrent: () -> Boolean
    ): NativeAuthResultV2 {
        return if (isCurrent()) track(result) else result
    }
}
