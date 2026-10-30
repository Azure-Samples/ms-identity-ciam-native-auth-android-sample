package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import com.microsoft.identity.client.claims.ClaimsRequest
import com.microsoft.identity.nativeauth.AuthMethod
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication
import com.microsoft.identity.nativeauth.UserAttributes
import com.microsoft.identity.nativeauth.parameters.NativeAuthResetPasswordParametersV2
import com.microsoft.identity.nativeauth.parameters.NativeAuthSignInParameters
import com.microsoft.identity.nativeauth.parameters.NativeAuthSignUpParametersV2
import com.microsoft.identity.nativeauth.statemachine.errors.NativeAuthErrorV2
import com.microsoft.identity.nativeauth.statemachine.results.NativeAuthResultV2
import com.microsoft.identity.nativeauth.statemachine.states.AttributesInvalidStateV2
import com.microsoft.identity.nativeauth.statemachine.states.AttributesRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.CodeRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.ExistingAccountCodeRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.ExistingAccountPasswordRequiredStateV2
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
        claimsRequest: ClaimsRequest? = null,
        isCurrent: () -> Boolean = { true }
    ): NativeAuthResultV2 {
        val parameters = NativeAuthSignInParameters(username = email)
        parameters.password = password
        parameters.claimsRequest = claimsRequest
        return trackIfCurrent(application.signInV2(parameters), isCurrent)
    }

    suspend fun signUp(
        email: String,
        attributes: UserAttributes? = null,
        claimsRequest: ClaimsRequest? = null,
        isCurrent: () -> Boolean = { true }
    ): NativeAuthResultV2 {
        val parameters = NativeAuthSignUpParametersV2(username = email)
        parameters.attributes = attributes
        parameters.claimsRequest = claimsRequest
        return trackIfCurrent(application.signUpV2(parameters), isCurrent)
    }

    suspend fun resetPassword(
        email: String,
        claimsRequest: ClaimsRequest? = null,
        isCurrent: () -> Boolean = { true }
    ): NativeAuthResultV2 {
        val parameters = NativeAuthResetPasswordParametersV2(username = email)
        parameters.claimsRequest = claimsRequest
        return trackIfCurrent(application.resetPasswordV2(parameters), isCurrent)
    }

    suspend fun submitCode(
        code: String,
        isCurrent: () -> Boolean = { true }
    ): NativeAuthResultV2? =
        when (val state = currentState) {
            is CodeRequiredStateV2 ->
                trackIfCurrent(state.submitCode(code), isCurrent)
            is ExistingAccountCodeRequiredStateV2 ->
                trackIfCurrent(state.submitCode(code), isCurrent)
            else -> null
        }

    suspend fun resendCode(isCurrent: () -> Boolean = { true }): NativeAuthResultV2? =
        when (val state = currentState) {
            is CodeRequiredStateV2 ->
                trackIfCurrent(state.resendCode(), isCurrent)
            is ExistingAccountCodeRequiredStateV2 ->
                trackIfCurrent(state.resendCode(), isCurrent)
            else -> null
        }

    suspend fun submitPassword(
        password: CharArray,
        isCurrent: () -> Boolean = { true }
    ): NativeAuthResultV2? =
        when (val state = currentState) {
            is PasswordRequiredStateV2 ->
                trackIfCurrent(state.submitPassword(password), isCurrent)
            is ExistingAccountPasswordRequiredStateV2 ->
                trackIfCurrent(state.submitPassword(password), isCurrent)
            else -> null
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
        when (val state = currentState) {
            is MFAVerificationRequiredStateV2 ->
                trackIfCurrent(state.resendChallenge(), isCurrent)
            is StrongAuthVerificationRequiredStateV2 ->
                trackIfCurrent(state.resendChallenge(), isCurrent)
            else -> null
        }

    fun clearState() {
        currentState = null
    }

    internal fun track(result: NativeAuthResultV2): NativeAuthResultV2 {
        val nextState = when (result) {
            is NativeAuthResultV2.CodeRequired -> result.nextState
            is NativeAuthResultV2.ExistingAccountCodeRequired -> result.nextState
            is NativeAuthResultV2.PasswordRequired -> result.nextState
            is NativeAuthResultV2.ExistingAccountPasswordRequired -> result.nextState
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
