/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.wido;

import static android.util.Log.d;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Setup display fragments and ensure the device supports Bluetooth.
 */
public class MainActivity extends FragmentActivity {

    private static final int PERMISSION_REQUEST_CODE = 200;
    private static final String TAG = MainActivity.class.getSimpleName();
    private BluetoothAdapter mBluetoothAdapter;
    private BleService mbluetoothService;
    private BluetoothDevice device;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mbluetoothService = ((BleService.BleBinder) service).getService();
            if (mbluetoothService != null) {
                if (!mbluetoothService.initialize()) {
                    Log.e(TAG, "Unable to initialize Bluetooth");
                    finish();
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mbluetoothService = null;
        }
    };

    private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BleService.ACTION_GATT_CONNECTED.equals(action)) {
                setupConnectedFragments();
            } else if (BleService.ACTION_GATT_DISCONNECTED.equals(action)) {
                setupScanFragments();
            }
        }
    };

    private boolean checkPermission() {
        int result = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION);
        int result1 = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH);

        return result == PackageManager.PERMISSION_GRANTED && result1 == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH}, PERMISSION_REQUEST_CODE);
    }

    private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(MainActivity.this)
                .setMessage(message)
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0) {

                    boolean locationAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    boolean bluetoothAccepted = grantResults[1] == PackageManager.PERMISSION_GRANTED;

                    if (locationAccepted && bluetoothAccepted)
                        d(TAG, "Permission Granted, Now you can access location data and bluetooth.");
                    else {
                        d(TAG, "Permission Denied, You cannot access location data and/or bluetooth.");
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                                showMessageOKCancel("You need to allow access to both the permissions",
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH},
                                                            PERMISSION_REQUEST_CODE);
                                                }
                                            }
                                        });
                                return;
                            }
                        }
                    }
                }

                break;
            default:
                throw new IllegalStateException("Unexpected value: " + requestCode);
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle(R.string.activity_main_title);

        if (savedInstanceState == null) {
            mBluetoothAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE))
                    .getAdapter();
            // Is Bluetooth supported on this device?
            if (mBluetoothAdapter != null) {
                // Is Bluetooth turned on?
                if (mBluetoothAdapter.isEnabled()) {
                    // Are Bluetooth Advertisements supported on this device?
                    if (mBluetoothAdapter.isMultipleAdvertisementSupported()) {
                        if (!checkPermission()) {
                            Log.d(TAG, "Request permissions");
                            requestPermission();
                        }
                        // Everything is supported and enabled, load the fragments.
                        setupScanFragments();
                        bindService();
                    } else {
                        // Bluetooth Advertisements are not supported.
                        showErrorText(R.string.bt_ads_not_supported);
                    }
                } else {
                    // Prompt user to turn on Bluetooth (logic continues in onActivityResult()).
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, Constants.REQUEST_ENABLE_BT);
                }
            } else {
                // Bluetooth is not supported.
                showErrorText(R.string.bt_not_supported);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case Constants.REQUEST_ENABLE_BT:

                if (resultCode == RESULT_OK) {
                    // Bluetooth is now Enabled, are Bluetooth Advertisements supported on
                    // this device?
                    if (mBluetoothAdapter.isMultipleAdvertisementSupported()) {

                        // Everything is supported and enabled, load the fragments.
                        setupScanFragments();
                        bindService();
                    } else {
                        // Bluetooth Advertisements are not supported.
                        showErrorText(R.string.bt_ads_not_supported);
                    }
                } else {
                    // User declined to enable Bluetooth, exit the app.
                    Toast.makeText(this, R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    finish();
                }

            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    protected void onStop() {
        Log.d(TAG, "Stopping...");
        getApplicationContext().unbindService(serviceConnection);
        super.onStop();
    }

    private void setupScanFragments() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        ScannerFragment scannerFragment = new ScannerFragment();
        // Fragments can't access system services directly, so pass it the BluetoothAdapter
        scannerFragment.setBluetoothAdapter(mBluetoothAdapter);
        transaction.replace(R.id.scanner_fragment_container, scannerFragment);

        transaction.commit();
    }

    private void setupConnectedFragments() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        ConnectedFragment connectedFragment = new ConnectedFragment();
        connectedFragment.setBleService(mbluetoothService);
        transaction.replace(R.id.scanner_fragment_container, connectedFragment);
        transaction.commit();
    }

    private void bindService() {
        Intent gattServiceIntent = new Intent(this, BleService.class);
        bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void showErrorText(int messageId) {

        TextView view = findViewById(R.id.error_textview);
        view.setText(getString(messageId));
    }

    public void connect(BluetoothDevice dev) {
        String name = (dev.getName() == null) ? getString(R.string.no_name) : dev.getName();
        Log.d(TAG, "Connecting to device ");
        Log.d(TAG, name);
        Log.d(TAG, dev.getAddress());
        device = dev;

        if (mbluetoothService != null) {
            mbluetoothService.connect(dev, name);
            setTitle(name);
            Toast.makeText(this, R.string.status_connecting,
                    Toast.LENGTH_SHORT).show();
        }
        else {
            Log.e(TAG, "no bluetooth service");
        }
    }

    protected void onResume() {
        super.onResume();

        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mbluetoothService != null && device != null) {
            String name = (device.getName() == null) ? getString(R.string.no_name) : device.getName();
            final boolean result = mbluetoothService.connect(device, name);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(gattUpdateReceiver);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BleService.ACTION_GATT_DISCONNECTED);
        return intentFilter;
    }

}