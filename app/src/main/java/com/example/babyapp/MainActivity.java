package com.example.babyapp;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
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
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.LocationManager;
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

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

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
    private static final int REQUEST_CHECK_SETTINGS = 7;

    // BLE prarm
    public static BluetoothGatt mBluetoothGatt;
    public BluetoothAdapter bluetoothAdapter;
    public BluetoothLeScanner bluetoothLeScanner;
    private static final UUID myUUIDSevice = UUID.fromString("713d0001-503e-4c75-ba94-3148f18d941e");
    private static final UUID myUUIDChar_gyro = UUID.fromString("713d0002-503e-4c75-ba94-3148f18d941e");
    private static final UUID myUUIDChar_temp = UUID.fromString("713d0003-503e-4c75-ba94-3148f18d941e");
    private static final UUID myUUIDChar_ECG = UUID.fromString("713d0004-503e-4c75-ba94-3148f18d941e");
    private static final UUID myUUIDChar_sound = UUID.fromString("713d0005-503e-4c75-ba94-3148f18d941e");
    private static final UUID myUUIDChar_breath = UUID.fromString("713d0006-503e-4c75-ba94-3148f18d941e");
    private static final UUID[] myUUIDChars = {myUUIDChar_gyro, myUUIDChar_temp, myUUIDChar_ECG, myUUIDChar_sound, myUUIDChar_breath};
    // *** Layout param *******************************
    // Textview
    private TextView tv_status, tv_breath, tv_ECG, tv_temp, tv_gyro;
    // Button
    private Button btn_connect, btn_scan;
    // Listview
    private LeDeviceListAdapter leDeviceListAdapter;
    private AlertDialog dialog;
    //Scope
    private Scope2 mscope1, mscope2;

    // *** Passing data *************************************
    private Handler handler_scan;
    private HandlerThread handlerThreadECG = new HandlerThread("mhandlerthreadECG");
    private HandlerThread handlerThreadSound = new HandlerThread("mhandlerthreadECG");
    private String output;
    private Timer timerECG;
    private Timer timerSound;
    private Handler handlerECG;
    private Handler handlerSound;

    private ConcurrentLinkedQueue<String> dataBuffer;
    private ConcurrentLinkedQueue<Integer> ECG_buffer;
    private ConcurrentLinkedQueue<Integer> Sound_buffer;
    private FIR firLowpass;
    private DSP dsp;
    private int pitch = 0;
    private int roll = 0;
    private int breathCounter = 0;
    private int ambientTemp = 0;
    private int objectTemp = 0;


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
        tv_breath = findViewById(R.id.tv_breath);
        tv_gyro= findViewById(R.id.tv_gyro);
        tv_ECG = findViewById(R.id.tv_ECG);
        tv_temp = findViewById(R.id.tv_temp);
        // Button
        btn_connect = findViewById(R.id.btn_connect);
        // Scope
        mscope1 = (Scope2) findViewById(R.id.scope2_ECG);
        mscope2 = (Scope2) findViewById(R.id.scope2_Sound);
        mscope1.setDrawValue(4000, 4f);
        mscope2.setDrawValue(4000, 4f);
        mscope1.post(new Runnable() {
            @Override
            public void run() {
                mscope1.resume();
            }
        });
        mscope2.post(new Runnable() {
            @Override
            public void run() {
                mscope2.resume();
            }
        });
        // BLE
        final BluetoothManager bluetoothManager =
            (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        // ListView
//        listViewBleDevice = findViewById(R.id.listViewBleDevice);
        leDeviceListAdapter = new LeDeviceListAdapter(this);
//        listViewBleDevice.setAdapter(leDeviceListAdapter);
//        listViewBleDevice.setOnItemClickListener(onClickListView);// 指定事件 Method
        // Passing data
        handlerThreadECG.start();
        handlerThreadSound.start();
        handler_scan = new Handler(handlerThreadECG.getLooper());
        handlerECG = new Handler(handlerThreadECG.getLooper());
        handlerSound = new Handler(handlerThreadSound.getLooper());
        dataBuffer = new ConcurrentLinkedQueue<>();
        ECG_buffer = new ConcurrentLinkedQueue<>();
        Sound_buffer = new ConcurrentLinkedQueue<>();
        timerECG = new Timer();
        timerSound = new Timer();
        dsp = new DSP();
        firLowpass = new FIR(dsp.coffee);
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
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(30000);
        mLocationRequest.setFastestInterval(10000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
            .addLocationRequest(mLocationRequest);
        builder.setNeedBle(true);
        Task<LocationSettingsResponse> result =
            LocationServices.getSettingsClient(this).checkLocationSettings(builder.build());
        result.addOnCompleteListener(new OnCompleteListener<LocationSettingsResponse>() {
            @Override
            public void onComplete(Task<LocationSettingsResponse> task) {
                try {
                    LocationSettingsResponse response = task.getResult(ApiException.class);
                    // All location settings are satisfied. The client can initialize location
                    // requests here.
                } catch (ApiException exception) {
                    switch (exception.getStatusCode()) {
                        case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                            // Location settings are not satisfied. But could be fixed by showing the
                            // user a dialog.
                            try {
                                // Cast to a resolvable exception.
                                ResolvableApiException resolvable = (ResolvableApiException) exception;
                                // Show the dialog by calling startResolutionForResult(),
                                // and check the result in onActivityResult().
                                resolvable.startResolutionForResult(
                                    MainActivity.this,
                                    REQUEST_CHECK_SETTINGS);
                            } catch (IntentSender.SendIntentException e) {
                                // Ignore the error.
                            } catch (ClassCastException e) {
                                // Ignore, should be an impossible error.
                            }
                            break;
                        case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                            // Location settings are not satisfied. However, we have no way to fix the
                            // settings so we won't show the dialog.
                            break;
                    }
                }
            }
        });
    }

    // 接收Activity 的 Result
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        //final LocationSettingsStates states = LocationSettingsStates.fromIntent(intent);
        switch (requestCode) {
            case REQUEST_CHECK_SETTINGS:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        Log.d("onActivityResult", "GPS ON");
                        // All required changes were successfully made
                        break;
                    case Activity.RESULT_CANCELED:
                        //Log.d("onActivityResult","GPS Not ON");
                        finish();
                        // The user was asked to change settings, but chose not to
                        break;
                    default:
                        break;
                }
                break;
            case REQUEST_ENABLE_BT:
                final BluetoothManager bluetoothManager =
                    (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                bluetoothAdapter = bluetoothManager.getAdapter();
                bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                break;
        }
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
                    if (dialog != null && dialog.isShowing())
                        dialog.dismiss();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tv_status.setText("STATE_CONNECTED");
                        }
                    });
                    Log.d(TAG, "STATE_CONNECTED");
                    Log.d(TAG, "Attempting to start service discovery:" +
                        gatt.discoverServices());  // to discover the Services
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            btn_connect.setText("disconnect");
                        }
                    });
                    break;

                case BluetoothProfile.STATE_DISCONNECTED:
                    connect = false;
                    Log.d(TAG, "STATE_DISCONNECTED");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            btn_connect.setText("connect");
                        }
                    });
                    break;
                default:
                    break;
            }
        }

        private boolean[] setComplete = {false, false, false, false, false};//service 1 status

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
                for (UUID uuid : myUUIDChars) {
                    setNotify(gatt, myUUIDSevice, myUUIDChar_gyro);
                }

