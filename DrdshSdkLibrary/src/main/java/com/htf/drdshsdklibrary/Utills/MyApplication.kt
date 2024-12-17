package com.htf.drdshsdklibrary.Utills

import android.app.Application
import android.content.Context

class MyApplication : Application() {

    companion object {
        private lateinit var mAppContext: Context

        fun getAppContext(): Context {
            return mAppContext
        }
    }

    override fun onCreate() {
        super.onCreate()
        mAppContext = applicationContext
    }
}
