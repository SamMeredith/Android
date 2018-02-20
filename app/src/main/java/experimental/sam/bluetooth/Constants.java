package experimental.sam.bluetooth;

/**
 *  Defines several constants used between {@link BluetoothConnect} and the UI.
 */
class Constants {

    //Message types sent from the BluetoothConnect Handler
    static final int MESSAGE_STATE_CHANGE = 1;
    static final int MESSAGE_READ = 2;
    static final int MESSAGE_WRITE = 3;
    static final int MESSAGE_DEVICE_NAME = 4;
    static final int ERROR_DISCONNECT = 5;
    static final int MESSAGE_NAK = 6;
    static final int ERROR_SOCKET = 7;
    static final int UPLOAD_PROGRESS = 8;
    static final int CONNECTION_FAIL = 9;

    //Numerical constants
    static final int CONNECTION_MAX = 5;

    //Key names received from the BluetoothConnect Handler
    static final String DEVICE_NAME = "device_name";

    //File IO Constants
    static final byte ACK = 0x06;
    static final byte NAK = 0x15;

    //State constants
    static final int STATE_NONE = 10;
    static final int STATE_CONNECTING = 12;
    static final int STATE_CONNECTED = 13;

    //Setup Constants
    static final int REQUEST_CONNECT_DEVICE = 21;
    static final int REQUEST_ENABLE_BT = 22;
}

