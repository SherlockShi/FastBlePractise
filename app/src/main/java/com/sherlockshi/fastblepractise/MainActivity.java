package com.sherlockshi.fastblepractise;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.Toast;

import com.blankj.utilcode.constant.PermissionConstants;
import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.PermissionUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleGattCallback;
import com.clj.fastble.callback.BleScanCallback;
import com.clj.fastble.callback.BleWriteCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;

import java.util.ArrayList;
import java.util.List;

@SuppressLint("NewApi")
public class MainActivity extends AppCompatActivity {

    // 由蓝牙设备厂商提供
    private String WRITE_CHARACTERISTIC_UUID = "0000ff01-0000-1000-8000-008012345678";

    private static final int REQUEST_ENABLE_BT = 1;

    private ImageView img_loading;
    private Animation operatingAnim;
    private ProgressDialog progressDialog;

    private BluetoothAdapter mBluetoothAdapter;
    private RecyclerView rvBluetoothDevice;
    private BaseQuickAdapter<BleDevice, BaseViewHolder> mBluetoothQuickAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                        && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestLocationPermission();
                } else {
                    initBluetooth();
                }
            }
        });

        initLoading();
        
        initRecyclerView();

        initBleManager();
    }

    private void initLoading() {
        img_loading = (ImageView) findViewById(R.id.img_loading);
        operatingAnim = AnimationUtils.loadAnimation(this, R.anim.rotate);
        operatingAnim.setInterpolator(new LinearInterpolator());
        progressDialog = new ProgressDialog(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        showConnectedDevice();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        BleManager.getInstance().disconnectAllDevice();
        BleManager.getInstance().destroy();
    }

    private void initBleManager() {
        BleManager.getInstance().init(getApplication());
        BleManager.getInstance()
                .enableLog(true)
                .setReConnectCount(1, 5000)
                .setConnectOverTime(20000)
                .setOperateTimeout(5000);
    }

    private void showConnectedDevice() {
        List<BleDevice> deviceList = BleManager.getInstance().getAllConnectedDevice();
        mBluetoothQuickAdapter.setNewData(new ArrayList<BleDevice>());
        for (BleDevice bleDevice : deviceList) {
            mBluetoothQuickAdapter.addData(bleDevice);
        }
    }

    private void initRecyclerView() {
        rvBluetoothDevice = findViewById(R.id.rv_bluetooth_device);

        rvBluetoothDevice.setLayoutManager(new LinearLayoutManager(MainActivity.this));

        mBluetoothQuickAdapter = new BaseQuickAdapter<BleDevice, BaseViewHolder>(R.layout.rv_item_bt_device) {

            @Override
            protected void convert(BaseViewHolder helper, BleDevice item) {
                // 设备名称
                helper.setText(R.id.tv_device_name, item.getName());

                // 设备地址
                helper.setText(R.id.tv_device_address, item.getMac());
            }
        };

        mBluetoothQuickAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                final BleDevice device = mBluetoothQuickAdapter.getData().get(position);
                if (device == null) {
                    return;
                }

                connect(device);
            }
        });

        rvBluetoothDevice.setAdapter(mBluetoothQuickAdapter);
    }

    private void requestLocationPermission() {
        PermissionUtils.permission(PermissionConstants.LOCATION)
                .rationale(new PermissionUtils.OnRationaleListener() {
                    @Override
                    public void rationale(final ShouldRequest shouldRequest) {
                        DialogHelper.showRationaleDialog(shouldRequest, R.string.please_agree_to_location_permission_to_use_ble_function);
                    }
                })
                .callback(new PermissionUtils.FullCallback() {
                    @Override
                    public void onGranted(List<String> permissionsGranted) {
                        LogUtils.d(permissionsGranted);

                        initBluetooth();
                    }

                    @Override
                    public void onDenied(List<String> permissionsDeniedForever,
                                         List<String> permissionsDenied) {
                        if (!permissionsDeniedForever.isEmpty()) {
                            DialogHelper.showOpenAppSettingDialog();
                        }
                        LogUtils.d(permissionsDeniedForever, permissionsDenied);
                        ToastUtils.showShort(R.string.please_agree_to_location_permission_to_use_ble_function);
                    }
                })
                .theme(new PermissionUtils.ThemeCallback() {
                    @Override
                    public void onActivityCreate(Activity activity) {
                    }
                })
                .request();
    }

    private void initBluetooth() {
        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        // 检查当前手机是否支持 ble 蓝牙
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            return; // or finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        // 初始化 Bluetooth adapter, 通过蓝牙管理器得到一个参考蓝牙适配器(API必须在以上android4.3或以上和版本)
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        // 检查设备上是否支持蓝牙
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            return; // or finish();
        }

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        // 检查蓝牙是否开启
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                return;
            }
        }

        startScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            // User chose not to enable Bluetooth.
            if (resultCode == Activity.RESULT_CANCELED) {
                ToastUtils.showShort(R.string.please_open_bluetooth_to_use_ble_function);
            } else if (resultCode == Activity.RESULT_OK) {
                ToastUtils.showShort(R.string.start_scanning_bluetooth_device);

                mBluetoothQuickAdapter.setNewData(new ArrayList());

                // 扫描蓝牙设备
                startScan();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void startScan() {
        BleManager.getInstance().scan(new BleScanCallback() {
            @Override
            public void onScanStarted(boolean success) {
                mBluetoothQuickAdapter.setNewData(new ArrayList<BleDevice>());
                img_loading.startAnimation(operatingAnim);
                img_loading.setVisibility(View.VISIBLE);
            }

            @Override
            public void onLeScan(BleDevice bleDevice) {
                super.onLeScan(bleDevice);
            }

            @Override
            public void onScanning(BleDevice bleDevice) {
                mBluetoothQuickAdapter.addData(bleDevice);
            }

            @Override
            public void onScanFinished(List<BleDevice> scanResultList) {
                img_loading.clearAnimation();
                img_loading.setVisibility(View.INVISIBLE);
            }
        });
    }

    private void connect(final BleDevice bleDevice) {
        BleManager.getInstance().connect(bleDevice, new BleGattCallback() {
            @Override
            public void onStartConnect() {
                progressDialog.show();
            }

            @Override
            public void onConnectFail(BleDevice bleDevice, BleException exception) {
                img_loading.clearAnimation();
                img_loading.setVisibility(View.INVISIBLE);
                progressDialog.dismiss();
                Toast.makeText(MainActivity.this, getString(R.string.connect_fail), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
                progressDialog.dismiss();

//                mBluetoothQuickAdapter.addData(bleDevice);

                // TODO: 2018/7/16 搜索指定 Characteristic UUID，并 Write
                searchSpecifiedCharacteristicUuid(bleDevice, WRITE_CHARACTERISTIC_UUID);
            }

            @Override
            public void onDisConnected(boolean isActiveDisConnected, BleDevice bleDevice, BluetoothGatt gatt, int status) {
                progressDialog.dismiss();

                if (mBluetoothQuickAdapter.getData().contains(bleDevice)) {
                    mBluetoothQuickAdapter.remove(mBluetoothQuickAdapter.getData().indexOf(bleDevice));
                }

                Toast.makeText(MainActivity.this, getString(R.string.active_disconnected), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void searchSpecifiedCharacteristicUuid(BleDevice bleDevice, String characteristicUuid) {
        String uuid = "";
        BluetoothGatt bluetoothGatt = BleManager.getInstance().getBluetoothGatt(bleDevice);

        List<BluetoothGattService> serviceList = bluetoothGatt.getServices();
        for (BluetoothGattService service : serviceList) {
            List<BluetoothGattCharacteristic> characteristicList = service.getCharacteristics();

            for(BluetoothGattCharacteristic characteristic : characteristicList) {
                uuid = characteristic.getUuid().toString();
                if (!TextUtils.isEmpty(uuid) && TextUtils.equals(uuid, characteristicUuid)) {
                    writeCommand(bleDevice, characteristic);
                    return;
                }
            }
        }
    }

    private void writeCommand(BleDevice bleDevice, BluetoothGattCharacteristic characteristic) {
        // 此处的 input 是蓝牙设备厂商内置的命令格式
        byte[] input = GenOpenBytes(bleDevice.getName().substring(5));

        BleManager.getInstance().write(
                bleDevice,
                characteristic.getService().getUuid().toString(),
                characteristic.getUuid().toString(),
                input,
                new BleWriteCallback() {

                    @Override
                    public void onWriteSuccess(final int current, final int total, final byte[] justWrite) {
                        Toast.makeText(MainActivity.this, R.string.write_success, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onWriteFailure(final BleException exception) {
                        Toast.makeText(MainActivity.this, R.string.write_failure, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private byte[] GenOpenBytes(String mac) {
        byte[] b = new byte[78];
        // ...
        return b;
    }
}
