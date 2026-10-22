package com.fingersonic.keysledtest;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@SuppressLint("MissingPermission") // Scan entry checks runtime permissions before accessing Bluetooth.
public final class MainActivity extends Activity implements BleMidiClient.Listener {
    private static final int BG = Color.rgb(16, 24, 39);
    private static final int PANEL = Color.rgb(29, 41, 59);
    private static final int ACCENT = Color.rgb(84, 224, 207);
    private static final int MUTED = Color.rgb(175, 188, 205);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Button[] tiles = new Button[24];
    private final Button[] modes = new Button[3];
    private final int[] lastColors = new int[24];
    private final boolean[] known = new boolean[24];
    private final List<Button> commands = new ArrayList<>();
    private final Set<String> devices = new HashSet<>();
    private final SeekBar[] sliders = new SeekBar[4];
    private final TextView[] values = new TextView[4];
    private BleMidiClient client;
    private LinearLayout deviceList;
    private TextView status;
    private TextView selectedLabel;
    private TextView modeHelp;
    private TextView preview;
    private Button scanButton;
    private Button walkButton;
    private Button stopButton;
    private int selected;
    private int mode;
    private boolean initialized;
    private boolean walking;
    private boolean stopRequested;
    private int walkIndex;
    private int walkColor;
    private final Runnable nextStep = this::walkStep;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(24));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(dp(20) + insets.getSystemWindowInsetLeft(), dp(20) + insets.getSystemWindowInsetTop(),
                    dp(20) + insets.getSystemWindowInsetRight(), dp(24) + insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        scroll.addView(root);
        setContentView(scroll);
        root.addView(label("FINGERSONIC / ANDROID", 11, ACCENT));
        root.addView(label("Keys LED Test", 30, Color.WHITE));
        root.addView(label("24 lights. One simple hardware check.", 14, MUTED));

        section(root, "1 / Connect Keys");
        status = label("Power on Keys, then scan. Close other apps connected to its Bluetooth MIDI.", 13, MUTED);
        root.addView(status);
        LinearLayout connection = row(root);
        scanButton = button("Scan for Keys", ACCENT, () -> prepareScan());
        addWeighted(connection, scanButton);
        addWeighted(connection, button("Disconnect", PANEL, () -> client.disconnect("Disconnected")));
        deviceList = new LinearLayout(this);
        deviceList.setOrientation(LinearLayout.VERTICAL);
        root.addView(deviceList);

        section(root, "2 / Choose a test mode");
        LinearLayout modeRow = row(root);
        String[] names = {"Direct LEDs", "Follow", "Melody"};
        for (int i = 0; i < 3; i++) {
            final int choice = i;
            modes[i] = button(names[i], PANEL, () -> selectMode(choice));
            addWeighted(modeRow, modes[i]);
        }
        modeHelp = label("Choose a mode after connecting. Follow: C3-B4. Melody: C5-B6.", 12, MUTED);
        root.addView(modeHelp);

        section(root, "3 / Select a light");
        root.addView(label("Tap a tile to select it. Tiles show the last completed command, not a device readback.", 12, MUTED));
        for (int r = 0; r < 4; r++) {
            LinearLayout line = row(root);
            for (int col = 0; col < 6; col++) {
                final int index = r * 6 + col;
                Button tile = button("", PANEL, () -> { selected = index; updateTiles(); });
                tile.setTextSize(10);
                tile.setPadding(0, dp(5), 0, dp(5));
                tile.setMinWidth(0);
                tile.setMinimumWidth(0);
                tile.setMinHeight(dp(58));
                tiles[index] = tile;
                addWeighted(line, tile);
            }
        }
        selectedLabel = label("", 16, Color.WHITE);
        root.addView(selectedLabel);

        section(root, "4 / Pick a test color");
        preview = label("", 16, Color.WHITE);
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(dp(12), dp(16), dp(12), dp(16));
        root.addView(preview);
        String[] channels = {"Red", "Green", "Blue", "Brightness"};
        int[] defaults = {80, 220, 255, 25};
        for (int i = 0; i < 4; i++) {
            final int channel = i;
            values[i] = label("", 12, MUTED);
            root.addView(values[i]);
            SeekBar slider = new SeekBar(this);
            slider.setMax(i == 3 ? 100 : 255);
            slider.setProgress(defaults[i]);
            slider.setContentDescription(channels[i]);
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                    values[channel].setText(channels[channel] + ": " + value + (channel == 3 ? "%" : ""));
                    updatePreview();
                }
                @Override public void onStartTrackingTouch(SeekBar bar) { }
                @Override public void onStopTrackingTouch(SeekBar bar) { }
            });
            sliders[i] = slider;
            values[i].setText(channels[i] + ": " + defaults[i] + (i == 3 ? "%" : ""));
            root.addView(slider, new LinearLayout.LayoutParams(-1, dp(44)));
        }
        LinearLayout presets = row(root);
        String[] presetNames = {"Red", "Green", "Blue", "White"};
        int[] presetColors = {0xff0000, 0x00ff00, 0x0000ff, 0xffffff};
        for (int i = 0; i < 4; i++) {
            final int rgb = presetColors[i];
            Button preset = button(presetNames[i], PANEL, () -> {
                sliders[0].setProgress((rgb >> 16) & 255);
                sliders[1].setProgress((rgb >> 8) & 255);
                sliders[2].setProgress(rgb & 255);
            });
            preset.setTextSize(11);
            addWeighted(presets, preset);
        }
        root.addView(label("Sliders preview only. Press Set & on to send. Brightness starts at 25%.", 12, MUTED));
        LinearLayout single = row(root);
        command(single, "Set & on", ACCENT, () -> setOne(true));
        command(single, "Selected off", PANEL, () -> setOne(false));
        LinearLayout all = row(root);
        command(all, "All on", PANEL, () -> setAll(true, null));
        command(all, "All off", PANEL, () -> setAll(false, null));
        LinearLayout walk = row(root);
        walkButton = button("Run 24-key walk", ACCENT, this::startWalk);
        addWeighted(walk, walkButton);
        stopButton = button("Stop test", PANEL, this::stopWalk);
        addWeighted(walk, stopButton);
        root.addView(label("Tests use temporary colors. Nothing is saved to Keys. Reboot to reload its saved palette. Leaving this app disconnects Bluetooth.", 12, MUTED));
        root.addView(label("Follow modes reset transpose/octave and suspend chord mode. Hint notes use velocity 2 and can make quiet sounds. All off also stops hint audio.", 12, MUTED));
        root.addView(label("Official Keys firmware with the lighting CC interface is required. Neon Orbit and other custom firmware need their own compatible implementation.", 12, MUTED));
        root.addView(label("0.1.0-beta.1 / Bluetooth device testing pending", 11, MUTED));

        client = new BleMidiClient(this, this);
        updatePreview();
        updateTiles();
        updateButtons();
    }

    private void prepareScan() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            status.setText("This phone does not support Bluetooth LE."); return;
        }
        String[] required = Build.VERSION.SDK_INT >= 31
                ? new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}
                : new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
        List<String> missing = new ArrayList<>();
        for (String permission : required) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) missing.add(permission);
        }
        if (!missing.isEmpty()) {
            requestPermissions(missing.toArray(new String[0]), 10); return;
        }
        BluetoothAdapter adapter = client.adapter();
        if (adapter == null) { status.setText("Bluetooth is unavailable."); return; }
        if (!adapter.isEnabled()) {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 11); return;
        }
        if (Build.VERSION.SDK_INT <= 30) {
            android.location.LocationManager location = getSystemService(android.location.LocationManager.class);
            if (location != null && !location.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                    && !location.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                status.setText("Android 8-11 requires Location services for BLE scans. Turn Location on, then scan again.");
                return;
            }
        }
        devices.clear(); deviceList.removeAllViews();
        client.scan();
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code != 10) return;
        for (int result : results) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                status.setText("Bluetooth permission is required. If denied permanently, enable it in Android app settings.");
                return;
            }
        }
        if (results.length > 0) prepareScan();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 11 && resultCode == RESULT_OK) prepareScan();
    }

    @Override public void onDevice(BluetoothDevice device, String name, int rssi) {
        if (!devices.add(device.getAddress())) return;
        Button choice = button(name + " / " + rssi + " dBm", PANEL, () -> {
            deviceList.removeAllViews(); client.connect(device, name);
        });
        deviceList.addView(choice);
    }

    @Override public void onMidi(byte[] data) {
        // Subscription enables the MIDI session; notifications are not LED-state acknowledgements.
    }

    @Override public void onState(String text, boolean ready, boolean busy) {
        status.setText(text);
        if (!ready) {
            initialized = false; walking = false; stopRequested = true;
            modeHelp.setText("Choose a mode after connecting. Follow: C3-B4. Melody: C5-B6.");
            handler.removeCallbacks(nextStep);
            Arrays.fill(known, false);
            updateTiles();
        }
        updateButtons();
    }

    private void selectMode(int choice) {
        if (!client.isReady() || client.isBusy() || walking) return;
        client.send(LedProtocol.followMode(choice), success -> {
            if (!success) return;
            mode = choice; initialized = true;
            Arrays.fill(known, false);
            modeHelp.setText(choice == 0 ? "Direct LEDs / temporary RGB preview, without MIDI notes."
                    : choice == 1 ? "Follow / MIDI notes 48-71 (C3-B4), hint velocity 2."
                    : "Melody / MIDI notes 72-95 (C5-B6), hint velocity 2.");
            updateTiles(); updateButtons();
        });
    }

    private List<int[]> onMessages(int index, int rgb) {
        List<int[]> messages = LedProtocol.one(index, rgb);
        if (mode != 0) messages.add(LedProtocol.hint(mode, index, true));
        return messages;
    }

    private List<int[]> offMessages(int index) {
        if (mode == 0) return LedProtocol.one(index, 0);
        List<int[]> messages = new ArrayList<>();
        messages.add(LedProtocol.hint(mode, index, false));
        return messages;
    }

    private void setOne(boolean on) {
        int index = selected;
        int rgb = on ? chosenColor() : 0;
        client.send(on ? onMessages(index, rgb) : offMessages(index), success -> {
            if (success) { lastColors[index] = rgb; known[index] = true; updateTiles(); }
        });
    }

    private void setAll(boolean on, Runnable after) {
        int rgb = on ? chosenColor() : 0;
        int[] colors = new int[24]; Arrays.fill(colors, rgb);
        List<int[]> messages;
        if (mode != 0 && !on) messages = LedProtocol.clearHints(mode);
        else {
            messages = LedProtocol.all(colors);
            if (mode != 0) for (int i = 0; i < 24; i++) messages.add(LedProtocol.hint(mode, i, true));
        }
        client.send(messages, success -> {
            if (!success) return;
            Arrays.fill(lastColors, rgb); Arrays.fill(known, true); updateTiles();
            if (after != null) after.run();
        });
    }

    private void startWalk() {
        if (!initialized || !client.isReady() || client.isBusy() || walking) return;
        walking = true; stopRequested = false; walkIndex = 0; walkColor = chosenColor();
        updateButtons();
        setAll(false, this::walkStep);
    }

    private void walkStep() {
        if (!walking || !client.isReady()) return;
        if (stopRequested || walkIndex >= 24) { finishWalk(); return; }
        int index = walkIndex++;
        List<int[]> messages = new ArrayList<>();
        if (index > 0) messages.addAll(offMessages(index - 1));
        messages.addAll(onMessages(index, walkColor));
        client.send(messages, success -> {
            if (!success || !walking) return;
            if (index > 0) lastColors[index - 1] = 0;
            lastColors[index] = walkColor; known[index] = true; selected = index;
            updateTiles();
            handler.postDelayed(nextStep, stopRequested ? 0 : 500);
        });
    }

    private void stopWalk() {
        stopRequested = true;
        handler.removeCallbacks(nextStep);
        updateButtons();
        if (walking && !client.isBusy()) finishWalk();
    }

    private void finishWalk() {
        if (!client.isReady() || client.isBusy()) return;
        setAll(false, () -> {
            walking = false;
            status.setText(stopRequested ? "Test stopped. All-off commands sent." : "24-key walk complete. All-off commands sent.");
            updateButtons();
        });
    }

    private int chosenColor() {
        if (sliders[3] == null) return 0;
        float brightness = sliders[3].getProgress() / 100f;
        return (Math.round(sliders[0].getProgress() * brightness) << 16)
                | (Math.round(sliders[1].getProgress() * brightness) << 8)
                | Math.round(sliders[2].getProgress() * brightness);
    }

    private void updatePreview() {
        if (sliders[3] == null) return;
        int rgb = chosenColor();
        preview.setBackground(background(Color.BLACK | rgb, 0));
        preview.setText(String.format(Locale.US, "Test color / #%06X", rgb));
        preview.setTextColor(luminance(rgb) > 145 ? Color.BLACK : Color.WHITE);
    }

    private void updateTiles() {
        if (selectedLabel == null) return;
        for (int i = 0; i < 24; i++) {
            String state = !known[i] ? "?" : lastColors[i] == 0 ? "OFF" : "ON";
            tiles[i].setText(String.format(Locale.US, "%02d\n%s", i + 1, state));
            int color = known[i] && lastColors[i] != 0 ? Color.BLACK | lastColors[i] : PANEL;
            tiles[i].setBackground(background(color, i == selected ? ACCENT : BG));
            tiles[i].setTextColor(luminance(color) > 145 ? Color.BLACK : Color.WHITE);
            tiles[i].setContentDescription("LED " + (i + 1) + ", last sent " + state + (i == selected ? ", selected" : ""));
        }
        selectedLabel.setText(String.format(Locale.US, "Selected / LED %02d%s", selected + 1,
                initialized && mode != 0 ? " / MIDI " + ((mode == 1 ? 48 : 72) + selected) : ""));
    }

    private void updateButtons() {
        boolean ready = client != null && client.isReady();
        boolean busy = client != null && client.isBusy();
        for (Button command : commands) {
            command.setEnabled(ready && !busy && initialized && !walking);
            command.setAlpha(command.isEnabled() ? 1f : .45f);
        }
        for (int i = 0; i < 3; i++) {
            modes[i].setEnabled(ready && !busy && !walking);
            modes[i].setBackground(background(initialized && mode == i ? ACCENT : PANEL, 0));
            modes[i].setTextColor(initialized && mode == i ? BG : Color.WHITE);
            modes[i].setAlpha(modes[i].isEnabled() ? 1f : .45f);
        }
        scanButton.setEnabled(!busy && !walking);
        walkButton.setEnabled(ready && !busy && initialized && !walking);
        stopButton.setEnabled(walking && !stopRequested);
        scanButton.setAlpha(scanButton.isEnabled() ? 1f : .45f);
        walkButton.setAlpha(walkButton.isEnabled() ? 1f : .45f);
        stopButton.setAlpha(stopButton.isEnabled() ? 1f : .45f);
    }

    @Override protected void onStop() {
        walking = false; stopRequested = true;
        handler.removeCallbacks(nextStep);
        if (client != null) client.disconnect("Disconnected. Scan again to continue testing.");
        super.onStop();
    }

    private void command(LinearLayout row, String text, int color, Runnable action) {
        Button button = button(text, color, action);
        commands.add(button); addWeighted(row, button);
    }
    private void section(LinearLayout root, String text) {
        TextView heading = label(text, 17, Color.WHITE);
        heading.setPadding(0, dp(24), 0, dp(8));
        heading.setTypeface(null, Typeface.BOLD); root.addView(heading);
    }
    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color);
        view.setPadding(0, dp(4), 0, dp(8)); return view;
    }
    private LinearLayout row(LinearLayout root) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(row); return row;
    }
    private Button button(String text, int color, Runnable action) {
        Button button = new Button(this); button.setText(text); button.setAllCaps(false); button.setTextSize(12);
        button.setTextColor(color == ACCENT ? BG : Color.WHITE); button.setBackground(background(color, 0));
        button.setMinHeight(dp(48)); button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setOnClickListener(view -> action.run()); return button;
    }
    private void addWeighted(LinearLayout row, View view) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(dp(2), dp(4), dp(2), dp(4)); row.addView(view, params);
    }
    private GradientDrawable background(int color, int border) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(10));
        if (border != 0) shape.setStroke(dp(2), border); return shape;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private int luminance(int rgb) {
        return (int) (((rgb >> 16) & 255) * .299 + ((rgb >> 8) & 255) * .587 + (rgb & 255) * .114);
    }
}
