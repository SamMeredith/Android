package experimental.sam.bluetooth;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import static experimental.sam.bluetooth.BluetoothConnect.mSocket;

public class UploadService extends Service{

    private static final String TAG = "UploadService";
    static boolean isRunning = false;

    private final IBinder mBinder = new LocalBinder();
    private ConnectedThread mConnectedThread;
    private ArrayList<String> hexLines;

    private Handler mHandler;

    NotificationManager mNotifyManager;
    NotificationCompat.Builder mBuilder;

    public class LocalBinder extends Binder {
        UploadService getService() {
            return UploadService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent){
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startID) {
        return START_NOT_STICKY;
    }

    public void setHandler(Handler handler)
    {
        mHandler = handler;
    }

    public void uploadFile(ArrayList<String> mFile) {
        hexLines = mFile;

        mNotifyManager =
                (NotificationManager)this.getSystemService(Context.NOTIFICATION_SERVICE);
        mBuilder = new NotificationCompat.Builder(this);
        mBuilder.setContentTitle("Software Upload")
                .setContentText("Upload in progress")
                .setSmallIcon(R.drawable.at_logo);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(contentIntent);
        startForeground(1, mBuilder.build());

        connected(mSocket);
    }

    private void connectionLost() {
        //Send a failure message back to the Activity
        mHandler.obtainMessage(Constants.ERROR_DISCONNECT).sendToTarget();
    }

    synchronized void connected(BluetoothSocket socket) {

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                mHandler.obtainMessage(Constants.ERROR_SOCKET);
                mConnectedThread.interrupt();
                mConnectedThread = null;
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            isRunning = true;

            try {
                byte[] buffer = (hexLines.get(0) + "\r").getBytes();
                mmOutStream.write(buffer);

                for (int i = 1; i < hexLines.size();) {

                    int message = mmInStream.read();

                    if (message == Constants.ACK) {
                        buffer = (hexLines.get(i) + "\r").getBytes();
                        uploadProgress(++i);
                        mmOutStream.write(buffer);
                    } else if (message == Constants.NAK) {
                        //Device was not ready to buffer next line, resend.
                        mmOutStream.write(buffer);
                    }
                }
                uploadComplete();
            } catch (IOException e) {
                connectionLost();
            } finally {
                try {
                    mmInStream.close();
                    mmOutStream.close();
                } catch (IOException e2) {
                    Log.e(TAG, "close() of network streams failed", e2);
                }
                isRunning = false;
                stopSelf();
            }
        }

        void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connected socket failed", e);
            }
        }

        private void uploadProgress(int progress) {
            mBuilder.setProgress(100, (progress*100)/hexLines.size(), false);
            mNotifyManager.notify(1, mBuilder.build());
            mHandler.obtainMessage(Constants.UPLOAD_PROGRESS, (progress*100)/hexLines.size())
                    .sendToTarget();
        }

        private void uploadComplete() {
            mBuilder.setContentText("Upload Complete.");
            mBuilder.setAutoCancel(true);
            stopForeground(false);
            mNotifyManager.notify(2, mBuilder.build());
            mHandler.obtainMessage(Constants.UPLOAD_PROGRESS, 101).sendToTarget();
        }
    }



    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
