package com.github.mikeshevchenko.sinclair;

import android.graphics.Bitmap;

import java.util.Arrays;

final class GraphicsArt {
    static final int SIZE = 176;
    static final int SCREEN_BYTES = 6912;

    private GraphicsArt() {
    }

    static byte[] convert(Bitmap source) {
        int side = Math.min(source.getWidth(), source.getHeight());
        int left = (source.getWidth() - side) / 2;
        int top = (source.getHeight() - side) / 2;
        Bitmap square = Bitmap.createBitmap(source, left, top, side, side);
        Bitmap scaled = Bitmap.createScaledBitmap(square, SIZE, SIZE, true);
        int[] pixels = new int[SIZE * SIZE];
        scaled.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE);
        int[] work = new int[pixels.length];
        int[] histogram = new int[256];
        for (int i = 0; i < pixels.length; i++) {
            int red = (pixels[i] >> 16) & 0xff;
            int green = (pixels[i] >> 8) & 0xff;
            int blue = pixels[i] & 0xff;
            int luminance = (299 * red + 587 * green + 114 * blue) / 1000;
            work[i] = luminance;
            histogram[luminance]++;
        }
        stretchContrast(work, histogram);
        byte[] screen = new byte[SCREEN_BYTES];
        Arrays.fill(screen, 6144, SCREEN_BYTES, (byte) 56);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int index = y * SIZE + x;
                int old = work[index];
                int value = old < 128 ? 0 : 255;
                if (value == 0) {
                    int address = ((y & 0xc0) << 5) | ((y & 7) << 8)
                            | ((y & 0x38) << 2) | (10 + x / 8);
                    screen[address] |= (byte) (0x80 >> (x % 8));
                }
                int error = old - value;
                addError(work, index + 1, error * 7 / 16, x + 1 < SIZE, y);
                addError(work, index + SIZE - 1, error * 3 / 16, x > 0, y + 1);
                addError(work, index + SIZE, error * 5 / 16, x >= 0, y + 1);
                addError(work, index + SIZE + 1, error / 16, x + 1 < SIZE, y + 1);
            }
        }
        if (scaled != square) {
            scaled.recycle();
        }
        if (square != source) {
            square.recycle();
        }
        return screen;
    }

    private static void stretchContrast(int[] values, int[] histogram) {
        int low = 0;
        int high = 255;
        int lowCount = values.length / 50;
        int highCount = values.length - lowCount;
        int count = 0;
        while (low < 255 && count < lowCount) {
            count += histogram[low++];
        }
        count = 0;
        while (high > 0 && count < highCount) {
            count += histogram[high--];
        }
        int range = high - low;
        if (range <= 0) {
            return;
        }
        for (int i = 0; i < values.length; i++) {
            values[i] = Math.min(255, Math.max(0, (values[i] - low) * 255 / range));
        }
    }

    private static void addError(int[] work, int index, int error, boolean xValid, int y) {
        if (xValid && y < SIZE && index >= 0 && index < work.length) {
            work[index] = Math.min(255, Math.max(0, work[index] + error));
        }
    }
}
