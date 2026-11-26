package com.github.mikeshevchenko.sinclair;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class TrDosImage {
    private static final int SECTOR_SIZE = 256;
    private static final int SECTORS_PER_TRACK = 16;
    private static final int LOGICAL_TRACKS = 160;
    private static final int FIRST_DATA_TRACK = 1;
    private static final int DATA_OFFSET = FIRST_DATA_TRACK * SECTORS_PER_TRACK * SECTOR_SIZE;
    private static final int DISK_INFO_OFFSET = 8 * SECTOR_SIZE;
    private static final int TOTAL_SECTORS = (LOGICAL_TRACKS - FIRST_DATA_TRACK)
            * SECTORS_PER_TRACK;

    private TrDosImage() {
    }

    static byte[] create(byte[] text) {
        return create(text, 30000);
    }

    static byte[] createScreen(byte[] screen) {
        if (screen.length != GraphicsArt.SCREEN_BYTES) {
            throw new IllegalArgumentException("Spectrum screen must be 6912 bytes");
        }
        return create(screen, 16384);
    }

    private static byte[] create(byte[] data, int loadAddress) {
        int dataSectors = sectors(data.length);
        if (dataSectors > 255 || dataSectors > TOTAL_SECTORS) {
            throw new IllegalArgumentException("TR-DOS file is too large");
        }
        byte[] image = new byte[LOGICAL_TRACKS * SECTORS_PER_TRACK * SECTOR_SIZE];
        writeTextEntry(image, data, dataSectors, loadAddress);

        image[DISK_INFO_OFFSET + 225] = (byte) (dataSectors % SECTORS_PER_TRACK);
        image[DISK_INFO_OFFSET + 226] = (byte) (FIRST_DATA_TRACK
                + dataSectors / SECTORS_PER_TRACK);
        image[DISK_INFO_OFFSET + 227] = 0x16;
        image[DISK_INFO_OFFSET + 228] = 1;
        putWord(image, DISK_INFO_OFFSET + 229, TOTAL_SECTORS - dataSectors);
        image[DISK_INFO_OFFSET + 231] = 0x10;
        Arrays.fill(image, DISK_INFO_OFFSET + 233, DISK_INFO_OFFSET + 242, (byte) ' ');
        byte[] label = "SINCLAIR".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(label, 0, image, DISK_INFO_OFFSET + 245, label.length);
        System.arraycopy(data, 0, image, DATA_OFFSET, data.length);
        return image;
    }

    private static int sectors(int length) {
        return (length + SECTOR_SIZE - 1) / SECTOR_SIZE;
    }

    private static void writeTextEntry(byte[] image, byte[] text, int sectors, int loadAddress) {
        writeName(image, 0, "photo   ");
        image[8] = 'C';
        putWord(image, 9, loadAddress);
        putWord(image, 11, text.length);
        image[13] = (byte) sectors;
        writeLocation(image, 14, 0);
    }

    private static void writeName(byte[] image, int offset, String name) {
        byte[] bytes = name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, image, offset, bytes.length);
    }

    private static void writeLocation(byte[] image, int offset, int sector) {
        image[offset] = (byte) (sector % SECTORS_PER_TRACK);
        image[offset + 1] = (byte) (FIRST_DATA_TRACK + sector / SECTORS_PER_TRACK);
    }

    private static void putWord(byte[] output, int offset, int value) {
        output[offset] = (byte) value;
        output[offset + 1] = (byte) (value >> 8);
    }
}
