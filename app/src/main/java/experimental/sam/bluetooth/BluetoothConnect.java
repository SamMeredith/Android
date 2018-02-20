package experimental.sam.bluetooth;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.util.UUID;

class BluetoothConnect {

    private static final String TAG = "BluetoothConnService";

    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    private final BluetoothAdapter mBluetoothAdapter;
    private final Handler mHandler;
    private int mState;
    private ConnectThread mConnectThread;
    static BluetoothSocket mSocket;

    BluetoothConnect(Handler handler) {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = Constants.STATE_NONE;
        mHandler = handler;
    }

    /**
     * Set the current state of the connection
     * @param state An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        mState = state;

        //Send state so UI thread can update ListView
        mHandler.obtainMessage(Constants.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    synchronized  int getState() {
        return mState;
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device
     * @param device The BluetoothDevice to connect
     */
    synchronized void connect(BluetoothDevice device) {

        //Don't allow two concurrent connection attempts
        if (mState == Constants.STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(Constants.STATE_CONNECTING);
    }

    synchronized void startConnect() {

        if(mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
    }

    synchronized void stopConnect() {

        if(mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        setState(Constants.STATE_NONE);
    }

    /**
     * This thread runs while attempting to make an outgoing connection with a device.
     * It runs straight through; the connection either succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            //Get a BluetoothSocket for a connection with the given BluetoothDevice
            try {
                tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                mHandler.obtainMessage(Constants.ERROR_SOCKET).sendToTarget();
            }
            mmSocket = tmp;
        }

        public void run() {
            //Always cancel discovery because it will slow down a connection
            mBluetoothAdapter.cancelDiscovery();

            //Make a connection to the BluetoothSocket
            for (int i = 1; i <= Constants.CONNECTION_MAX; i++) {
                try {
                    //This is a blocking call
                    mmSocket.connect();
                    break;
                } catch (IOException e) {
                    mHandler.obtainMessage(Constants.CONNECTION_FAIL, i).sendToTarget();
                    if (i == Constants.CONNECTION_MAX) {
                        try {
                            mmSocket.close();
                        } catch (IOException e2) {
                            Log.e(TAG, "unable to close() socket during connection failure", e2);
                        }
                        stopConnect();
                        return;
                    }
                }
            }

            //Reset the ConnectThread because we're done, make socket available to UploadService
            synchronized (BluetoothConnect.this) {
                mConnectThread = null;
                mSocket = mmSocket;
            }

            setState(Constants.STATE_CONNECTED);

            //UI thread displays device connected toast
            Message msg = mHandler.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
            Bundle bundle = new Bundle();
            bundle.putString(Constants.DEVICE_NAME, mmDevice.getName());
            msg.setData(bundle);
            mHandler.sendMessage(msg);
        }

        void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
}
