package com.htf.drdshsdklibrary.Activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.nkzawa.emitter.Emitter
import com.github.nkzawa.socketio.client.Ack
import com.github.nkzawa.socketio.client.IO
import com.github.nkzawa.socketio.client.Socket
import com.github.nkzawa.socketio.client.Socket.EVENT_CONNECT
import com.github.nkzawa.socketio.client.Socket.EVENT_DISCONNECT
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.htf.drdshsdklibrary.Adapter.ChatAdapter
import com.htf.drdshsdklibrary.BuildConfig
import com.htf.drdshsdklibrary.Models.*
import com.htf.drdshsdklibrary.R
import com.htf.drdshsdklibrary.Utills.AppUtils
import com.htf.drdshsdklibrary.Utills.AppUtils.getFileSize
import com.htf.drdshsdklibrary.Utills.AppUtils.getMimeType
import com.htf.drdshsdklibrary.Utills.AppUtils.getMimeTypeFromExtension
import com.htf.drdshsdklibrary.Utills.AppUtils.showToast
import com.htf.drdshsdklibrary.Utills.Constants
import com.htf.drdshsdklibrary.Utills.Constants.ACTION_AGENT_ACCEPT_CHAT_REQUEST
import com.htf.drdshsdklibrary.Utills.Constants.AGENT_IS_TYPING
import com.htf.drdshsdklibrary.Utills.Constants.AGENT_TYPING_START
import com.htf.drdshsdklibrary.Utills.FileCompression.IImageCompressTaskListener
import com.htf.drdshsdklibrary.Utills.FileCompression.ImageCompressTask
import com.htf.drdshsdklibrary.Utills.LocalizeActivity
import com.htf.drdshsdklibrary.Utills.RegExp
import com.htf.learnchinese.utils.AppPreferences
import com.htf.learnchinese.utils.AppSession
import com.htf.learnchinese.utils.AppSession.Companion.mSocket
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_chat.*
import kotlinx.android.synthetic.main.dialog_close_chat.view.*
import kotlinx.android.synthetic.main.dialog_email.view.*
import kotlinx.android.synthetic.main.dialog_for_offline_msg.view.*
import kotlinx.android.synthetic.main.row_incoming_msg.view.*
import kotlinx.android.synthetic.main.row_outgoing_msg.view.*
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class ChatActivity : LocalizeActivity(), View.OnClickListener {
    private var message = Message()
    private lateinit var mAdapter: ChatAdapter
    private var agentAcceptChat = AgentAcceptChat()
    private var currActivity: Activity = this
    private var verifyIdentity: VerifyIdentity? = null
    private var joinChatRoom: JoinChatRoom? = null
    private var arrRandomId = ArrayList<String>()
    private var arrMessage = ArrayList<Message>()

    private var unReadChatMsgId = ""

    //    private val REQUEST_CODE_PHOTO = 101
    var timer: CountDownTimer? = null
    private var file: File? = null
    private var imageCompressTask: ImageCompressTask? = null

    private var _REQUEST_CODE_DOCUMENT = 8444
    private var _UPLOAD_REQUEST_CODE = 0
    private val MAX_VIDEO_SIZE_IN_BYTES = 20000000L

    //create a single thread pool to our image compression class.
    private val mExecutorService: ExecutorService = Executors.newFixedThreadPool(1)


    companion object {
        fun open(currActivity: Activity) {
            val intent = Intent(currActivity, ChatActivity::class.java)
            currActivity.startActivity(intent)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        verifyIdentity = AppPreferences.getInstance(currActivity).getIdentityDetails()
        val window: Window = this.window
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.parseColor(verifyIdentity?.embeddedChat?.topBarBgColor)
        setContentView(R.layout.activity_chat)
        setListener()
        initRecycler()

        setEmbeddedChat()
        visitorLoadChatHistory()

        val joinChatRoom = AppPreferences.getInstance(currActivity).getJoinChatRoomDetails()
        if (joinChatRoom != null) {
            if (joinChatRoom.visitorMessageId == null) {
                llWaiting.visibility = View.VISIBLE
                visitorMaxWaitTime()
            } else {
                llWaiting.visibility = View.GONE
            }
        }


    }

    /* Set Embedded Chat Dynamic Data*/
    private fun setEmbeddedChat() {
        toolbar.setBackgroundColor(Color.parseColor(verifyIdentity?.embeddedChat?.topBarBgColor))
        btnDropMsg.backgroundTintList =
            ColorStateList.valueOf(Color.parseColor(verifyIdentity?.embeddedChat?.buttonColor))
        btnRestartChat.backgroundTintList =
            ColorStateList.valueOf(Color.parseColor(verifyIdentity?.embeddedChat?.buttonColor))
        Picasso.get().load(
            Constants.ATTACHMENT_MESSAGE_URL +
                    verifyIdentity?.embeddedChat?.messengerBodyImage
        ).into(ivDrawable)
    }


    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.iv_back -> finish()
            R.id.ivDislike -> if (!isFinishing) updateVisitorRating("bad")
            R.id.ivLike -> if (!isFinishing) updateVisitorRating("good")
            R.id.ivMail -> if (!isFinishing) openMailDialog()
            R.id.btnRestartChat -> {
                llWaiting.visibility = View.GONE
//                arrMessage.clear()
                restartChat()
            }
            R.id.btnDropMsg -> {
//                arrMessage.clear()
                invitationMaxWaitTimeExceeded()
            }

            R.id.tvCloseChat -> if (!isFinishing) openCloseChatDialog(
                Constants.ALERT_TYPE_CLOSE_CHAT,
                currActivity
            )
            R.id.ivSend -> {
                val message = etMessage.text.toString().trim()
                if (message.isNotBlank())
                    sendVisitorMessage(message, Constants.NORMAL_MESSAGE, null, null, null, file)
            }
            R.id.ivAttachment -> {
                if (checkCameraPermission(currActivity, 100, null)) {
                    AppUtils.documentPickerIntent(
                        currActivity,
                        _REQUEST_CODE_DOCUMENT
                    )
                }
            }
        }
    }

    private fun checkCameraPermission(
        activity: Activity,
        permissionCode: Int,
        fragment: Fragment?
    ): Boolean {
        val currentAPIVersion = Build.VERSION.SDK_INT
        return if (currentAPIVersion >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    currActivity,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                if (fragment != null)
                    fragment.requestPermissions(
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ), permissionCode
                    )
                else
                    ActivityCompat.requestPermissions(
                        currActivity,
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ),
                        permissionCode
                    )
                false
            } else {
                true
            }
        } else {
            true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            100 -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    AppUtils.documentPickerIntent(currActivity, _REQUEST_CODE_DOCUMENT)

                    /*   FilePickerBuilder.instance.setMaxCount(1).setActivityTheme(R.style.AppTheme1)
                           .setActivityTitle(getString(R.string.select_photo))
                           .pickPhoto(this, REQUEST_CODE_PHOTO)*/
                } else {
                    if (checkCameraPermission(currActivity, 100, null))

                        AppUtils.documentPickerIntent(currActivity, _REQUEST_CODE_DOCUMENT)

                    /*             FilePickerBuilder.instance.setMaxCount(1)
                                         .setActivityTheme(R.style.AppTheme1)
                                         .setActivityTitle(getString(R.string.select_photo))
                                         .pickPhoto(this, REQUEST_CODE_PHOTO)*/
                }
            }

        }
    }


    private fun socketConnecting() {
        try {
            if (mSocket == null) {
                mSocket = IO.socket("https://www.drdsh.live/")
                mSocket?.io()?.reconnection(false)
            }

            if (mSocket != null) {
                if (!mSocket!!.connected()) {
                    mSocket?.on(EVENT_CONNECT, onConnected)
                    mSocket?.on(EVENT_DISCONNECT, onDisconnect)
                    mSocket?.connect()
                }
            }

            mSocket?.on("totalOnlineAgents", totalOnlineAgents)
            mSocket?.on("agentSendNewMessage", agentSendNewMessage)
            mSocket?.on("agentTypingListener", agentTypingListener)
            mSocket?.on("agentChatSessionTerminated", agentChatSessionTerminated)
            mSocket?.on("agentAcceptedChatRequest", agentAcceptedChatRequest)
            mSocket?.on("ipBlocked", agentIpBlockedRequest)
            mSocket?.on("isDeliveredListener", isDeliveredListener)
            mSocket?.on("isReadListener", isReadListener)


        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private var onConnected: Emitter.Listener = Emitter.Listener { args ->


    }

    private var onDisconnect: Emitter.Listener = Emitter.Listener { args ->
        try {
            val data = if (args.isNotEmpty()) args[0]?.toString() else null
            runOnUiThread {
                if (data != null) {
                    Log.e("onDisconnect", data)
                    if (mSocket != null) {
                        if (!mSocket!!.connected()) {
                            mSocket?.on(Socket.EVENT_CONNECT, onConnected)
                            mSocket?.connect()
                        }
                    }


                }
            }

        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }

    }

    private fun getData() {
        verifyIdentity = AppPreferences.getInstance(currActivity).getIdentityDetails()
        joinChatRoom = AppPreferences.getInstance(currActivity).getJoinChatRoomDetails()
        when (verifyIdentity?.visitorConnectedStatus) {
            Constants.CHAT_IN_NORMAL_MODE -> {
                // showWaitingMsg(true)

            }

            Constants.CHAT_IN_WAITING_MODE -> {

            }

            Constants.CHAT_IN_CONNECTED_MODE -> {
                visitorJoinAgentRoom()
                llAgent.visibility = View.VISIBLE
                tvAgentName.text = joinChatRoom?.agentName.toString()

            }

        }

    }

    private fun setListener() {
        ivDislike.setOnClickListener(this)
        iv_back.setOnClickListener(this)
        ivLike.setOnClickListener(this)
        ivMail.setOnClickListener(this)
        btnDropMsg.setOnClickListener(this)
        btnRestartChat.setOnClickListener(this)
        tvCloseChat.setOnClickListener(this)
        ivSend.setOnClickListener(this)
        ivAttachment.setOnClickListener(this)
        etMessage.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {

            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val joinChatRoom = AppPreferences.getInstance(currActivity).getJoinChatRoomDetails()
                when (count) {
                    0 -> {
                        val msg = getString(R.string.visto_stop_typing)
                        val message = msg.replace("[X]", joinChatRoom?.name!!)
                        visitorTyping(2, message, true)
                    }
                    1 -> {
                        val msg = getString(R.string.visitor_start_typing)
                        val message = msg.replace("[X]", joinChatRoom?.name!!)
                        visitorTyping(1, message, false)
                    }
                    else -> {
                        val msg = getString(R.string.visitor_is_typing)
                        val message = msg.replace("[X]", joinChatRoom?.name!!)
                        visitorTyping(2, message, false)
                    }
                }


            }

        })

    }

    private fun initRecycler() {
        val mLayout = LinearLayoutManager(currActivity)
        mAdapter = ChatAdapter(currActivity, arrMessage)
        recycler.layoutManager = mLayout
        recycler.adapter = mAdapter
        etMessage.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                if (arrMessage.size > 0)
                    recycler.scrollToPosition(arrMessage.size - 1)
            }
        }
        recycler.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (bottom < oldBottom) {
                if (arrMessage.size > 0) {
                    recycler.smoothScrollToPosition(arrMessage.size - 1)
                }
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
                ColorStateList.valueOf(Color.parseColor(verifyIdentity?.embeddedChat?.buttonColor))

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
                        showToast(
                            this.currActivity,
                            getString(R.string.name_required),
                            true
                        )

                    }
                    strEmail == "" -> {
                        showToast(
                            this.currActivity,
                            getString(R.string.email_id_requied),
                            true
                        )

                    }
                    strMobile == "" -> {
                        showToast(
                            this.currActivity,
                            getString(R.string.mobile_required),
                            true
                        )

                    }
                    strSubject == "" -> {
                        showToast(
                            this.currActivity,
                            getString(R.string.subject_required),
                            true
                        )

                    }
                    strMsg == "" -> {
                        showToast(
                            this.currActivity,
                            getString(R.string.message_required),
                            true
                        )

                    }
                    else -> {
                        sendOfflineMessage(dialog, strName, strEmail, strMobile, strSubject, strMsg)
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


/*    private fun openRateDialog(type: Int) {
        try {
            val builder = AlertDialog.Builder(currActivity)
            val dialogView = currActivity.layoutInflater.inflate(R.layout.dialog_rate, null)
            builder.setView(dialogView)
            builder.setCancelable(true)
            val dialog = builder.show()

            dialogView.tvRate.backgroundTintList =
                ColorStateList.valueOf(Color.parseColor(verifyIdentity?.embeddedChat?.buttonColor))

            var feedback = ""
            when (type) {
                TYPE_DISLIKE -> {
                    dialogView.ivLikeDislike.setImageResource(R.drawable.dislike)
                    feedback = "Bad"
                }
                TYPE_LIKE -> {
                    dialogView.ivLikeDislike.setImageResource(R.drawable.like)
                    feedback = "Good"
                }
            }
            dialogView.tvRate.setOnClickListener {
            }


            val window = dialog.window
            window?.setLayout(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            window?.setGravity(Gravity.CENTER)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.show()

        } catch (e: Exception) {
            e.printStackTrace()
        }

    }*/

    private fun openCloseChatDialog(type: Int, currActivity: Activity) {
        try {
            val builder = AlertDialog.Builder(currActivity)
            val dialogView = currActivity.layoutInflater.inflate(R.layout.dialog_close_chat, null)
            builder.setView(dialogView)
            builder.setCancelable(true)
            val dialog = builder.show()

            var feedbackStatus = "Good"
            var feedback = ""

            dialogView.btnCloseChat.backgroundTintList =
                ColorStateList.valueOf(Color.parseColor(verifyIdentity?.embeddedChat?.buttonColor))

            dialogView.ivThumbUp.setOnClickListener {
                dialogView.ivThumbUp.setImageResource(R.drawable.thumbup)
                dialogView.ivThumbDown.setImageResource(R.drawable.dislike)
                feedbackStatus = "Good"
            }

            dialogView.ivThumbDown.setOnClickListener {
                dialogView.ivThumbUp.setImageResource(R.drawable.like)
                dialogView.ivThumbDown.setImageResource(R.drawable.thums_down)
                feedbackStatus = "Bad"
            }
            dialogView.btnCloseChat.setOnClickListener {
                feedback = dialogView.etFeedback.text.toString().trim()
                when (type) {
                    Constants.ALERT_TYPE_CLOSE_CHAT -> {
                        if (feedback != "") {
                            visitorEndChatSession(feedbackStatus, feedback, dialog)
                        } else {
                            showToast(
                                this.currActivity,
                                getString(R.string.feedback_requied),
                                true
                            )
                        }
                    }
                    Constants.ALERT_TYPE_CHAT_RATING -> {
                        updateVisitorRating(feedbackStatus, dialog)
                    }
                }
            }


            val window = dialog.window
            window!!.setLayout(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            window.setGravity(Gravity.BOTTOM)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.show()
        } catch (e: java.lang.Exception) {

        }


    }

    private fun openMailDialog() {
        try {
            val builder = AlertDialog.Builder(currActivity)
            val dialogView = currActivity.layoutInflater.inflate(R.layout.dialog_email, null)
            builder.setView(dialogView)
            builder.setCancelable(true)
            val dialog = builder.show()
            var emailId = ""

            dialogView.tvEmailSend.backgroundTintList =
                ColorStateList.valueOf(Color.parseColor(verifyIdentity?.embeddedChat?.buttonColor))

            dialogView.tvEmailSend.setOnClickListener {
                emailId = dialogView.etEmailId.text.toString().trim()
                if (emailId.isNotEmpty()){
                    if (RegExp.isValidEmail(emailId)){
                        if (dialog.isShowing){
                            dialog.dismiss()
                            emailChatTranscript(emailId)
                        }
                    }
                    else
                        showToast(this.currActivity, getString(R.string.email_invalid), true)
                }
                else
                    showToast(this.currActivity, getString(R.string.please_input_your_email_address), true)

            }

            dialogView.tvCloseEmail.setOnClickListener {
                dialog.dismiss()
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
            e.printStackTrace()

        }


    }

    private fun showWaitingMsg(isFirstTime: Boolean) {
        try {
            if (isFirstTime) {
                val message1 = Message()
                message1.messageType = Constants.MESSAGE_TYPE_SENT
                message1.message = joinChatRoom?.message!!
                message1.isSystem = false
                message1.createdAt = AppUtils.getCurrentTime()
                arrMessage.add(message1)
            }
            llBottomWaiting.visibility = View.GONE
            val message2 = Message()
            message2.message = verifyIdentity?.embeddedChat?.onHoldMsg!!
            message2.messageType = Constants.MESSAGE_TYPE_INFO
            message2.isSystem = true
            message2.messageInfoTypeShowLoading = true
            arrMessage.add(message2)
            if (arrMessage.isNotEmpty()) {
                mAdapter.notifyItemInserted(arrMessage.size - 1)
                recycler.smoothScrollToPosition(arrMessage.size - 1)
            } else
                mAdapter.notifyDataSetChanged()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun showResendOption(holder: ChatAdapter.InfoViewHolder, position: Int) {
        val time = verifyIdentity?.embeddedChat?.maxWaitTime?.toLong()!! * 1000
        if (timer == null) {
            timer = object : CountDownTimer(time, 1000) {
                override fun onFinish() {
                    llBottomWaiting.visibility = View.VISIBLE
                    btnDropMsg.visibility = View.VISIBLE
                    val message = arrMessage[position]
                    message.messageInfoTypeShowLoading = false
                    message.message = getString(R.string.no_agent_available)
                    arrMessage[position] = message
                    mAdapter.notifyItemChanged(position)
                    timer = null
                }

                override fun onTick(millisUntilFinished: Long) {
                    Log.e("SECOND:-", "${millisUntilFinished * 1000}")
                }

            }.start()
        }

    }

    private fun visitorMaxWaitTime() {
        val time = verifyIdentity?.embeddedChat?.maxWaitTime?.toLong()!! * 1000
        if (timer == null) {
            timer = object : CountDownTimer(time, 1000) {
                override fun onFinish() {
                    llBottomWaiting.visibility = View.VISIBLE
                    llWaiting.visibility = View.GONE
                    progressView.visibility = View.GONE
                    btnDropMsg.visibility = View.VISIBLE
                    tvWaitingMsg.text = getString(R.string.no_agent_available)
                    timer = null
                }

                override fun onTick(millisUntilFinished: Long) {
                    Log.e("SECOND:-", "${millisUntilFinished * 1000}")
                }

            }.start()
        }

    }

    private fun restartChat() {
        if (!mSocket!!.connected()) {
            mSocket!!.connect()
        }
        val verifyIdentity = AppPreferences.getInstance(currActivity).getIdentityDetails()
        val joinChatRoom = AppPreferences.getInstance(currActivity).getJoinChatRoomDetails()
        if (verifyIdentity != null && joinChatRoom != null) {
            val userJson = JSONObject()
            userJson.put("_id", verifyIdentity.visitorID)
            userJson.put("name", joinChatRoom.name)
            userJson.put("email", joinChatRoom.email)
            userJson.put("mobile", joinChatRoom.mobile)
            userJson.put("message", joinChatRoom.message)
            userJson.put("appSid", AppSession.appSid)
            userJson.put("device", AppSession.deviceType)
            val dialog = AppUtils.showProgress(currActivity)
            mSocket!!.emit("inviteChat", userJson, Ack { args ->
                val data = if (args.isNotEmpty()) args[0]?.toString() else null
                if (data != null) {
                    try {
                        Log.d("inviteChat", data)
                        dialog.dismiss()
                        runOnUiThread {
                            showWaitingMsg(false)
                        }
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
            })
        }
    }

    private fun invitationMaxWaitTimeExceeded() {
        if (!mSocket!!.connected()) {
            mSocket!!.connect()
        }
        val verifyIdentity = AppPreferences.getInstance(currActivity).getIdentityDetails()
        val joinChatRoom = AppPreferences.getInstance(currActivity).getJoinChatRoomDetails()
        if (verifyIdentity != null && joinChatRoom != null) {
            val userJson = JSONObject()

            userJson.put("vid", verifyIdentity.visitorID)
            userJson.put("form", verifyIdentity.embeddedChat!!.displayForm)
            userJson.put("appSid", AppSession.appSid)
            userJson.put("device", AppSession.deviceType)
            mSocket!!.emit("invitationMaxWaitTimeExceeded", userJson, Ack { args ->
                val data = if (args.isNotEmpty()) args.first()?.toString() else null
                if (data != null) {
                    try {
                        runOnUiThread {
                            Log.d("updateVisitorRating", data)
                            if (!isFinishing) {
                                openDropOfflineMessageDialog(data)
                            }
                        }
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                } else {
                    try {
                        runOnUiThread {
                            if (!isFinishing) {
                                openDropOfflineMessageDialog(data)
                            }
                        }
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
            })
        }

    }

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("SetTextI18n")
        override fun onReceive(context: Context, intent: Intent) {
            Toast.makeText(context, "action:${intent.action}", Toast.LENGTH_SHORT).show()
            when (intent.action) {
                ACTION_AGENT_ACCEPT_CHAT_REQUEST -> {
                    if (intent.hasExtra("agentAcceptedChat")) {
                        val agentAcceptChat =
                            intent.getSerializableExtra("agentAcceptedChat") as AgentAcceptChat
                        setAgentAcceptChatData(agentAcceptChat)
                    }
                }
            }
        }
    }

    private fun setAgentAcceptChatData(agentAcceptChat: AgentAcceptChat) {
        val joinChatRoom = AppPreferences.getInstance(currActivity).getJoinChatRoomDetails()
        joinChatRoom?.agentId = agentAcceptChat.agentId
        joinChatRoom?.agentImage = agentAcceptChat.agentImage
        joinChatRoom?.agentName = agentAcceptChat.agentName
        joinChatRoom?.visitorMessageId = agentAcceptChat.vd!!.visitorMessageId
        AppPreferences.getInstance(currActivity).saveJoinChatRoomDetails(joinChatRoom!!)
        llBottomWaiting.visibility = View.GONE
        llAgent.visibility = View.VISIBLE
        tvAgentName.text = agentAcceptChat.agentName
        if (timer != null) {
            timer?.cancel()
            timer = null
        }

        val msg = arrMessage.filter { it.messageType == Constants.MESSAGE_TYPE_INFO }
            .filter { it.messageInfoTypeShowLoading }
        if (msg.isNotEmpty()) {
            for (m in msg) {
                val position = arrMessage.indexOf(m)
                arrMessage.removeAt(position)
                mAdapter.notifyItemRemoved(position)
            }
        }

        val msg1 = arrMessage.filter { it.messageType == Constants.MESSAGE_TYPE_INFO }
            .filter { it.message == getString(R.string.no_agent_available) }
        if (msg1.isNotEmpty()) {
            for (m in msg1) {
                val position = arrMessage.indexOf(m)
                arrMessage.removeAt(position)
                mAdapter.notifyItemRemoved(position)

            }
        }

        visitorLoadChatHistory()

    }

    private var agentSendNewMessage: Emitter.Listener = Emitter.Listener { args ->

        try {
            val data = if (args.isNotEmpty()) args[0]?.toString() else null
            currActivity.runOnUiThread {
                if (data != null) {
                    Log.e("agentSendNewMessage", data)
                    val type = object : TypeToken<Message>() {}.type
                    message = Gson().fromJson(data, type)
                    unReadChatMsgId = message.id!!
                    if (!arrMessage.filter { it.id == message.id }.isNotEmpty()) {
                        arrMessage.add(message)
                        if (arrMessage.isNotEmpty()) {
                            mAdapter.notifyItemInserted(arrMessage.size - 1)
                            recycler.scrollToPosition(arrMessage.size - 1)
                        } else
                            mAdapter.notifyDataSetChanged()

                        llTyping.visibility = View.INVISIBLE

                    }

                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var agentTypingListener: Emitter.Listener = Emitter.Listener { args ->
        try {
            val data = if (args.isNotEmpty()) args[0]?.toString() else null
            currActivity.runOnUiThread {
                if (data != null) {
                    Log.e("agentTypingListener", data)
                    val type = object : TypeToken<AgentTyping>() {}.type
                    val agentTyping = Gson().fromJson<AgentTyping>(data, type)
                    if (agentTyping.vid == verifyIdentity?.visitorID) {
                        Picasso.get().load(Constants.AGENT_IMAGE_URL + joinChatRoom?.agentImage).placeholder(R.drawable.user).into(ivAgentTyping)
                        tvAgentTyping.text = agentTyping.message
                        when (agentTyping.ts) {
                            AGENT_TYPING_START -> {
                                llTyping.visibility = View.VISIBLE
                            }
                            AGENT_IS_TYPING -> {
                                if (agentTyping?.stop!!) {
                                    llTyping.visibility = View.INVISIBLE
                                } else {
                                    llTyping.visibility = View.VISIBLE
                                }
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var agentChatSessionTerminated: Emitter.Listener = Emitter.Listener { args ->
        try {
            val data = if (args.isNotEmpty()) args[0]?.toString() else null
            currActivity.runOnUiThread {
                if (data != null) {
                    Log.e("agentChatTerminated", data)
                    val type = object : TypeToken<AgentChatTerminate>() {}.type
                    val agentChatTerminate = Gson().fromJson<AgentChatTerminate>(data, type)
                    val message = Message()
                    message.apply {
                        message.message = agentChatTerminate.message
                        message.messageType = Constants.MESSAGE_TYPE_INFO
                        message.isSystem = true
                    }
                    arrMessage.add(message)
                    if (arrMessage.isNotEmpty()) {
                        mAdapter.notifyItemInserted(arrMessage.size - 1)
                        recycler.smoothScrollToPosition(arrMessage.size - 1)
                    }
                    mAdapter.notifyDataSetChanged()

                    llBottomWaiting.visibility = View.VISIBLE
                    btnDropMsg.visibility = View.VISIBLE
                    openCloseChatDialog(Constants.ALERT_TYPE_CHAT_RATING, currActivity)
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var agentAcceptedChatRequest: Emitter.Listener = Emitter.Listener { args ->
        try {
            val data = if (args.isNotEmpty()) args[0]?.toString() else null
            currActivity.runOnUiThread {
                if (data != null) {
                    Log.e("agentAcceptedChat", data)
                    llWaiting.visibility = View.GONE
                    val type = object : TypeToken<AgentAcceptChat>() {}.type
                    agentAcceptChat = Gson().fromJson<AgentAcceptChat>(data, type)
                    setAgentAcceptChatData(agentAcceptChat)

                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var agentIpBlockedRequest: Emitter.Listener = Emitter.Listener { args ->
        try {
            val data = if (args.isNotEmpty()) args[0]?.toString() else null
            currActivity.runOnUiThread {
                if (data != null) {
                    Log.e("ipBlocked", data)
                    agentChatSessionTerminated
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var totalOnlineAgents: Emitter.Listener = Emitter.Listener { args ->
        try {
            val data = if (args.isNotEmpty()) args[0]?.toString() else null
            currActivity.runOnUiThread {
                if (data != null) {
                    Log.e("totalOnlineAgents", data)
                } else {
                    if (data != null) {
                        Log.e("totalOnlineAgents", data)
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Delivered Listener
    private var isDeliveredListener: Emitter.Listener = Emitter.Listener { args ->

        currActivity.runOnUiThread {
            try {
                val data = if (args.isNotEmpty()) args[0]?.toString() else null
                if (data != null) {
                    Log.e("delivered_response", data)

                    val jsonObject = JSONObject(data)
                    val chatId = jsonObject.optString("_id")
                    val chatDeliveredAt = jsonObject.optString("deliveredAt")
                    val chatMessage = jsonObject.optString("message")

                    val position = arrMessage.withIndex().find { it.value.id == chatId }?.index
                    position?.run {
                        arrMessage[this].deliveredAt = chatDeliveredAt
                        arrMessage[this].message = chatMessage
                        mAdapter.notifyItemChanged(this)

                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    //Read Listener
    private var isReadListener: Emitter.Listener = Emitter.Listener { args ->
        currActivity.runOnUiThread {
            try {
                val data = if (args.isNotEmpty()) args[0]?.toString() else null
                if (data != null) {
                    Log.e("response readListener", data)
                    val jsonObject = JSONObject(data)
                    val chatId = jsonObject.optString("_id")
                    val chatReadAt = jsonObject.optString("readAt")
                    val chatMessage = jsonObject.optString("message")
                    val position = arrMessage.withIndex().find { it.value.id == chatId }?.index
                    position?.run {
                        arrMessage[this].readAt = chatReadAt
                        arrMessage[this].message = chatMessage
                        mAdapter.notifyItemChanged(this)

                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

    }


    private fun visitorEndChatSession(
        feedbackStatus: String,
        feedback: String,
        dialog: AlertDialog
    ) {
        mSocket?.run {
            if (!this.connected())
                this.connect()
        }
        val verifyIdentity = AppPreferences.getInstance(currActivity).getIdentityDetails()
        val joinChatRoom = AppPreferences.getInstance(currActivity).getJoinChatRoomDetails()
        if (verifyIdentity != null && joinChatRoom != null) {
            val userJson = JSONObject()
            userJson.put("vid", verifyIdentity.visitorID)
            userJson.put("id", verifyIdentity.companyId)
            userJson.put("name", joinChatRoom.name)
            userJson.put("comment", feedback)
            userJson.put("feedback", feedbackStatus)
            userJson.put("appSid", AppSession.appSid)
            userJson.put("device", AppSession.deviceType)
            mSocket!!.emit("visitorEndChatSession", userJson, Ack { args ->
                val data = if (args.isNotEmpty()) args[0]?.toString() else null
                if (data != null) {
                    try {
                        runOnUiThread {
                            Log.d("visitorEndChatSession", data)
                            val type = object : TypeToken<VisitorChatTerminate>() {}.type
                            val visitorChatTerminate =
                                Gson().fromJson<VisitorChatTerminate>(data, type)
                            val message = Message()
                            message.apply {
                                message.message = visitorChatTerminate.message
                                message.messageType = Constants.MESSAGE_TYPE_INFO
                                message.isSystem = true
                            }
                            arrMessage.add(message)
                            if (arrMessage.isNotEmpty()) {
                                mAdapter.notifyItemInserted(arrMessage.size - 1)
                                recycler.smoothScrollToPosition(arrMessage.size - 1)
                            } else
                                mAdapter.notifyDataSetChanged()
                            llBottomWaiting.visibility = View.VISIBLE
                            btnDropMsg.visibility = View.VISIBLE
                            dialog.dismiss()
                        }

                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            })
        }

    }

    private fun visitorLoadChatHistory() {
        val verifyIdentity = AppPreferences.getInstance(currActivity).getIdentityDetails()
        val joinChatRoom = AppPreferences.getInstance(currActivity).getJoinChatRoomDetails()
        Picasso.get().load(Constants.AGENT_IMAGE_URL + joinChatRoom?.agentImage).placeholder(R.drawable.user).into(ivAgent)


        if (verifyIdentity != null && joinChatRoom != null) {
            val userJson = JSONObject()
            userJson.put("mid", joinChatRoom.visitorMessageId)
            userJson.put("appSid", AppSession.appSid)
            userJson.put("device", AppSession.deviceType)
            mSocket?.emit("visitorLoadChatHistory", userJson, Ack { args ->
                val data = if (args.isNotEmpty()) args[0]?.toString() else null
                if (data != null) {
                    try {
                        runOnUiThread {
                            Log.d("visitorLoadChatHistory", data)
                            val type = object : TypeToken<ArrayList<Message>>() {}.type
                            val message = Gson().fromJson<ArrayList<Message>>(data, type)
                            arrMessage.addAll(message)
                            arrMessage.filter { it.agentId != null }
                            if (arrMessage.isNotEmpty()) {
                                mAdapter.notifyItemInserted(arrMessage.size - 1)
                                recycler.scrollToPosition(arrMessage.size - 1)
                            } else
                                mAdapter.notifyDataSetChanged()

                            getData()
                        }
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
            })
        }
    }

    private fun updateVisitorRating(feedback: String, dialog: Dialog? = null) {
        mSocket?.run {
            if (!this.connected())
                this.connect()
        }
        val verifyIdentity = AppPreferences.getInstance(currActivity).getIdentityDetails()
        val joinChatRoom = AppPreferences.getInstance(currActivity).getJoinChatRoomDetails()
        if (verifyIdentity != null && joinChatRoom != null) {
            val userJson = JSONObject()
            userJson.put("mid", joinChatRoom.visitorMessageId)
            userJson.put("vid", verifyIdentity.visitorID)
            userJson.put("feedback", feedback)
            userJson.put("appSid", AppSession.appSid)
            userJson.put("device", AppSession.deviceType)
            userJson.put("locale", AppSession.locale)
            println("Sending Feedback :: $userJson")
            mSocket?.emit("updateVisitorRating", userJson, Ack { args ->
                val data = if (args.isNotEmpty()) args.first()?.toString() else null
                if (data != null) {
                    try {
                        runOnUiThread {
                            Log.d("updateVisitorRating", data)
                            if (dialog?.isShowing == true)
                                dialog.dismiss()
                        }
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
            })
        }

    }

    private fun emailChatTranscript(emailId: String) {
        mSocket?.run {
            if (!this.connected())
                this.connect()
        }
        val verifyIdentity = AppPreferences.getInstance(currActivity).getIdentityDetails()
        val joinChatRoom = AppPreferences.getInstance(currActivity).getJoinChatRoomDetails()
        if (verifyIdentity != null && joinChatRoom != null) {
            val userJson = JSONObject()

            userJson.put("mid", joinChatRoom.visitorMessageId)
            userJson.put("vid", verifyIdentity.visitorID)
            userJson.put("email", emailId)
            userJson.put("appSid", AppSession.appSid)
            userJson.put("device", AppSession.deviceType)
            mSocket!!.emit("emailChatTranscript", userJson, Ack { args ->
                val data = if (args.isNotEmpty()) args.first()?.toString() else null
                if (data != null) {
                    try {
                        runOnUiThread {
                            Log.d("updateVisitorRating", data)
                        }
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
            })
        }

    }


    private fun sendOfflineMessage(
        dialog: AlertDialog,
        strName: String,
        strEmail: String,
        strMobile: String,
        strSubject: String,
        strMsg: String
    ) {
        mSocket?.run {
            if (!this.connected())
                this.connect()
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
                val data = if (args.isNotEmpty()) args.first()?.toString() else null
                runOnUiThread {
                    if (data != null) {
                        try {
                            Log.d("inviteChat", data)
                            dialog.dismiss()
                            currActivity.finish()
                        } catch (e: JSONException) {
                            e.printStackTrace()
                        }
                    }
                }
            })
        }

    }


    private fun visitorJoinAgentRoom() {
        mSocket?.run {
            if (!this.connected())
                this.connect()
        }
        val verifyIdentity = AppPreferences.getInstance(currActivity).getIdentityDetails()
        val joinChatRoom = AppPreferences.getInstance(currActivity).getJoinChatRoomDetailsInString()
        if (verifyIdentity != null && joinChatRoom != null) {
            val userJson = JSONObject(joinChatRoom)
            userJson.put("appSid", AppSession.appSid)
            userJson.put("device", AppSession.deviceType)
            mSocket?.emit("visitorJoinAgentRoom", userJson, Ack { args ->
                val data = if (args.isNotEmpty()) args.first()?.toString() else null
                if (data != null) {
                    try {
                        Log.d("visitorJoinAgentRoom", data)
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
            })
        }

    }

    private fun visitorTyping(ts: Int, message: String, stop: Boolean) {
        mSocket?.run {
            if (!this.connected())
                this.connect()
        }
        val verifyIdentity = AppPreferences.getInstance(currActivity).getIdentityDetails()
        val joinChatRoom = AppPreferences.getInstance(currActivity).getJoinChatRoomDetails()
        if (verifyIdentity != null && joinChatRoom != null) {
            val userJson = JSONObject()

            /*   vid: (VISITOR ID),
               id: (Company ID),
               agent_id: (AGENT ID),
               ts: (When start typing then 1 and continue typing 2, and when stop typing then pass 0),
               message: `X start typing...` when continues typing `X is typing...`,
               stop: when stop typing then true else false
               */

            userJson.put("vid", verifyIdentity.visitorID)
            userJson.put("id", verifyIdentity.companyId)
            userJson.put("agent_id", joinChatRoom.agentId)
            userJson.put("ts", ts)
            userJson.put("message", message)
            userJson.put("stop", stop)
            userJson.put("appSid", AppSession.appSid)
            userJson.put("device", AppSession.deviceType)

            mSocket?.emit("visitorTyping", userJson, Ack { args ->
                val data = if (args.isNotEmpty()) args.first()?.toString() else null
                currActivity.runOnUiThread {
                    if (data != null) {
                        try {
                            Log.d("visitorTyping", data)
                        } catch (e: JSONException) {
                            e.printStackTrace()
                        }
                    }
                }

            })
        }

    }

    private fun sendVisitorMessage(
        message: String,
        isAttachment: Int,
        attachmentFile: String?,
        fileType: String?,
        fileSize: String?,
        attachedFile: File? = null
    ) {
        if (!mSocket!!.connected()) {
            mSocket!!.connect()
        }

        val verifyIdentity = AppPreferences.getInstance(currActivity).getIdentityDetails()
        val joinChatRoom = AppPreferences.getInstance(currActivity).getJoinChatRoomDetails()
        if (verifyIdentity != null && joinChatRoom != null) {
            //for typing event...................
            val msg = getString(R.string.visto_stop_typing)
            val text = msg.replace("[X]", joinChatRoom.name!!)
            visitorTyping(2, text, true)

            //...............................................................
            val randomId = generateRandomId()
            val userJson = JSONObject()
            userJson.put("dc_id", verifyIdentity.companyId)
            userJson.put("dc_vid", verifyIdentity.visitorID)
            if (joinChatRoom.agentId != null) {
                userJson.put("dc_agent_id", joinChatRoom.agentId)
            } else {
                userJson.put("dc_agent_id", "")
            }
            userJson.put("dc_mid", joinChatRoom.visitorMessageId)
            userJson.put("dc_name", joinChatRoom.name)
            userJson.put("send_by", 2)
            userJson.put("message", message)
            userJson.put("localId", randomId)
            userJson.put("is_attachment", isAttachment)
            if (isAttachment == Constants.ATTACHMENT_MESSAGE) {
                userJson.put("attachment_file", attachmentFile)
                userJson.put("file_type", fileType)
                userJson.put("file_size", fileSize)
            }
            userJson.put("appSid", AppSession.appSid)
            userJson.put("device", AppSession.deviceType)

            //for show message locally
            addLocal(message, randomId, isAttachment, attachedFile, fileType)

            mSocket?.emit("sendVisitorMessage", userJson, Ack { args ->
                val data = if (args.isNotEmpty()) args.first()?.toString() else null
                if (data != null) {
                    try {
                        runOnUiThread {
                            Log.d("sendVisitorMessage", data)
                            val type = object : TypeToken<Message>() {}.type
                            val msg = Gson().fromJson<Message>(data, type)
                            msg.attachmentFile?.let { Log.e("sendVisitorMessage", it) }

                            for (id in arrRandomId) {
                                if (id == msg.localId) {
                                    arrMessage.filter { it.isLocal }.filter { it.localId == id }
                                        .forEach {
                                            it.isLocal = true
                                            it.message = msg.message
                                            it.attachmentFile = msg.attachmentFile
                                            unReadChatMsgId = msg.id ?: ""
                                        }
                                    arrRandomId.remove(id)
                                }
                            }
                            mAdapter.notifyDataSetChanged()


                            for (i in 0.until(arrMessage.size)) {
                                if (arrMessage[i].readAt == null) {
                                    unReadChatMsgId = arrMessage[i].id!!
                                    isReadToUserEmitter()
                                }
                            }

                            isDeliveredToUserEmitter()
                        }
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
            })
        }

    }

    private fun addLocal(
        msg: String, localId: String, attachment: Int, file: File?, fileType: String?
    ) {
        val message = Message()
        message.isLocal = true
        message.sendBy = 2
        message.isSystem = false
        message.createdAt = AppUtils.getCurrentTime()
        message.localId = localId
        message.isAttachment = attachment
        if (attachment == Constants.ATTACHMENT_MESSAGE) {
            message.tempFile = file
            message.fileType = fileType
        }
        message.message = msg
        arrRandomId.add(localId)
        arrMessage.add(message)
        if (arrMessage.isNotEmpty()) {
            mAdapter.notifyItemInserted(arrMessage.size - 1)
            recycler.smoothScrollToPosition(arrMessage.size - 1)
        } else
            mAdapter.notifyDataSetChanged()

        etMessage.setText("")
    }

    private fun generateRandomId(): String {
        val AlphaNumericString = ("ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                + "0123456789"
                + "abcdefghijklmnopqrstuvxyz")

        val sb = StringBuilder(10)
        for (i in 0 until 10) {
            val index = (AlphaNumericString.length
                    * Math.random()).toInt()
            sb.append(
                AlphaNumericString[index]
            )
        }
        return sb.toString()
    }

    override fun onResume() {
        super.onResume()
        socketConnecting()
        /*  val filter = IntentFilter()
          filter.addAction(ACTION_AGENT_ACCEPT_CHAT_REQUEST)*/
        /*   LocalBroadcastManager.
           getInstance(currActivity).
           registerReceiver(receiver, filter)*/

    }

    fun downloadImageFile(
        context: Activity,
        url: String,
        fileName: String, holder: ChatAdapter.ReceivedViewHolder
    ) {

        val folderPath = File(Environment.getExternalStorageDirectory(), "Drdsh")
        if (!folderPath.exists()) {
            folderPath.mkdir()
        }

        val mediaPath = File(folderPath, "Media")
        if (!mediaPath.exists()) {
            mediaPath.mkdir()
        }


        val tempFile = File(
            mediaPath.absolutePath,
            fileName
        )
        Log.e("FILE PATH", "File Path:$tempFile")
        if (tempFile.exists()) {
            return
        }

        holder.itemView.ivIncomingImageDownload.visibility = View.GONE
        holder.itemView.pbIncomingImage.visibility = View.VISIBLE

        val dmRequest = DownloadManager.Request(Uri.parse(url))
        dmRequest.setTitle(fileName)
        dmRequest.setDescription(context.getString(R.string.downloading))
        dmRequest.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        dmRequest.setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS,
            File.separator + fileName
        )
        dmRequest.setDestinationInExternalFilesDir(
            context,
            Environment.DIRECTORY_DOWNLOADS,
            fileName
        )
        dmRequest.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE or DownloadManager.Request.NETWORK_WIFI)
        dmRequest.setAllowedOverMetered(true)
        dmRequest.setAllowedOverRoaming(true)
        val dm =
            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val onComplete: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                context.unregisterReceiver(this)
                val downloadId =
                    intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                val c =
                    dm.query(DownloadManager.Query().setFilterById(downloadId))
                if (c.moveToFirst()) {
                    val indexAt = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (indexAt !=-1){
                        val status = c.getInt(indexAt)
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            holder.itemView.pbIncomingImage.visibility = View.GONE
                            mAdapter.notifyDataSetChanged()
                        }
                    }

                }
                c.close()
            }
        }
        context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        dm.enqueue(dmRequest)
    }


    fun downloadImageFile(
        context: Activity,
        url: String,
        fileName: String, holder: ChatAdapter.SentViewHolder
    ) {

        val folderPath = File(Environment.getExternalStorageDirectory(), "Drdsh")
        if (!folderPath.exists()) {
            folderPath.mkdir()
        }

        val mediaPath = File(folderPath, "Media")
        if (!mediaPath.exists()) {
            mediaPath.mkdir()
        }


        val tempFile = File(
            mediaPath.absolutePath,
            fileName
        )
        Log.e("FILE PATH", "File Path:$tempFile")
        if (tempFile.exists()) {
            return
        }

        holder.itemView.ivBtnPlaySender.visibility = View.GONE
        holder.itemView.pbOutGoingImage.visibility = View.VISIBLE

        val dmRequest = DownloadManager.Request(Uri.parse(url))
        dmRequest.setTitle(fileName)
        dmRequest.setDescription(context.getString(R.string.downloading))
        dmRequest.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        dmRequest.setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS,
            File.separator + fileName
        )
        dmRequest.setDestinationInExternalFilesDir(
            context,
            Environment.DIRECTORY_DOWNLOADS,
            fileName
        )
        dmRequest.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE or DownloadManager.Request.NETWORK_WIFI)
        dmRequest.setAllowedOverMetered(true)
        dmRequest.setAllowedOverRoaming(true)
        val dm =
            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val onComplete: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                context.unregisterReceiver(this)
                val downloadId =
                    intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                val c =
                    dm.query(DownloadManager.Query().setFilterById(downloadId))
                if (c.moveToFirst()) {
                    val safeIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (safeIndex != -1){
                        val status = c.getInt(safeIndex)
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            holder.itemView.ivBtnPlaySender.visibility = View.VISIBLE
                            holder.itemView.pbOutGoingImage.visibility = View.GONE
                            mAdapter.notifyDataSetChanged()
                        }

                    }

                }
                c.close()
            }
        }
        context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        dm.enqueue(dmRequest)
    }

    private fun uploadFile(file: File) {
        val bytes = file.readBytes()
        val base64 = Base64.encodeToString(bytes, 0)
        val mime_type = getMimeType(Uri.fromFile(file), currActivity)
        val fileSize = getFileSize(Uri.fromFile(file).scheme!!, Uri.fromFile(file), currActivity)
        val destinationFile =
            File(currActivity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), file.name)
        val source = FileInputStream(file).channel
        val destination = FileOutputStream(destinationFile).channel
        if (destination != null && source != null) {
            destination.transferFrom(source, 0, source.size())
        }
        source?.close()
        destination?.close()

        if (mime_type != null) {
            Log.e("MIME_TYPE", mime_type)
        }
        Log.e("FILE_SIZE", "$fileSize")
        Log.e("BASE_64", base64)
        Log.e("FILE_NAME", file.name)
        sendVisitorMessage(
            file.name,
            Constants.ATTACHMENT_MESSAGE,
            base64,
            mime_type,
            fileSize.toString(),
            file
        )
    }

    //image compress task callback
    private val iImageCompressTaskListener: IImageCompressTaskListener =
        object :
            IImageCompressTaskListener {
            override fun onComplete(compressed: List<File>) {
                //Photo Compressed SuccessFully
                val fileCompressed = compressed.firstOrNull()
                fileCompressed?.run {
                    Log.d(
                        "ImageCompressor",
                        "New photo size ==> " + this.length()
                    ) //log new file size.
                    uploadFile(this)
                }
            }

            override fun onError(error: Throwable) {
                //very unlikely, but it might happen on a device with extremely low storage.
                //log it, log.WhatTheFuck?, or show a dialog asking the user to delete some files....etc, etc
                Log.wtf("ImageCompressor", "Error occurred", error)
            }
        }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            _REQUEST_CODE_DOCUMENT -> {
                if (data != null && Activity.RESULT_OK == resultCode) {
                    _UPLOAD_REQUEST_CODE = requestCode
                    val uri = data.data
                    uri?.run {
                        val realPath =
                            AppUtils.getRealPath(currActivity, this)
                        realPath?.let {
                            file = File(it)
                            file?.let { safeFile ->
                                println("Length ${safeFile.length()}")
                                if (safeFile.length() <= MAX_VIDEO_SIZE_IN_BYTES) {
                                    if (getMimeType(safeFile)?.contains(Constants.IMAGE) == true) {
                                        imageCompressTask = ImageCompressTask(
                                            currActivity,
                                            it,
                                            iImageCompressTaskListener
                                        )
                                        mExecutorService.execute(imageCompressTask)
                                    } else
                                        uploadFile(safeFile)
                                } else
                                    showToast(currActivity, getString(R.string.max_file_size), true)

                            }
                        }
                    }
                }
            }
        }

    }


    // Message Delivered to User Using Emitter
    private fun isDeliveredToUserEmitter() {
        val joinChatRoom = AppPreferences.getInstance(currActivity).getJoinChatRoomDetails()

        if (joinChatRoom != null) {
            val userJson = JSONObject()

            userJson.put("device", AppSession.deviceType)
            userJson.put("locale", AppSession.locale)
            userJson.put("_id", message.id)
            mSocket!!.emit("isDelivered", userJson, Ack { args ->

                val data = if (args.isNotEmpty()) args[0]?.toString() else null
                if (data != null) {
                    try {
//                            val chatMessage = Gson().fromJson<Message>(data, object : TypeToken<Message>() {}.type)
                        isReadToUserEmitter()
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
            })
        }
    }

    //Message Read By User Using Emitter
    private fun isReadToUserEmitter() {
        val joinChatRoom = AppPreferences.getInstance(currActivity).getJoinChatRoomDetails()

        if (joinChatRoom != null) {
            val userJson = JSONObject()
            userJson.put("device", AppSession.deviceType)
            userJson.put("locale", AppSession.locale)
            userJson.put("_id", unReadChatMsgId)
            mSocket!!.emit("isRead", userJson, Ack { args ->

                val data = if (args.isNotEmpty()) args[0]?.toString() else null
                if (data != null) {
                    try {

                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
            })
        }
    }


    fun openFile(selectedItem: File) {
        // Get URI and MIME type of file
        val uri = FileProvider.getUriForFile(
            currActivity.applicationContext,
            "${BuildConfig.LIBRARY_PACKAGE_NAME}.fileProvider",
            selectedItem
        )
        val mime: String = getMimeTypeFromExtension(uri.toString())

        // Open file with user selected app
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.setDataAndType(uri, mime)
            currActivity.startActivity(intent)
        } catch (e: Exception) {
            showToast(currActivity, getString(R.string.failed), true)
            e.printStackTrace()
        }

    }


}