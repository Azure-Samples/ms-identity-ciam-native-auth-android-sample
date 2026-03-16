package com.example.myapplication.helpers

object ApiEndpoints {
    // Base URLs
    // TODO: Replace the endpoint with the tenant specific endpoint (using tenant id)
    // Format: https://<custom domain>/<tenant id>/
    const val AUTH_API_BASE_URL_SIGNIN = "<Tenant specific endpoint (using tenant id)>"

    // Sign-in Endpoints
    const val SIGNIN_INITIATE = "oauth2/v2.0/initiate"
    const val SIGNIN_CHALLENGE = "oauth2/v2.0/challenge"
    const val SIGNIN_TOKEN = "oauth2/v2.0/token"
    const val SIGNIN_INTROSPECT = "oauth2/v2.0/introspect"
    
    const val SIGNUP_TOKEN = "OAuth2/v2.0/token"

    // App Credentials
    const val CLIENT_ID = "019f8c18-e680-43b3-9ac3-d9e118b69c0d"

    // ThreatMetrix Constants
    // TODO: Replace with appropriate FP server from LNR profile
    const val TMX_FP_SERVER = "<fp_server>"
}
