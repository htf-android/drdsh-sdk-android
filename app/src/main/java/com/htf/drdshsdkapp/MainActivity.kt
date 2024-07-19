package com.htf.drdshsdkapp

import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.htf.drdshsdklibrary.Activities.UserDetailActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val deviceId = Settings.Secure.getString(this.contentResolver, Settings.Secure.ANDROID_ID)

        UserDetailActivity.open(
            currActivity = this,
            appSid = "612e0581dc220d28580b03be.d9443487762e8844672be133b3aa574cbbd04e58",
            locale = "en",
            deviceID = deviceId,
            domain = "drdsh.live"
        )
       /* UserDetailActivity.open(
            currActivity = this,
            appSid = "YOUR_APP_S_ID",
            locale = "en",
            deviceID = deviceId,
            domain = "YOUR_DOMAIN_NAME"
        )*/
    }
}
