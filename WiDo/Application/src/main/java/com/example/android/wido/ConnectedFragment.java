package com.example.android.wido;

import static android.support.v4.os.HandlerCompat.postDelayed;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import com.example.android.wido.R;

public class ConnectedFragment extends Fragment {
    private static final String TAG = ConnectedFragment.class.getSimpleName();
    private Boolean flag_view = false;
    private Boolean flag_services = false;
    private final Handler handler = new Handler();
    private TextView t_distance;
    private TextView t_battery;
    private SeekBar t_max_distance;

    private BleService mService;

    private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, "Received " + action);
            if (BleService.ACTION_GATT_SERVICES_DISCOVERED.equals(action))  {
                flag_services = true;
                if (flag_view) {
                    fetchData();
                }
            } else if (BleService.ACTION_DATA_AVAILABLE.equals(action))  {
                String myUuid = intent.getStringExtra("Characteristic");
                int myValue = intent.getIntExtra("Value", 0);
                if (myUuid.equals(Constants.Distance_Characteristic_UUID.toString())) {
                    Log.d(TAG, "New distance value " + myValue);
                    t_distance.setText(" " + myValue);
                    mService.getMaxDistance();
                } else if (myUuid.equals(Constants.MaxDistance_Characteristic_UUID.toString())) {
                    Log.d(TAG, "New maximum distance" + myValue);
                    t_max_distance.setProgress(myValue);
                    mService.getBatteryLevel();
                } else if (myUuid.equals(Constants.Battery_Characteristic_UUID.toString())) {
                    Log.d(TAG, "New battery value " + myValue);
                    t_battery.setText(" " + myValue + " %");
                }

            }
        }
    };

    public ConnectedFragment() {
        // Required empty public constructor
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        flag_view = true;
        if (flag_services) {
            fetchData();
        }
    }

    private void fetchData() {
        mService.getCurrentDistance();
        final int delay = 5000;
        final int period = 5000;
        final Runnable r = new Runnable() {
            public void run() {
                fetchData();
            }
        };
        handler.postDelayed(r, delay);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_connected, container, false);
        t_distance = (TextView) v.findViewById(R.id.t_distance);
        t_battery = (TextView) v.findViewById(R.id.t_battery);
        t_max_distance = (SeekBar) v.findViewById(R.id.seekBar);
        t_max_distance.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {
                Log.d(TAG, "ProgessChanged: " + progress + "("+fromUser+")");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Do nothing
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mService.setMaxDistance(seekBar.getProgress());
            }
        });
        return v;
    }

    public void setBleService(BleService bluetoothService) {
        mService = bluetoothService;
    }

    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called");
        getContext().registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter());
      }

      public void onPause() {
        super.onPause();
        getContext().unregisterReceiver(gattUpdateReceiver);
      }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BleService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}