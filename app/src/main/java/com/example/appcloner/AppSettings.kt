package com.example.appcloner

import android.content.Context
import android.content.SharedPreferences

class AppSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("AppClonerPrefs", Context.MODE_PRIVATE)

    var installImmediately: Boolean
        get() = prefs.getBoolean("installImmediately", false)
        set(value) = prefs.edit().putBoolean("installImmediately", value).apply()

    var customOutputFolderUri: String?
        get() = prefs.getString("customOutputFolderUri", null)
        set(value) = prefs.edit().putString("customOutputFolderUri", value).apply()
}