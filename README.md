# Sign in users and call a protected web API in Android (Kotlin) using native authentication

* [Overview](#overview)
* [Contents](#contents)
* [Prerequisites](#prerequisites)
* [Project setup](#project-setup)
* [Key concepts](#key-concepts)
* [Reporting problems](#reporting-problems)
* [Contributing](#contributing)

## Overview

This sample Android application demonstrates how to integrate 3P Bot protection for the signup flow using CIAM Native API end-points. This example is created using Human Security as the 3P Bot protection provider.

This example is for demonstration purpose only. Appropriate setup with WAF to intercept calls during signup flow and service with Human is required for this flow to work. Please contact with Human to acquire required SDK's required for the protections.

## Contents

| File/folder | Description |
|-------------|-------------|
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

### Step 6: Configure Human (3P Bot protection provider) service profile

Configure the sample Android mobile application by following the steps in [Human documentation](https://docs.humansecurity.com/applications/mobile-sdk-intro).

### Step 7: Configure WAF profile

Configure WAF to intercept the signin requests following the steps in this [Tutorial](https://review.learn.microsoft.com/en-us/entra/external-id/customers/tutorial-third-party-bot-protection-native-api-signup?branch=pr-en-us-11558).

## Key concepts

This is a sample code to demonstrate an approach to use 3P Bot protection provider (Human in this example) for the External ID Authentication flow using Native API endpoints. This sample code is not to be used in any Production environment as it is and appropriate API permission with the 3P is required along with a WAF setup is required to achieve this protection. For more information, please refer to [Tutorial](https://review.learn.microsoft.com/en-us/entra/external-id/customers/tutorial-third-party-bot-protection-native-api-signup?branch=pr-en-us-11558).

## Reporting problems

* Search the [GitHub issues](https://github.com/Azure-Samples/ms-identity-ciam-native-auth-android-sample/issues) in the repository - your problem might already have been reported or have an answer.
* Nothing similar? [Open an issue](https://github.com/Azure-Samples/ms-identity-ciam-native-auth-android-sample/issues/new) that clearly explains the problem you're having running the sample app.

## Contributing

This project has adopted the [Microsoft Open Source Code of Conduct](https://opensource.microsoft.com/codeofconduct/). For more information, see the [Code of Conduct FAQ](https://opensource.microsoft.com/codeofconduct/faq/) or contact [opencode@microsoft.com](mailto:opencode@microsoft.com) with any additional questions or comments.
