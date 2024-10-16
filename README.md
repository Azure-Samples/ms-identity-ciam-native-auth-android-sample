# Sign in users and call a protected web API in Android (Kotlin) using native authentication

* [Overview](#overview)
* [Contents](#contents)
* [Prerequisites](#prerequisites)
* [Project setup](#project-setup)
* [Key concepts](#key-concepts)
* [Reporting problems](#reporting-problems)
* [Contributing](#contributing)

## Overview

This sample Android application demonstrates how to handle sign-up, sign-in, sign-out, and password reset scenarios using Microsoft Entra for customers. 
You can configure the sample to call a protected web API after authenticating successfully.

## Contents

| File/folder | Description |
|-------------|-------------|
| `app/src/main/res/raw/native_auth_sample_app_config.json`       | Configuration file. |
| `.gitignore` | Define what to ignore at commit time. |
| `README.md` | This README file. |
| `LICENSE`   | The license for the sample. |

## Prerequisites

* <a href="https://developer.android.com/studio/archive" target="_blank">Android Studio Dolphin | 2021.3.1 Patch 1</a>.
- An external tenant. To create one, choose from the following methods:
    * (Recommended) Use the [Microsoft Entra External ID extension](https://aka.ms/ciamvscode/readme/marketplace) to set up an external tenant directly in Visual Studio Code.
    * [Create a new external tenant](https://learn.microsoft.com/entra/external-id/customers/how-to-create-external-tenant-portal) in the Microsoft Entra admin center.
- A user account in your **Microsoft Entra External ID** tenant.

## Project setup

To enable your application to authenicate users with Microsoft Entra, Microsoft Entra for customers must be made aware of the application you create. The following steps show you how to:

### Step 1: Register an application

Register your app in the Microsoft Entra admin center using the steps in [Register an application](https://learn.microsoft.com/entra/external-id/customers/how-to-run-native-authentication-sample-android-app#register-an-application).

### Step 2: Enable public client and native authentication flows

Enable public client and native authentication flows for the registered application using the steps in [Enable public client and native authentication flows](https://learn.microsoft.com/entra/external-id/customers/how-to-run-native-authentication-sample-android-app#enable-public-client-and-native-authentication-flows).

### Step 3: Grant API permissions

Grant API permissions to the registered application by following the steps in [Grant API permissions](https://learn.microsoft.com/entra/external-id/customers/how-to-run-native-authentication-sample-android-app#grant-api-permissions).

### Step 4: Create user flow

Create a user flow by following the steps in [Create a user flow](https://learn.microsoft.com/entra/external-id/customers/how-to-run-native-authentication-sample-android-app#create-a-user-flow).

### Step 5: Associate the app with the user flow

Associate the application with the user flow by following the steps in [Associate the application with the user flow](https://learn.microsoft.com/entra/external-id/customers/how-to-run-native-authentication-sample-android-app#associate-the-app-with-the-user-flow).

### Step 6: Clone sample Android mobile application

Clone the sample Android mobile application by following the steps outlined in [Clone sample Android mobile application](https://learn.microsoft.com/entra/external-id/customers/how-to-run-native-authentication-sample-android-app#clone-sample-android-mobile-application).

### Step 7: Configure the sample Android mobile application

Configure the sample Android mobile application by following the steps in [Configure the sample Android mobile application](https://learn.microsoft.com/entra/external-id/customers/how-to-run-native-authentication-sample-android-app#configure-the-sample-android-mobile-application).

### Step 8: Run and test sample Android mobile application

Run and test the Android sample mobile application by following the steps in [Run and test sample Android mobile application](https://learn.microsoft.com/entra/external-id/customers/how-to-run-native-authentication-sample-android-app#run-and-test-sample-android-mobile-application).

### Step 9: Call a protected web API

Follow the steps in [Sign in users and call an API in a sample Android mobile app by using native authentication](https://learn.microsoft.com/entra/external-id/customers/sample-native-authentication-android-sample-app-call-web-api) to sign in users and call a protected API in the Android sample mobile app.

### Additional step: Add email one-time passcode MFA to your Android app (Private Preview)

Add email one-time passcode MFA to the sign in flow by following the steps in [Add email one-time passcode MFA to your Android app](https://github.com/microsoft/entra-previews/blob/PP5/docs/Native-Auth/Developer-guides/0-Android-Kotlin/Add-email-otp-mfa-sign-in.md).

## Key concepts

Open `app/src/main/res/raw/native_auth_sample_app_config.json` file and you find the following json configurations:

```kotlin
{
  "client_id": "Enter_the_Application_Id_Here",
  "authorities": [
    {
      "type": "CIAM",
      "authority_url": "https://Enter_the_Tenant_Subdomain_Here.ciamlogin.com/Enter_the_Tenant_Subdomain_Here.onmicrosoft.com/"
    }
  ],
  "challenge_types": ["oob", "password"],
  "logging": {
    "pii_enabled": false,
    "log_level": "INFO",
    "logcat_enabled": true
  }
}
```

The JSON configuration file has:

* _client_id_ - the value _Enter_the_Application_Id_Here_ is replaced with **Application (client) ID** of the app you register during the project setup. The **Application (client) ID** is unique identifier of your registered application.
* _Enter_the_Tenant_Subdomain_Here_ - the value _Enter_the_Tenant_Subdomain_Here_ is replaced with the Directory (tenant) subdomain. The tenant subdomain URL is used to construct the authentication endpoint for your app.

You use `app/src/main/res/raw/native_auth_sample_app_config.json` file to set configuration options when you initialize the client app in the Microsoft Authentication Library (MSAL).

To create SDK instance, use the following code:

```kotlin
private lateinit var authClient: INativeAuthPublicClientApplication 
 
override fun onCreate(savedInstanceState: Bundle?) { 
    super.onCreate(savedInstanceState) 
    setContentView(R.layout.activity_main) 

    authClient = PublicClientApplication.createNativeAuthPublicClientApplication( 
        this, 
        R.raw.auth_config_native_auth 
    ) 
} 
```

In the `onCreate` method, create an MSAL instance so that we can perform authentication logic and interact with our tenant through native authentication APIs. The `createNativeAuthPublicClientApplication()` method returns an instance called `authClient`. The JSON configuration `app/src/main/res/raw/native_auth_sample_app_config.json` file is passed as parameter. For more information about SDK instance, see [Tutorial: Prepare your Android app for native authentication](https://learn.microsoft.com/en-us/entra/external-id/customers/tutorial-native-authentication-prepare-android-app#create-sdk-instance)

## Reporting problems

* Search the [GitHub issues](https://github.com/Azure-Samples/ms-identity-ciam-native-auth-android-sample/issues) in the repository - your problem might already have been reported or have an answer.
* Nothing similar? [Open an issue](https://github.com/Azure-Samples/ms-identity-ciam-native-auth-android-sample/issues/new) that clearly explains the problem you're having running the sample app.

## Contributing

This project has adopted the [Microsoft Open Source Code of Conduct](https://opensource.microsoft.com/codeofconduct/). For more information, see the [Code of Conduct FAQ](https://opensource.microsoft.com/codeofconduct/faq/) or contact [opencode@microsoft.com](mailto:opencode@microsoft.com) with any additional questions or comments.
