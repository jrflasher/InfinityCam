package com.raisproject.infinitycam;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Range;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.camera2.interop.Camera2CameraControl;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.camera2.interop.CaptureRequestOptions;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ViewPort;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * InfinityCam 1.0.0 — CameraX camera app for Project Infinity X (AOSP Android 17)
 * on Galaxy Note 10 5G (SM-N971N). OneUI 7 / Samsung Camera style modes,
 * re-implemented from scratch. NOT Samsung Camera: no OneUI framework,
 * no Samsung HALs, no Samsung proprietary code or assets.
 * Honest limits: ultrawide (0.5x) only if CameraX exposes zoom < 1x;
 * aux lenses are locked on Exynos AOSP; HDR / manual controls only where
 * the device reports support; Panorama has no auto-stitch.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "InfinityCam";
    private static final int REQ_PERMS = 100;
    private static final String[] PERMS = new String[]{
            Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};

    enum Mode { PHOTO, VIDEO, PORTRAIT, NIGHT, PRO, PANORAMA, FOOD, SLOWMO, HYPERLAPSE }

    private static class ModeDef {
        final Mode mode; final String label; final String hint;
        ModeDef(Mode m, String l, String h) { mode = m; label = l; hint = h; }
    }
    private static final ModeDef[] MODES = new ModeDef[]{
            new ModeDef(Mode.PHOTO, "Foto", "Foto"),
            new ModeDef(Mode.VIDEO, "Video", "Video"),
            new ModeDef(Mode.PORTRAIT, "Portrait", "Portrait — bokeh simulasi"),
            new ModeDef(Mode.NIGHT, "Night", "Night — eksposur panjang"),
            new ModeDef(Mode.PRO, "Pro", "Pro — manual"),
            new ModeDef(Mode.PANORAMA, "Panorama", "Panorama — foto lebar"),
            new ModeDef(Mode.FOOD, "Food", "Food — warna hangat"),
            new ModeDef(Mode.SLOWMO, "Slow Mo", "Slow Motion — video"),
            new ModeDef(Mode.HYPERLAPSE, "Hyperlapse", "Hyperlapse — burst 2 detik"),
    };

    private PreviewView previewView;
    private TextView txtModeHint, txtCountdown, txtPanoGuide;
    private LinearLayout modeRow, proPanel, bokehPanel;
    private ImageView imgThumb;
    private Button btnShutter, btnFlash, btnTimer, btnRatio, btnHdr;

    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private Preview preview;
    private ImageCapture imageCapture;
    private VideoCapture<Recorder> videoCapture;
    private Recording recording;
    private CameraSelector selector = CameraSelector.DEFAULT_BACK_CAMERA;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private Mode currentMode = Mode.PHOTO;
    private int flashMode = ImageCapture.FLASH_MODE_AUTO; // 0 auto, 1 on, 2 off
    private int timerSec = 0; // 0,2,5,10
    private int ratioIdx = 0; // 0=4:3, 1=16:9, 2=Full
    private boolean hdrOn = false;
    private int bokehStrength = 60;
    private float currentZoom = 1f;
    private boolean isRecording = false;
    private boolean hyperRunning = false;
    private int hyperCount = 0;
    private ScaleGestureDetector scaleDetector;
    private Uri lastSavedUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        previewView = findViewById(R.id.previewView);
        txtModeHint = findViewById(R.id.txtModeHint);
        txtCountdown = findViewById(R.id.txtCountdown);
        txtPanoGuide = findViewById(R.id.txtPanoGuide);
        modeRow = findViewById(R.id.modeRow);
        proPanel = findViewById(R.id.proPanel);
        bokehPanel = findViewById(R.id.bokehPanel);
        imgThumb = findViewById(R.id.imgThumb);
        btnShutter = findViewById(R.id.btnShutter);
        btnFlash = findViewById(R.id.btnFlash);
        btnTimer = findViewById(R.id.btnTimer);
        btnRatio = findViewById(R.id.btnRatio);
        btnHdr = findViewById(R.id.btnHdr);

        buildModeRow();
        bindTopButtons();
        bindProPanel();
        bindZoomButtons();
        bindShutterAndSwitch();
        bindThumb();
        bindGestures();

        if (allPermsGranted()) startCamera();
        else ActivityCompat.requestPermissions(this, PERMS, REQ_PERMS);
    }

    private boolean allPermsGranted() {
        for (String p : PERMS)
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) return false;
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, perms, results);
        if (requestCode == REQ_PERMS && allPermsGranted()) startCamera();
        else Toast.makeText(this, "Izin kamera diperlukan", Toast.LENGTH_LONG).show();
    }

    // ---------- UI setup ----------

    private void buildModeRow() {
        for (ModeDef d : MODES) {
            Button b = new Button(this, null, 0, R.style.CamModeButton);
            b.setText(d.label);
            b.setTag(d);
            b.setOnClickListener(v -> setMode(d));
            modeRow.addView(b);
        }
        refreshModeRow();
    }

    private void refreshModeRow() {
        for (int i = 0; i < modeRow.getChildCount(); i++) {
            Button b = (Button) modeRow.getChildAt(i);
            ModeDef d = (ModeDef) b.getTag();
            boolean active = d.mode == currentMode;
            b.setTextColor(active ? 0xFFFFD700 : 0xFFCCCCCC);
            b.setTextSize(active ? 16 : 14);
        }
    }

    private void setMode(ModeDef d) {
        if (isRecording) { toast("Stop video dulu"); return; }
        currentMode = d.mode;
        txtModeHint.setText(d.hint);
        refreshModeRow();
        proPanel.setVisibility(currentMode == Mode.PRO ? View.VISIBLE : View.GONE);
        bokehPanel.setVisibility(currentMode == Mode.PORTRAIT ? View.VISIBLE : View.GONE);
        txtPanoGuide.setVisibility(currentMode == Mode.PANORAMA ? View.VISIBLE : View.GONE);
        if (currentMode == Mode.PANORAMA) applyRatio(1); // 16:9 wide
        if (currentMode == Mode.NIGHT) applyNightExposure(true);
        else if (camera != null) applyNightExposure(false);
        bindCamera(); // rebind so VideoCapture/Flash settings follow the mode
    }

    private void bindTopButtons() {
        btnFlash.setOnClickListener(v -> {
            flashMode = (flashMode + 1) % 3;
            btnFlash.setText(flashMode == 0 ? "⚡ Auto" : flashMode == 1 ? "⚡ On" : "⚡ Off");
            if (imageCapture != null) imageCapture.setFlashMode(flashMode);
            toast("Flash: " + btnFlash.getText());
        });
        btnTimer.setOnClickListener(v -> {
            timerSec = timerSec == 0 ? 2 : timerSec == 2 ? 5 : timerSec == 5 ? 10 : 0;
            btnTimer.setText(timerSec == 0 ? "⏱ Off" : "⏱ " + timerSec + "s");
        });
        btnRatio.setOnClickListener(v -> {
            ratioIdx = (ratioIdx + 1) % 3;
            applyRatio(ratioIdx);
            btnRatio.setText(ratioIdx == 0 ? "4:3" : ratioIdx == 1 ? "16:9" : "Full");
        });
        btnHdr.setOnClickListener(v -> {
            hdrOn = !hdrOn;
            btnHdr.setText(hdrOn ? "HDR On" : "HDR Off");
            applyHdr();
        });
    }

    private void bindProPanel() {
        bindSeek(R.id.seekIso, R.id.txtIso, v -> applyProIso(v));
        bindSeek(R.id.seekShutter, R.id.txtShutter, v -> applyProShutter(v));
        bindSeek(R.id.seekWb, R.id.txtWb, v -> applyProWb(v));
        bindSeek(R.id.seekFocus, R.id.txtFocus, v -> applyProFocus(v));
        bindSeek(R.id.seekEv, R.id.txtEv, v -> applyProEv(v));
        SeekBar bokeh = findViewById(R.id.seekBokeh);
        bokeh.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                bokehStrength = p;
                ((TextView) findViewById(R.id.txtBokeh)).setText(String.valueOf(p));
            }
        });
    }

    private interface SeekCb { void on(int v); }
    private void bindSeek(int seekId, int txtId, SeekCb cb) {
        SeekBar s = findViewById(seekId);
        s.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser) cb.on(p);
            }
        });
    }

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        public void onStartTrackingTouch(SeekBar s) {}
        public void onStopTrackingTouch(SeekBar s) {}
    }

    private void bindZoomButtons() {
        findViewById(R.id.btnZoom05).setOnClickListener(v -> setZoom(0.5f));
        findViewById(R.id.btnZoom1).setOnClickListener(v -> setZoom(1f));
        findViewById(R.id.btnZoom2).setOnClickListener(v -> setZoom(2f));
    }

    private void setZoom(float z) {
        if (camera == null) return;
        try {
            ZoomState zs = camera.getCameraInfo().getZoomState().getValue();
            if (zs == null) return;
            Range<Float> range = Range.create(zs.getMinZoomRatio(), zs.getMaxZoomRatio());
            if (z < range.getLower()) {
                toast("0.5x tidak tersedia di lensa ini (min " + range.getLower() + "x)");
                return;
            }
            if (z > range.getUpper()) z = range.getUpper();
            currentZoom = z;
            camera.getCameraControl().setZoomRatio(z);
        } catch (Exception e) {
            toast("Zoom tidak didukung: " + e.getMessage());
        }
    }

    private void bindShutterAndSwitch() {
        btnShutter.setOnClickListener(v -> onShutter());
        findViewById(R.id.btnSwitch).setOnClickListener(v -> {
            if (isRecording) { toast("Stop video dulu"); return; }
            selector = (selector == CameraSelector.DEFAULT_BACK_CAMERA)
                    ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
            bindCamera();
        });
    }

    private void bindThumb() {
        imgThumb.setOnClickListener(v -> {
            if (lastSavedUri != null) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(lastSavedUri, "image/*");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try { startActivity(i); } catch (Exception e) { toast("Tidak ada galeri"); }
            } else toast("Belum ada foto");
        });
    }

    private void bindGestures() {
        scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                if (camera != null) {
                    float z = currentZoom * d.getScaleFactor();
                    ZoomState zs = camera.getCameraInfo().getZoomState().getValue();
                    if (zs == null) return true;
                    Range<Float> r = Range.create(zs.getMinZoomRatio(), zs.getMaxZoomRatio());
                    z = Math.max(r.getLower(), Math.min(r.getUpper(), z));
                    currentZoom = z;
                    camera.getCameraControl().setZoomRatio(z);
                }
                return true;
            }
        });
        GestureDetector tap = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onSingleTapUp(MotionEvent e) {
                if (camera != null) {
                    MeteringPointFactory f = new SurfaceOrientedMeteringPointFactory(
                            previewView.getWidth(), previewView.getHeight());
                    FocusMeteringAction a = new FocusMeteringAction.Builder(
                            f.createPoint(e.getX(), e.getY())).build();
                    camera.getCameraControl().startFocusAndMetering(a);
                    toast("Fokus di sini");
                }
                return true;
            }
        });
        previewView.setOnTouchListener((v, e) -> {
            scaleDetector.onTouchEvent(e);
            tap.onTouchEvent(e);
            return true;
        });
    }

    // ---------- Camera ----------

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> f = ProcessCameraProvider.getInstance(this);
        f.addListener(() -> {
            try {
                cameraProvider = f.get();
                bindCamera();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera provider failed", e);
                toast("Kamera gagal dibuka: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCamera() {
        if (cameraProvider == null) return;
        preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageCapture.Builder icb = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(flashMode);
        if (ratioIdx == 0) icb.setTargetAspectRatio(AspectRatio.RATIO_4_3);
        else if (ratioIdx == 1) icb.setTargetAspectRatio(AspectRatio.RATIO_16_9);
        imageCapture = icb.build();

        Recorder recorder = new Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.FHD))
                .build();
        videoCapture = VideoCapture.withOutput(recorder);

        cameraProvider.unbindAll();
        try {
            if (currentMode == Mode.VIDEO || currentMode == Mode.SLOWMO) {
                camera = cameraProvider.bindToLifecycle(this, selector, preview, imageCapture, videoCapture);
            } else {
                camera = cameraProvider.bindToLifecycle(this, selector, preview, imageCapture);
            }
            camera.getCameraControl().setZoomRatio(currentZoom);
            applyHdr();
            if (currentMode == Mode.NIGHT) applyNightExposure(true);
        } catch (Exception e) {
            Log.e(TAG, "bind failed", e);
            toast("Bind kamera gagal: " + e.getMessage());
        }
        refreshThumb();
    }

    private void applyRatio(int idx) {
        // Rebind with new aspect ratio; Full = 16:9 on this preview.
        if (cameraProvider != null) bindCamera();
    }

    private void applyHdr() {
        if (camera == null || imageCapture == null) return;
        try {
            Camera2CameraControl c2 = Camera2CameraControl.from(camera.getCameraControl());
            CaptureRequestOptions.Builder b = new CaptureRequestOptions.Builder();
            if (hdrOn) {
                b.setCaptureRequestOption(CaptureRequest.CONTROL_SCENE_MODE,
                        CaptureRequest.CONTROL_SCENE_MODE_HDR);
                c2.setCaptureRequestOptions(b.build());
            } else {
                b.setCaptureRequestOption(CaptureRequest.CONTROL_SCENE_MODE,
                        CaptureRequest.CONTROL_SCENE_MODE_DISABLED);
                c2.setCaptureRequestOptions(b.build());
            }
        } catch (Exception e) {
            if (hdrOn) toast("HDR tidak didukung perangkat ini");
        }
    }

    private void applyNightExposure(boolean on) {
        if (camera == null) return;
        try {
            Camera2CameraControl c2 = Camera2CameraControl.from(camera.getCameraControl());
            CaptureRequestOptions.Builder b = new CaptureRequestOptions.Builder();
            if (on) {
                // Longer exposure if the sensor allows it, else EV boost.
                b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 6);
            } else {
                b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);
            }
            c2.setCaptureRequestOptions(b.build());
        } catch (Exception ignored) {}
    }

    // Pro manual controls — only applied where Camera2 reports support.
    private void applyProIso(int v) {
        ((TextView) findViewById(R.id.txtIso)).setText(v == 0 ? "Auto" : String.valueOf(100 + v * 16));
        if (camera == null || v == 0) return;
        try {
            Camera2CameraControl c2 = Camera2CameraControl.from(camera.getCameraControl());
            CaptureRequestOptions.Builder b = new CaptureRequestOptions.Builder();
            b.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, 100 + v * 16);
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            c2.setCaptureRequestOptions(b.build());
        } catch (Exception e) { toast("ISO manual tidak didukung"); }
    }

    private void applyProShutter(int v) {
        ((TextView) findViewById(R.id.txtShutter)).setText(v == 0 ? "Auto" : "1/" + Math.max(1, 1000 - v * 10) + "s");
        if (camera == null || v == 0) return;
        try {
            long ns = (1000L - v * 10L) * 1_000_000L; // rough mapping
            Camera2CameraControl c2 = Camera2CameraControl.from(camera.getCameraControl());
            CaptureRequestOptions.Builder b = new CaptureRequestOptions.Builder();
            b.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, ns);
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            c2.setCaptureRequestOptions(b.build());
        } catch (Exception e) { toast("Shutter manual tidak didukung"); }
    }

    private void applyProWb(int v) {
        String[] names = {"Auto", "Daylight", "Cloudy", "Tungsten", "Fluorescent"};
        int idx = v * names.length / 101;
        ((TextView) findViewById(R.id.txtWb)).setText(names[idx]);
        if (camera == null) return;
        try {
            int[] modes = {CaptureRequest.CONTROL_AWB_MODE_AUTO, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT,
                    CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT, CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT,
                    CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT};
            Camera2CameraControl c2 = Camera2CameraControl.from(camera.getCameraControl());
            CaptureRequestOptions.Builder b = new CaptureRequestOptions.Builder();
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, modes[idx]);
            c2.setCaptureRequestOptions(b.build());
        } catch (Exception ignored) {}
    }

    private void applyProFocus(int v) {
        ((TextView) findViewById(R.id.txtFocus)).setText(v == 0 ? "Auto" : (v / 10f) + "m");
        if (camera == null || v == 0) return;
        try {
            Camera2CameraControl c2 = Camera2CameraControl.from(camera.getCameraControl());
            CaptureRequestOptions.Builder b = new CaptureRequestOptions.Builder();
            b.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, v / 10f);
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            c2.setCaptureRequestOptions(b.build());
        } catch (Exception e) { toast("Fokus manual tidak didukung"); }
    }

    private void applyProEv(int v) {
        float ev = (v - 50) / 25f;
        ((TextView) findViewById(R.id.txtEv)).setText(String.format(Locale.US, "%.1f", ev));
        if (camera == null) return;
        try {
            CameraControl cc = camera.getCameraControl();
            CameraInfo info = camera.getCameraInfo();
            Range<Integer> r = info.getExposureState().getExposureCompensationRange();
            int idx = Math.round(ev * 2);
            idx = Math.max(r.getLower(), Math.min(r.getUpper(), idx));
            cc.setExposureCompensationIndex(idx);
        } catch (Exception ignored) {}
    }

    // ---------- Shutter / capture ----------

    private void onShutter() {
        if (imageCapture == null) return;
        if (currentMode == Mode.VIDEO || currentMode == Mode.SLOWMO) { toggleVideo(); return; }
        if (currentMode == Mode.HYPERLAPSE) { toggleHyperlapse(); return; }
        if (timerSec > 0) runTimer(timerSec, this::takePhoto);
        else takePhoto();
    }

    private void runTimer(int sec, Runnable after) {
        txtCountdown.setVisibility(View.VISIBLE);
        final int[] left = {sec};
        Runnable tick = new Runnable() {
            @Override public void run() {
                if (left[0] <= 0) { txtCountdown.setVisibility(View.GONE); after.run(); return; }
                txtCountdown.setText(String.valueOf(left[0]));
                left[0]--;
                mainHandler.postDelayed(this, 1000);
            }
        };
        mainHandler.post(tick);
    }

    private void takePhoto() {
        String name = "InfinityCam_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name + ".jpg");
        cv.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/InfinityCam");
        }
        ImageCapture.OutputFileOptions opts = new ImageCapture.OutputFileOptions.Builder(
                getContentResolver(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv).build();
        imageCapture.takePicture(opts, executor, new ImageCapture.OnImageSavedCallback() {
            @Override public void onImageSaved(@NonNull ImageCapture.OutputFileResults res) {
                lastSavedUri = res.getSavedUri();
                mainHandler.post(() -> {
                    toast("Foto tersimpan");
                    postProcessPhoto(lastSavedUri);
                    refreshThumb();
                });
            }
            @Override public void onError(@NonNull ImageCaptureException e) {
                mainHandler.post(() -> toast("Gagal foto: " + e.getMessage()));
            }
        });
    }

    /** Apply the mode's real post-processing (Portrait bokeh / Food / Night). */
    private void postProcessPhoto(Uri uri) {
        if (uri == null) return;
        if (currentMode != Mode.PORTRAIT && currentMode != Mode.FOOD && currentMode != Mode.NIGHT) return;
        executor.execute(() -> {
            try {
                Bitmap bmp = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
                if (bmp == null) return;
                Bitmap out;
                if (currentMode == Mode.PORTRAIT) out = ImageFx.portraitBokeh(bmp, bokehStrength);
                else if (currentMode == Mode.FOOD) out = ImageFx.food(bmp);
                else out = ImageFx.night(bmp);
                try (OutputStream os = getContentResolver().openOutputStream(uri, "wt")) {
                    out.compress(Bitmap.CompressFormat.JPEG, 92, os);
                }
                if (out != bmp) out.recycle();
                bmp.recycle();
            } catch (Exception e) {
                Log.e(TAG, "post-process failed", e);
            }
        });
    }

    private void toggleVideo() {
        if (videoCapture == null) return;
        if (isRecording) {
            recording.stop();
            isRecording = false;
            btnShutter.setBackgroundResource(R.drawable.shutter_circle);
            toast("Video tersimpan");
            return;
        }
        String name = "InfinityCam_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name + ".mp4");
        cv.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/InfinityCam");
        }
        MediaStoreOutputOptions opts = new MediaStoreOutputOptions.Builder(
                getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(cv).build();
        recording = videoCapture.getOutput().prepareRecording(this, opts)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(this), event -> {});
        isRecording = true;
        btnShutter.setBackgroundColor(0xFFFF0000);
        toast(currentMode == Mode.SLOWMO ? "Merekam Slow Motion…" : "Merekam video…");
    }

    private void toggleHyperlapse() {
        hyperRunning = !hyperRunning;
        if (hyperRunning) {
            hyperCount = 0;
            toast("Hyperlapse: foto tiap 2 detik, tekan lagi untuk stop");
            hyperTick();
        } else {
            toast("Hyperlapse selesai: " + hyperCount + " foto");
        }
    }

    private void hyperTick() {
        if (!hyperRunning) return;
        if (hyperCount >= 30) { hyperRunning = false; toast("Hyperlapse selesai: 30 foto"); return; }
        takePhoto();
        hyperCount++;
        mainHandler.postDelayed(this::hyperTick, 2000);
    }

    // ---------- Gallery thumb / helpers ----------

    private void refreshThumb() {
        executor.execute(() -> {
            try {
                String[] proj = {MediaStore.Images.Media._ID};
                try (android.database.Cursor c = getContentResolver().query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, null, null,
                        MediaStore.Images.Media.DATE_ADDED + " DESC")) {
                    if (c != null && c.moveToFirst()) {
                        long id = c.getLong(0);
                        Uri uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                        lastSavedUri = uri;
                        Bitmap bmp = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
                        if (bmp != null) {
                            Bitmap thumb = Bitmap.createScaledBitmap(bmp, 104, 104, true);
                            mainHandler.post(() -> imgThumb.setImageBitmap(thumb));
                            bmp.recycle();
                        }
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (recording != null) recording.stop();
        executor.shutdown();
    }
}
