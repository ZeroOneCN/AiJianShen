package com.yuanlian.app;

import android.annotation.SuppressLint;
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
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

/**
 * 体脂秤蓝牙读取：移植自 AntAF 的 {@code weight/scale_reader.py}。
 *
 * <p>秤用私有服务 0xFFB0：写入 FFB1、通知 FFB2，无需配对。连上后周期性下发
 * 「时间 + 用户资料」包（0xD0）使能阻抗测量，随后收到 0xD5 体重包与 0xD6 阻抗包。
 * 帧长 20 字节，包头 0xAC，包类型在 data[18]。
 */
public class BleScale {

    private static final String TAG = "BleScale";

    private static final UUID SERVICE = uuid("0000ffb0");
    private static final UUID WRITE = uuid("0000ffb1");
    private static final UUID NOTIFY = uuid("0000ffb2");
    private static final UUID CCCD = uuid("00002902");

    private static final int TYPE_WEIGHT = 0xD5;
    private static final int TYPE_IMPEDANCE = 0xD6;
    private static final int TYPE_ACK = 0xD8;

    private static final int DEFAULT_DEVICE_TYPE = 0x29;
    private static final int MIN_GRAMS = -800;
    private static final int MAX_GRAMS = 250000;
    private static final long PROFILE_RESEND_MS = 15000L;
    private static final long SCAN_TIMEOUT_MS = 30000L;

    private static UUID uuid(String short16) {
        return UUID.fromString("0000" + short16.substring(4)
                + "-0000-1000-8000-00805f9b34fb");
    }

    /** 秤的回调，全部在主线程。 */
    public interface Listener {
        /** 扫描到的候选设备。 */
        void onFound(String name, String address, int rssi);

        /** 连接状态文字。 */
        void onState(String state);

        /** 实时重量（未稳定），用于滚动显示。 */
        void onLive(float kg);

        /** 稳定读数。impedance ≤ 0 表示这次没测到阻抗。 */
        void onStable(float kg, float impedance);

