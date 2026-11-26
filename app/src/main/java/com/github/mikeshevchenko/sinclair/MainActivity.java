package com.github.mikeshevchenko.sinclair;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_PERMISSION = 1;
    private static final int REQUEST_CAMERA = 2;
    private static final int REQUEST_STORAGE = 3;
    private static final String PREFS = "storage";
    private static final String PREF_TREE = "tree";
    private static final String STATE_ASCII = "ascii";
    private static final String STATE_ASCII_MODE = "ascii_mode";

    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private TextView status;
    private Button makePhotoButton;
    private String pendingAsciiPath;
    private boolean asciiMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        makePhotoButton = findViewById(R.id.make_photo);
        makePhotoButton.setOnClickListener(view -> makePhoto());
        CheckBox asciiModeInput = findViewById(R.id.ascii_mode);
        if (savedInstanceState != null) {
            pendingAsciiPath = savedInstanceState.getString(STATE_ASCII);
            asciiMode = savedInstanceState.getBoolean(STATE_ASCII_MODE);
        }
        asciiModeInput.setChecked(asciiMode);
        asciiModeInput.setOnCheckedChangeListener(
                (button, checked) -> asciiMode = checked);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_ASCII, pendingAsciiPath);
        outState.putBoolean(STATE_ASCII_MODE, asciiMode);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        writer.shutdown();
        super.onDestroy();
    }

    private void makePhoto() {
        if (checkSelfPermission(Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_PERMISSION);
        }
    }

    private void launchCamera() {
        Intent intent = new Intent(this, CameraActivity.class);
        intent.putExtra(CameraActivity.EXTRA_ASCII_MODE, asciiMode);
        startActivityForResult(intent, REQUEST_CAMERA);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else if (requestCode == REQUEST_PERMISSION) {
            status.setText("Camera permission is required");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CAMERA) {
            if (resultCode == RESULT_OK && data != null) {
                pendingAsciiPath = data.getStringExtra(CameraActivity.EXTRA_ASCII_PATH);
                beginSaving();
            } else {
                status.setText("Photo cancelled");
            }
        } else if (requestCode == REQUEST_STORAGE && resultCode == RESULT_OK && data != null) {
            Uri tree = data.getData();
            if (tree == null || !UsbStorage.isUsbTree(this, tree)) {
                status.setText("Select the root of the connected USB flash drive");
                return;
            }
            int flags = data.getFlags();
            if ((flags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                getContentResolver().takePersistableUriPermission(
                        tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            if ((flags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) {
                getContentResolver().takePersistableUriPermission(
                        tree, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } else {
                status.setText(R.string.usb_access_required);
                return;
            }
            preferences().edit().putString(PREF_TREE, tree.toString()).apply();
            savePendingPhoto(tree);
        } else if (requestCode == REQUEST_STORAGE) {
            status.setText("USB drive access is required to save the photo");
        }
    }

    private void beginSaving() {
        if (pendingAsciiPath == null) {
            return;
        }
        if (!UsbStorage.isMassStorageAttached(this)) {
            status.setText(
                    "Connect a USB flash drive using an OTG cable and take the photo again");
            return;
        }
        Uri savedTree = savedTree();
        if (savedTree == null) {
            requestStorageAccess();
        } else {
            savePendingPhoto(savedTree);
        }
    }

    private Uri savedTree() {
        String value = preferences().getString(PREF_TREE, null);
        if (value == null) {
            return null;
        }
        Uri tree = Uri.parse(value);
        if (!UsbStorage.isUsbTree(this, tree)) {
            return null;
        }
        for (UriPermission permission : getContentResolver().getPersistedUriPermissions()) {
            if (permission.getUri().equals(tree) && permission.isWritePermission()) {
                return tree;
            }
        }
        return null;
    }

    private void requestStorageAccess() {
        status.setText("Select the root of the connected USB flash drive");
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        Uri initial = UsbStorage.initialRootUri(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && initial != null) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initial);
        }
        startActivityForResult(intent, REQUEST_STORAGE);
    }

    private void savePendingPhoto(Uri tree) {
        File asciiFile = new File(pendingAsciiPath);
        makePhotoButton.setEnabled(false);
        status.setText(asciiMode ? "Saving photo.txt and photo.trd..." : "Saving photo.trd...");
        writer.execute(() -> {
            try {
                UsbStorage.writeOutputs(this, tree, asciiFile, asciiMode);
                runOnUiThread(() -> {
                    pendingAsciiPath = null;
                    makePhotoButton.setEnabled(true);
                    status.setText(asciiMode
                            ? "Saved photo.txt and photo.trd on the USB flash drive"
                            : "Saved photo.trd on the USB flash drive");
                });
            } catch (SecurityException e) {
                preferences().edit().remove(PREF_TREE).apply();
                runOnUiThread(() -> {
                    makePhotoButton.setEnabled(true);
                    requestStorageAccess();
                });
            } catch (IOException | RuntimeException e) {
                runOnUiThread(() -> {
                    makePhotoButton.setEnabled(true);
                    status.setText("Cannot save files: " + e.getMessage());
                });
            }
        });
    }

    private SharedPreferences preferences() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }
}
