package com.fingersonic.keysledtest;

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
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** All state and GATT operations are serialized on the main looper. */
@SuppressLint("MissingPermission")
public final class BleMidiClient {
    public interface Listener {
        void onState(String text, boolean ready, boolean busy);
        void onDevice(BluetoothDevice device, String name, int rssi);
        void onMidi(byte[] data);
    }
    public interface Completion { void done(boolean success); }

    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic midi;
    private boolean scanning;
    private boolean ready;
    private boolean waiting;
    private boolean busy;
    private int writeType;
    private int total;
    private String deviceName = "Keys";
    private Completion completion;
    private final ArrayDeque<int[]> queue = new ArrayDeque<>();
    private final Runnable scanTimeout = () -> stopScan(true);
    private final Runnable connectTimeout = () -> disconnect("Connection timed out. Try again.");
    private final Runnable writeTimeout = () -> disconnect("Write timed out. Reconnect before testing.");
    private final Runnable nextWrite = this::writeNext;

    public BleMidiClient(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        adapter = manager == null ? null : manager.getAdapter();
    }

    public BluetoothAdapter adapter() { return adapter; }
    public boolean isReady() { return ready; }
    public boolean isBusy() { return busy; }

    public void scan() {
        disconnect("Scanning for Fingersonic Keys...");
        try {
            if (adapter == null || !adapter.isEnabled()) {
                state("Turn on Bluetooth to scan.");
                return;
            }
            scanner = adapter.getBluetoothLeScanner();
            if (scanner == null) { state("Bluetooth LE scanner is unavailable."); return; }
            scanning = true;
            scanner.startScan(null, new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback);
            handler.postDelayed(scanTimeout, 12000);
        } catch (SecurityException | IllegalStateException ex) {
            scanning = false;
            state("Bluetooth scan unavailable. Check permissions and Bluetooth settings.");
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            handler.post(() -> {
                if (!scanning) return;
                try {
                    String name = result.getScanRecord() == null ? null : result.getScanRecord().getDeviceName();
                    if (name == null) name = result.getDevice().getName();
                    if (name == null) return;
                    String normalized = name.toLowerCase(Locale.ROOT);
                    if (normalized.contains("fingersonic") && normalized.contains("keys")) {
                        listener.onDevice(result.getDevice(), name, result.getRssi());
                    }
                } catch (SecurityException ex) { disconnect("Bluetooth permission was revoked."); }
            });
        }
        @Override public void onScanFailed(int code) {
            handler.post(() -> {
                if (!scanning) return;
                stopScan(false); state("Scan failed (" + code + "). Wait and try again.");
            });
        }
    };

    private void stopScan(boolean announce) {
        handler.removeCallbacks(scanTimeout);
        if (scanning && scanner != null) {
            try { scanner.stopScan(scanCallback); } catch (SecurityException | IllegalStateException ignored) { }
        }
        scanning = false;
        if (announce) state("Scan finished. Select Keys above, or scan again.");
    }

