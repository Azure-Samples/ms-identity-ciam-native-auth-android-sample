package com.azuresamples.msalnativeauthandroidkotlinsampleapp

object Configuration {
    /**
     * Global switch between the Native Auth V1 and V2 SDK surfaces. Set to false to use V1.
     */
    const val useNativeAuthV2 = true
    const val authenticationContextClaim = "{\"access_token\":{\"acrs\":{\"essential\":true,\"value\":\"c4\"}}}"
}
