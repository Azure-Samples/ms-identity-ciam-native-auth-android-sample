package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.Application
import android.util.Log
import org.json.JSONObject

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        Log.d("MS", "Application onCreate called")
        initializeProveIfConfigured()
    }

    private fun initializeProveIfConfigured() {
        // Read Prove credentials directly from the packaged config file:
        // `app/src/main/assets/config.json`
        val (clientId, clientSecret) = try {
            val jsonText = assets.open("config.json").bufferedReader().use { it.readText() }
            val proveJson = JSONObject(jsonText).optJSONObject("Prove")
            val id = proveJson?.optString("clientId").orEmpty().trim()
            val secret = proveJson?.optString("clientSecret").orEmpty().trim()
            id to secret
        } catch (t: Throwable) {
            Log.e("MS", "Failed to read Prove config from assets/config.json", t)
            "" to ""
        }

        if (clientId.isBlank() || clientSecret.isBlank()) {
            Log.w(
                "MS",
                "Prove not initialized. Set Prove.clientId and Prove.clientSecret in app/src/main/assets/config.json."
            )
            return
        }

        ProveManager.getInstance().initialize(
            clientId = clientId,
            clientSecret = clientSecret
        )
    }
}