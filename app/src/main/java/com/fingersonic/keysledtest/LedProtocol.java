package com.fingersonic.keysledtest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Public control messages only; no device firmware implementation. */
public final class LedProtocol {
    public static final int LED_COUNT = 24;
    public static final UUID SERVICE = UUID.fromString("03b80e5a-ede8-4b33-a751-6ce34ec4c700");
    public static final UUID MIDI = UUID.fromString("7772e5db-3868-4112-a1a9-f2669d106bf3");
    public static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private LedProtocol() { }

    public static byte[] packet(int[] message, long milliseconds) {
        if (message == null || message.length != 3 ||
                (message[0] != 0xB0 && message[0] != 0x90 && message[0] != 0x80)) {
            throw new IllegalArgumentException("Expected a channel 1 CC or note message");
        }
        sevenBit(message[1]);
        sevenBit(message[2]);
        int time = (int) (milliseconds & 0x1fff);
        return new byte[]{(byte) (0x80 | (time >> 7)), (byte) (0x80 | (time & 0x7f)),
                (byte) message[0], (byte) message[1], (byte) message[2]};
    }

    /** Nearest supported device intensity: 0, 2, ... 252, 255. */
    public static int toSevenBit(int component) {
        if (component < 0 || component > 255) throw new IllegalArgumentException("RGB must be 0..255");
        return component >= 254 ? 127 : Math.min(126, (component + 1) / 2);
    }

    public static List<int[]> one(int index, int rgb) {
        if (index < 0 || index >= LED_COUNT) throw new IllegalArgumentException("LED must be 0..23");
        List<int[]> messages = begin();
        append(messages, index, rgb);
        messages.add(cc(86, 90));
        return messages;
    }

    public static List<int[]> all(int[] colors) {
        if (colors == null || colors.length != LED_COUNT) throw new IllegalArgumentException("Expected 24 colors");
        List<int[]> messages = begin();
        for (int i = 0; i < LED_COUNT; i++) append(messages, i, colors[i]);
        messages.add(cc(86, 90));
        return messages;
    }

    public static List<int[]> followMode(int mode) {
        if (mode < 0 || mode > 2) throw new IllegalArgumentException("Mode must be 0..2");
        List<int[]> messages = new ArrayList<>();
        messages.add(cc(86, 90));
        messages.add(cc(85, 0));
        if (mode != 0) messages.add(cc(85, mode));
        return messages;
    }

    public static int[] hint(int mode, int index, boolean on) {
        if ((mode != 1 && mode != 2) || index < 0 || index >= LED_COUNT) {
            throw new IllegalArgumentException("Invalid follow hint");
        }
        return new int[]{on ? 0x90 : 0x80, (mode == 1 ? 48 : 72) + index, 2};
    }

    public static List<int[]> clearHints(int mode) {
        List<int[]> messages = new ArrayList<>();
        for (int i = 0; i < LED_COUNT; i++) messages.add(hint(mode, i, false));
        messages.add(cc(85, 5));
        messages.add(cc(85, 6));
        return messages;
    }

    private static List<int[]> begin() {
        List<int[]> messages = new ArrayList<>();
        // Exit first to recover the state machine after an interrupted transfer.
        messages.add(cc(86, 90));
        messages.add(cc(86, 88));
        return messages;
    }

    private static void append(List<int[]> messages, int index, int rgb) {
        messages.add(cc(86, index));
        messages.add(cc(87, toSevenBit((rgb >> 16) & 255)));
        messages.add(cc(88, toSevenBit((rgb >> 8) & 255)));
        messages.add(cc(89, toSevenBit(rgb & 255)));
    }

    private static int[] cc(int control, int value) { return new int[]{0xB0, control, value}; }
    private static void sevenBit(int value) {
        if (value < 0 || value > 127) throw new IllegalArgumentException("MIDI data must be 0..127");
    }
}
