package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.Application
import android.content.Context
import android.util.Log
import com.microsoft.identity.client.Logger
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication

object AuthClient : Application() {
    private lateinit var authClient: INativeAuthPublicClientApplication
    private lateinit var applicationContext : Context

    @JvmStatic
    fun getAuthClient(): INativeAuthPublicClientApplication {
        return authClient
    }

    @JvmStatic
    fun getAppContext(): Context {
        return applicationContext
    }

    @JvmStatic
    fun initialize(context: Context) {
        /*Logger.getInstance().setExternalLogger { tag, logLevel, message, containsPII ->
            Log.e(
                "MSAL",
                "$tag $logLevel $message"
            )
        }*/

        authClient = PublicClientApplication.createNativeAuthPublicClientApplication(
            context,
            R.raw.native_auth_sample_app_config
        )
        applicationContext = context.applicationContext
    }
}