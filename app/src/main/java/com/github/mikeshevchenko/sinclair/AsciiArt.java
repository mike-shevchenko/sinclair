package com.github.mikeshevchenko.sinclair;

import android.graphics.Bitmap;

final class AsciiArt {
    static final int WIDTH = 58;
    static final int HEIGHT = 29;
    private static final char[] PALETTE = " .:-=+*#%?".toCharArray();

    private AsciiArt() {
    }

    static String convert(Bitmap source) {
        int side = Math.min(source.getWidth(), source.getHeight());
        int left = (source.getWidth() - side) / 2;
        int top = (source.getHeight() - side) / 2;
        Bitmap square = Bitmap.createBitmap(source, left, top, side, side);
        Bitmap scaled = Bitmap.createScaledBitmap(square, WIDTH, HEIGHT, true);
        int[] pixels = new int[WIDTH * HEIGHT];
        scaled.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT);
        int[] luminances = new int[pixels.length];
        int[] histogram = new int[256];
        for (int i = 0; i < pixels.length; i++) {
            int red = (pixels[i] >> 16) & 0xff;
            int green = (pixels[i] >> 8) & 0xff;
            int blue = pixels[i] & 0xff;
            int luminance = (299 * red + 587 * green + 114 * blue) / 1000;
            luminances[i] = luminance;
            histogram[luminance]++;
        }
        int low = 0;
        int high = 255;
        int lowCount = pixels.length / 50;
        int highCount = pixels.length - lowCount;
        int count = 0;
        while (low < 255 && count < lowCount) {
            count += histogram[low++];
        }
        count = 0;
        while (high > 0 && count < highCount) {
            count += histogram[high--];
        }
        int range = high - low;
        StringBuilder result = new StringBuilder(HEIGHT * (WIDTH + 1));
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int luminance = luminances[y * WIDTH + x];
                if (range > 0) {
                    luminance = Math.min(255, Math.max(0, (luminance - low) * 255 / range));
                }
                int index = (255 - luminance) * (PALETTE.length - 1) / 255;
                result.append(PALETTE[index]);
            }
            result.append('\r');
        }
        if (scaled != square) {
            scaled.recycle();
        }
        if (square != source) {
            square.recycle();
        }
        return result.toString();
    }
}
