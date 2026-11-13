package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication

class NativeAuthViewModelFactory(
    private val authClient: INativeAuthPublicClientApplication,
    private val authManager: AuthManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NativeAuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NativeAuthViewModel(authClient, authManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
