package com.azuresamples.msalnativeauthandroidkotlinsampleapp.utils

import android.app.AlertDialog
import android.content.Context
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.R
import com.microsoft.identity.nativeauth.statemachine.errors.GetAccessTokenError
import com.microsoft.identity.nativeauth.statemachine.errors.GetAccountError
import com.microsoft.identity.nativeauth.statemachine.errors.MFAError
import com.microsoft.identity.nativeauth.statemachine.errors.ResendCodeError
import com.microsoft.identity.nativeauth.statemachine.errors.ResetPasswordError
import com.microsoft.identity.nativeauth.statemachine.errors.ResetPasswordSubmitPasswordError
import com.microsoft.identity.nativeauth.statemachine.errors.SignInContinuationError
import com.microsoft.identity.nativeauth.statemachine.errors.SignInError
import com.microsoft.identity.nativeauth.statemachine.errors.SignOutError
import com.microsoft.identity.nativeauth.statemachine.errors.SignUpError
import com.microsoft.identity.nativeauth.statemachine.errors.SubmitChallengeError
import com.microsoft.identity.nativeauth.statemachine.errors.SubmitCodeError

class ErrorHandlerUtil(private val context: Context) {
    private val displayHelper = DisplayHelper(context)

    fun handleSignUpError(error: SignUpError) {
        when {
            error.isInvalidUsername() || error.isInvalidPassword() || error.isInvalidAttributes() ||
                    error.isUserAlreadyExists() || error.isAuthNotSupported() || error.isBrowserRequired()
            -> {
                displayHelper.displayDialog(error.error, error.errorMessage)
            }

            else -> {
                handleUnexpectedError(error.errorMessage)
            }
        }
    }

    fun handleGetAccountError(error: GetAccountError) {
        handleUnexpectedError(error.errorMessage)
    }

    fun handleGetAccessTokenError(error: GetAccessTokenError) {
        handleMSALException(error.errorMessage)
    }

   fun handleSignInError(error: SignInError) {
        when {
            error.isInvalidCredentials() || error.isBrowserRequired() || error.isUserNotFound() -> {
                displayHelper.displayDialog(error.error, error.errorMessage)
            }
            else -> {
                handleUnexpectedError(error.errorMessage)
            }
        }
    }

    fun handleSignInContinuationError(error: SignInContinuationError) {
        handleMSALException(error.errorMessage)
    }

    fun handleResetPasswordError(error: ResetPasswordError) {
        when {
            error.isBrowserRequired() || error.isUserNotFound() -> {
                displayHelper.displayDialog(error.error, error.errorMessage)
            }
            else -> {
                handleUnexpectedError(error.errorMessage)
            }
        }
    }

    fun handleResetPasswordSubmitPasswordError(error: ResetPasswordSubmitPasswordError) {
        when {
            error.isInvalidPassword() || error.isPasswordResetFailed() -> {
                displayHelper.displayDialog(error.error, error.errorMessage)
            }
            else -> {
                handleUnexpectedError(error.errorMessage)
            }
        }
    }

    fun handleMFAError(error: MFAError) {
        when {
            error.isError()
            -> {
                displayHelper.displayDialog(error.error, error.errorMessage)
            }

            else -> {
                handleUnexpectedError(error.errorMessage)
            }
        }
    }

    fun handleSubmitCodeError(error: SubmitCodeError) {
        when {
            error.isBrowserRequired() || error.isInvalidCode() -> {
                displayHelper.displayDialog(error.error, error.errorMessage)
            }
            else -> {
                handleUnexpectedError(error.errorMessage)
            }
        }
    }

    fun handleResendCodeError(error: ResendCodeError) {
        handleMSALException(error.errorMessage)
    }

    fun handleSignOutError(error: SignOutError) {
        handleUnexpectedError(error.errorMessage)
    }

    fun handleMSALException(error: String?) {
        displayHelper.displayDialog(context.getString(R.string.msal_exception_title), error)
    }

    fun handleUnexpectedError(error: String?) {
        displayHelper.displayDialog(context.getString(R.string.unexpected_sdk_error_title), error)
    }

    private class DisplayHelper(private val context: Context) {
        fun displayDialog(error: String? = null, message: String?) {
            val builder = AlertDialog.Builder(context)
            builder.setTitle(error)
                .setMessage(message)
            val alertDialog = builder.create()
            alertDialog.show()
        }
    }
}