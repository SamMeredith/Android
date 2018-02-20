package experimental.sam.bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;

public class DeviceListActivity extends Activity {

    private static final String TAG = "DeviceListActivity";

    public static String EXTRA_DEVICE_ADDRESS = "device_address";

    private BluetoothAdapter mBluetoothAdapter;
    private ArrayAdapter<String> mNewDevicesArrayAdapter;
    private ArrayAdapter<String> mPairedDevicesArrayAdapter;
    private ArrayList<BluetoothDevice> devices = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_device_list);

        //Set result CANCELED in case the user backs out
        setResult(Activity.RESULT_CANCELED);

        Button scanButton = (Button) findViewById(R.id.button_scan);
        scanButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                doDiscovery();
                view.setVisibility(View.GONE);
            }
        });

        mPairedDevicesArrayAdapter =
                new ArrayAdapter<>(this, R.layout.device_name);
        mNewDevicesArrayAdapter = new ArrayAdapter<>(this, R.layout.device_name);

        ListView pairedListView = (ListView) findViewById(R.id.paired_devices);
        pairedListView.setAdapter(mPairedDevicesArrayAdapter);
        pairedListView.setOnItemClickListener(mDeviceClickListener);
        pairedListView.setOnItemLongClickListener(mDeviceLongClickListener);

        ListView newDevicesListView = (ListView) findViewById(R.id.new_devices);
        newDevicesListView.setAdapter(mNewDevicesArrayAdapter);
        newDevicesListView.setOnItemClickListener(mDeviceClickListener);

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(mReceiver, filter);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);
            for (BluetoothDevice device : pairedDevices) {
                if (device.getName().startsWith("A+T")) {
                    mPairedDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                }
            }
        } else {
            String noDevices = getResources().getText(R.string.none_paired).toString();
            mPairedDevicesArrayAdapter.add(noDevices);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.cancelDiscovery();
        }

        this.unregisterReceiver(mReceiver);
    }

    private void doDiscovery() {

        findViewById(R.id.title_new_devices).setVisibility(View.VISIBLE);
        findViewById(R.id.scan_progress).setVisibility(View.VISIBLE);

        if (mBluetoothAdapter.isDiscovering()){
            mBluetoothAdapter.cancelDiscovery();
        }

        mBluetoothAdapter.startDiscovery();
    }


    private AdapterView.OnItemLongClickListener mDeviceLongClickListener
            = new AdapterView.OnItemLongClickListener() {
        public boolean onItemLongClick(AdapterView<?> av, View v, int arg2, long arg3) {

            // Get the device MAC address, which is the last 17 chars in the View
            String info = ((TextView) v).getText().toString();
            String address = info.substring(info.length() - 17);

            BluetoothDevice mDevice = mBluetoothAdapter.getRemoteDevice(address);
            try {
                //Reflect removeBond, not exposed otherwise?
                Method m = mDevice.getClass().getMethod("removeBond", (Class[]) null);
                m.invoke(mDevice, (Object[]) null);
                mPairedDevicesArrayAdapter.remove(info);
                mPairedDevicesArrayAdapter.notifyDataSetChanged();
            }
            catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
            return true;
        }
    };

    private AdapterView.OnItemClickListener mDeviceClickListener
            = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            // Cancel discovery because it's costly and we're about to connect
            mBluetoothAdapter.cancelDiscovery();

            // Get the device MAC address, which is the last 17 chars in the View
            String info = ((TextView) v).getText().toString();
            String address = info.substring(info.length() - 17);

            Intent intent = new Intent();
            intent.putExtra(EXTRA_DEVICE_ADDRESS, address);

            setResult(Activity.RESULT_OK, intent);
            finish();
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // If it's already paired, skip it, because it's been listed already
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    devices.add(device);
                    if (device.getName() != null && device.getName().startsWith("A+T")) {
                        mNewDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                        mNewDevicesArrayAdapter.notifyDataSetChanged();
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                findViewById(R.id.scan_progress).setVisibility(View.GONE);
                setTitle(R.string.select_device);
                for (BluetoothDevice mDevice : devices) {
                    if (mDevice.getName() != null && mDevice.getName().startsWith("A+T")) {
                        mNewDevicesArrayAdapter.remove(mDevice.getName() + "\n" + mDevice.getAddress());
                        mNewDevicesArrayAdapter.add(mDevice.getName() + "\n" + mDevice.getAddress());
                        mNewDevicesArrayAdapter.notifyDataSetChanged();
                    }
                }
                if (mNewDevicesArrayAdapter.getCount() == 0) {
                    String noDevices = getResources().getText(R.string.none_found).toString();
                    mNewDevicesArrayAdapter.add(noDevices);
                    mNewDevicesArrayAdapter.notifyDataSetChanged();
                }
            }
        }
    };
}
