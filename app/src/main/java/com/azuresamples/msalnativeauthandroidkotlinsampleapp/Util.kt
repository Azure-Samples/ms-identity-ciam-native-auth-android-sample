package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.content.Context
import org.json.JSONObject

fun Context.readSiteFromRawJsonFile(resourceId: Int): String? {
    return try {
        val jsonString = resources.openRawResource(resourceId).use {
            it.reader().readText()
        }

        val jsonObject = JSONObject(jsonString)
        if (jsonObject.has("site")) {
            return jsonObject.getString("site")
        } else {
            null // "site" key not found
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun Context.readScopesFromRawJsonFile(resourceId: Int): List<String>? {
    return try {
        val jsonString = resources.openRawResource(resourceId).use {
            it.reader().readText()
        }

        val jsonObject = JSONObject(jsonString)
        if (jsonObject.has("scopes")) {
            val scopesArray = jsonObject.getJSONArray("scopes")
            return List(scopesArray.length()) { index ->
                scopesArray.getString(index)
            }
        } else {
            null // "scopes" key not found
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}