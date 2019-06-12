package com.nocholla.androidkotlinbluetooth.services

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Bundle
import android.os.Handler
import com.nocholla.androidkotlinbluetooth.BluetoothChatActivity
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */

class BluetoothChatService
/**
 * Constructor. Prepares a new BluetoothChatActivity session.
 *
 * @param context The UI Activity Context
 * @param handler A Handler to send messages back to the UI Activity
 */
    (context: Context, private val mHandler: Handler) {

    // Member fields
    private val mAdapter: BluetoothAdapter
    private var mAcceptThread: AcceptThread? = null
    private var mConnectThread: ConnectThread? = null
    private var mConnectedThread: ConnectedThread? = null
    private var mState: Int = 0

    /**
     * Return the current connection state.
     */
    /**
     * Set the current state of the chat connection
     *
     * @param state An integer defining the current connection state
     */
    // Give the new state to the Handler so the UI Activity can update
    var state: Int
        @Synchronized get() = mState
        @Synchronized private set(state) {
            mState = state
            mHandler.obtainMessage(BluetoothChatActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget()
        }

    init {
        mAdapter = BluetoothAdapter.getDefaultAdapter()
        mState = STATE_NONE
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    @Synchronized
    fun start() {
        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }
        // Start the thread to listen on a BluetoothServerSocket
        if (mAcceptThread == null) {
            mAcceptThread = AcceptThread()
            mAcceptThread!!.start()
        }
        state = STATE_LISTEN
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param device The BluetoothDevice to connect
     */
    @Synchronized
    fun connect(device: BluetoothDevice) {
        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread!!.cancel()
                mConnectThread = null
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }

        // Start the thread to connect with the given device
        mConnectThread = ConnectThread(device)
        mConnectThread!!.start()

        state = STATE_CONNECTING
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    @Synchronized
    fun connected(socket: BluetoothSocket, device: BluetoothDevice) {
        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }

        // Cancel the accept thread because we only want to connect to one device
        if (mAcceptThread != null) {
            mAcceptThread!!.cancel()
            mAcceptThread = null
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = ConnectedThread(socket)
        mConnectedThread!!.start()

        // Send the name of the connected device back to the UI Activity
        val msg = mHandler.obtainMessage(BluetoothChatActivity.MESSAGE_DEVICE_NAME)
        val bundle = Bundle()
        bundle.putString(BluetoothChatActivity.DEVICE_NAME, device.name)
        msg.data = bundle
        mHandler.sendMessage(msg)

        state = STATE_CONNECTED
    }

    /**
     * Stop all threads
     */
    @Synchronized
    fun stop() {
        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }

        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }

        if (mAcceptThread != null) {
            mAcceptThread!!.cancel()
            mAcceptThread = null
        }

        state = STATE_NONE
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread.write
     */
    fun write(out: ByteArray) {
        // Create temporary object
        val r: ConnectedThread?

        // Synchronize a copy of the ConnectedThread
        synchronized(this) {
            if (mState != STATE_CONNECTED) return
            r = mConnectedThread
        }

        // Perform the write unsynchronized
        r!!.write(out)
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private fun connectionFailed() {
        state = STATE_LISTEN

        // Send a failure message back to the Activity
        val msg = mHandler.obtainMessage(BluetoothChatActivity.MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString(BluetoothChatActivity.TOAST, "Unable to connect device")
        msg.data = bundle

        mHandler.sendMessage(msg)
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private fun connectionLost() {
        state = STATE_LISTEN

        // Send a failure message back to the Activity
        val msg = mHandler.obtainMessage(BluetoothChatActivity.MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString(BluetoothChatActivity.TOAST, "Device connection was lost")
        msg.data = bundle

        mHandler.sendMessage(msg)
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private inner class AcceptThread : Thread() {
        // The local server socket
        private val mmServerSocket: BluetoothServerSocket?

        init {
            var tmp: BluetoothServerSocket? = null
            // Create a new listening server socket
            try {
                tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME, MY_UUID)
            } catch (e: IOException) {
            }

            mmServerSocket = tmp
        }

        override fun run() {
            name = "AcceptThread"
            var socket: BluetoothSocket? = null
            // Listen to the server socket if we're not connected
            while (mState != STATE_CONNECTED) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = mmServerSocket!!.accept()
                } catch (e: IOException) {
                    break
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized(this@BluetoothChatService) {
                        when (mState) {
                            STATE_LISTEN, STATE_CONNECTING ->
                                // Situation normal. Start the connected thread.
                                connected(socket, socket.remoteDevice)
                            STATE_NONE, STATE_CONNECTED ->
                                // Either not ready or already connected. Terminate new socket.
                                try {
                                    socket.close()
                                } catch (e: IOException) {
                                }

                        }
                    }
                }
            }
        }

        fun cancel() {
            try {
                mmServerSocket!!.close()
            } catch (e: IOException) {
            }

        }
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private inner class ConnectThread(private val mmDevice: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket?

        init {
            var tmp: BluetoothSocket? = null
            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                tmp = mmDevice.createRfcommSocketToServiceRecord(MY_UUID)
            } catch (e: IOException) {
            }

            mmSocket = tmp
        }

        override fun run() {
            name = "ConnectThread"
            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery()
            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket!!.connect()
            } catch (e: IOException) {
                connectionFailed()
                // Close the socket
                try {
                    mmSocket!!.close()
                } catch (e2: IOException) {
                }

                // Start the service over to restart listening mode
                this@BluetoothChatService.start()
                return
            }

            // Reset the ConnectThread because we're done
            synchronized(this@BluetoothChatService) {
                mConnectThread = null
            }
            // Start the connected thread
            connected(mmSocket, mmDevice)
        }

        fun cancel() {
            try {
                mmSocket!!.close()
            } catch (e: IOException) {
            }

        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream?
        private val mmOutStream: OutputStream?

        init {
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = mmSocket.inputStream
                tmpOut = mmSocket.outputStream
            } catch (e: IOException) {
            }

            mmInStream = tmpIn
            mmOutStream = tmpOut
        }

        override fun run() {
            val buffer = ByteArray(1024)
            var bytes: Int

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream!!.read(buffer)
                    // Send the obtained bytes to the UI Activity
                    mHandler.obtainMessage(BluetoothChatActivity.MESSAGE_READ, bytes, -1, buffer)
                        .sendToTarget()
                } catch (e: IOException) {
                    connectionLost()
                    break
                }

            }
        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer The bytes to write
         */
        fun write(buffer: ByteArray) {
            try {
                mmOutStream!!.write(buffer)
                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(BluetoothChatActivity.MESSAGE_WRITE, -1, -1, buffer)
                    .sendToTarget()
            } catch (e: IOException) {
            }

        }

        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
            }

        }
    }

    companion object {

        // Name for the SDP record when creating server socket
        private val NAME = "BluetoothChatActivity"

        // Unique UUID for this application
        private val MY_UUID = UUID.fromString("fa98c1d1-afac-22de-9a49-0900200c9a77")

        // Constants that indicate the current connection state
        val STATE_NONE = 0       // we're doing nothing
        val STATE_LISTEN = 1     // now listening for incoming connections
        val STATE_CONNECTING = 2 // now initiating an outgoing connection
        val STATE_CONNECTED = 3  // now connected to a remote device
    }
}