//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        //listViewBleDevice.setVisibility(View.GONE);
//                        //btn_connect.setEnabled(false);
//                    }
//                });
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            //super.onCharacteristicRead(gatt, characteristic, status);
            //Log.d(TAG,"onCharacteristicRead" + "  + status = " + status);
            //btn_connect.setEnabled(false);
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
                if (characteristic.getUuid().equals(myUUIDChar_ECG)) {
                    List<Integer> li = DataProcess(data);
                    for (int i : li) {
                        ECG_buffer.offer(i);
                    }
                    //Log.d("Read","myUUIDChar_ECG: " + listToString(li));
                } else if (characteristic.getUuid().equals(myUUIDChar_sound)) {
                    List<Integer> li = BitConvert(data, 10);
                    for (int i : li) {
                        Sound_buffer.offer(i);
                    }
                    //Log.d("Read","myUUIDChar_sound: " + listToString(li));

                } else if (characteristic.getUuid().equals(myUUIDChar_gyro)) {
                    List<Integer> li = BitConvert(data, 2);
                    pitch = li.get(0);
                    roll = li.get(1);
                    attitude();
                    Log.d("Read","myUUIDChar_gyro: " + listToString(li));

                } else if (characteristic.getUuid().equals(myUUIDChar_breath)) {
                    List<Integer> li = BitConvert(data, 1);
                    breathCounter = li.get(0);
                    breath();
                    //Log.d("Read","myUUIDChar_breath: " + listToString(li));

                } else if (characteristic.getUuid().equals(myUUIDChar_temp)) {
                    List<Integer> li = DataProcess(data);
                    ambientTemp = li.get(0);
                    objectTemp = li.get(1);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tv_temp.setText(ambientTemp + "/" + objectTemp + " 度");
                        }
                    });
                    //Log.d("Read","myUUIDChar_temp: " + listToString(li));
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            for (int i = 0; i < 5; i++) {
                if (!setComplete[i]) {
                    try {
                        Thread.sleep(100);
                        setComplete[i] = setNotify(gatt, myUUIDSevice, myUUIDChars[i]);
                        Log.d("onDescriptorWrite", "setting " + i);
                    } catch (Exception e) {
                        Log.d("onDescriptorWrite", "fail at setting " + i);
                    }
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

    private String listToString(List<Integer> li) {
        String s = "";
        for (int i : li) {
            s = s + i + ",";
        }
        return s;
    }

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
    private int SCAN_PERIOD = 3000;

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
                    Log.d("scan", "stop scan");
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

    private void setTimerTask() {
        timerECG.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                handlerECG.post(ECG_event);
            }
        }, 1, 2);
        timerSound.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                handlerSound.post(Sound_event);
            }
        }, 1, 2);
    }

    public void Connect(View view) {
        if (connect) {
            if (mBluetoothGatt == null) {
                return;
            }
            mBluetoothGatt.close();
            mBluetoothGatt = null;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    tv_status.setText("STATE_DISCONNECTED");
                    btn_connect.setText("connect");
                }
            });
            //listViewBleDevice.setVisibility(View.VISIBLE);
            //btn_connect.setEnabled(true);
            connect = false;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View popup = inflater.inflate(R.layout.scan_dialog, null);
        ListView listViewBleDevice_d = popup.findViewById(R.id.listViewBleDevice_d);
        listViewBleDevice_d.setAdapter(leDeviceListAdapter);
        listViewBleDevice_d.setOnItemClickListener(onClickListView);// 指定事件 Method
        Button btn_scan_d = popup.findViewById(R.id.btn_scan_d);
        btn_scan = btn_scan_d;
        builder.setView(popup);
        dialog = builder.create();
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
                mScanning = false;
                scanEnable = true;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btn_scan.setText("Scan");
                    }
                });
                bluetoothLeScanner.stopScan(scanCallback);
                Log.d("scan", "stop scan");
            }
        });
        btn_scan_d.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Scan(v);
            }
        });
        dialog.show();
        if (!mScanning)
            Scan(view);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handlerThreadECG.quit();
    }

    public static String fmt(float d) {
        return String.format("%.2f", d);
    }// float to

    public List<Integer> DataProcess(byte[] data) {// byte to int
        List<Integer> result = new ArrayList<>();
        for (byte b : data) {
            int r = b & 0xFF;
            result.add(r);
        }
        return result;
    }

    public List<Integer> BitConvert(byte[] data, int data_num) {// 16bits little endian to int
        List<Integer> result = new ArrayList<>();
        if (data_num * 2 != data.length) {
            Log.d("BitConvert", data.length + "");
            for (int i = 0; i < data_num; i++)
                result.add(0);
            return result;
        }
        for (int i = 0; i < data.length; i += 2) {
            int b1 = data[i] & 0x0FF;
            int b2 = data[i + 1] & 0x0FF;
            int convert_value = 0;
            convert_value = b1 + ((b2 & 0x7f) << 8);
            if ((b2 & 0x80) != 0)// the signed bit
                convert_value -= Math.pow(2, 15);
            result.add(convert_value);
        }
        return result;
    }

    private Runnable ECG_event = new Runnable() {
        @Override
        public void run() {
            //Log.d("still run","run !");
            synchronized (ECG_buffer) {
                if (!ECG_buffer.isEmpty()) {
                    Object o;
                    if ((o = ECG_buffer.poll()) != null) {
                        int data = ((int) o) << 2;
                        float output = firLowpass.getOutputSample(data);
                        mscope1.addNewData(output);
                    } else if (ECG_buffer.size() > 1000) {
                        //mscope3.resume();
                        Log.d("ECG_buffer", "Scope restart + size is " + ECG_buffer.size());
                    }
                    //Log.d("ECG_buffer", "the size is : " + ECG_buffer.size());
                }
            }
        }
    };

    private Runnable Sound_event = new Runnable() {
        @Override
        public void run() {
            //Log.d("still run","run !");
            synchronized (Sound_buffer) {
                if (!Sound_buffer.isEmpty()) {
                    Object o;
                    if ((o = Sound_buffer.poll()) != null) {
                        int data = ((int) o);
                        //float output = firLowpass.getOutputSample(data);
                        mscope2.addNewData(data);
                    } else if (Sound_buffer.size() > 1000) {
                        //mscope3.resume();
                        Log.d("Sound_buffer", "Scope restart + size is " + Sound_buffer.size());
                    }
                    //Log.d("ECG_buffer", "the size is : " + ECG_buffer.size());
                }
            }
        }
    };
    private void attitude(){
        String output = "";
        if( 90 < pitch && pitch < 270)
            output = "趴臥";
        else
            output = "正躺";
        TextSendingRunnable textSendingRunnable = new TextSendingRunnable(tv_gyro,output);
        runOnUiThread(textSendingRunnable);
    }
    public class TextSendingRunnable implements Runnable{
        TextView tv;
        Boolean set = false;
        String data = "";
        public TextSendingRunnable(TextView tv,String data){
            this.tv = tv;
            this.data = data;
            set = true;
        }
        @Override
        public void run() {
            if(set){
                tv.setText(data);
            }
        }
    }
    private void breath(){

    }
}
