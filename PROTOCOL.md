# Keys LED test control interface

This document describes public wire messages needed by an independent phone controller. It contains no device firmware implementation. Compatibility was checked against the locally available official Keys interface; physical interoperability of this Android build is pending.

## Transport

- BLE MIDI service: `03b80e5a-ede8-4b33-a751-6ce34ec4c700`
- MIDI characteristic: `7772e5db-3868-4112-a1a9-f2669d106bf3`
- Notification CCCD: `00002902-0000-1000-8000-00805f9b34fb`
- MIDI channel 1: status `B0` for CC, `90` for Note On, `80` for Note Off.
- The app uses one complete three-byte MIDI message per five-byte BLE packet. Bytes: `80 | ((timestamp >> 7) & 3F)`, `80 | (timestamp & 7F)`, MIDI status, data 1, data 2. Timestamp is monotonic milliseconds modulo 8192. This fits the default ATT MTU.

The controller first enables notifications and writes the CCCD. It prefers write-with-response when supported, otherwise uses write-without-response, and serializes the Android write callbacks. A successful GATT write is not an application-level LED-state acknowledgement. No lighting-state readback is defined by this app.

## RGB preview

For zero-based LED index 0-23, send these CC messages in this exact order:

```text
CC86 = 90       Exit any interrupted color transaction
CC86 = 88       Enter color editing
CC86 = index    Select LED 0-23
CC87 = red      0-127
CC88 = green    0-127
CC89 = blue     0-127
CC86 = 90       Exit color editing
```

The device previews after each component update. Multiple LEDs can be addressed between one enter/exit pair: repeat index, red, green, blue for each LED. Each 7-bit component maps to twice its value, except 127 maps to 255. The app chooses the nearest representable intensity after applying its brightness slider. To preview off, send RGB zero.

The device also has a save command `CC86 = 89`; **this test app never sends it**. Do not confuse the save command with **controller number 89**, which carries the blue component. The app also avoids CC90, which persists an onboard lighting mode.

## Follow modes

| Message | Meaning |
| --- | --- |
| CC85 = 0 | Exit follow mode; clear active/follow notes and restore onboard lighting behavior |
| CC85 = 1 | Enter normal follow mode: notes 48-71 map to LEDs 0-23 |
| CC85 = 2 | Enter melody follow mode: notes 72-95 map to LEDs 0-23 |
| Note On, velocity 2 | Light the mapped hint LED using its current RAM color |
| Note Off, velocity 2 | Release the mapped hint LED |
| CC85 = 5 | Clear all follow hint LEDs while in a follow mode |
| CC85 = 6 | Stop device synthesizer notes without changing the follow LED mode |

Only the two explicitly supported ranges are sent. A normal Note Off with velocity zero must not be substituted for the hint Note Off because the inspected interface checks for velocity 2. The app sends all 24 matching Note Off messages before CC85 = 5 and CC85 = 6 when clearing a follow test. Follow mode selection resets musical state as described in the README.

## Official references

- [Android Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- [Android BluetoothGatt](https://developer.android.com/reference/android/bluetooth/BluetoothGatt)
- [Keys hardware guide](https://github.com/lastness09/fingersonic-keys-devkit)

Custom firmware must implement this public control interface itself to work with this app. Providing BLE MIDI notes alone is insufficient.
