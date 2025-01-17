package com.example.android.wido;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.List;

public class BleService extends Service {
    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    private static final String TAG = BleService.class.getSimpleName();
    private final Binder binder = new BleBinder();
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothDevice mDevice;
    private String device_name;
    private BluetoothGattCharacteristic mDistanceChar;
    private BluetoothGattCharacteristic mMaxDistanceChar;
    private BluetoothGattCharacteristic mBatteryChar;

    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "successfully connected to GATT Server");
                broadcastUpdate(ACTION_GATT_CONNECTED);
                // Attempts to discover services after successful connection.
                bluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "disconnected from GATT Server");
                broadcastUpdate(ACTION_GATT_DISCONNECTED);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                getCharacteristics();
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead (BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            int myValue = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            String myUuid = characteristic.getUuid().toString();
            Log.d(TAG, "Received value " + myValue + " for characteristic " + myUuid);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, myUuid, myValue);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            int myValue = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            String myUuid = characteristic.getUuid().toString();
            broadcastUpdate(ACTION_DATA_AVAILABLE, myUuid, myValue);
        }

        public void onCharacteristicWrite (BluetoothGatt gatt,
                                           BluetoothGattCharacteristic characteristic,
                                           int status) {

        }

    };

    private void getCharacteristics()
    {
        BluetoothGattService mService = bluetoothGatt.getService(Constants.Distance_Service_UUID.getUuid());
        mDistanceChar = mService.getCharacteristic(Constants.Distance_Characteristic_UUID.getUuid());
        mMaxDistanceChar = mService.getCharacteristic(Constants.MaxDistance_Characteristic_UUID.getUuid());
        mService = bluetoothGatt.getService(Constants.Battery_Service_UUID.getUuid());
        mBatteryChar = mService.getCharacteristic(Constants.Battery_Characteristic_UUID.getUuid());
    }

    private boolean getCharacteristicValue(BluetoothGattCharacteristic characteristic) {
        if (characteristic == null) {
            Log.e(TAG, "characteristic is null");
            return false;
        }
        if (bluetoothGatt != null)
            return bluetoothGatt.readCharacteristic(characteristic);
        else return false;
    }

    private boolean setCharacteristicValue(BluetoothGattCharacteristic characteristic, int value) {
        if (characteristic == null || bluetoothGatt == null) {
            return false;
        }
        if (characteristic.setValue(value, BluetoothGattCharacteristic.FORMAT_UINT8, 0)) {
            return bluetoothGatt.writeCharacteristic(characteristic);
        } else {
            return false;
        }
    }

    public boolean getCurrentDistance() {
        return getCharacteristicValue(mDistanceChar);
    }

    public boolean getMaxDistance() {
        Log.d(TAG, "getMaxDistance called");
        return getCharacteristicValue(mMaxDistanceChar);
    }

    public boolean getBatteryLevel() {
        return getCharacteristicValue(mBatteryChar);
    }

    public BleService() {
    }

    public List<BluetoothGattService> getSupportedGattServices() {
        if (bluetoothGatt == null) return null;
        return bluetoothGatt.getServices();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public boolean initialize() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }
        return true;
    }

    public boolean connect(final BluetoothDevice device, String name) {
        if (bluetoothAdapter == null || device == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified device.");
            return false;
        }
        try {
            // connect to the GATT server on the device
            Log.d(TAG, "Connect to GATT server");
            bluetoothGatt = device.connectGatt(this, false, bluetoothGattCallback);
            mDevice = device;
            device_name = name;
            return true;
        } catch (IllegalArgumentException exception) {
            Log.w(TAG, "Device not found with provided address.  Unable to connect.");
            return false;
        }
    }

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        intent.putExtra("Device", device_name);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action, final String characteristic, final int value) {
        final Intent intent = new Intent(action);
        intent.putExtra("Device", device_name);
        intent.putExtra("Characteristic", characteristic);
        intent.putExtra("Value", value);
        sendBroadcast(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        close();
        return super.onUnbind(intent);
    }

    private void close() {
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }

    public Boolean setMaxDistance(int value) {
        Log.d(TAG, "setMaxDistance: " + value);
        return setCharacteristicValue(mMaxDistanceChar, value);
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class BleBinder extends Binder {
        BleService getService() {
            // Return this instance of LocalService so clients can call public methods
            return BleService.this;
        }
    }
}