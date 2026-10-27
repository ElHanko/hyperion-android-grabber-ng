package com.elhanko.hyperiongrabber.ng.common;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.nio.ByteBuffer;

public class HyperionScreenEncoderPixelTest {
    @Test
    public void pixelsPreserveRgbOrderAndUnsignedChannelsWithoutAlpha() {
        assertArrayEquals(new byte[] {
                0, 127, (byte) 128, (byte) 255, (byte) 254, (byte) 253,
                1, 2, 3, 4, 5, 6
        }, HyperionScreenEncoder.getPixels(rgbaImage(), 2, 2, 8, 4, 0, 0));
    }

    @Test
    public void pixelsSkipRowAndPixelPaddingWithoutChangingBufferState() {
        ByteBuffer buffer = paddedImage(2, 2, 16, 6).asReadOnlyBuffer();
        buffer.position(2);
        int limit = buffer.limit();

        assertArrayEquals(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11},
                HyperionScreenEncoder.getPixels(buffer, 2, 2, 16, 6, 0, 0));
        assertEquals(2, buffer.position());
        assertEquals(limit, buffer.limit());
    }

    @Test
    public void pixelsCropBothAxesWithDifferentBorderWidths() {
        assertArrayEquals(new byte[] {
                33, 34, 35, 36, 37, 38, 39, 40, 41,
                48, 49, 50, 51, 52, 53, 54, 55, 56
        }, HyperionScreenEncoder.getPixels(paddedImage(5, 6, 33, 6), 5, 6, 33, 6, 1, 2));
    }

    @Test
    public void averageUsesUnsignedChannelsAndTruncatesEachChannel() {
        assertArrayEquals(new byte[] {65, 97, 97},
                HyperionScreenEncoder.getAverageColor(rgbaImage(), 2, 2, 8, 4, 0, 0));
    }

    @Test
    public void averageSkipsRowAndPixelPaddingWithoutChangingBufferState() {
        ByteBuffer buffer = paddedImage(2, 2, 16, 6).asReadOnlyBuffer();
        buffer.position(2);
        int limit = buffer.limit();

        assertArrayEquals(new byte[] {4, 5, 6},
                HyperionScreenEncoder.getAverageColor(buffer, 2, 2, 16, 6, 0, 0));
        assertEquals(2, buffer.position());
        assertEquals(limit, buffer.limit());
    }

    @Test
    public void averageExcludesBordersOnBothAxes() {
        ByteBuffer buffer = paddedImage(5, 6, 33, 6);
        // Break the image's symmetric color distribution outside the retained rectangle.
        buffer.put(0, (byte) 255);

        assertArrayEquals(new byte[] {43, 44, 45},
                HyperionScreenEncoder.getAverageColor(buffer, 5, 6, 33, 6, 1, 2));
    }

    @Test
    public void bothModesPreserveASingleRemainingPixel() {
        ByteBuffer buffer = paddedImage(3, 5, 20, 4);
        byte[] expected = {21, 22, 23};

        assertArrayEquals(expected,
                HyperionScreenEncoder.getPixels(buffer, 3, 5, 20, 4, 1, 2));
        assertArrayEquals(expected,
                HyperionScreenEncoder.getAverageColor(buffer, 3, 5, 20, 4, 1, 2));
    }

    private static ByteBuffer rgbaImage() {
        return ByteBuffer.wrap(new byte[] {
                0, 127, (byte) 128, 42, (byte) 255, (byte) 254, (byte) 253, 0,
                1, 2, 3, (byte) 255, 4, 5, 6, (byte) 128
        });
    }

    private static ByteBuffer paddedImage(int width, int height, int rowStride, int pixelStride) {
        // The final row ends after RGB; no trailing alpha or padding may be read.
        int size = (height - 1) * rowStride + (width - 1) * pixelStride + 3;
        ByteBuffer buffer = ByteBuffer.allocateDirect(size);
        for (int i = 0; i < size; i++) {
            buffer.put(i, (byte) 0xee);
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int offset = y * rowStride + x * pixelStride;
                int red = (y * width + x) * 3;
                buffer.put(offset, (byte) red);
                buffer.put(offset + 1, (byte) (red + 1));
                buffer.put(offset + 2, (byte) (red + 2));
            }
        }
        return buffer;
    }
}
