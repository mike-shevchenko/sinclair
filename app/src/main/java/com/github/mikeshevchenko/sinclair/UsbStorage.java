package com.github.mikeshevchenko.sinclair;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

final class UsbStorage {
    private static final String AUTHORITY = "com.android.externalstorage.documents";

    private UsbStorage() {
    }

    static boolean isMassStorageAttached(Context context) {
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (manager == null) {
            return false;
        }
        for (UsbDevice device : manager.getDeviceList().values()) {
            if (device.getDeviceClass() == UsbConstants.USB_CLASS_MASS_STORAGE) {
                return true;
            }
            for (int i = 0; i < device.getInterfaceCount(); i++) {
                if (device.getInterface(i).getInterfaceClass()
                        == UsbConstants.USB_CLASS_MASS_STORAGE) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean isUsbTree(Context context, Uri tree) {
        if (!AUTHORITY.equals(tree.getAuthority())) {
            return false;
        }
        String rootId;
        try {
            rootId = DocumentsContract.getTreeDocumentId(tree);
        } catch (IllegalArgumentException e) {
            return false;
        }
        StorageManager manager = (StorageManager) context.getSystemService(
                Context.STORAGE_SERVICE);
        if (manager == null) {
            return false;
        }
        for (StorageVolume volume : manager.getStorageVolumes()) {
            String uuid = volume.getUuid();
            if (volume.isRemovable() && uuid != null && (uuid + ":").equalsIgnoreCase(rootId)) {
                return true;
            }
        }
        return false;
    }

    static Uri initialRootUri(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return null;
        }
        StorageManager manager = (StorageManager) context.getSystemService(
                Context.STORAGE_SERVICE);
        if (manager == null) {
            return null;
        }
        for (StorageVolume volume : manager.getStorageVolumes()) {
            if (volume.isRemovable() && volume.getUuid() != null) {
                return DocumentsContract.buildRootUri(AUTHORITY, volume.getUuid() + ":");
            }
        }
        return null;
    }

    static void writeOutputs(Context context, Uri tree, File asciiFile, boolean asciiMode)
            throws IOException {
        byte[] data = readFile(asciiFile);
        ContentResolver resolver = context.getContentResolver();
        if (asciiMode) {
            writeDocument(resolver, tree, "photo.txt", "text/plain", data);
            writeDocument(resolver, tree, "photo.trd", "application/octet-stream",
                    TrDosImage.create(data));
        } else {
            writeDocument(resolver, tree, "photo.trd", "application/octet-stream",
                    TrDosImage.createScreen(data));
        }
    }

    private static byte[] readFile(File file) throws IOException {
        try (InputStream input = new FileInputStream(file);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static void writeDocument(ContentResolver resolver, Uri tree, String name,
            String mimeType, byte[] content) throws IOException {
        String treeId = DocumentsContract.getTreeDocumentId(tree);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId);
        String documentId = findDocument(resolver, children, name);
        Uri document;
        if (documentId == null) {
            Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, treeId);
            document = DocumentsContract.createDocument(resolver, parent, mimeType, name);
        } else {
            document = DocumentsContract.buildDocumentUriUsingTree(tree, documentId);
        }
        if (document == null) {
            throw new IOException("Cannot create " + name);
        }
        ParcelFileDescriptor descriptor = resolver.openFileDescriptor(document, "rwt");
        if (descriptor == null) {
            throw new FileNotFoundException("Cannot open " + name);
        }
        try (FileOutputStream output =
                new ParcelFileDescriptor.AutoCloseOutputStream(descriptor)) {
            output.write(content);
            output.flush();
            output.getFD().sync();
        }
        verifyDocument(resolver, document, name, content);
    }

    private static void verifyDocument(ContentResolver resolver, Uri document, String name,
            byte[] content) throws IOException {
        try (InputStream input = resolver.openInputStream(document);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) {
                throw new FileNotFoundException("Cannot read back " + name);
            }
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            byte[] saved = output.toByteArray();
            if (!Arrays.equals(content, saved)) {
                throw new IOException("Cannot verify " + name + ": " + saved.length
                        + " of " + content.length + " bytes saved");
            }
        }
    }

    private static String findDocument(ContentResolver resolver, Uri children, String name)
            throws IOException {
        String[] projection = {Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME};
        try (Cursor cursor = resolver.query(children, projection, null, null, null)) {
            if (cursor == null) {
                throw new IOException("Cannot read USB drive directory");
            }
            while (cursor.moveToNext()) {
                if (name.equals(cursor.getString(1))) {
                    return cursor.getString(0);
                }
            }
        }
        return null;
    }
}
