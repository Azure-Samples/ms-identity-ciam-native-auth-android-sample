// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.os.Parcelable
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.microsoft.identity.nativeauth.AuthMethod
import com.microsoft.identity.nativeauth.statemachine.NativeAuthFlowScenarioV2
import com.microsoft.identity.nativeauth.statemachine.errors.NativeAuthErrorV2
import com.microsoft.identity.nativeauth.statemachine.results.NativeAuthResultV2
import kotlinx.coroutines.launch

/**
 * Routes V2 continuations without assuming that enrollment finishes the originating flow.
 * Each screen still owns its retry state; errors must not replace that state.
 */
internal fun Fragment.routeNativeAuthResultV2(result: NativeAuthResultV2) {
    AuthClient.getAuthManager().track(result)
    when (result) {
        is NativeAuthResultV2.Complete -> {
            Toast.makeText(requireContext(), getString(R.string.sign_in_successful_message), Toast.LENGTH_SHORT).show()
            val manager = parentFragmentManager
            // Return to the scenario entry, not an already-consumed enrollment/password step.
            val entryNames = setOf(
                EmailSignInSignUpFragment::class.java.name,
                EmailPasswordSignInSignUpFragment::class.java.name,
                PasswordResetFragment::class.java.name,
                MFAFragment::class.java.name
            )
            val entry = (manager.backStackEntryCount - 1 downTo 0)
                .map { manager.getBackStackEntryAt(it).name }
                .firstOrNull { it in entryNames }
            if (entry != null) manager.popBackStack(entry, 0)
        }
        is NativeAuthResultV2.StrongAuthRegistrationRequired ->
            openAuthMethods(result.nextState, result.authMethods)
        is NativeAuthResultV2.MFARequired ->
            openAuthMethods(result.nextState, result.authMethods)
        is NativeAuthResultV2.ResetPasswordMethodRequired ->
            openAuthMethods(result.nextState, result.authMethods)
        is NativeAuthResultV2.StrongAuthVerificationRequired ->
            openVerification(result.nextState, result.sentTo, result.channel, result.codeLength)
        is NativeAuthResultV2.MFAVerificationRequired ->
            openVerification(result.nextState, result.sentTo, result.channel, result.codeLength)
        is NativeAuthResultV2.CodeRequired -> {
            val fragment = when (result.scenario) {
                NativeAuthFlowScenarioV2.RESET_PASSWORD -> PasswordResetCodeFragment()
                NativeAuthFlowScenarioV2.SIGN_UP -> SignUpCodeFragment()
                else -> SignInCodeFragment()
            }
            openContinuation(fragment, Bundle().apply {
                putParcelable(Constants.STATE, result.nextState)
                putString(Constants.SENT_TO, result.sentTo)
                putString(Constants.CHANNEL, result.channel)
            })
        }
        is NativeAuthResultV2.AttributesRequired ->
            openAttributes(result.nextState, result.requiredAttributes.mapNotNull { it.attributeName })
        is NativeAuthResultV2.AttributesInvalid ->
            openAttributes(result.nextState, result.invalidAttributes)
        is NativeAuthResultV2.PasswordRequired ->
            requestContinuationPassword { password -> result.nextState.submitPassword(password) }
        is NativeAuthResultV2.NewPasswordRequired ->
            openContinuation(PasswordResetNewPasswordFragment(), Bundle().apply {
                putParcelable(Constants.STATE, result.nextState)
            })
        is NativeAuthResultV2.SignInAfterSignUpRequired ->
            continueSignIn { result.nextState.signIn() }
        is NativeAuthResultV2.SignInAfterResetPasswordRequired ->
            continueSignIn { result.nextState.signIn() }
        is NativeAuthErrorV2 -> showNativeAuthError(result)
        else -> AlertDialog.Builder(requireContext())
            .setTitle(R.string.unexpected_sdk_result_title)
            .setMessage(R.string.unknown_error_message)
            .show()
    }
}

private fun Fragment.openAuthMethods(state: Parcelable, methods: List<AuthMethod>) =
    openContinuation(PickAuthMethodFragment(), Bundle().apply {
        putParcelable(Constants.STATE, state)
        putSerializable(Constants.AUTH_METHOD_LIST, ArrayList(methods))
    })

private fun Fragment.openVerification(state: Parcelable, sentTo: String, channel: String, codeLength: Int) =
    openContinuation(MFAVerificationFragment(), Bundle().apply {
        putParcelable(Constants.STATE, state)
        putString(Constants.SENT_TO, sentTo)
        putString(Constants.CHANNEL, channel)
        putInt(Constants.CODE_LENGTH, codeLength)
    })

private fun Fragment.openAttributes(state: Parcelable, names: List<String>) =
    openContinuation(SignUpAttributesFragmentV2(), Bundle().apply {
        putParcelable(Constants.STATE, state)
        putStringArrayList(Constants.REQUIRED_ATTRIBUTES, ArrayList(names))
    })

private fun Fragment.openContinuation(fragment: Fragment, bundle: Bundle) {
    fragment.arguments = bundle
    parentFragmentManager.beginTransaction()
        .setReorderingAllowed(true)
        .addToBackStack(fragment::class.java.name)
        .replace(R.id.scenario_fragment, fragment)
        .commit()
}

private fun Fragment.requestContinuationPassword(submit: suspend (CharArray) -> NativeAuthResultV2) {
    val input = EditText(requireContext()).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        hint = getString(R.string.password_required_hint)
    }
    val dialog = AlertDialog.Builder(requireContext())
        .setTitle(R.string.password_required_dialog_title)
        .setMessage(R.string.password_required_dialog_message)
        .setView(input)
        .setPositiveButton(R.string.submit_password, null)
        .setNegativeButton(R.string.cancel_message, null)
        .create()
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val password = CharArray(input.length())
            input.text.getChars(0, input.length(), password, 0)
            input.text.clear()
            viewLifecycleOwner.lifecycleScope.launch {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                try {
                    val result = submit(password)
                    if (result is NativeAuthErrorV2) {
                        showNativeAuthError(result)
                    } else {
                        dialog.dismiss()
                        routeNativeAuthResultV2(result)
                    }
                } finally {
                    password.fill('\u0000')
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                }
            }
        }
    }
    showViewDialog(dialog)
}

private fun Fragment.continueSignIn(call: suspend () -> NativeAuthResultV2) {
    viewLifecycleOwner.lifecycleScope.launch {
        val result = call()
        if (result is NativeAuthErrorV2) {
            showNativeAuthError(result) { continueSignIn(call) }
        } else {
            routeNativeAuthResultV2(result)
        }
    }
}

private fun Fragment.showNativeAuthError(error: NativeAuthErrorV2, retry: (() -> Unit)? = null) {
    val builder = AlertDialog.Builder(requireContext())
        .setTitle(error.error ?: getString(R.string.unexpected_sdk_error_title))
        .setMessage(error.errorMessage)
        .setNegativeButton(R.string.cancel_message, null)
    if (retry != null) builder.setPositiveButton(R.string.retry_message) { _, _ -> retry() }
    showViewDialog(builder.create())
}

private fun Fragment.showViewDialog(dialog: AlertDialog) {
    val lifecycle = viewLifecycleOwner.lifecycle
    val observer = object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            dialog.dismiss()
        }
    }
    lifecycle.addObserver(observer)
    dialog.setOnDismissListener { lifecycle.removeObserver(observer) }
    dialog.show()
}
