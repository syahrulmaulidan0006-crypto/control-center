package com.example.controlcenter;

import android.accessibilityservice.AccessibilityService;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.function.IntConsumer;

public class OverlayService extends AccessibilityService {
    /** Ubah angka ini untuk mengatur transparansi (00 = bening, FF = pekat). */
    private static final int BACKGROUND = 0x55000000;
    private static final int TILE = 0x33FFFFFF;
    private static final int TILE_ON = 0xCC2F80ED;

    private WindowManager wm;
    private View handle, panel;
    private CameraManager camera;
    private AudioManager audio;
    private String torchId;
    private boolean torchOn;
    private TextView torchTile;

    private final CameraManager.TorchCallback torchCallback = new CameraManager.TorchCallback() {
        @Override public void onTorchModeChanged(String id, boolean enabled) {
            if (!id.equals(torchId)) return;
            torchOn = enabled;
            if (torchTile != null) paint(torchTile, enabled);
        }
    };

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override public void onInterrupt() { }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        if (handle != null) return;
        wm = getSystemService(WindowManager.class);
        audio = getSystemService(AudioManager.class);
        camera = getSystemService(CameraManager.class);
        try {
            for (String id : camera.getCameraIdList()) {
                Boolean flash = camera.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(flash)) { torchId = id; break; }
            }
            camera.registerTorchCallback(torchCallback, new Handler(Looper.getMainLooper()));
        } catch (Exception ignored) { }
        showHandle();
    }

    @Override public void onDestroy() {
        hidePanel();
        if (handle != null) wm.removeView(handle);
        handle = null;
        if (camera != null) camera.unregisterTorchCallback(torchCallback);
        super.onDestroy();
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if (handle == null) return;
        hidePanel();
        wm.removeView(handle);
        showHandle();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private void showHandle() {
        FrameLayout area = new FrameLayout(this);
        // Area transparan di atas status bar bagian kanan: geseran di sini
        // membuka panel kita, bagian kiri tetap milik sistem (notifikasi).
        int width = getResources().getDisplayMetrics().widthPixels * 2 / 5;
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        int height = Math.max(dp(32), id > 0 ? getResources().getDimensionPixelSize(id) : 0);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(width, height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.RIGHT;
        if (Build.VERSION.SDK_INT >= 30) lp.setFitInsetsTypes(0);
        lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;

        final float[] start = new float[1];
        area.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) start[0] = e.getRawY();
            if (e.getAction() == MotionEvent.ACTION_UP && e.getRawY() - start[0] > dp(20)) showPanel();
            return true;
        });
        handle = area;
        wm.addView(handle, lp);
    }

    private void showPanel() {
        if (panel != null) return;
        FrameLayout back = new FrameLayout(this) {
            @Override public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    if (e.getAction() == KeyEvent.ACTION_UP) hidePanel();
                    return true;
                }
                return super.dispatchKeyEvent(e);
            }
        };
        back.setBackgroundColor(BACKGROUND);
        back.setOnClickListener(v -> hidePanel());

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(16));
        box.setClickable(true);

        LinearLayout r1 = row(box);
        tile(r1, "✈\nPesawat", v -> launch(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)));
        tile(r1, "📶\nWi-Fi", v -> launch(new Intent(Settings.Panel.ACTION_WIFI)));
        tile(r1, "🌐\nInternet", v -> launch(new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)));
        tile(r1, "ᛒ\nBluetooth", v -> launch(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));

        LinearLayout r2 = row(box);
        tile(r2, "⏮\nSebelum", v -> media(KeyEvent.KEYCODE_MEDIA_PREVIOUS));
        tile(r2, "⏯\nPutar", v -> media(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
        tile(r2, "⏭\nBerikut", v -> media(KeyEvent.KEYCODE_MEDIA_NEXT));

        LinearLayout r3 = row(box);
        TextView rotate = tile(r3, "🔒\nKunci Rotasi", this::toggleRotation);
        paint(rotate, Settings.System.getInt(getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 1) == 0);
        TextView dnd = tile(r3, "🌙\nJangan Ganggu", this::toggleDnd);
        paint(dnd, dndOn());

        int bright = Settings.System.getInt(getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, 128);
        slider(box, "☀ Kecerahan", 254, Math.max(0, Math.min(254, bright - 1)), value -> {
            if (!canWrite()) return;
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, value + 1);
        });
        slider(box, "🔊 Volume", audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                audio.getStreamVolume(AudioManager.STREAM_MUSIC), value -> {
            try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0); }
            catch (SecurityException e) { toast("Izin Jangan Ganggu diperlukan"); }
        });

        LinearLayout r4 = row(box);
        torchTile = tile(r4, "🔦\nSenter", v -> {
            if (torchId == null) { toast("Senter tidak tersedia"); return; }
            try { camera.setTorchMode(torchId, !torchOn); }
            catch (Exception e) { toast("Kamera sedang dipakai"); }
        });
        paint(torchTile, torchOn);
        tile(r4, "⏱\nTimer", v -> launch(new Intent(AlarmClock.ACTION_SHOW_ALARMS)));
        tile(r4, "🧮\nKalkulator", v -> launch(Intent.makeMainSelectorActivity(
                Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)));
        tile(r4, "📷\nKamera", v -> launch(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)));

        FrameLayout.LayoutParams boxLp = new FrameLayout.LayoutParams(
                Math.min(getResources().getDisplayMetrics().widthPixels, dp(400)),
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(box);
        boxLp.topMargin = dp(40);
        back.addView(scroll, boxLp);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        // Efek blur kaca hanya didukung Android 12+ (dan tergantung perangkat).
        if (Build.VERSION.SDK_INT >= 31) {
            lp.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
            lp.setBlurBehindRadius(dp(30));
        }
        panel = back;
        wm.addView(panel, lp);
        panel.setAlpha(0f);
        panel.animate().alpha(1f).setDuration(180).start();
    }

    private void hidePanel() {
        if (panel == null) return;
        wm.removeView(panel);
        panel = null;
        torchTile = null;
    }

    private LinearLayout row(LinearLayout parent) {
        LinearLayout r = new LinearLayout(this);
        parent.addView(r, new LinearLayout.LayoutParams(-1, -2));
        return r;
    }

    private TextView tile(LinearLayout row, String text, View.OnClickListener click) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(12);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(TILE, 22));
        t.setOnClickListener(click);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(78), 1);
        p.setMargins(dp(5), dp(5), dp(5), dp(5));
        row.addView(t, p);
        return t;
    }

    private void paint(View v, boolean on) { v.setBackground(round(on ? TILE_ON : TILE, 22)); }

    private void slider(LinearLayout parent, String label, int max, int value, IntConsumer change) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(round(TILE, 22));
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(Color.WHITE);
        box.addView(t);
        SeekBar s = new SeekBar(this);
        s.setMax(max);
        s.setProgress(value);
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int v, boolean fromUser) {
                if (fromUser) change.accept(v);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        box.addView(s, new LinearLayout.LayoutParams(-1, dp(36)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(5), dp(5), dp(5), dp(5));
        parent.addView(box, p);
    }

    private boolean canWrite() {
        if (Settings.System.canWrite(this)) return true;
        toast("Buka aplikasi dan berikan izin nomor 2");
        return false;
    }

    private void toggleRotation(View v) {
        if (!canWrite()) return;
        boolean auto = Settings.System.getInt(getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 1) == 1;
        Settings.System.putInt(getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, auto ? 0 : 1);
        paint(v, auto);
    }

    private boolean dndOn() {
        int f = getSystemService(NotificationManager.class).getCurrentInterruptionFilter();
        return f != NotificationManager.INTERRUPTION_FILTER_ALL
                && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN;
    }

    private void toggleDnd(View v) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (!nm.isNotificationPolicyAccessGranted()) {
            toast("Buka aplikasi dan berikan izin nomor 3");
            return;
        }
        boolean on = !dndOn();
        nm.setInterruptionFilter(on ? NotificationManager.INTERRUPTION_FILTER_PRIORITY
                : NotificationManager.INTERRUPTION_FILTER_ALL);
        paint(v, on);
    }

    private void media(int key) {
        audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
        audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
    }

    private void launch(Intent i) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) { toast("Aplikasi tidak ditemukan"); }
        hidePanel();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
