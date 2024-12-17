package com.htf.drdshsdklibrary.Utills

import android.content.Context

object AppContextProvider {
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun getContext(): Context {
        return appContext ?: throw IllegalStateException("Context is not initialized")
    }
}
