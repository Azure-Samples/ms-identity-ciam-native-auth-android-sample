package com.example.myapplication.helpers

object ApiEndpoints {
    // Base URLs
    // TODO: Replace with your actual base URLs. Format: https://<your-domain>/<your-tenant-url>
    const val AUTH_API_BASE_URL_SIGNUP = "<base-url-for-signup-api>"

    // Sign-up Endpoints
    const val SIGNUP_START = "signup/v1.0/start"
    const val SIGNUP_CHALLENGE = "signup/v1.0/challenge"
    const val SIGNUP_CONTINUE = "signup/v1.0/continue"
    const val SIGNUP_TOKEN = "OAuth2/v2.0/token"

    // App Credentials
    const val CLIENT_ID = "019f8c18-e680-43b3-9ac3-d9e118b69c0d"

    // ThreatMetrix Constants
    const val TMX_FP_SERVER = "h-sdk.online"
}
