package com.azuresamples.msalnativeauthandroidkotlinsampleapp.utils

import android.app.Activity
import android.content.Context

class AppUtil(context: Context, activity: Activity) {
    val navigation: NavigationUtil = NavigationUtil(activity)
    val errorHandler: ErrorHandlerUtil = ErrorHandlerUtil(context)
}