package com.nocholla.androidkotlinbluetooth

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.nocholla.androidkotlinbluetooth.adapter.MessageAdapter
import com.nocholla.androidkotlinbluetooth.model.Message
import com.nocholla.androidkotlinbluetooth.services.BluetoothChatService
import java.util.ArrayList


class BluetoothChatActivity : Activity() {

    // Creates a single instance of the Object. Similar to 'Static'
    companion object {
        // Message types sent from the BluetoothChatService Handler
        const val MESSAGE_STATE_CHANGE = 1
        const val MESSAGE_READ = 2
        const val MESSAGE_WRITE = 3
        const val MESSAGE_DEVICE_NAME = 4
        const val MESSAGE_TOAST = 5

        // Key names received from the BluetoothChatService Handler
        const val DEVICE_NAME = "device_name"
        const val TOAST = "toast"

        // Intent request codes
        private const val REQUEST_CONNECT_DEVICE = 1
        private const val REQUEST_ENABLE_BT = 2
    }

    private var mOutEditText: EditText? = null
    private var mSendButton: Button? = null

    // Name of the connected device
    private val mConnectedDeviceName: String? = null

    // String buffer for outgoing messages
    private var mOutStringBuffer: StringBuffer? = null

    // Local Bluetooth adapter
    private var mBluetoothAdapter: BluetoothAdapter? = null

    // Member object for the chat services
    private var mChatService: BluetoothChatService? = null

    private var mRecyclerView: RecyclerView? = null
    private var mLayoutManager: LinearLayoutManager? = null
    private var mAdapter: MessageAdapter? = null

    var counter = 0

    private val messageList = ArrayList<Message>()

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.main_activity)

        mRecyclerView = findViewById(R.id.my_recycler_view)
        mRecyclerView!!.setHasFixedSize(true)
        mLayoutManager = LinearLayoutManager(this)
        mRecyclerView!!.layoutManager = mLayoutManager
        mAdapter = MessageAdapter(baseContext, messageList)
        mRecyclerView!!.adapter = mAdapter
        mRecyclerView!!.itemAnimator = DefaultItemAnimator()

        // Bluetooth
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show()
            finish()
            return
        }
    }

    // Connect Button
    fun connect(v: View) {
        val serverIntent = Intent(this, DeviceListActivity::class.java)
        startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE)
    }

    // Discover Button
    fun discoverable(v: View) {
        ensureDiscoverable()
    }

    private fun ensureDiscoverable() {
        if (mBluetoothAdapter!!.scanMode != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            startActivity(discoverableIntent)
        }
    }

    public override fun onStart() {
        super.onStart()

        if (!mBluetoothAdapter!!.isEnabled) {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT)
        } else {
            if (mChatService == null) setupChat()
        }
    }

    @Synchronized
    public override fun onResume() {
        super.onResume()

        if (mChatService != null) {
            if (mChatService!!.state === BluetoothChatService.STATE_NONE) {
                mChatService!!.start()
            }
        }
    }

    @Synchronized
    public override fun onPause() {
        super.onPause()

    }

    public override fun onStop() {
        super.onStop()

    }

    public override fun onDestroy() {
        super.onDestroy()

        // Stop the Bluetooth chat services
        if (mChatService != null) mChatService!!.stop()
    }

    private fun setupChat() {
        mOutEditText = findViewById(R.id.edit_text_out)
        mOutEditText!!.setOnEditorActionListener(mWriteListener)
        mSendButton = findViewById(R.id.button_send)
        mSendButton!!.setOnClickListener {
            val view = findViewById<TextView>(R.id.edit_text_out)
            val message = view.text.toString()
            sendMessage(message)
        }

        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = BluetoothChatService(this, mHandler)

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = StringBuffer("")
    }

    private fun sendMessage(message: String) {
        // Check that we're actually connected before trying anything
        if (mChatService!!.state !== BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show()
            return
        }

        // Check that there's actually something to send
        if (message.isNotEmpty()) {

            // Get the message bytes and tell the BluetoothChatService to write
            val send = message.toByteArray()
            mChatService!!.write(send)

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer!!.setLength(0)
            mOutEditText!!.setText(mOutStringBuffer)
        }
    }

    // The action listener for the EditText widget, to listen for the return key
    private val mWriteListener = TextView.OnEditorActionListener { view, actionId, event ->
        // If the action is a key-up event on the return key, send the message
        if (actionId == EditorInfo.IME_NULL && event.action == KeyEvent.ACTION_UP) {
            val message = view.text.toString()
            sendMessage(message)
        }
        true
    }

    private val mHandler = object : Handler() {
        fun handleMessage(msg: Message) {

        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        when (requestCode) {
            REQUEST_CONNECT_DEVICE ->
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    // Get the device MAC address
                    val address = data.extras!!.getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS)

                    // Get the BLuetoothDevice object
                    val device = mBluetoothAdapter!!.getRemoteDevice(address)

                    // Attempt to connect to the device
                    mChatService!!.connect(device)
                }
            REQUEST_ENABLE_BT ->
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    setupChat()
                } else {
                    // User did not enable Bluetooth or an error occured
                    Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show()
                    finish()
                }
        }
    }

}
