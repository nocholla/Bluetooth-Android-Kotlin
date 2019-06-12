package com.nocholla.androidkotlinbluetooth.adapter

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.collections.ArrayList
import com.nocholla.androidkotlinbluetooth.R
import com.nocholla.androidkotlinbluetooth.model.Message

class MessageAdapter(private var context: Context?, private var messageList: ArrayList<Message>?, var SENDER: Int = 0, var RECIPIENT: Int = 1) : RecyclerView.Adapter<MessageAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val mTextView = itemView.findViewById(R.id.text) as TextView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageAdapter.ViewHolder {
        return if (viewType == 1) {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.chat_item_purple, parent, false)
            ViewHolder(v as LinearLayout)
        } else {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.chat_item_green, parent, false)
            ViewHolder(v as LinearLayout)
        }
    }

    private fun remove(pos: Int) {
        messageList!!.removeAt(pos)
        notifyItemRemoved(pos)
        notifyItemRangeChanged(pos, messageList!!.size)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = messageList!![position]

        // Chat Text
        val messageChatText = message.message.toString()
        Log.d("DEBUG CHAT TEXT", messageChatText)
        holder.mTextView!!.text = messageChatText

        // Open Details Page
        holder.itemView.setOnClickListener {
            remove(position)
        }

//        holder.itemView.setOnClickListener {
//            var messageIntent = Intent(context, messageDetailsActivity::class.java)
//            messageIntent.putExtra("title", messageChatText)
//
//            context!!.startActivity(messageIntent)
//        }

    }

    override fun getItemCount(): Int {
        return messageList!!.size
    }

    override fun getItemViewType(position: Int): Int {
        val message = messageList!![position]

        return if (message.senderName.equals("Me")) {
            SENDER
        } else {
            RECIPIENT
        }
    }

    fun setFilter(messageModels: List<Message>) {
        messageList = ArrayList()
        messageList!!.addAll(messageModels)
        notifyDataSetChanged()
    }

}