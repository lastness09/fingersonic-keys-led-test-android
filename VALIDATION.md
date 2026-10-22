# Release validation

Version: **0.1.0-beta.1**.

## Build and host checks

Completed for this release:

- `testDebugUnitTest`: **8 tests passed**, zero failures or errors.
- `lintDebug`: **zero errors**, nine non-blocking warnings about English-only UI strings and API-version/backup attributes.
- `assembleDebug` and `assembleRelease`: successful with the pinned toolchain.
- Release APK signature: verified with APK Signature Schemes v2 and v3; 3072-bit RSA publisher key.
- APK: `com.fingersonic.keysledtest`, version code 1, Android API 26 minimum / API 35 target; release is not debuggable and requests no Internet permission.
- Release packaging excludes private repository VCS metadata, device firmware source and signing keys.
- APK size: **33,330 bytes**. SHA-256: `bfe7dd0b9f2fcb5d78d3a990d478c6159b81adced7ec9b72a39081c5b8ddfa8a`.
- Signing certificate SHA-256: `f06f69b489e3190abdf57e1ec3490759f9507b412def6ce561cfea7b9658af0e`.

Build results and APK checksums are recorded with the published download. Before publication, run the protocol unit tests, Android lint and both APK assembly tasks. Verify the signed APK with `apksigner verify --verbose --print-certs` and inspect the manifest to confirm the package name, version, API levels and absence of Internet permission.

Host protocol tests cover ordered/resynchronizing RGB transactions, all 24 independently addressed LEDs, all-off values, absence of color-save commands, every RGB quantization input, BLE timestamp wrapping, both follow note ranges, velocity-2 Note Off, clearing hints/audio and rejected invalid inputs.

## Device tests still required

- Install on a real Android phone; grant, deny and revoke permissions without a crash. Check Android 8-11 Location requirements and Android 12+ Nearby devices permissions.
- Scan, connect, disconnect, power-cycle Keys and reconnect. Reject a device without the correct MIDI characteristic. Confirm a busy/failed write never reports a completed lighting change.
- In Direct LEDs, verify red, green, blue, white, off, brightness zero and maximum at LED 1 and LED 24. Walk all 24 LEDs and verify ordering.
- In Follow, verify notes 48 and 71 address LEDs 1 and 24. Verify both Note On and velocity-2 Note Off.
- In Melody, repeat using notes 72 and 95. Confirm the firmware's octave/transposition behavior and the normal return path.
- In both follow modes, press and release a hinted physical key; verify white press feedback and restoration of the hint color.
- Check individual on/off, all on/off, stop during a walk and leaving the app during a Bluetooth transfer. No stuck hint notes should remain after a completed all-off command or a normal firmware disconnect.
- Verify colors are not saved across a reboot. Check the firmware restores the saved palette and its onboard lighting policy.
- Check small screens, large text, landscape, navigation insets and screen readers.

No physical Keys device or Android installation result is claimed until these checks are actually performed and recorded.