        void onError(String message);
    }

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());

    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic writeChar;
    private BluetoothGattCharacteristic notifyChar;

    private boolean scanning;
    private int deviceType = DEFAULT_DEVICE_TYPE;
    private float lastKg;
    private float impedance;
    private final List<String> seen = new ArrayList<>();

    public BleScale(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    // ---- 权限 ----

    public static boolean hasPermission(Context c) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return c.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED
                    && c.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return c.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** 需要申请的运行时权限列表。 */
    public static String[] permissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[]{
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT
            };
        }
        return new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION};
    }

    public static boolean bluetoothOn() {
        BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
        return a != null && a.isEnabled();
    }

    // ---- 扫描 ----

    @SuppressLint("MissingPermission")
    public void startScan() {
        BluetoothManager bm = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = bm == null ? null : bm.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            listener.onError("请先打开系统蓝牙");
            return;
        }
        if (!hasPermission(context)) {
            listener.onError("缺少蓝牙权限");
            return;
        }
        if (scanning) {
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            listener.onError("这台设备不支持蓝牙低功耗扫描");
            return;
        }
        seen.clear();
        scanning = true;
        listener.onState("正在搜索体重秤，踩一下秤把它唤醒…");

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        scanner.startScan(null, settings, scanCallback);

        main.postDelayed(() -> {
            if (scanning) {
                stopScan();
                listener.onState("没有找到体重秤。踩一下秤唤醒它，再点重新搜索。");
            }
        }, SCAN_TIMEOUT_MS);
    }

    @SuppressLint("MissingPermission")
    public void stopScan() {
        if (!scanning) {
            return;
        }
        scanning = false;
        try {
            if (scanner != null) {
                scanner.stopScan(scanCallback);
            }
        } catch (Exception e) {
            Log.w(TAG, "stopScan", e);
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (result == null || result.getDevice() == null) {
                return;
            }
            BluetoothDevice d = result.getDevice();
            String name = d.getName();
            if (name == null) {
                return;
            }
            // 秤的设备名以 AFU 开头；没有名字的设备（多为其他外设）直接忽略
            if (!name.startsWith("AFU")) {
                return;
            }
            if (!seen.contains(d.getAddress())) {
                seen.add(d.getAddress());
            }
            listener.onFound(name, d.getAddress(), result.getRssi());
        }

        @Override
        public void onScanFailed(int errorCode) {
            scanning = false;
            listener.onError("扫描失败（" + errorCode + "）");
        }
    };

    // ---- 连接 ----

    @SuppressLint("MissingPermission")
    public void connect(String address) {
        stopScan();
        if (adapter == null) {
            listener.onError("蓝牙不可用");
            return;
        }
        BluetoothDevice device;
        try {
            device = adapter.getRemoteDevice(address);
        } catch (Exception e) {
            listener.onError("地址无效");
            return;
        }
        listener.onState("正在连接体重秤…");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            gatt = device.connectGatt(context, false, gattCallback,
                    BluetoothDevice.TRANSPORT_LE);
        } else {
            gatt = device.connectGatt(context, false, gattCallback);
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                listener.onState("已连接，正在准备…");
                g.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                stopProfileLoop();
                listener.onState("连接已断开");
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            BluetoothGattService svc = g.getService(SERVICE);
            if (svc == null) {
                listener.onError("这个设备没有 0xFFB0 服务，可能不是支持的体脂秤");
                g.disconnect();
                return;
            }
            writeChar = svc.getCharacteristic(WRITE);
            notifyChar = svc.getCharacteristic(NOTIFY);
            if (writeChar == null || notifyChar == null) {
                listener.onError("秤的特征缺失，无法读取");
                g.disconnect();
                return;
            }
            g.setCharacteristicNotification(notifyChar, true);
            BluetoothGattDescriptor desc = notifyChar.getDescriptor(CCCD);
            if (desc != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                } else {
                    desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    g.writeDescriptor(desc);
                }
            }
            listener.onState("已连接，光脚站上秤保持不动");
            sendProfile();
            main.postDelayed(profileLoop, PROFILE_RESEND_MS);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g,
                                            BluetoothGattCharacteristic ch) {
            handle(ch.getValue());
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g,
                                            BluetoothGattCharacteristic ch, byte[] value) {
            handle(value);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int status) {
            Log.d(TAG, "cccd written: " + status);
        }
    };

    /** 每 15 秒重发一次资料包，保证秤唤醒后不会丢掉用户资料。 */
    private final Runnable profileLoop = new Runnable() {
        @Override
        public void run() {
            if (gatt == null) {
                return;
            }
            sendProfile();
            main.postDelayed(this, PROFILE_RESEND_MS);
        }
    };

    private void stopProfileLoop() {
        main.removeCallbacks(profileLoop);
    }

    // ---- 下发资料包（0xD0） ----

    @SuppressLint("MissingPermission")
    private void sendProfile() {
        if (gatt == null || writeChar == null) {
            return;
        }
        byte[] packet = buildProfile();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(writeChar, packet,
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            } else {
                writeChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                writeChar.setValue(packet);
                gatt.writeCharacteristic(writeChar);
            }
        } catch (Exception e) {
            Log.w(TAG, "sendProfile", e);
        }
    }

    /** 20 字节：AC | 型号 | 时间4B | 时区 | 单位 | 用户号 | 身高 | 档案体重2B | 年龄 | 性别 | 目标2B | 功能位 | 00 | D0 | 校验。 */
    byte[] buildProfile() {
        int height = Math.round(Store.heightCm(context));
        int age = Store.age(context);
        if (height <= 0) {
            height = 173;
        }
        if (age <= 0) {
            age = 25;
        }
        byte[] p = new byte[20];
        p[0] = (byte) 0xAC;
        p[1] = (byte) deviceType;
        long now = System.currentTimeMillis() / 1000L;
        p[2] = (byte) ((now >> 24) & 0xFF);
        p[3] = (byte) ((now >> 16) & 0xFF);
        p[4] = (byte) ((now >> 8) & 0xFF);
        p[5] = (byte) (now & 0xFF);
        int quarterHours = TimeZone.getDefault().getOffset(now * 1000L) / (15 * 60 * 1000);
        p[6] = (byte) (quarterHours & 0xFF);
        p[7] = 0;                       // 单位：0 = kg
        p[8] = 1;                       // 用户号
        p[9] = (byte) (height & 0xFF);
        p[10] = 0;                      // 档案体重（不填）
        p[11] = 0;
        p[12] = (byte) (age & 0xFF);
        p[13] = (byte) (Store.isMale(context) ? 1 : 2);
        p[14] = 0;                      // 目标体重（不填）
        p[15] = 0;
        p[16] = 0x03;                   // 功能位：使能阻抗测量
        p[17] = 0;
        p[18] = (byte) 0xD0;            // 包类型
        int sum = 0;
        for (int i = 2; i <= 18; i++) {
            sum += p[i] & 0xFF;
        }
        p[19] = (byte) (sum & 0xFF);
        return p;
    }

    // ---- 解析通知帧 ----

    private void handle(byte[] raw) {
        if (raw == null || raw.length < 20 || (raw[0] & 0xFF) != 0xAC) {
            return;
        }
        deviceType = raw[1] & 0xFF;
        int type = raw[18] & 0xFF;

        if (type == TYPE_ACK) {
            return;
        }
        float kg;
        boolean stable;
        int grams;
        int imp = 0;

        if (type == TYPE_WEIGHT) {
            int enc = be32(raw, 2);
            grams = enc & 0x3FFFF;
            int division = (enc & 0x1C0000) >> 18;
            kg = gunit(grams / 1000.0, division);
            stable = (enc & (1 << 31)) != 0;
        } else if (type == TYPE_IMPEDANCE) {
            int count = raw[2] & 0xFF;
            if (count <= 0 || 5 + count * 2 + 4 > raw.length) {
                return;
            }
            int channels = 0;
            for (int i = 0; i < count; i++) {
                int v = be16(raw, 4 + i * 2);
                if (v > 0) {
                    channels += v;
                }
            }
            if (channels > 0 && count > 0) {
                imp = Math.round(channels / (float) count);
            }
            int enc = be32(raw, 5 + count * 2);
            grams = enc & 0x3FFFF;
            int division = (enc & 0x1C0000) >> 18;
            kg = gunit(grams / 1000.0, division);
            stable = (enc & (1 << 31)) != 0;
        } else {
            return;
        }

        if (grams < MIN_GRAMS || grams > MAX_GRAMS) {
            return;
        }

        final float fkg = kg;
        final int fimp = imp;
        final boolean fstable = stable;
        main.post(() -> {
            if (!fstable) {
                if (Math.abs(fkg - lastKg) >= 0.05f) {
                    lastKg = fkg;
                    listener.onLive(fkg);
                }
                return;
            }
            lastKg = fkg;
            if (fimp > 0) {
                impedance = fimp;
            }
            listener.onLive(fkg);
            listener.onStable(fkg, impedance);
        });
    }

    private static int be16(byte[] d, int o) {
        return ((d[o] & 0xFF) << 8) | (d[o + 1] & 0xFF);
    }

    private static int be32(byte[] d, int o) {
        return ((d[o] & 0xFF) << 24) | ((d[o + 1] & 0xFF) << 16)
                | ((d[o + 2] & 0xFF) << 8) | (d[o + 3] & 0xFF);
    }

    /**
     * 按刻度精度把公斤值换算成秤的显示值（复刻协议里的 _gunit）。
     *
     * @param valueKg  原始公斤值
     * @param division 精度档位（编码 bit18-20）
     */
    static float gunit(double valueKg, int division) {
        switch (division) {
            case 0:
                return (intRound(valueKg * 1000.0) + 5) / 10 / 100.0f;
            case 1: {
                int raw = intRound(valueKg * 1000.0);
                if (raw % 10 == 9) {
                    raw += 10;
                }
                int scaled = raw / 10;
                if (scaled % 2 != 0) {
                    scaled += 1;
                }
                return scaled / 100.0f;
            }
            case 2: {
                int raw = (intRound(valueKg * 1000.0) + 20) / 10;
                int scaled = raw % 10 >= 5 ? (raw / 10) * 10 + 5 : (raw / 10) * 10;
                return scaled / 100.0f;
            }
            case 3:
                return (intRound(valueKg * 100.0) + 5) / 10 / 10.0f;
            case 4: {
                int raw = intRound(valueKg * 100.0);
                if (raw % 10 == 9) {
                    raw += 10;
                }
                int scaled = raw / 10;
                if (scaled % 2 != 0) {
                    scaled += 1;
                }
                return scaled / 10.0f;
            }
            default:
                return Math.round(valueKg * 100) / 100f;
        }
    }

    /** 复刻协议里的取整修正：小数部分 ≥ 0.9 时进位。 */
    private static int intRound(double v) {
        return ((int) (v * 10.0)) % 10 >= 9 ? (int) (v + 1.0) : (int) v;
    }

    /** 把地址显示成人看的短标签。 */
    public static String label(String address) {
        return address == null ? "" : address.toUpperCase(Locale.US);
    }

    @SuppressLint("MissingPermission")
    public void close() {
        stopScan();
        stopProfileLoop();
        if (gatt != null) {
            try {
                gatt.disconnect();
                gatt.close();
            } catch (Exception ignored) {
            }
            gatt = null;
        }
    }
}
