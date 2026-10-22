package com.fingersonic.keysledtest;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public final class LedProtocolTest {
    @Test public void directColorTransactionHasResynchronizationAndOrderedRgb() {
        List<int[]> messages = LedProtocol.one(23, 0xff8001);
        assertEquals(7, messages.size());
        assertArrayEquals(new int[]{0xb0, 86, 90}, messages.get(0));
        assertArrayEquals(new int[]{0xb0, 86, 88}, messages.get(1));
        assertArrayEquals(new int[]{0xb0, 86, 23}, messages.get(2));
        assertArrayEquals(new int[]{0xb0, 87, 127}, messages.get(3));
        assertArrayEquals(new int[]{0xb0, 88, 64}, messages.get(4));
        assertArrayEquals(new int[]{0xb0, 89, 1}, messages.get(5));
        assertArrayEquals(new int[]{0xb0, 86, 90}, messages.get(6));
    }

    @Test public void allOffAddressesEveryLedAndNeverSavesToFlash() {
        List<int[]> messages = LedProtocol.all(new int[24]);
        assertEquals(99, messages.size());
        for (int i = 0; i < 24; i++) {
            assertArrayEquals(new int[]{0xb0, 86, i}, messages.get(2 + i * 4));
            for (int c = 0; c < 3; c++) {
                assertArrayEquals(new int[]{0xb0, 87 + c, 0}, messages.get(3 + i * 4 + c));
            }
        }
        for (int[] message : messages) assertFalse(message[1] == 86 && message[2] == 89);
    }

    @Test public void palettePreservesIndependentIndexAndChannelValues() {
        int[] colors = new int[24];
        colors[7] = 0x00ff00;
        colors[18] = 0x0000ff;
        List<int[]> messages = LedProtocol.all(colors);
        assertArrayEquals(new int[]{0xb0, 88, 127}, messages.get(4 + 7 * 4));
        assertArrayEquals(new int[]{0xb0, 89, 127}, messages.get(5 + 18 * 4));
        assertArrayEquals(new int[]{0xb0, 89, 0}, messages.get(5 + 17 * 4));
    }

    @Test public void quantizerUsesNearestAvailableIntensityAcrossEntireRange() {
        for (int input = 0; input < 256; input++) {
            int output = LedProtocol.toSevenBit(input);
            int reconstructed = output == 127 ? 255 : output * 2;
            assertTrue(Math.abs(reconstructed - input) <= 1);
            assertTrue(output >= 0 && output <= 127);
        }
        assertEquals(0, LedProtocol.toSevenBit(0));
        assertEquals(127, LedProtocol.toSevenBit(255));
    }

    @Test public void bleMidiTimestampWrapsAt8192Milliseconds() {
        assertArrayEquals(new byte[]{(byte)0x80,(byte)0x80,(byte)0xb0,86,88},
                LedProtocol.packet(new int[]{0xb0,86,88},8192));
        assertArrayEquals(new byte[]{(byte)0xbf,(byte)0xff,(byte)0x90,48,2},
                LedProtocol.packet(new int[]{0x90,48,2},8191));
    }

    @Test public void twoFollowModesHaveDistinctRangesAndExactHintVelocity() {
        for (int mode = 1; mode <= 2; mode++) {
            assertArrayEquals(new int[]{0xb0,85,mode}, LedProtocol.followMode(mode).get(2));
            for (int i = 0; i < 24; i++) {
                int note = (mode == 1 ? 48 : 72) + i;
                assertArrayEquals(new int[]{0x90,note,2}, LedProtocol.hint(mode,i,true));
                assertArrayEquals(new int[]{0x80,note,2}, LedProtocol.hint(mode,i,false));
            }
        }
    }

    @Test public void clearHintsReleasesAllNotesBeforeClearingLightsAndAudio() {
        for (int mode : Arrays.asList(1, 2)) {
            List<int[]> messages = LedProtocol.clearHints(mode);
            assertEquals(26, messages.size());
            for (int i = 0; i < 24; i++) assertArrayEquals(LedProtocol.hint(mode,i,false), messages.get(i));
            assertArrayEquals(new int[]{0xb0,85,5}, messages.get(24));
            assertArrayEquals(new int[]{0xb0,85,6}, messages.get(25));
        }
    }

    @Test public void rejectsInvalidIndicesAndMidiData() {
        assertThrows(IllegalArgumentException.class, () -> LedProtocol.one(-1,0));
        assertThrows(IllegalArgumentException.class, () -> LedProtocol.one(24,0));
        assertThrows(IllegalArgumentException.class, () -> LedProtocol.all(new int[23]));
        assertThrows(IllegalArgumentException.class, () -> LedProtocol.toSevenBit(256));
        assertThrows(IllegalArgumentException.class, () -> LedProtocol.hint(0,0,true));
        assertThrows(IllegalArgumentException.class, () -> LedProtocol.followMode(3));
        assertThrows(IllegalArgumentException.class, () -> LedProtocol.packet(new int[]{0xb0,86,128},0));
    }
}
