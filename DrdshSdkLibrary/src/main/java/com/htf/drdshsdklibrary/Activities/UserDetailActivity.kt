package com.htf.drdshsdklibrary.Activities

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.PowerManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.github.nkzawa.emitter.Emitter
import com.github.nkzawa.socketio.client.Ack
import com.github.nkzawa.socketio.client.IO
import com.github.nkzawa.socketio.client.Socket
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.htf.drdshsdklibrary.Models.AgentAcceptChat
import com.htf.drdshsdklibrary.Models.JoinChatRoom
import com.htf.drdshsdklibrary.Models.VerifyIdentity
import com.htf.drdshsdklibrary.NetUtils.APIClient
import com.htf.drdshsdklibrary.NetUtils.RetrofitResponse
import com.htf.drdshsdklibrary.R
import com.htf.drdshsdklibrary.Utills.AppUtils
import com.htf.drdshsdklibrary.Utills.Constants
import com.htf.drdshsdklibrary.Utills.Constants.ACTION_AGENT_ACCEPT_CHAT_REQUEST
import com.htf.drdshsdklibrary.Utills.Constants.IS_AGENT_OFFLINE
import com.htf.drdshsdklibrary.Utills.Constants.IS_AGENT_ONLINE
import com.htf.drdshsdklibrary.Utills.LocalizeActivity
import com.htf.drdshsdklibrary.Utills.RegExp
import com.htf.learnchinese.utils.AppPreferences
import com.htf.learnchinese.utils.AppSession
import com.htf.learnchinese.utils.AppSession.Companion.mSocket
import kotlinx.android.synthetic.main.activity_user_detail.*
import kotlinx.android.synthetic.main.dialog_for_offline_msg.view.*
import kotlinx.android.synthetic.main.toolbar.*
import org.json.JSONException
import org.json.JSONObject


class UserDetailActivity : LocalizeActivity(), View.OnClickListener {

    private val currActivity: Activity = this
    private var appSid = ""
    private var locale = ""
    private var domain = ""
    private var displayMetrics = DisplayMetrics()
    private var deviceId = ""
    var mWakeLock: PowerManager.WakeLock? = null

    private var strName = ""
    private var strEmail = ""
    private var strMobile = ""
    private var strMsg = ""

    private var name: String? = null
    private var email: String? = null
    private var mobile: String? = null
    private var vistorId: String? = null
    private var verifyIdentityResponse: VerifyIdentity? = null


    companion object {
        fun open(
            currActivity: Activity,
            appSid: String,
            locale: String,
            deviceID: String,
            domain: String
        ) {
            val intent = Intent(currActivity, UserDetailActivity::class.java)
            intent.putExtra("appSid", appSid)
            intent.putExtra("locale", locale)
            intent.putExtra("deviceId", deviceID)
            intent.putExtra("domain", domain)
            currActivity.startActivity(intent)
        }
    }

    @SuppressLint("HardwareIds", "InvalidWakeLockTag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_detail)
        setListener()
        getExtra()
        tvCloseChat.visibility = View.GONE
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        this.mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag")
        this.mWakeLock!!.acquire(10 * 60 * 1000L /*10 minutes*/)
        displayMetrics = AppUtils.getDisplayMatrix(currActivity)


    }

    private fun getExtra() {
        appSid = intent.getStringExtra("appSid")!!
        locale = intent.getStringExtra("locale")!!
        domain = intent.getStringExtra("domain")!!
        deviceId = intent.getStringExtra("deviceId")!!
        AppSession.appSid = appSid
        callVerifyIdentity()
    }

