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
import com.microsoft.identity.nativeauth.statemachine.states.SignInAfterResetPasswordStateV2
import com.microsoft.identity.nativeauth.statemachine.states.StrongAuthRegistrationRequiredStateV2
import com.microsoft.identity.nativeauth.statemachine.states.StrongAuthVerificationRequiredStateV2

/**
 * Facade over the Native Auth V2 SDK surface. Starts the V2 entry points, retains the state
 * handed back so a multi-step flow can be continued, and returns the unified [NativeAuthResultV2].
 */
class AuthManager(private val application: INativeAuthPublicClientApplication) {

    var currentState: NativeAuthBaseStateV2? = null
        private set

    /**
     * Single switch the MFA screens read to decide whether to drive the Native Auth V2 SDK surface
     * (true) or the legacy V1 surface (false). Defaults to [Configuration.useNativeAuthV2] so the
     * whole sample stays on one surface unless overridden.
     */
    var useV2APIs: Boolean = Configuration.useNativeAuthV2

    suspend fun signIn(email: String, password: CharArray? = null): NativeAuthResultV2 {
        val parameters = NativeAuthSignInParameters(username = email)
        parameters.password = password
        return track(application.signInV2(parameters))
    }

    suspend fun signUp(email: String, password: CharArray? = null): NativeAuthResultV2 {
        val parameters = NativeAuthSignUpParameters(username = email)
        parameters.password = password
        return track(application.signUpV2(parameters))
    }

    suspend fun resetPassword(email: String): NativeAuthResultV2 {
        val parameters = NativeAuthResetPasswordParameters(username = email)
        return track(application.resetPasswordV2(parameters))
    }

    suspend fun submitCode(code: String): NativeAuthResultV2? =
        (currentState as? CodeRequiredStateV2)?.let { track(it.submitCode(code)) }

    suspend fun resendCode(): NativeAuthResultV2? =
        (currentState as? CodeRequiredStateV2)?.let { track(it.resendCode()) }

    suspend fun submitPassword(password: CharArray): NativeAuthResultV2? =
        (currentState as? PasswordRequiredStateV2)?.let { track(it.submitPassword(password)) }

    suspend fun submitNewPassword(password: CharArray): NativeAuthResultV2? =
        (currentState as? NewPasswordRequiredStateV2)?.let { track(it.submitNewPassword(password)) }

    suspend fun signInAfterPasswordReset(): NativeAuthResultV2? =
        (currentState as? SignInAfterResetPasswordStateV2)?.let { track(it.signIn()) }

    suspend fun submitAttributes(attributes: UserAttributes): NativeAuthResultV2? =
        when (val state = currentState) {
            is AttributesRequiredStateV2 -> track(state.submitAttributes(attributes))
            is AttributesInvalidStateV2 -> track(state.submitAttributes(attributes))
            else -> null
        }

    suspend fun selectAuthMethod(method: AuthMethod, verificationContact: String? = null): NativeAuthResultV2? =
        when (val state = currentState) {
            is MFARequiredStateV2 -> track(state.selectAuthMethod(method, verificationContact))
            is StrongAuthRegistrationRequiredStateV2 -> track(state.selectAuthMethod(method, verificationContact))
            else -> null
        }

    suspend fun submitChallenge(challenge: String): NativeAuthResultV2? =
        when (val state = currentState) {
            is MFAVerificationRequiredStateV2 -> track(state.submitChallenge(challenge))
            is StrongAuthVerificationRequiredStateV2 -> track(state.submitChallenge(challenge))
            else -> null
        }

    private fun track(result: NativeAuthResultV2): NativeAuthResultV2 {
        currentState = when (result) {
            is NativeAuthResultV2.CodeRequired -> result.nextState
            is NativeAuthResultV2.PasswordRequired -> result.nextState
            is NativeAuthResultV2.NewPasswordRequired -> result.nextState
            is NativeAuthResultV2.AttributesRequired -> result.nextState
            is NativeAuthResultV2.AttributesInvalid -> result.nextState
            is NativeAuthResultV2.MFARequired -> result.nextState
            is NativeAuthResultV2.MFAVerificationRequired -> result.nextState
            is NativeAuthResultV2.SignInAfterResetPasswordRequired -> result.nextState
            is NativeAuthResultV2.StrongAuthRegistrationRequired -> result.nextState
            is NativeAuthResultV2.StrongAuthVerificationRequired -> result.nextState
            else -> null
        }
        return result
    }
}
