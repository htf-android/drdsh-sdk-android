package com.htf.drdshsdklibrary.Utills

import android.content.Context

class URLManager(context: Context) {
    private val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)

    fun getBaseUrl(): String {
        return prefs.getString("custom_url", AppConfig.DEFAULT_URL) ?: AppConfig.DEFAULT_URL
    }

    fun setBaseUrl(newUrl: String) {
        prefs.edit().putString("custom_url", newUrl).apply()
    }
}