    private fun setListener() {
        iv_back_pressed.setOnClickListener(this)
        btnStartChat.setOnClickListener(this)
        iv_back_pressed.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnStartChat -> {
                if (checkOnlineValidation()) {
                    when (verifyIdentityResponse?.agentOnline) {
                        IS_AGENT_ONLINE -> {
                            val joinChatRoom =
                                AppPreferences.getInstance(currActivity).getJoinChatRoomDetails()
                            joinChatRoom?.name = strName
                            joinChatRoom?.email = strEmail
                            joinChatRoom?.mobile = strMobile
                            joinChatRoom?.message = strMsg
                            joinChatRoom?.run {
                                AppPreferences.getInstance(currActivity)
                                    .saveJoinChatRoomDetails(this)
                                startChat()
                            }

                        }
                        IS_AGENT_OFFLINE -> sendOfflineMessage(
                            null,
                            strName,
                            strEmail,
                            strMobile,
                            "",
                            strMsg
                        )

                    }

                }
            }
            R.id.iv_back_pressed -> {
                currActivity.finish()
            }
        }
    }

    private fun callVerifyIdentity() {
        val joinChatRoom = AppPreferences.getInstance(currActivity).getJoinChatRoomDetails()
        if (joinChatRoom != null) {
            name = joinChatRoom.name
            email = joinChatRoom.email
            mobile = joinChatRoom.mobile
        }
        val verifyIdentity = AppPreferences.getInstance(currActivity).getIdentityDetails()
        if (verifyIdentity != null) {
            vistorId = verifyIdentity.visitorID
        }

        val call = APIClient.getClient().validateIdentity(
            appSid, locale,
            displayMetrics.widthPixels.toString(),
            displayMetrics.heightPixels.toString(),
            deviceId,
            AppUtils.getIPAddress(true)!!,
            "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:73.0)",
            domain,
            name,
            email,
            mobile,
            "Contact-Us",
            "Contact-Us",
            "${displayMetrics.widthPixels}x${displayMetrics.heightPixels}",
            vistorId,
            AppUtils.getIPAddress(true),
            "${displayMetrics.widthPixels}x${displayMetrics.heightPixels}",
            AppUtils.getTimeZone().id,
            null,
            AppSession.deviceType

        )

        APIClient.getResponse(call, currActivity, null, true, object : RetrofitResponse {
            override fun onResponse(response: String?) {
                try {
                    if (response != null) {
                        ll_main.visibility = View.VISIBLE
                        val jsonObject = JSONObject(response)
                        val data = jsonObject.optJSONObject("data")
                        if (data != null) {
                            val type = object : TypeToken<VerifyIdentity>() {}.type
                            val verifyIdentity =
                                Gson().fromJson<VerifyIdentity>(data.toString(), type)
                            AppPreferences.getInstance(currActivity)
                                .saveIdentityDetails(verifyIdentity)
                            setData(verifyIdentity)
                            Log.d("verifyIdentity", verifyIdentity.toString())
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

        })
    }

    private fun socketConnecting(verifyIdentity: VerifyIdentity?) {

        try {
            if (mSocket == null) {
                val opts =
                    IO.Options()
                opts.forceNew = true
                opts.path = "/dc/socket-connect/io/socket.io"
                //opts.query="socket=connect&acid=${verifyIdentity?.accountID}&vid=${verifyIdentity?.visitorID}&device=${AppSession.deviceType}&locale=$locale"
                mSocket = IO.socket("https://www.drdsh.live", opts)
                mSocket!!.io().reconnection(false)
            }


            /* if(mSocket==null){
                 mSocket = IO.socket("https://www.drdsh.live/")
                 mSocket!!.io().reconnection(false)
             }*/


            if (mSocket != null) {
                if (!mSocket!!.connected()) {
                    mSocket!!.on(Socket.EVENT_CONNECT, onConnected)
                    mSocket!!.on("disconnect", onDisconnect)
                    mSocket!!.connect()
                } else {
                    joinVisitorsRoom()
                    mSocket!!.on("inviteVisitorListener", inviteVisitorListener)
                }
            } else {
                joinVisitorsRoom()
                mSocket!!.on("inviteVisitorListener", inviteVisitorListener)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }


    private var inviteVisitorListener: Emitter.Listener = Emitter.Listener { args ->
        try {
            val data = if (args.isNotEmpty()) args[0]?.toString() else null
            currActivity.runOnUiThread {
                if (data != null) {
                    val type = object : TypeToken<JoinChatRoom>() {}.type
                    val joinChatRoom = Gson().fromJson<JoinChatRoom>(data.toString(), type)
                    AppPreferences.getInstance(currActivity).saveJoinChatRoomDetails(joinChatRoom)
                    ChatActivity.open(currActivity)
                    currActivity.finish()
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun joinVisitorsRoom() {
        val verifyIdentity = AppPreferences.getInstance(currActivity).getIdentityDetails()
        if (verifyIdentity != null) {
            val userJson = JSONObject()
            userJson.put("dc_vid", verifyIdentity.visitorID)
            userJson.put("appSid", appSid)
            userJson.put("device", AppSession.deviceType)
            mSocket!!.emit("joinVisitorsRoom", userJson, Ack { args ->
                val data = if (args.isNotEmpty()) args[0]?.toString() else null
                if (data != null) {
                    runOnUiThread {
                        try {
                            Log.d("Socket Agent ID ", data)
                            val type = object : TypeToken<JoinChatRoom>() {}.type
                            val joinChatRoom = Gson().fromJson<JoinChatRoom>(data.toString(), type)
                            AppPreferences.getInstance(currActivity)
                                .saveJoinChatRoomDetails(joinChatRoom)
                        } catch (e: JSONException) {
                            e.printStackTrace()
                        }
                    }
                }
            })
        }
    }

    private fun startChat() {
        if (!mSocket!!.connected()) {
            mSocket!!.connect()
        }
        val verifyIdentity = AppPreferences.getInstance(currActivity).getIdentityDetails()
        if (verifyIdentity != null) {
            val userJson = JSONObject()
            userJson.put("_id", verifyIdentity.visitorID)
            userJson.put("name", strName)
            userJson.put("email", strEmail)
            userJson.put("mobile", strMobile)
            userJson.put("message", strMsg)
            userJson.put("appSid", appSid)
            userJson.put("device", AppSession.deviceType)
            mSocket!!.emit("inviteChat", userJson, Ack { args ->
                val data = if (args.isNotEmpty()) args[0]?.toString() else null
                runOnUiThread {
                    if (data != null) {
                        try {
                            Log.d("inviteChat", data)
                            etFullName.setText("")
                            etEmail.setText("")
                            etMobile.setText("")
                            etQuestion.setText("")
                            ChatActivity.open(currActivity)
                            currActivity.finish()
                        } catch (e: JSONException) {
                            e.printStackTrace()
                        }
                    }
                }
            })
        }

    }


    private fun setData(verifyIdentity: VerifyIdentity?) {
        verifyIdentityResponse = verifyIdentity
        socketConnecting(verifyIdentity)
        when (verifyIdentity?.visitorConnectedStatus) {
            Constants.CHAT_IN_NORMAL_MODE -> {
                when (verifyIdentity.agentOnline) {
                    IS_AGENT_ONLINE -> btnStartChat.text = getString(R.string.start_chat)
                    IS_AGENT_OFFLINE -> btnStartChat.text = getString(R.string.drop_a_message)
                }
            }
            Constants.CHAT_IN_WAITING_MODE -> {
                ChatActivity.open(currActivity)
                currActivity.finish()
            }

            Constants.CHAT_IN_CONNECTED_MODE -> {
                ChatActivity.open(currActivity)
                currActivity.finish()
            }
        }

        /* here is the option for email and number dynamic show
        * */

        if (verifyIdentity!!.embeddedChat!!.emailRequired == 0) {
            etEmail.visibility = View.GONE
        } else {
            etEmail.visibility = View.VISIBLE
        }

        if (verifyIdentity.embeddedChat!!.mobileRequired == 0) {
            etMobile.visibility = View.GONE
        } else {
            etMobile.visibility = View.VISIBLE
        }


        rlt_top_bar.setBackgroundColor(Color.parseColor(verifyIdentity.embeddedChat?.topBarBgColor))
        btnStartChat.backgroundTintList =
            ColorStateList.valueOf(Color.parseColor(verifyIdentity.embeddedChat?.buttonColor))

    }

    private fun checkOnlineValidation(): Boolean {
        var isValid = true
        strName = etFullName.text.toString().trim()
        strEmail = etEmail.text.toString().trim()
        strMobile = etMobile.text.toString().trim()
        strMsg = etQuestion.text.toString().trim()

        when {
            RegExp.chkEmpty(strName) -> {
                isValid = false
                AppUtils.showToast(currActivity, getString(R.string.name_required), true)
            }
            /*         RegExp.chkEmpty(strEmail)->{
                         isValid=false
                         AppUtils.showSnackBar(currActivity,tvError,getString(R.string.email_required))
                     }
                     !RegExp.isValidEmail(strEmail)->{
                         isValid=false
                         AppUtils.showSnackBar(currActivity,tvError,getString(R.string.email_invalid))
                     }
                     RegExp.chkEmpty(strMobile)->{
                         isValid=false
                         AppUtils.showSnackBar(currActivity,tvError,getString(R.string.mobile_required))
                     }
                     !RegExp.isValidPhone(strMobile)->{
                         isValid=false
                         AppUtils.showSnackBar(currActivity,tvError,getString(R.string.mobile_invalid))
                     }
                     */
            RegExp.chkEmpty(strMsg) -> {
                isValid = false
                AppUtils.showToast(currActivity, getString(R.string.message_required), true)
            }
            else -> {
                tvError.visibility = View.GONE
            }
        }
        return isValid
    }

    private var agentAcceptedChatRequest: Emitter.Listener = Emitter.Listener { args ->
        try {
            val data = if (args.isNotEmpty()) args[0]?.toString() else null
            currActivity.runOnUiThread {
                if (data != null) {
                    Log.e("agentAcceptedChat", data)
                    val type = object : TypeToken<AgentAcceptChat>() {}.type
                    val agentAcceptChat = Gson().fromJson<AgentAcceptChat>(data, type)
                    val intent = Intent(ACTION_AGENT_ACCEPT_CHAT_REQUEST)
                    intent.putExtra("agentAcceptedChat", agentAcceptChat)
                    LocalBroadcastManager.getInstance(currActivity).sendBroadcast(intent)
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private var onConnected: Emitter.Listener = Emitter.Listener { args ->
        joinVisitorsRoom()
        mSocket!!.on("inviteVisitorListener", inviteVisitorListener)
    }

    private var onDisconnect: Emitter.Listener = Emitter.Listener { args ->
        try {
            val data = if (args.isNotEmpty()) args[0]?.toString() else null
            runOnUiThread {
                if (data != null) {
                    Log.e("onDisconnect", data)
                    if (mSocket != null) {
                        if (!mSocket!!.connected()) {
                            mSocket!!.on(Socket.EVENT_CONNECT, onConnected)
                            mSocket!!.connect()
                            joinVisitorsRoom()
                        }
                    }


                }
            }

        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }

    }

    override fun onResume() {
        super.onResume()

        if (mSocket != null) {
            if (mSocket!!.connected()) {
                joinVisitorsRoom()
                mSocket!!.on("inviteVisitorListener", inviteVisitorListener)
            }
        }

    }


    private fun openDropOfflineMessageDialog(dataMessage: String?) {
        try {
            val builder = AlertDialog.Builder(currActivity)
            val dialogView =
                currActivity.layoutInflater.inflate(R.layout.dialog_for_offline_msg, null)
            builder.setView(dialogView)
            builder.setCancelable(true)
            val dialog = builder.show()
            var strName = ""
            var strEmail = ""
            var strMobile = ""
            var strSubject = ""
            var strMsg = ""

            dialogView.btnSendMessage.backgroundTintList =
                ColorStateList.valueOf(Color.parseColor(verifyIdentityResponse?.embeddedChat?.buttonColor))

            val myAnim = AnimationUtils.loadAnimation(currActivity, R.anim.slide_up)
            dialogView.startAnimation(myAnim)


            dialogView.tvWaitingMessage.text = dataMessage

            dialogView.btnSendMessage.setOnClickListener {
                strName = dialogView.etFullName.text.toString().trim()
                strEmail = dialogView.etEmail.text.toString().trim()
                strMobile = dialogView.etMobile.text.toString().trim()
                strSubject = dialogView.etSubject.text.toString().trim()
                strMsg = dialogView.etQuestion.text.toString().trim()

                when {
                    strName == "" -> {
                        AppUtils.showToast(
                            this.currActivity,
                            getString(R.string.name_required),
                            true
                        )

                    }
                    strEmail == "" -> {
                        AppUtils.showToast(
                            this.currActivity,
                            getString(R.string.email_id_requied),
                            true
                        )

                    }
                    strMobile == "" -> {
                        AppUtils.showToast(
                            this.currActivity,
                            getString(R.string.mobile_required),
                            true
                        )

                    }
                    strSubject == "" -> {
                        AppUtils.showToast(
                            this.currActivity,
                            getString(R.string.subject_required),
                            true
                        )

                    }
                    strMsg == "" -> {
                        AppUtils.showToast(
                            this.currActivity,
                            getString(R.string.message_required),
                            true
                        )

                    }
                    else -> {
                        sendOfflineMessage(dialog, strName, strEmail, strMobile, "", strMsg)
                    }
                }
            }

            val window = dialog.window
            window!!.setLayout(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            window.setGravity(Gravity.CENTER)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.show()

        } catch (e: Exception) {

        }

    }


    private fun sendOfflineMessage(
        dialog: AlertDialog? = null,
        strName: String,
        strEmail: String,
        strMobile: String,
        strSubject: String,
        strMsg: String
    ) {
        if (!mSocket!!.connected()) {
            mSocket!!.connect()
        }
        val verifyIdentity = AppPreferences.getInstance(currActivity).getIdentityDetails()
        if (verifyIdentity != null) {
            val userJson = JSONObject()
            userJson.put("_id", verifyIdentity.visitorID)
            userJson.put("name", strName)
            userJson.put("email", strEmail)
            userJson.put("mobile", strMobile)
            userJson.put("message", strMsg)
            userJson.put("subject", strSubject)
            userJson.put("appSid", AppSession.appSid)
            userJson.put("device", AppSession.deviceType)
            mSocket!!.emit("submitOfflineMessage", userJson, Ack { args ->
                val data = if (args.isNotEmpty()) args[0]?.toString() else null
                runOnUiThread {
                    if (data != null) {
                        try {
                            Log.d("inviteChat", data)
                            dialog?.dismiss()
                            currActivity.finish()
                        } catch (e: JSONException) {
                            e.printStackTrace()
                        }
                    }
                }
            })
        }

    }


}