    public void connect(BluetoothDevice device, String name) {
        disconnect("Connecting to " + name + "...");
        deviceName = name;
        try {
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE);
            if (gatt == null) { state("Could not open a Bluetooth connection."); return; }
            handler.postDelayed(connectTimeout, 20000);
        } catch (SecurityException | IllegalArgumentException ex) {
            disconnect("Cannot connect. Check Nearby devices permission.");
        }
    }

    private final BluetoothGattCallback callback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt connection, int status, int newState) {
            handler.post(() -> {
                if (connection != gatt) return;
                if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                    disconnect(status == 0 ? "Disconnected" : "Connection failed (" + status + "). Try reconnecting.");
                } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                    state("Checking MIDI service...");
                    try {
                        if (!connection.discoverServices()) disconnect("Service discovery could not start.");
                    } catch (SecurityException ex) { disconnect("Bluetooth permission was revoked."); }
                }
            });
        }

        @Override public void onServicesDiscovered(BluetoothGatt connection, int status) {
            handler.post(() -> {
                if (connection != gatt) return;
                if (status != BluetoothGatt.GATT_SUCCESS) { disconnect("MIDI service discovery failed."); return; }
                BluetoothGattService service = connection.getService(LedProtocol.SERVICE);
                midi = service == null ? null : service.getCharacteristic(LedProtocol.MIDI);
                if (midi == null) { disconnect("This device does not expose the BLE MIDI service."); return; }
                int properties = midi.getProperties();
                if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
                    writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
                } else if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                    writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
                } else { disconnect("The MIDI characteristic is not writable."); return; }
                BluetoothGattDescriptor descriptor = midi.getDescriptor(LedProtocol.CCCD);
                boolean notify = (properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
                boolean indicate = (properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0;
                if (descriptor == null || (!notify && !indicate)) {
                    disconnect("The MIDI notification descriptor is missing."); return;
                }
                try {
                    if (!connection.setCharacteristicNotification(midi, true)) {
                        disconnect("Could not enable MIDI notifications."); return;
                    }
                    byte[] value = notify ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            : BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
                    boolean accepted;
                    if (Build.VERSION.SDK_INT >= 33) accepted = connection.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS;
                    else { descriptor.setValue(value); accepted = connection.writeDescriptor(descriptor); }
                    if (!accepted) disconnect("Could not subscribe to MIDI notifications.");
                } catch (SecurityException ex) { disconnect("Bluetooth permission was revoked."); }
            });
        }

        @Override public void onDescriptorWrite(BluetoothGatt connection, BluetoothGattDescriptor descriptor, int status) {
            handler.post(() -> {
                if (connection != gatt || !LedProtocol.CCCD.equals(descriptor.getUuid())) return;
                if (status != BluetoothGatt.GATT_SUCCESS) { disconnect("MIDI subscription failed."); return; }
                handler.removeCallbacks(connectTimeout);
                ready = true;
                state("Connected to " + deviceName + ". No test commands sent yet.");
            });
        }

        @Override public void onCharacteristicWrite(BluetoothGatt connection, BluetoothGattCharacteristic characteristic, int status) {
            handler.post(() -> {
                if (connection != gatt || !waiting || !LedProtocol.MIDI.equals(characteristic.getUuid())) return;
                handler.removeCallbacks(writeTimeout);
                waiting = false;
                if (status != BluetoothGatt.GATT_SUCCESS) { disconnect("Write failed (" + status + "). Reconnect to retry."); return; }
                queue.removeFirst();
                if (queue.isEmpty()) finishBatch();
                else {
                    state("Sending " + (total - queue.size()) + "/" + total + "...");
                    handler.postDelayed(nextWrite, 20);
                }
            });
        }

        @Override public void onCharacteristicChanged(BluetoothGatt connection, BluetoothGattCharacteristic characteristic, byte[] value) {
            deliver(connection, characteristic, value);
        }
        @Override public void onCharacteristicChanged(BluetoothGatt connection, BluetoothGattCharacteristic characteristic) {
            byte[] value = characteristic.getValue();
            if (value != null) deliver(connection, characteristic, value);
        }
    };

    private void deliver(BluetoothGatt connection, BluetoothGattCharacteristic characteristic, byte[] value) {
        if (!LedProtocol.MIDI.equals(characteristic.getUuid())) return;
        byte[] copy = Arrays.copyOf(value, value.length);
        handler.post(() -> { if (connection == gatt && ready) listener.onMidi(copy); });
    }

    public boolean send(List<int[]> messages, Completion onComplete) {
        if (!ready || busy || messages == null || messages.isEmpty()) return false;
        for (int[] message : messages) LedProtocol.packet(message, 0);
        for (int[] message : messages) queue.addLast(Arrays.copyOf(message, message.length));
        total = queue.size();
        completion = onComplete;
        busy = true;
        state("Sending 0/" + total + "...");
        writeNext();
        return true;
    }

    private void writeNext() {
        if (!ready || !busy || waiting || queue.isEmpty()) return;
        try {
            byte[] packet = LedProtocol.packet(queue.getFirst(), SystemClock.uptimeMillis());
            waiting = true;
            boolean accepted;
            if (Build.VERSION.SDK_INT >= 33) accepted = gatt.writeCharacteristic(midi, packet, writeType) == BluetoothStatusCodes.SUCCESS;
            else { midi.setWriteType(writeType); midi.setValue(packet); accepted = gatt.writeCharacteristic(midi); }
            if (!accepted) { disconnect("Bluetooth is busy or rejected the write. Reconnect to retry."); return; }
            handler.postDelayed(writeTimeout, 4000);
        } catch (SecurityException | IllegalArgumentException ex) {
            disconnect("Bluetooth write unavailable. Check permissions and reconnect.");
        }
    }

    private void finishBatch() {
        busy = false;
        Completion finished = completion;
        completion = null;
        state("Sent. Check the lights on Keys.");
        if (finished != null) finished.done(true);
    }

    public void disconnect(String reason) {
        stopScan(false);
        handler.removeCallbacks(connectTimeout);
        handler.removeCallbacks(writeTimeout);
        handler.removeCallbacks(nextWrite);
        ready = false; busy = false; waiting = false;
        queue.clear(); midi = null;
        BluetoothGatt old = gatt; gatt = null;
        if (old != null) {
            try { old.disconnect(); } catch (SecurityException ignored) { }
            try { old.close(); } catch (SecurityException ignored) { }
        }
        Completion interrupted = completion; completion = null;
        state(reason);
        if (interrupted != null) interrupted.done(false);
    }

    private void state(String text) { listener.onState(text, ready, busy); }
}
