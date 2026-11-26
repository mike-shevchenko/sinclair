package com.github.mikeshevchenko.sinclair;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Button;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.Semaphore;

public class CameraActivity extends Activity {
    static final String EXTRA_ASCII_PATH = "ascii_path";
    static final String EXTRA_ASCII_MODE = "ascii_mode";
    private static final int MAX_JPEG_PIXELS = 4_000_000;

    private final Semaphore cameraLock = new Semaphore(1);
    private final TextureView.SurfaceTextureListener surfaceListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width,
                        int height) {
                    openCamera(width, height);
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width,
                        int height) {
                    configureTransform(width, height);
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                }
            };

    private TextureView preview;
    private Button captureButton;
    private String cameraId;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private CaptureRequest.Builder previewRequest;
    private ImageReader imageReader;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private Size previewSize;
    private Size jpegSize;
    private Rect cropRegion;
    private int sensorOrientation;
    private boolean frontCamera;
    private boolean asciiMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        asciiMode = getIntent().getBooleanExtra(EXTRA_ASCII_MODE, false);
        preview = findViewById(R.id.preview);
        captureButton = findViewById(R.id.capture);
        findViewById(R.id.cancel).setOnClickListener(view -> finish());
        captureButton.setOnClickListener(view -> takePhoto());
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (preview.isAvailable()) {
            openCamera(preview.getWidth(), preview.getHeight());
        } else {
            preview.setSurfaceTextureListener(surfaceListener);
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("Camera");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        backgroundThread = null;
        backgroundHandler = null;
    }

    private void openCamera(int width, int height) {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            finish();
            return;
        }
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        boolean lockAcquired = false;
        try {
            selectCamera(manager);
            imageReader = ImageReader.newInstance(
                    jpegSize.getWidth(), jpegSize.getHeight(), ImageFormat.JPEG, 2);
            imageReader.setOnImageAvailableListener(this::onImageAvailable, backgroundHandler);
            if (!cameraLock.tryAcquire()) {
                throw new IllegalStateException("Camera is already opening");
            }
            lockAcquired = true;
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice device) {
                    cameraLock.release();
                    camera = device;
                    createPreviewSession();
                }

                @Override
                public void onDisconnected(CameraDevice device) {
                    cameraLock.release();
                    device.close();
                    camera = null;
                }

                @Override
                public void onError(CameraDevice device, int error) {
                    cameraLock.release();
                    device.close();
                    camera = null;
                    showError("Camera error: " + error);
                }
            }, backgroundHandler);
            configureTransform(width, height);
        } catch (CameraAccessException | RuntimeException e) {
            if (lockAcquired) {
                cameraLock.release();
            }
            showError("Cannot open camera: " + e.getMessage());
        }
    }

    private void selectCamera(CameraManager manager) throws CameraAccessException {
        String fallback = null;
        for (String id : manager.getCameraIdList()) {
            if (fallback == null) {
                fallback = id;
            }
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                configureCamera(id, characteristics);
                return;
            }
        }
        if (fallback == null) {
            throw new IllegalStateException("No camera found");
        }
        configureCamera(fallback, manager.getCameraCharacteristics(fallback));
    }

    private void configureCamera(String id, CameraCharacteristics characteristics) {
        cameraId = id;
        Integer orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        sensorOrientation = orientation == null ? 0 : orientation;
        Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        frontCamera = facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT;
        StreamConfigurationMap map = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            throw new IllegalStateException("Camera has no output sizes");
        }
        Size square = chooseSquareSize(map.getOutputSizes(ImageFormat.JPEG));
        if (square != null) {
            jpegSize = square;
            previewSize = square;
            cropRegion = centerSquare(characteristics);
        } else {
            jpegSize = chooseJpegSize(map.getOutputSizes(ImageFormat.JPEG));
            previewSize = choosePreviewSize(map.getOutputSizes(SurfaceTexture.class), jpegSize);
        }
    }

    private static Size chooseSquareSize(Size[] sizes) {
        Size best = null;
        for (Size size : sizes) {
            long pixels = (long) size.getWidth() * size.getHeight();
            if (size.getWidth() == size.getHeight() && pixels <= MAX_JPEG_PIXELS
                    && (best == null || pixels > area(best))) {
                best = size;
            }
        }
        return best;
    }

    private static Rect centerSquare(CameraCharacteristics characteristics) {
        Rect active = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        int side = Math.min(active.width(), active.height());
        int left = active.left + (active.width() - side) / 2;
        int top = active.top + (active.height() - side) / 2;
        return new Rect(left, top, left + side, top + side);
    }

    private static Size chooseJpegSize(Size[] sizes) {
        Size best = null;
        for (Size size : sizes) {
            long pixels = (long) size.getWidth() * size.getHeight();
            if (pixels <= MAX_JPEG_PIXELS && (best == null || pixels > area(best))) {
                best = size;
            }
        }
        if (best != null) {
            return best;
        }
        return Collections.min(
                Arrays.asList(sizes), Comparator.comparingLong(CameraActivity::area));
    }

    private static Size choosePreviewSize(Size[] sizes, Size jpeg) {
        Size best = null;
        double jpegRatio = (double) jpeg.getWidth() / jpeg.getHeight();
        for (Size size : sizes) {
            double ratio = (double) size.getWidth() / size.getHeight();
            if (Math.abs(ratio - jpegRatio) > 0.01 || area(size) > MAX_JPEG_PIXELS) {
                continue;
            }
            if (best == null || area(size) > area(best)) {
                best = size;
            }
        }
        if (best != null) {
            return best;
        }
        return sizes[0];
    }

    private static long area(Size size) {
        return (long) size.getWidth() * size.getHeight();
    }

    private void createPreviewSession() {
        try {
            SurfaceTexture texture = preview.getSurfaceTexture();
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            configureTransform(preview.getWidth(), preview.getHeight());
            Surface surface = new Surface(texture);
            previewRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            if (cropRegion != null) {
                previewRequest.set(CaptureRequest.SCALER_CROP_REGION, cropRegion);
            }
            previewRequest.addTarget(surface);
            camera.createCaptureSession(
                    Arrays.asList(surface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession captureSession) {
                            if (camera == null) {
                                return;
                            }
                            session = captureSession;
                            try {
                                previewRequest.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                session.setRepeatingRequest(previewRequest.build(), null,
                                        backgroundHandler);
                            } catch (CameraAccessException e) {
                                showError("Cannot start preview: " + e.getMessage());
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession captureSession) {
                            showError("Cannot configure camera preview");
                        }
                    }, backgroundHandler);
        } catch (CameraAccessException e) {
            showError("Cannot create preview: " + e.getMessage());
        }
    }

    private void takePhoto() {
        if (camera == null || session == null) {
            return;
        }
        captureButton.setEnabled(false);
        try {
            CaptureRequest.Builder request = camera.createCaptureRequest(
                    CameraDevice.TEMPLATE_STILL_CAPTURE);
            if (cropRegion != null) {
                request.set(CaptureRequest.SCALER_CROP_REGION, cropRegion);
            }
            request.addTarget(imageReader.getSurface());
            request.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            request.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation());
            session.capture(request.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            captureButton.setEnabled(true);
            showError("Cannot take photo: " + e.getMessage());
        }
    }

    private int jpegOrientation() {
        int deviceOrientation;
        switch (preview.getDisplay().getRotation()) {
            case Surface.ROTATION_90:
                deviceOrientation = 90;
                break;
            case Surface.ROTATION_180:
                deviceOrientation = 180;
                break;
            case Surface.ROTATION_270:
                deviceOrientation = 270;
                break;
            default:
                deviceOrientation = 0;
        }
        int sign = frontCamera ? -1 : 1;
        return (sensorOrientation - deviceOrientation * sign + 360) % 360;
    }

    private void onImageAvailable(ImageReader reader) {
        Image image = reader.acquireLatestImage();
        if (image == null) {
            return;
        }
        try {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] jpeg = new byte[buffer.remaining()];
            buffer.get(jpeg);
            processPhoto(jpeg);
        } finally {
            image.close();
        }
    }

    private void processPhoto(byte[] jpeg) {
        try {
            Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
            if (bitmap == null) {
                throw new IOException("Camera returned an invalid JPEG image");
            }
            bitmap = applyExifOrientation(jpeg, bitmap);
            byte[] data = asciiMode
                    ? AsciiArt.convert(bitmap).getBytes(StandardCharsets.US_ASCII)
                    : GraphicsArt.convert(bitmap);
            bitmap.recycle();
            File output = new File(getCacheDir(), "photo-ascii.txt");
            try (FileOutputStream stream = new FileOutputStream(output, false)) {
                stream.write(data);
            }
            Intent result = new Intent();
            result.putExtra(EXTRA_ASCII_PATH, output.getAbsolutePath());
            runOnUiThread(() -> {
                setResult(RESULT_OK, result);
                finish();
            });
        } catch (IOException | RuntimeException e) {
            showError("Cannot process photo: " + e.getMessage());
            runOnUiThread(() -> captureButton.setEnabled(true));
        }
    }

    private Bitmap applyExifOrientation(byte[] jpeg, Bitmap bitmap) throws IOException {
        ExifInterface exif = new ExifInterface(new ByteArrayInputStream(jpeg));
        int orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90);
                break;
            default:
                return bitmap;
        }
        Bitmap oriented = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (oriented != bitmap) {
            bitmap.recycle();
        }
        return oriented;
    }

    private void configureTransform(int width, int height) {
        if (previewSize == null || width == 0 || height == 0) {
            return;
        }
        if (previewSize.getWidth() == previewSize.getHeight()) {
            preview.setTransform(null);
            return;
        }
        int rotation = preview.getDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF view = new RectF(0, 0, width, height);
        RectF buffer = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        float centerX = view.centerX();
        float centerY = view.centerY();
        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            buffer.offset(centerX - buffer.centerX(), centerY - buffer.centerY());
            float scaleX = (float) width / buffer.width();
            float scaleY = (float) height / buffer.height();
            float scale = Math.max(scaleX, scaleY);
            matrix.setScale(scale / scaleX, scale / scaleY, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else {
            float scaleX = (float) width / previewSize.getWidth();
            float scaleY = (float) height / previewSize.getHeight();
            float scale = Math.max(scaleX, scaleY);
            matrix.postScale(scale / scaleX, scale / scaleY, centerX, centerY);
            if (rotation == Surface.ROTATION_180) {
                matrix.postRotate(180, centerX, centerY);
            }
        }
        preview.setTransform(matrix);
    }

    private void closeCamera() {
        try {
            cameraLock.acquire();
            if (session != null) {
                session.close();
                session = null;
            }
            if (camera != null) {
                camera.close();
                camera = null;
            }
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            cameraLock.release();
        }
    }

    private void showError(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }
}
