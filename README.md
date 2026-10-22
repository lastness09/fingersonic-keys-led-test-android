# Keys LED Test for Android

A small, open-source Bluetooth test tool for **Fingersonic Keys**. Test the 24 LEDs individually, choose RGB colors, turn one or all lights off, and check both follow modes. This repository contains the Android app only. The official device firmware remains closed source.

**Version 0.1.0-beta.1 is a test build. Android installation and physical Keys/BLE interoperability still require device testing.** A successful build does not verify physical LED behavior.

- [Download APK, source ZIP and checksums](https://github.com/lastness09/fingersonic-keys-led-test-android/releases/tag/v0.1.0-beta.1)
- [Keys developer guide](https://github.com/lastness09/fingersonic-keys-devkit)
- [Control protocol](PROTOCOL.md) and [device test checklist](VALIDATION.md)

## Requirements

- Android 8.0 or later, with Bluetooth LE.
- Fingersonic Keys running official firmware with the lighting Control Change interface described in `PROTOCOL.md`.
- Close other phone apps or DAWs connected to Keys before scanning. The app discovers devices whose advertised name contains both `Fingersonic` and `Keys`, then verifies the BLE MIDI service before enabling controls.

Neon Orbit and other custom firmware are not assumed to implement these lighting commands. No firmware is installed or updated by this app. Power, battery-level and charging management remain with the instrument's independent hardware; this tool has no battery or shutdown controls.

## Install and use

1. Download the APK from this repository's Releases page. Android may ask you to allow installation from the browser or file manager that opens it. This is a directly distributed test APK, not a Play Store listing.
2. Turn on Keys. Open **Keys LED Test**, press **Scan for Keys** and grant the requested Bluetooth permissions. On Android 8-11, BLE scanning also requires Location permission and enabled Location services. No location is collected or transmitted.
3. Select the discovered Keys device. Choose **Direct LEDs**, **Follow** or **Melody** after connecting. Connecting alone does not send lighting commands.
4. Select one of the 24 tiles. Adjust RGB and brightness, or choose Red, Green, Blue or White. Sliders only change the preview. Press **Set & on** to send the selected color and turn that LED on. Press **Selected off** to turn it off.
5. **All on** applies the current test color to every LED. **All off** clears every LED; in follow modes it also sends hint Note Off messages and stops hint audio.
6. **Run 24-key walk** lights LEDs 1-24 in sequence, with a short hold on each. **Stop test** clears the lights after the current ordered Bluetooth transfer finishes.
7. Leaving the app disconnects Bluetooth. Reconnect and choose a mode to continue. Reboot Keys to reload its saved palette.

Start at the default 25% brightness. Tiles show the app's last completed send, not measured physical state or an acknowledgement that the firmware supports the command. A connection to BLE MIDI alone cannot verify firmware compatibility. Mode changes and disconnection can restore the instrument's own lighting behavior.

## The two follow tests

| Mode | Firmware command | Hint notes | LED mapping |
| --- | --- | --- | --- |
| Follow | CC85 = 1 | MIDI 48-71, C3-B4 | Note 48 = LED 1; note 71 = LED 24 |
| Melody | CC85 = 2 | MIDI 72-95, C5-B6 | Note 72 = LED 1; note 95 = LED 24 |

Both hint Note On and Note Off use **velocity 2** on channel 1, which the inspected official interface uses for lighting hints. These notes may also be audible at low volume. This app tests sending hints; it is not a song player or an automatic lesson-scoring app. The two ranges are fixed test mappings, regardless of a DAW's octave-name convention.

After sending a hint with **Set & on**, press the corresponding physical key. The official firmware should show its white press feedback, then restore the hint color when the key is released. **Selected off** releases that hint. The walking test sends hints in order; it does not wait for a correct key press.

Entering follow modes stops active notes, resets transpose, sets the corresponding octave state and temporarily suspends chord mode. Returning to Direct LEDs sends CC85 = 0, which exits follow mode, stops active notes and resets the octave state. Use the app while testing, rather than during a performance.

## Temporary color behavior

The app updates the device's **RAM palette** and previews each selected LED. It never sends the color-save command (`CC86 = 89`) or the persistent lighting-mode command (`CC90`). Color components are quantized to the device's 7-bit control range: 0, 2, 4, ... 252, 255. A single RGB transaction can briefly show intermediate R/G/B values.

In Direct LEDs, turning a light off temporarily sets its RAM color to black. Reapply a color to turn it on again, or reboot to reload the saved device palette. Physical key presses, onboard effects and mode changes may overwrite a preview. Disconnecting is not a guaranteed restoration of all RAM colors.

## Build from source

Pinned tools: **JDK 21**, **Gradle 8.9**, **Android Gradle Plugin 8.7.3**, **Android platform 35**, **Build Tools 35.0.0**. Java source compatibility is 17. The app has no external runtime dependencies; JUnit 4.13.2 is used for host protocol tests.

Install the Android SDK, set `ANDROID_HOME` to it (or create an untracked `local.properties` containing `sdk.dir=...`), and install `platforms;android-35` and `build-tools;35.0.0` through Android Studio's SDK Manager. Review and accept any SDK licenses in your own development environment.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

On Windows, use `gradlew.bat` with the same tasks. The installable debug build is `app/build/outputs/apk/debug/app-debug.apk`. `assembleRelease` produces an unsigned release APK for signing with your own private key; the publisher's signing key is not included. A build signed with another key cannot replace the published APK in place.

The distributed APK is signed for direct installation. The source ZIP excludes build caches, SDK/JDK tools, signing keys, credentials and proprietary device source. See `VALIDATION.md` for the precise checks completed for each release.

## Privacy and lifecycle

No Internet permission, analytics, account, cloud service, advertising or telemetry. Bluetooth addresses are held in memory only while showing scan results. Bluetooth scan and connection permissions are requested at runtime. The app serializes GATT writes, waits for each Android write result, paces consecutive writes, times out failed transfers and discards pending commands on disconnect. Partial transfers are reported as failures; the next RGB transaction begins by resetting the color-command state machine.

## License

MIT for this original Android application and its documentation. This license does not apply to Fingersonic's proprietary firmware, hardware designs, trademarks or third-party build tools.
