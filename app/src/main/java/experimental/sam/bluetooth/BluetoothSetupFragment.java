package experimental.sam.bluetooth;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import static android.app.Activity.RESULT_OK;

public class BluetoothSetupFragment extends Fragment{

    //TODO Test functional from remote download (fix file name)

    public static final String TAG = "BluetoothSetupFragment";

    //Layout views
    private ListView mConnectionView;
    private Button mSendButton;
    private Button mOpenButton;
    private Button mConnectButton;

    private String mConnectedDeviceName = null;
    private ArrayAdapter<String> mConnectionArrayAdapter;
    private ArrayList<String> hexLines = null;
    private String hexLine = null;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothConnect mConnectService = null;
    private boolean mBound = false;
    private int progressBarStatus = 0;
    UploadService mUploadService;
    ProgressDialog progressBar;

    private final MyHandler mHandler = new MyHandler(this);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            FragmentActivity activity = getActivity();
            Toast.makeText(activity, R.string.bt_not_available, Toast.LENGTH_LONG).show();
            activity.finish();
        }
        progressBar = new ProgressDialog(getContext());
        progressBar.setCancelable(true);
        progressBar.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressBar.setProgress(0);
        progressBar.setMax(100);
    }

    @Override
    public void onStart() {
        super.onStart();

        Intent mIntent = new Intent(getContext(), UploadService.class);
        getContext().startService(mIntent);
        getContext().bindService(mIntent, mConnection, Context.BIND_AUTO_CREATE);
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, Constants.REQUEST_ENABLE_BT);
        } else {
            setupConnect();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        //Perform check in onResume() in case BT was not enabled during onStart()
        if (mConnectService != null) {
            if (mConnectService.getState() == Constants.STATE_NONE) {
                //Start the Bluetooth connect service
                mConnectService.startConnect();
            }
        }
        if (UploadService.isRunning) {
            mConnectionArrayAdapter.add("Upload in progress...");
            mConnectionArrayAdapter.notifyDataSetChanged();
            progressBar.show();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mConnectService != null) {
            mConnectService.stopConnect();
        }
        if (mBound) {
            getContext().unbindService(mConnection);
            mBound = false;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bluetooth_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        mSendButton = (Button) view.findViewById(R.id.button_send);
        mConnectionView = (ListView) view.findViewById(R.id.in);
        mOpenButton = (Button) view.findViewById(R.id.button_open);
        mConnectButton = (Button) view.findViewById(R.id.button_connect);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_main, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_connect:
                Intent connectIntent = new Intent(getActivity(), DeviceListActivity.class);
                startActivityForResult(connectIntent, Constants.REQUEST_CONNECT_DEVICE);
                return true;
            default: return super.onOptionsItemSelected(item);
        }
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            UploadService.LocalBinder binder = (UploadService.LocalBinder) service;
            mUploadService = binder.getService();
            mUploadService.setHandler(mHandler);
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    /**
     * Set up the UI and background operations for connection.
     */
    private void setupConnect() {

        mConnectionArrayAdapter = new ArrayAdapter<>(getActivity(),R.layout.message);
        mConnectionView.setAdapter(mConnectionArrayAdapter);

        //Initialise the connect device button with a listener for click events
        mConnectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent connectIntent = new Intent(getActivity(), DeviceListActivity.class);
                startActivityForResult(connectIntent, Constants.REQUEST_CONNECT_DEVICE);
            }
        });

        //Initialise the send button with a listener for click events
        mSendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                progressBarStatus = 0;

                //Send initial message
                View view = getView();
                if (null != view) {
                    //Check that we're actually connected before trying anything
                    if (mConnectService.getState() != Constants.STATE_CONNECTED) {
                        Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    //Check that there's actually something to send
                    if (!hexLines.isEmpty() && mBound) {
                        progressBar.setMessage("File uploading ...");
                        progressBar.show();

                        mUploadService.uploadFile(hexLines);
                    } else {
                        Toast.makeText(getActivity(), R.string.no_file, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        hexLines = new ArrayList<>();

        //Initialise the open file button with a listener for click events
        mOpenButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                FileChooser fileChooser = new FileChooser(getActivity());
                fileChooser.setFileListener(new FileChooser.FileSelectedListener() {
                    @Override
                    public void fileSelected(File file) {
                        try {
                            FileReader fileReader = new FileReader(file);
                            BufferedReader bufferedReader = new BufferedReader(fileReader);
                            hexLine = bufferedReader.readLine();
                            if (!hexLine.contains("A+T")) {
                                Toast.makeText(getActivity(), R.string.bad_file,
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                            hexLines.add(hexLine);
                            while ((hexLine = bufferedReader.readLine()) != null) {
                                hexLines.add(hexLine);
                            }
                            mConnectionArrayAdapter.add("Opened " + file.getName());
                        } catch (IOException e) {
                            Toast.makeText(getActivity(), R.string.read_fail, Toast.LENGTH_SHORT)
                                    .show();
                        }
                    }
                });
                fileChooser.setExtension("hex");
                fileChooser.showDialog();
            }
        });

        mConnectService = new BluetoothConnect(mHandler);
    }

    private ArrayList<String> DownloadFromInternet(String... sUrl) {

        ArrayList<String> downloadLines = new ArrayList<>();
        String downloadLine;
        HttpURLConnection mConnection = null;
        InputStream mInputStream = null;
        BufferedReader mBufferedReader;

        ConnectivityManager connMgr = (ConnectivityManager)
                getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();

        if (networkInfo != null && networkInfo.isConnected()) {
            try {
                URL url = new URL(sUrl[0]);
                mConnection = (HttpURLConnection) url.openConnection();
                mConnection.connect();

                //Expect HTTP 200 OK
                if (mConnection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new Exception("Server returned HTTP " + mConnection.getResponseCode()
                            + " " + mConnection.getResponseMessage());
                }

                int fileLength = mConnection.getContentLength();
                mInputStream = mConnection.getInputStream();
                mBufferedReader = new BufferedReader(new InputStreamReader(mInputStream));
                progressBar.setMessage("Software downloading ...");
                progressBar.show();

                int total = 0;
                while ((downloadLine = mBufferedReader.readLine()) != null) {
                    downloadLines.add(downloadLine);
                    total += downloadLine.getBytes().length;
                    if (fileLength > 0) {
                        progressBar.setProgress((total/fileLength)*100);
                    }
                }
            } catch (Exception e) {
                Toast.makeText(getActivity(), R.string.bad_download, Toast.LENGTH_SHORT).show();
                return null;
            } finally {
                try {
                    if (mInputStream != null) {
                        mInputStream.close();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Unable to close() input stream during network failure");
                }
                if (mConnection != null) {
                    mConnection.disconnect();
                }
            }
        } else {
            Toast.makeText(getActivity(), R.string.no_internet, Toast.LENGTH_SHORT ).show();
        }
        return downloadLines;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case Constants.REQUEST_ENABLE_BT:
                if (resultCode == RESULT_OK) {
                    //BT is now on, setup the connection
                    setupConnect();
                } else {
                    //User did not enable Bluetooth or an error occurred
                    Toast.makeText(getActivity(),R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    getActivity().finish();
                }
                break;
            case Constants.REQUEST_CONNECT_DEVICE:
                if (resultCode == Activity.RESULT_OK) {
                    String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                    mConnectService.connect(device);
                }
                break;
        }
    }

    /**
     * The Handler that gets information back from BluetoothConnect. Should be static to allow
     * garbage collection, but needs weak reference to parent class to access objects.
     */
    private static class MyHandler extends Handler {
        private final WeakReference<BluetoothSetupFragment> bluetoothSetupFragmentReference;

        MyHandler(BluetoothSetupFragment bluetoothSetupFragmentInstance) {
            bluetoothSetupFragmentReference =
                    new WeakReference<>(bluetoothSetupFragmentInstance);
        }

        @Override
        public void handleMessage(Message msg) {
            BluetoothSetupFragment mBluetoothSetupFragment = bluetoothSetupFragmentReference.get();
            if (mBluetoothSetupFragment != null) {
                Activity activity = mBluetoothSetupFragment.getActivity();
                ArrayAdapter<String> mArrayAdapter = mBluetoothSetupFragment.mConnectionArrayAdapter;
                switch (msg.what) {
                    case Constants.MESSAGE_DEVICE_NAME:
                        //Save the connected device's name
                        mBluetoothSetupFragment.mConnectedDeviceName = msg.getData().getString(
                                Constants.DEVICE_NAME);
                        if (null != activity) {
                            Toast.makeText(activity, "Connected to "
                                    + mBluetoothSetupFragment.mConnectedDeviceName,
                                    Toast.LENGTH_SHORT).show();
                        }
                        break;
                    case Constants.MESSAGE_STATE_CHANGE:
                        switch (msg.arg1) {
                            case Constants.STATE_CONNECTING:
                                mArrayAdapter.add("Connecting..");
                                break;
                            case Constants.STATE_CONNECTED:
                                mArrayAdapter.add("Connected");
                                break;
                        }
                        break;
                    case Constants.MESSAGE_WRITE:
                        byte[] writeBuf = (byte[]) msg.obj;
                        //construct a string from the buffer
                        String writeMessage = new String(writeBuf);
                        mArrayAdapter.add(writeMessage);
                        break;
                    case Constants.MESSAGE_READ:
                        byte[] readBuf = (byte[]) msg.obj;
                        //Construct a string from the valid bytes in the buffer
                        String readMessage = new String(readBuf, 0, msg.arg1);
                        mArrayAdapter.add(
                                mBluetoothSetupFragment.mConnectedDeviceName + ": " + readMessage);
                        mArrayAdapter.notifyDataSetChanged();
                        break;
                    case Constants.ERROR_DISCONNECT:
                        mArrayAdapter.add("Connection Failed");
                        break;
                    case Constants.MESSAGE_NAK:
                        mArrayAdapter.add("NAK received, resending");
                        mArrayAdapter.notifyDataSetChanged();
                        break;
                    case Constants.ERROR_SOCKET:
                        mArrayAdapter.add("Connection Failed");
                        Toast.makeText(activity.getApplicationContext(),
                                "Unable to establish connection. Check Bluetooth is enabled.",
                                Toast.LENGTH_SHORT).show();
                        break;
                    case Constants.CONNECTION_FAIL:
                        Integer failCount = msg.arg1;
                        if (failCount < Constants.CONNECTION_MAX) {
                            mArrayAdapter.add("Unable to connect (" + failCount.toString() + "). Retrying...");
                        } else {
                            mArrayAdapter.add("Unable to connect. Ensure you have selected 'Yes' to 'Are you sure?'.");
                        }
                        mArrayAdapter.notifyDataSetChanged();
                        break;
                    case Constants.UPLOAD_PROGRESS:
                        mBluetoothSetupFragment.progressBarStatus = (int) msg.obj;
                        if (mBluetoothSetupFragment.progressBarStatus <= 100) {
                            mBluetoothSetupFragment.progressBar.setProgress(
                                    mBluetoothSetupFragment.progressBarStatus);
                        } else if (mBluetoothSetupFragment.progressBarStatus == 101) {
                            mBluetoothSetupFragment.progressBar.dismiss();
                            if (null != activity) {
                                Toast.makeText(activity.getApplicationContext(), "Upload Complete.",
                                        Toast.LENGTH_SHORT).show();
                            }
                            mArrayAdapter.add("Upload Complete.");
                            mArrayAdapter.notifyDataSetChanged();
                        }
                        break;
                }
            }
        }
    }
}
