# FastBlePractise

本文为 [FastBle](https://github.com/Jasonchenlijian/FastBle) 的使用教程，及部分补充内容（write 例子）。

关于 FastBle 的详细介绍，可参考 [Android BLE 开发详解和 FastBle 源码解析](https://www.jianshu.com/p/795bb0a08beb)。

# 一、概述
Android 4.3（API 级别 18）引入了内置平台支持低功耗蓝牙（BLE）的核心角色，并提供应用程序可用于发现设备，查询服务和传输信息的API。

与经典蓝牙（Classic Bluetooth）相比，低功耗蓝牙（BLE）旨在提供显着降低的功耗。这允许 Android 应用程序与具有更严格电源要求的 BLE 设备通信，例如接近传感器，心率监视器和健身设备。

![](http://7xlpfl.com1.z0.glb.clouddn.com/sherlockshi/2018-07-18-Android%20FastBle%E8%93%9D%E7%89%99%E5%BC%80%E5%8F%91%E6%B5%81%E7%A8%8B%20-1-.jpg)

（上图由 [ProcessOn](https://www.processon.com/i/5773e7a2e4b0913bfb63750c) 在线工具绘制）

# 二、配置 BLE 权限
### 1. 配置定位权限
由于 LE Beacons 通常与位置相关联。要在 BluetoothLeScanner 没有过滤器的情况下使用，您必须通过声明应用程序清单文件中的权限 `ACCESS_COARSE_LOCATION` 或 `ACCESS_FINE_LOCATION` 权限来请求用户的权限。没有这些权限，扫描将不会返回任何结果。

```xml
<!-- AndroidManifest.xml -->

<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

```java
// MainActivity.java

if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
    requestLocationPermission();
} else {
    initBluetooth();
}
```

### 2. 配置蓝牙权限
要在您的应用程序中使用蓝牙功能，您必须声明蓝牙权限 `BLUETOOTH`。您需要此权限才能执行任何蓝牙通信，例如请求连接，接受连接和传输数据。

如果您希望应用启动设备发现或操作蓝牙设置，则还必须声明 `BLUETOOTH_ADMIN` 权限。注意：如果您使用 `BLUETOOTH_ADMIN` 权限，则您还必须拥有 `BLUETOOTH` 权限。

```xml
<!-- AndroidManifest.xml -->

<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
```

如果您要声明您的应用仅适用于支持 BLE 的设备，请在应用的清单中包含以下 BLE 权限：

```xml
<!-- AndroidManifest.xml -->

<uses-feature android:name="android.hardware.bluetooth_le" android:required="true"/>
```

但是一般来说，蓝牙只是应用程序的一个小功能，所以我们只会在使用到蓝牙功能时，去检查 BLE 是否可用，所以并不需要上述 BLE 权限，取而代之的是使用时检查：

```java
// MainActivity.java

private void initBluetooth() {
    // Use this check to determine whether BLE is supported on the device.  Then you can
    // selectively disable BLE-related features.
    // 检查当前手机是否支持 ble 蓝牙
    if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
        Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
        return; // or finish();
    }

    ...
}
```

# 三、设置 BLE
如果不支持 BLE，则应优雅地禁用任何 BLE 功能。如果 BLE 受支持但已禁用，则可以请求用户启用蓝牙而无需离开您的应用程序。这个设置分两步完成，使用 BluetoothAdapter。

### 1. 获取 BluetoothAdapter
```java
// MainActivity.java

private BluetoothAdapter mBluetoothAdapter;
...
// Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
// BluetoothAdapter through BluetoothManager.
// 初始化 Bluetooth adapter, 通过蓝牙管理器得到一个参考蓝牙适配器(API必须在以上android4.3或以上和版本)
final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
mBluetoothAdapter = bluetoothManager.getAdapter();
```

### 2. 开启蓝牙
```java
// MainActivity.java

private static final int REQUEST_ENABLE_BT = 1;
...

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
```

```java
// MainActivity.java

@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == REQUEST_ENABLE_BT) {
        // User chose not to enable Bluetooth.
        if (resultCode == Activity.RESULT_CANCELED) {
            ToastUtils.showShort(R.string.please_open_bluetooth_to_use_ble_function);
        } else if (resultCode == Activity.RESULT_OK) {
            ToastUtils.showShort(R.string.start_scanning_bluetooth_device);

            // 扫描蓝牙设备
            ...
        }
    }
    super.onActivityResult(requestCode, resultCode, data);
}
```


# 四、初始化 BleManager
引入 FastBle：

```groovy
compile 'com.clj.fastble:FastBleLib:1.2.1'
```

```java
BleManager.getInstance().init(getApplication());
BleManager.getInstance()
        .enableLog(true)
        .setReConnectCount(1, 5000)
        .setConnectOverTime(20000)
        .setOperateTimeout(5000);
```

如果需要设备扫描规则：

```java
BleScanRuleConfig scanRuleConfig = new BleScanRuleConfig.Builder()
      .setServiceUuids(serviceUuids)
      .setDeviceName(true, names)
      .setDeviceMac(mac)
      .setAutoConnect(isAutoConnect)
      .setScanTimeOut(10000)
      .build();
BleManager.getInstance().initScanRule(scanRuleConfig);
```

# 五、扫描蓝牙设备
```java
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
```

# 六、连接蓝牙设备
连接蓝牙后，如果需要往指定的 Characteristic UUID 中，写入一定的开锁指令，可使用以下方法连接蓝牙、搜索指定 UUID：

```java
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

            // 搜索指定 Characteristic UUID，并 Write
            searchSpecifiedCharacteristicUuid(bleDevice, WRITE_CHARACTERISTIC_UUID);
        }

        @Override
        public void onDisConnected(boolean isActiveDisConnected, BleDevice bleDevice, BluetoothGatt gatt, int status) {
            progressDialog.dismiss();

            mBluetoothQuickAdapter.remove(mBluetoothQuickAdapter.getData().indexOf(bleDevice));

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
```

# 七、读取蓝牙设备的数据
读取例子可参考[官方 Demo](https://github.com/Jasonchenlijian/FastBle)。

# 八、写入数据到蓝牙设备
```java
private void writeCommand(BleDevice bleDevice, BluetoothGattCharacteristic characteristic) {
    // 此处的 input 是蓝牙设备厂商内置的命令格式
    byte[] input = GenOpenBytes(bleDevice.getName().substring(5), "13995534706", 0xffffffff, "0123456");

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
```

> PS：欢迎关注 [SherlockShi 个人博客](http://sherlockshi.github.io/)