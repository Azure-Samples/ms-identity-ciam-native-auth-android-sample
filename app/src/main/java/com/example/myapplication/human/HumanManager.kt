package com.example.myapplication.human

import android.app.Application
import com.humansecurity.mobile_sdk.HumanSecurity
import com.humansecurity.mobile_sdk.main.HSBotDefenderDelegate
import com.humansecurity.mobile_sdk.main.policy.HSAutomaticInterceptorType
import com.humansecurity.mobile_sdk.main.policy.HSPolicy
import com.humansecurity.mobile_sdk.main.policy.HSStorageMethod

object HumanManager: HSBotDefenderDelegate {

    // HumanDelegate
    override fun botDefenderRequestBlocked(url: String?, appId: String) {
        println("Request Blocked")
    }

    override fun botDefenderChallengeSolved(appId: String) {
        println("Challenge Solved")
    }

    override fun botDefenderChallengeCancelled(appId: String) {
        println("Challenge Cancelled")
    }

    override fun botDefenderChallengeRendered(appId: String) {
        println("Challenge Rendered")
    }

    override fun botDefenderChallengeRenderFailed(appId: String) {
        println("Challenge Render Failed")
    }

    override fun botDefenderDidUpdateHeaders(headers: HashMap<String, String>, appId: String) {
        println("Headers Were Updated")
    }

    // properties
    // App id for entra-prod
    const val appId = "<app_id>"

    // HUMAN

    fun start(application: Application) {
        println("SDK version: ${HumanSecurity.sdkVersion()}")

        start(application, appId)
    }

    private fun start(application: Application, appId: String) {
        // Create and configure the policy //
        val policy = HSPolicy()
        policy.storageMethod = HSStorageMethod.DATA_STORE
        policy.automaticInterceptorPolicy.interceptorType = HSAutomaticInterceptorType.INTERCEPT_AND_RETRY_REQUEST
        // Doctor app policy will always force challenge. Use it only for testing purpose
        policy.doctorAppPolicy.enabled = false // true, if want to force challenge

        // Start HUMAN SDK with your AppID //
        HumanSecurity.start(application, appId, policy)
    }
}
