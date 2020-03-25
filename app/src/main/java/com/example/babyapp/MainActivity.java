package com.example.babyapp;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MainActivity extends AppCompatActivity {
    // APP setting
    private static final String TAG = "MainActivity";
    private static final int REQUEST_ENABLE_BT = 5;
    private static final int REQUEST_LOCATION_PERMISSION = 6;
    // BLE prarm
    public static BluetoothGatt mBluetoothGatt;
    public BluetoothAdapter bluetoothAdapter;
    public BluetoothLeScanner bluetoothLeScanner;

    //    private static final UUID myUUIDSevice_temp = UUID.fromString("713d0006-503e-4c75-ba94-3148f18d941e");
//    private static final UUID myUUIDChar_temp = UUID.fromString("713d0005-503e-4c75-ba94-3148f18d941e");
    private static final UUID myUUIDSevice_temp = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private static final UUID myUUIDChar_temp = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    // Listview
    private ListView listViewBleDevice;
    private LeDeviceListAdapter leDeviceListAdapter;
    private LayoutInflater inflater;
    // *** Layout param *******************************
    // Textview
    private TextView tv_status;
    // Button
    private Button btn_scan, btn_cancel;
    // *** Passing data *************************************
    private Handler handler_scan;
    private HandlerThread handlerThread = new HandlerThread("mhandlerthread");
    private ConcurrentLinkedQueue<String> dataBuffer;
    private String output;
    private Timer timer;
    private Handler handlerTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initialization();
        setTimerTask();
    }

    private void initialization() {
        statusCheck();
        // TextView
        tv_status = findViewById(R.id.tv_status);
        // Button
        btn_cancel = findViewById(R.id.btn_cancel);
        btn_scan = findViewById(R.id.btn_scan);
        // BLE
        final BluetoothManager bluetoothManager =
            (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        // ListView
        inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        listViewBleDevice = findViewById(R.id.listViewBleDevice);
        leDeviceListAdapter = new LeDeviceListAdapter(inflater);
        listViewBleDevice.setAdapter(leDeviceListAdapter);
        listViewBleDevice.setOnItemClickListener(onClickListView);// 指定事件 Method
        // Passing data
        handlerThread.start();
        handler_scan = new Handler(handlerThread.getLooper());
        handlerTimer = new Handler(handlerThread.getLooper());
        dataBuffer = new ConcurrentLinkedQueue<>();
        timer = new Timer();
    }

    private void statusCheck() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "ble_not_supported", Toast.LENGTH_SHORT).show();
            finish();
        }
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        if (ContextCompat.checkSelfPermission(MainActivity.this,
            Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "location permission granted");
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale
                (this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                new AlertDialog.Builder(this)
                    .setTitle("Permission needed")
                    .setMessage("This app need location permission to search ble device!")
                    .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ActivityCompat.requestPermissions(MainActivity.this, new
                                    String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                                REQUEST_LOCATION_PERMISSION);
                        }
                    })
                    .setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .create().show();
            } else {
                ActivityCompat.requestPermissions(this, new
                        String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
            }
        }

        final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            buildAlertMessageNoGps();
        }
    }

    //如果沒有GPS時
    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        //建立彈出視窗，問是否要開啟GPS
        builder.setMessage("Your GPS seems to be disabled, do you want to enable it?")
            .setCancelable(false)
            .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                public void onClick(final DialogInterface dialog, final int id) {
                    startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                }
            })
            .setNegativeButton("No", new DialogInterface.OnClickListener() {
                public void onClick(final DialogInterface dialog, final int id) {
                    dialog.cancel();
                    finish();
                }
            });
        final AlertDialog alert = builder.create();
        alert.show();
    }


    // 新增 onClickListView 事件
    private AdapterView.OnItemClickListener onClickListView = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Animation clickAnimation = new AlphaAnimation(0.3f, 1.0f);
            clickAnimation.setDuration(500);
            view.startAnimation(clickAnimation);
            Toast.makeText(MainActivity.this, "點選第 " + (position + 1) + " 個 \nName：" +
                    leDeviceListAdapter.getDevice(position).getName()
                    + "\nAddress：" + leDeviceListAdapter.getDevice(position).getAddress()
                , Toast.LENGTH_SHORT).show();

            //連接開始!!
            BluetoothDevice device = leDeviceListAdapter.getDevice(position);
            mBluetoothGatt = device.connectGatt(MainActivity.this, false, mGattcallback);

        }
    };

    BluetoothGattCallback mGattcallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    connect = true;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tv_status.setText("STATE_CONNECTED");
                        }
                    });
                    Log.d(TAG, "STATE_CONNECTED");
                    Log.d(TAG, "Attempting to start service discovery:" +
                        gatt.discoverServices());  // to discover the Services
                    break;

                case BluetoothProfile.STATE_DISCONNECTED:
                    connect = false;
                    Log.d(TAG, "STATE_DISCONNECTED");
                    break;
                default:
                    break;
            }
        }

        private boolean s1 = false;//service 1 status


        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tv_status.setText("SERVICE_DISCOVERED");
                    }
                });
                Log.d(TAG, "STATE_CONNECTED  SERVICE_DISCOVERED");
                List<BluetoothGattService> bleservices = gatt.getServices();
                for (BluetoothGattService s : bleservices) {
                    String c = s.getUuid().toString();
                    Log.d(TAG, "receive " + c + "\n");
                }
                //set the notification to receive the data when they update
                s1 = setNotify(gatt, myUUIDSevice_temp, myUUIDChar_temp);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        listViewBleDevice.setVisibility(View.GONE);
                        btn_scan.setEnabled(false);
                    }
                });
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            //super.onCharacteristicRead(gatt, characteristic, status);
            //Log.d(TAG,"onCharacteristicRead" + "  + status = " + status);
            btn_scan.setEnabled(false);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tv_status.setText("Characteristic Read");
                    }
                });
                Log.d(TAG, "STATE_CONNECTED Characteristic Read");
                // For all other profiles, writes the data formatted in HEX.
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            //super.onCharacteristicChanged(gatt, characteristic);
            final byte[] data = characteristic.getValue();
            //Log.d("current thread", Thread.currentThread().getName());
            if (data != null && data.length > 0) {
                if(characteristic.getUuid().equals(myUUIDChar_temp))
                {
                    final StringBuilder stringBuilder = new StringBuilder(data.length);
                    for (byte byteChar : data) {
                        stringBuilder.append(String.format("%02X", byteChar));
                    }
                    String s = stringBuilder.toString();
                    output = hexToAscii(s);
                    Log.d(TAG,output);
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (!s1) {
                try {
                    Thread.sleep(100);
                    s1 = setNotify(gatt, myUUIDSevice_temp, myUUIDChar_temp);
                } catch (Exception e) {
                }
            }
        }

        private boolean setNotify(BluetoothGatt gatt, UUID UUID_server, UUID UUID_char) {
            BluetoothGattService mBluetoothGattService = gatt.getService(UUID_server);
            BluetoothGattCharacteristic characteristic = mBluetoothGattService.getCharacteristic(UUID_char);
            // Setup the config of notify
            String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
            gatt.setCharacteristicNotification(characteristic, true);
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            if (gatt.writeDescriptor(descriptor)) {
                Log.d(TAG, "UUID: " + UUID_char.toString() + " successed");
                return true;//if successful
            } else
                return false;//if failed
        }
    };
    // Ref  https://www.baeldung.com/java-convert-hex-to-ascii
    private String hexToAscii(String hexStr) {
        StringBuilder output = new StringBuilder("");
        //一分為2  轉換成 Ascii code
        for (int i = 0; i < hexStr.length() - 1; i += 2) {
            String str = hexStr.substring(i, i + 2);//計算用 subString
            dataBuffer.offer(str);
        }
        //Log.d(TAG,"the data size is " + dataBuffer.size());
        if (dataBuffer.size() < 100) //字數太少
            return "";
        while (!dataBuffer.isEmpty()) {
            char checkChar = (char) Integer.parseInt(dataBuffer.poll(), 16);
            if (checkChar == '\n') {
                break;
            } else {
                output.append(checkChar);
            }
        }
        //int length = output.length();
        //output.delete(length - 1, length);
        return output.toString();
    }

    private boolean scanEnable = true;
    private boolean connect = false;
    private boolean mScanning = false;
    private int SCAN_PERIOD = 10000;


    public void Scan(View view) {
            if (scanEnable) {
                // Stops scanning after a pre-defined scan period.
                handler_scan.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mScanning = false;
                        scanEnable = true;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                btn_scan.setText("Scan");
                            }
                        });
                        bluetoothLeScanner.stopScan(scanCallback);
                        Log.d("scan","stop scan");
                    }
                }, SCAN_PERIOD);
                // Start to scan
                mScanning = true;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btn_scan.setText("Scaning");
                    }
                });
                bluetoothLeScanner.startScan(scanCallback);
                scanEnable = false;
            } else {
                mScanning = false;
                scanEnable = true;
                bluetoothLeScanner.stopScan(scanCallback);
            }
    }
    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (result.getDevice().getName() != null && result.getDevice().getName().length() > 0) {
                super.onScanResult(callbackType, result);
                leDeviceListAdapter.addDevice(result.getDevice());
                leDeviceListAdapter.notifyDataSetChanged();
                Log.d("scan", "scaning");
            }
        }
    };
    public void Cancel(View view) {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
        tv_status.setText("STATE_DISCONNECTED");
        listViewBleDevice.setVisibility(View.VISIBLE);
        btn_scan.setEnabled(true);
        connect = false;
    }
    private long lastTime = 0;
    private int counter = 0;
    private List<Integer> times = new ArrayList<>();
    private double avg = 0;
    private void setTimerTask(){
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if(counter == 100002) {
                    for (int i:times
                         ) {
                        avg += i;
                    }
                    Log.d("Timer:", "" + (avg/100000));
                    counter++;
                }
                else if(counter <= 100001) {
                    long t = System.currentTimeMillis() - lastTime;
                    int i = (int)t;
                    if(i < 50) {
                        times.add(i);
                    }
                    counter++;
                }
                lastTime = System.currentTimeMillis();
//                handlerTimer.post(
//                    new Runnable() {
//                        @Override
//                        public void run() {
//                            try {
//                                Thread.sleep(20);
//                            }catch (Exception e){
//                                e.printStackTrace();
//                            }
//                        }
//                    }
//                );
            }
        },1, 2);
    }


}
