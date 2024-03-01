# A native authentication Android (Kotlin) sample app using MSAL to authenticate users and call a web API using Microsoft Entra External ID

* [Overview](#overview)
* [Contents](#contents)
* [Prerequisites](#prerequisites)
* [Project setup](#project-setup)
* [Key concepts](#key-concepts)
* [Reporting problems](#reporting-problems)
* [Contributing](#contributing)

## Overview

This sample iOS sample applications demonstrates how to sign-up, sign in, sign out, and password reset scenarios using Microsoft Entra External ID for customers.

## Contents

| File/folder | Description |
|-------------|-------------|
| `ms-identity-ciam-native-auth-android-sample`       | Sample source code. |
| `.gitignore` | Define what to ignore at commit time. |
| `CHANGELOG.md` | List of changes to the sample. |
| `CONTRIBUTING.md` | Guidelines for contributing to the sample. |
| `README.md` | This README file. |
| `LICENSE`   | The license for the sample. |

## Prerequisites

* <a href="https://developer.android.com/studio/archive" target="_blank">Android Studio Dolphin | 2021.3.1 Patch 1</a>.
* Microsoft Entra External ID for customers tenant. If you don't already have one, <a href="https://aka.ms/ciam-free-trial?wt.mc_id=ciamcustomertenantfreetrial_linkclick_content_cnl" target="_blank">sign up for a free trial</a>.

## Project setup

To enable your application to authenicate users with Microsoft Entra, Microsoft Entra ID for customers must be made aware of the application you create. The following steps show you how to:

1. [Register an application](https://review.learn.microsoft.com/en-us/entra/external-id/customers/how-to-run-native-authentication-sample-android-app?branch=pr-en-us-2024#register-an-application)
1. [Enable public client and native authentication flows](https://review.learn.microsoft.com/en-us/entra/external-id/customers/how-to-run-native-authentication-sample-android-app?branch=pr-en-us-2024#enable-public-client-and-native-authentication-flows)
1. [Grant API permissions](https://review.learn.microsoft.com/en-us/entra/external-id/customers/how-to-run-native-authentication-sample-android-app?branch=pr-en-us-2024#grant-api-permissions)
1. [Create a user flow](https://review.learn.microsoft.com/en-us/entra/external-id/customers/how-to-run-native-authentication-sample-android-app?branch=pr-en-us-2024#create-a-user-flow)
1. [Associate the application with the user flow](https://review.learn.microsoft.com/en-us/entra/external-id/customers/how-to-run-native-authentication-sample-android-app?branch=pr-en-us-2024#associate-the--app-with-the-user-flow)
1. [Clone sample Android mobile application](https://review.learn.microsoft.com/en-us/entra/external-id/customers/how-to-run-native-authentication-sample-android-app?branch=pr-en-us-2024#clone-sample-android-mobile-application)
1. [Configure the sample Android mobile application](https://review.learn.microsoft.com/en-us/entra/external-id/customers/how-to-run-native-authentication-sample-android-app?branch=pr-en-us-2024#configure-the-sample-android-mobile-application)
1. [Run and test sample Android mobile application](https://review.learn.microsoft.com/en-us/entra/external-id/customers/how-to-run-native-authentication-sample-android-app?branch=pr-en-us-2024#run-and-test-sample-android-mobile-application)

## Key concepts

Let's take a quick review of what's happenning in the app. Open `app/src/main/res/raw/native_auth_sample_app_config.json` file and you find the following lines of code:

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

* _client_id_ - the value _Enter_the_Application_Id_Here_ is be replaced with **Application (client) ID** of the app you register during the project setup. The **Application (client) ID** is unique identifier of your registered application.
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
    getAccountState() 
} 
```

To learn more, see [Tutorial: Prepare your Android app for native authentication](https://review.learn.microsoft.com/en-us/entra/external-id/customers/tutorial-native-authentication-prepare-android-app?branch=pr-en-us-2024#create-sdk-instance)

## Reporting problems

* Search the [GitHub issues](https://github.com/Azure-Samples/ms-identity-ciam-native-auth-android-sample/issues) in the repository - your problem might already have been reported or have an answer.
* Nothing similar? [Open an issue](https://github.com/Azure-Samples/ms-identity-ciam-native-auth-android-sample/issues/new) that clearly explains the problem you're having running the sample app.

## Contributing

If you'd like to contribute to this sample, see [CONTRIBUTING.MD](/CONTRIBUTING.md).

This project has adopted the [Microsoft Open Source Code of Conduct](https://opensource.microsoft.com/codeofconduct/). For more information, see the [Code of Conduct FAQ](https://opensource.microsoft.com/codeofconduct/faq/) or contact [opencode@microsoft.com](mailto:opencode@microsoft.com) with any additional questions or comments.
