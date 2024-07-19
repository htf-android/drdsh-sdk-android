# Android-DRDSH_sdk
## Android-DRDSH_SDK provides the following features:-


### Real-time chat feature.
### User can share any kind of file inside the chat.
### User can download and preview all shared file inside the chat.
### If there is no agent online then user can drop a message.

## Setup
### Add it in your root build.gradle at the end of repositories
```
allprojects {
	repositories {
		...
		maven { url 'https://jitpack.io' }
	}
}
```

###  Add the dependency
```
dependencies {
        implementation 'com.github.cto-htfsa:drdsh-sdk-android:v1.0.4'
}
```

### Add Internet permission in you project.
### Just Call this method to start chat.

```
 UserDetailActivity.open(
            currActivity = this,
            appSid = "Your AppSID",
            locale = "en",
            deviceID = deviceId,
            domain = "Your Domain URL"
        )
        
```
       
### Here *currentActivity* is context of the activity and APPSID you will get it from *https://www.drdsh.live/company/api-key.* 
### and *domain* is your app base url.


