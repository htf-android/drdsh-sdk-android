package com.htf.drdshsdklibrary.Adapter

import ImageFullView
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.htf.drdshsdklibrary.Activities.ChatActivity
import com.htf.drdshsdklibrary.Models.Message
import com.htf.drdshsdklibrary.PhotoViewer.PhotoFullPopupWindow
import com.htf.drdshsdklibrary.R
import com.htf.drdshsdklibrary.Utills.AppUtils
import com.htf.drdshsdklibrary.Utills.Constants
import com.htf.drdshsdklibrary.Utills.Constants.ATTACHMENT_URL
import com.htf.learnchinese.utils.AppPreferences
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.row_incoming_msg.view.*
import kotlinx.android.synthetic.main.row_info_msg.view.*
import kotlinx.android.synthetic.main.row_outgoing_msg.view.*
import java.io.File


class
ChatAdapter(
    private val currActivity: Activity,
    private val arrMessage: ArrayList<Message>
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val VIEW_TYPE_MESSAGE_SENT = 1
    private val VIEW_TYPE_MESSAGE_RECEIVED = 2
    private val VIEW_TYPE_INFO_MESSAGE = 3


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val viewHolder: RecyclerView.ViewHolder = when (viewType) {
            VIEW_TYPE_MESSAGE_SENT -> SentViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.row_outgoing_msg,
                    parent,
                    false
                )
            )
            VIEW_TYPE_MESSAGE_RECEIVED -> ReceivedViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.row_incoming_msg,
                    parent,
                    false
                )
            )
            VIEW_TYPE_INFO_MESSAGE -> InfoViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.row_info_msg,
                    parent,
                    false
                )
            )
            else -> throw IllegalArgumentException()

        }
        return viewHolder
    }

    override fun getItemCount(): Int {
        return arrMessage.size
    }


    /* set the view type here*/

    override fun getItemViewType(position: Int): Int {
        val user = AppPreferences.getInstance(currActivity).getIdentityDetails()
        val message = arrMessage[position]

        val viewType = if (message.isSystem!!) {
            VIEW_TYPE_INFO_MESSAGE
        } else {
            when {
                (message.sendBy == Constants.FROM_AGENT) -> {
                    VIEW_TYPE_MESSAGE_RECEIVED
                }
                else -> {
                    VIEW_TYPE_MESSAGE_SENT
                }
            }
        }
        return viewType


    }

    /* Bind the view in View Holder*/

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val viewType = holder.itemViewType
        val model = arrMessage[position]
        when (viewType) {
            VIEW_TYPE_MESSAGE_SENT -> {
                holder as SentViewHolder

                holder.itemView.tvOutgoingMsgTime.text = AppUtils.convertDateFormat(
                    model.createdAt,
                    AppUtils.serverChatUTCDateTimeFormat,
                    AppUtils.targetTimeFormat
                )


                holder.itemView.ivOutgoingMsg.setOnClickListener {
                    if (model.fileType?.contains(Constants.IMAGE) == true) {
                        if (model.isLocal)
                            PhotoFullPopupWindow(holder.itemView, model.tempFile, currActivity)
                        else
                            ImageFullView(
                                holder.itemView,
                                ATTACHMENT_URL + model.attachmentFile,
                                currActivity
                            )
                    } else {
                        if (model.attachmentFile?.isFileDownloaded(currActivity) == true) {
                            try {
                                if (currActivity is ChatActivity)
                                    model.tempFile?.let { it1 -> currActivity.openFile(it1) }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else {
                            try {
                                if (model.attachmentFile != null) {
                                    (currActivity as ChatActivity).downloadImageFile(
                                        currActivity,
                                        ATTACHMENT_URL + model.attachmentFile,
                                        model.attachmentFile ?: "",
                                        holder
                                    )
                                }
                            } catch (ex: Exception) {
                                ex.printStackTrace()

                            }
                        }

                    }
                }

                when (model.isAttachment) {
                    Constants.ATTACHMENT_MESSAGE -> {
                        holder.itemView.tvOutgoingMsg.visibility = View.GONE
                        holder.itemView.ivOutgoingMsg.visibility = View.VISIBLE
                        if (model.fileType?.contains(Constants.IMAGE) == true) {
                            holder.itemView.ivBtnPlaySender.visibility = View.GONE
                            if (model.isLocal) {
                                Picasso.get().load(model.tempFile!!)
                                    .placeholder(R.drawable.image_placeholder)
                                    .into(holder.itemView.ivOutgoingMsg)
                            } else {
                                model.tempFile = getTempFile(currActivity, model.message?:"")
                                Picasso.get().load(ATTACHMENT_URL + model.attachmentFile)
                                    .placeholder(R.drawable.image_placeholder)
                                    .into(holder.itemView.ivOutgoingMsg)
                            }
                        } else if (model.fileType?.contains(Constants.VIDEO) == true) {
                            with(holder.itemView) {
                                ivBtnPlaySender.visibility = View.VISIBLE
                                if (model.attachmentFile?.isFileDownloaded(currActivity) == true) {
                                    model.tempFile = getTempFile(
                                        currActivity,
                                        model.attachmentFile!!
                                    )
                                    Glide.with(currActivity).asBitmap()
                                        .load(model.tempFile)
                                        .centerCrop()
                                        .override(256, 256)
                                        .into(ivOutgoingMsg)
                                } else {
                                    Glide.with(currActivity).asBitmap()
                                        .load(ATTACHMENT_URL + model.attachmentFile)
                                        .centerCrop()
                                        .override(256, 256)
                                        .into(ivOutgoingMsg)
                                }
                            }
                        } else {
                            with(holder.itemView) {
                                holder.itemView.ivBtnPlaySender.visibility = View.GONE
                                holder.itemView.tvOutgoingMsg.visibility = View.VISIBLE
                                holder.itemView.tvOutgoingMsg.text = model.message
                                if (model.attachmentFile?.isFileDownloaded(currActivity) == true) {
                                    model.tempFile = getTempFile(
                                        currActivity,
                                        model.attachmentFile!!
                                    )
                                    ivOutgoingMsg.setImageResource(R.drawable.ic_google_docs)
                                } else {
                                    ivOutgoingMsg.setImageResource(R.drawable.ic_google_docs)
                                }
                            }
                        }

                    }
                    Constants.NORMAL_MESSAGE -> {
                        with(holder.itemView) {
                            tvOutgoingMsg.visibility = View.VISIBLE
                            ivOutgoingMsg.visibility = View.GONE
                            tvOutgoingMsg.text = model.message
                            ivBtnPlaySender.visibility = View.GONE
                        }
                    }
                }


                when {
                    (model.deliveredAt?.isNotBlank() == true) -> {
                        holder.itemView.ivSent.setImageResource(R.drawable.ic_delivered)
                        holder.itemView.ivSent.imageTintList =
                            ColorStateList.valueOf(Color.parseColor("#322D33"))
                    }


                    (model.readAt?.isNotBlank() == true) -> {
                        holder.itemView.ivSent.setImageResource(R.drawable.ic_seen)
                        holder.itemView.ivSent.imageTintList =
                            ColorStateList.valueOf(Color.parseColor("#989898"))

                    }
                    else -> {
                        holder.itemView.ivSent.setImageResource(R.drawable.ic_delivered)
                        holder.itemView.ivSent.imageTintList =
                            ColorStateList.valueOf(Color.parseColor("#030404"))

                    }
                }
            }

            VIEW_TYPE_MESSAGE_RECEIVED -> {
                holder as ReceivedViewHolder
                Picasso.get().load(Constants.AGENT_IMAGE_URL + model.agentId?.image)
                    .placeholder(R.drawable.user).into(holder.itemView.ivAgent)
                holder.itemView.tvIncomingMsgTime.text = AppUtils.convertDateFormat(
                    model.createdAt,
                    AppUtils.serverChatUTCDateTimeFormat,
                    AppUtils.targetTimeFormat
                )
                holder.itemView.tvAgentName.text = model.agentId?.name!!

                when (model.isAttachment) {
                    Constants.ATTACHMENT_MESSAGE -> {
                        holder.itemView.tvIncomingMsg.visibility = View.GONE
                        if (model.fileType?.contains(Constants.IMAGE) == true) {
                            holder.itemView.rlIncomingImage.visibility = View.VISIBLE
                            holder.itemView.ivBtnPlay.visibility = View.GONE
                            if (model.attachmentFile?.isFileDownloaded(currActivity) == true) {
                                holder.itemView.rlIncomingImageTransparent.visibility = View.GONE
                                model.tempFile = getTempFile(currActivity, model.attachmentFile!!)
                                Picasso.get().load(model.tempFile!!)
                                    .into(holder.itemView.ivIncomingImage)
                            } else {
                                holder.itemView.rlIncomingImageTransparent.visibility = View.VISIBLE
                                Picasso.get().load(ATTACHMENT_URL + model.attachmentFile)
                                    .placeholder(R.drawable.image_placeholder)
                                    .into(holder.itemView.ivIncomingImage)
                            }
                        } else if (model.fileType?.contains(Constants.VIDEO) == true) {
                            with(holder.itemView) {
                                holder.itemView.rlIncomingImage.visibility = View.VISIBLE
                                if (model.attachmentFile?.isFileDownloaded(currActivity) == true) {
                                    ivBtnPlay.visibility = View.VISIBLE
                                    rlIncomingImageTransparent.visibility = View.GONE
                                    model.tempFile = getTempFile(
                                        currActivity,
                                        model.attachmentFile!!
                                    )
                                    Glide.with(currActivity).asBitmap()
                                        .load(model.tempFile)
                                        .centerCrop()
                                        .override(256, 256)
                                        .into(ivIncomingImage)
                                } else {
                                    rlIncomingImageTransparent.visibility = View.VISIBLE
                                    Glide.with(currActivity).asBitmap()
                                        .load(ATTACHMENT_URL + model.attachmentFile)
                                        .centerCrop()
                                        .override(256, 256)
                                        .into(ivIncomingImage)
                                }
                            }
                        } else {
                            with(holder.itemView) {
                                holder.itemView.rlIncomingImage.visibility = View.VISIBLE
                                holder.itemView.ivBtnPlay.visibility = View.GONE
                                holder.itemView.tvIncomingMsg.visibility = View.VISIBLE
                                holder.itemView.tvIncomingMsg.text = model.message
                                if (model.attachmentFile?.isFileDownloaded(currActivity) == true) {
                                    rlIncomingImageTransparent.visibility = View.GONE
                                    model.tempFile = getTempFile(
                                        currActivity,
                                        model.attachmentFile!!
                                    )
                                    ivIncomingImage.setImageResource(R.drawable.ic_google_docs)
                                } else {
                                    rlIncomingImageTransparent.visibility = View.VISIBLE
                                    ivIncomingImage.setImageResource(R.drawable.ic_google_docs)
                                }
                            }
                        }

                    }

                    Constants.NORMAL_MESSAGE -> {
                        holder.itemView.tvIncomingMsg.visibility = View.VISIBLE
                        holder.itemView.rlIncomingImage.visibility = View.GONE
                        holder.itemView.tvIncomingMsg.text = model.message
                    }
                }

                holder.itemView.ivIncomingImage.setOnClickListener {
                    if (model.attachmentFile?.isFileDownloaded(currActivity) == true) {
                        if (model.fileType?.contains(Constants.IMAGE) == true) {
                            PhotoFullPopupWindow(holder.itemView, model.tempFile!!, currActivity)
                        } else {
                            try {
                                if (currActivity is ChatActivity)
                                    model.tempFile?.let { it1 -> currActivity.openFile(it1) }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }

                holder.itemView.ivIncomingImageDownload.setOnClickListener {
                    try {
                        (currActivity as ChatActivity).downloadImageFile(
                            currActivity, ATTACHMENT_URL + model.attachmentFile,
                            model.attachmentFile!!, holder
                        )
                    } catch (ex: Exception) {
                        ex.printStackTrace()

                    }
                }
            }

            VIEW_TYPE_INFO_MESSAGE -> {
                holder as InfoViewHolder
                when {
                    model.messageInfoTypeShowLoading -> {
                        holder.itemView.tvInfoMsg.text = model.message
                        (currActivity as ChatActivity).showResendOption(holder, position)
                    }
                    else -> {
                        holder.itemView.pbWaiting.visibility = View.GONE
                        holder.itemView.tvInfoMsg.text = model.message
                    }
                }
            }

        }
    }


    /* There is no use of this Holder*/

    class SentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    }

    class ReceivedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    }

    class InfoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    }


    /* Download Image And File*/

    private fun String.isFileDownloaded(context: Context): Boolean {

        val tempFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), this)
        Log.e("File_path", "File Path:$tempFile")
        return tempFile.exists()
    }

    private fun getTempFile(context: Context, name: String): File {
        val tempFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            name
        )
        Log.e("FILE PATH", "File Path:$tempFile")
        return tempFile
    }
/*
    fun open_file(filename: String?) {
        val path: File = File(getFilesDir(), "dl")
        val file = File(path, filename)

        // Get URI and MIME type of file
        val uri: Uri =
            FileProvider.getUriForFile(this, App.PACKAGE_NAME.toString() + ".fileprovider", file)
        val mime: String = getContentResolver().getType(uri)

        // Open file with user selected app
        val intent = Intent()
        intent.setAction(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, mime)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(intent)
    }*/


}