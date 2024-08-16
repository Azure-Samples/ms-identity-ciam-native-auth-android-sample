package com.azuresamples.msalnativeauthandroidkotlinsampleapp.clients

import android.app.Application
import android.content.Context
import android.util.Log
import com.azuresamples.msalnativeauthandroidkotlinsampleapp.R
import com.microsoft.identity.client.Logger
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication

object AuthClient : Application() {
    private lateinit var authClient: INativeAuthPublicClientApplication

    @JvmStatic
    fun getAuthClient(): INativeAuthPublicClientApplication {
        return authClient
    }

    @JvmStatic
    fun initialize(context: Context) {
        Logger.getInstance().setExternalLogger { tag, logLevel, message, containsPII ->
            Log.e(
                "MSAL",
                "$tag $logLevel $message"
            )
        }

        authClient = PublicClientApplication.createNativeAuthPublicClientApplication(
            context,
            R.raw.auth_config_native_auth
        )
    }
}