package com.example.controlcenter;

import android.accessibilityservice.AccessibilityService;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.res.Configuration;
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
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

public class OverlayService extends AccessibilityService {
    /** Gelap latar: 00 = bening penuh, FF = pekat. */
    private static final int BACKGROUND = 0x59000000;
    /** Warna kartu kaca. */
    private static final int GLASS = 0x38FFFFFF;
    private static final int ACCENT = 0xFF2F8CFF;

    private WindowManager wm;
    private View handle, panel;
    private CameraManager camera;
    private AudioManager audio;
    private String torchId;
    private boolean torchOn;
    private ImageView torchTile;

    private final CameraManager.TorchCallback torchCallback = new CameraManager.TorchCallback() {
        @Override public void onTorchModeChanged(String id, boolean enabled) {
            if (!id.equals(torchId)) return;
            torchOn = enabled;
            if (torchTile != null) paint(torchTile, enabled, 0xFFFFC247);
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

    private GradientDrawable shape(int color, int radius) {
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

        DisplayMetrics screen = getResources().getDisplayMetrics();
        int width = Math.min(screen.widthPixels - dp(44), dp(400));
        int gap = dp(14);
        // Ukuran tombol mengikuti lebar layar, tetapi tetap muat secara tinggi.
        int byHeight = (screen.heightPixels - dp(290)) / 5 - gap;
        int unit = Math.max(dp(52), Math.min((width - 3 * gap) / 4, byHeight));
        width = unit * 4 + gap * 3;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClickable(true);

        // Kartu musik
        MusicCard music = new MusicCard(this, () -> {
            toast("Buka aplikasi dan berikan izin nomor 4");
            launch(new Intent(this, MainActivity.class));
        }, this::hidePanel);
        LinearLayout.LayoutParams card = new LinearLayout.LayoutParams(width, -2);
        card.bottomMargin = gap;
        box.addView(music, card);

        // Baris koneksi
        GridLayout net = grid(box, width, gap);
        boolean airplane = Settings.Global.getInt(getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
        boolean bluetooth = Settings.Global.getInt(getContentResolver(), "bluetooth_on", 0) == 1;
        WifiManager wifi = getApplicationContext().getSystemService(WifiManager.class);
        boolean online = getSystemService(ConnectivityManager.class).getActiveNetwork() != null;
        toggle(net, R.drawable.ic_airplane, "Mode pesawat", unit, gap, airplane, 0xFFFF9F43,
                v -> launch(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)));
        toggle(net, R.drawable.ic_wifi, "Wi-Fi", unit, gap, wifi.isWifiEnabled(), ACCENT,
                v -> launch(new Intent(Settings.Panel.ACTION_WIFI)));
        toggle(net, R.drawable.ic_internet, "Internet", unit, gap, online, 0xFF2ECC71,
                v -> launch(new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)));
        toggle(net, R.drawable.ic_bluetooth, "Bluetooth", unit, gap, bluetooth, ACCENT,
                v -> launch(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));

        // Tombol 2x2 dan dua slider vertikal
        LinearLayout middle = new LinearLayout(this);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(width, unit * 2 + gap);
        mp.bottomMargin = gap;
        box.addView(middle, mp);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        middle.addView(grid, new LinearLayout.LayoutParams(unit * 2 + gap * 2, -1));

        ImageView rotate = round(grid, R.drawable.ic_rotation, "Kunci rotasi", unit, gap, 0);
        paint(rotate, Settings.System.getInt(getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 1) == 0, 0xFFFF5C6C);
        rotate.setOnClickListener(this::toggleRotation);

        ImageView dnd = round(grid, R.drawable.ic_moon, "Jangan ganggu", unit, gap, 1);
        paint(dnd, dndOn(), 0xFF8B6CFF);
        dnd.setOnClickListener(this::toggleDnd);

        torchTile = round(grid, R.drawable.ic_flashlight, "Senter", unit, gap, 2);
        paint(torchTile, torchOn, 0xFFFFC247);
        torchTile.setOnClickListener(v -> {
            if (torchId == null) { toast("Senter tidak tersedia"); return; }
            try { camera.setTorchMode(torchId, !torchOn); }
            catch (Exception e) { toast("Kamera sedang dipakai"); }
        });

        ImageView ring = round(grid, R.drawable.ic_bell, "Mode getar", unit, gap, 3);
        paint(ring, audio.getRingerMode() != AudioManager.RINGER_MODE_NORMAL, 0xFFFF5C6C);
        ring.setOnClickListener(v -> {
            boolean normal = audio.getRingerMode() == AudioManager.RINGER_MODE_NORMAL;
            try {
                audio.setRingerMode(normal ? AudioManager.RINGER_MODE_VIBRATE
                        : AudioManager.RINGER_MODE_NORMAL);
                paint(v, normal, 0xFFFF5C6C);
            } catch (SecurityException e) { toast("Izin Jangan Ganggu diperlukan"); }
        });

        int bright = Settings.System.getInt(getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, 128);
        VerticalSlider light = new VerticalSlider(this, R.drawable.ic_sun,
                Math.min(1f, bright / 255f), value -> {
            if (!Settings.System.canWrite(this)) return;
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS,
                    Math.max(1, Math.round(value * 255)));
        }, this::canWrite);
        light.setContentDescription("Kecerahan");
        middle.addView(light, new LinearLayout.LayoutParams(unit, -1));

        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        VerticalSlider sound = new VerticalSlider(this, R.drawable.ic_volume,
                audio.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max, value -> {
            try {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(value * max), 0);
            } catch (SecurityException e) { toast("Izin Jangan Ganggu diperlukan"); }
        }, null);
        sound.setContentDescription("Volume");
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(unit, -1);
        sp.leftMargin = gap;
        middle.addView(sound, sp);

        // Dua baris pintasan
        GridLayout bottom = grid(box, width, gap);
        action(bottom, R.drawable.ic_calculator, "Kalkulator", unit, gap,
                v -> launch(Intent.makeMainSelectorActivity(
                        Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)));
        action(bottom, R.drawable.ic_camera, "Kamera", unit, gap,
                v -> launch(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)));
        action(bottom, R.drawable.ic_timer, "Alarm", unit, gap,
                v -> launch(new Intent(AlarmClock.ACTION_SHOW_ALARMS)));
        action(bottom, R.drawable.ic_screenshot, "Tangkapan layar", unit, gap, v -> {
            hidePanel();
            new Handler(Looper.getMainLooper()).postDelayed(
                    () -> performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT), 600);
        });
        action(bottom, R.drawable.ic_battery, "Penghemat baterai", unit, gap,
                v -> launch(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)));
        action(bottom, R.drawable.ic_settings, "Pengaturan", unit, gap,
                v -> launch(new Intent(Settings.ACTION_SETTINGS)));
        action(bottom, R.drawable.ic_lock, "Kunci layar", unit, gap, v -> {
            hidePanel();
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        });
        action(bottom, R.drawable.ic_power, "Menu daya", unit, gap, v -> {
            hidePanel();
            performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
        });

        // Ketuk area kosong atau geser ke atas untuk menutup.
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(0, dp(36), 0, dp(24));
        wrap.addView(box, new FrameLayout.LayoutParams(width, -2, Gravity.CENTER));
        final float[] startY = new float[1];
        wrap.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) startY[0] = e.getRawY();
            if (e.getAction() == MotionEvent.ACTION_UP
                    && e.getRawY() - startY[0] < dp(20)) hidePanel();
            return true;
        });
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(wrap);
        back.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        if (Build.VERSION.SDK_INT >= 30) lp.setFitInsetsTypes(0);
        lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        // Efek blur kaca hanya didukung Android 12+ (dan tergantung perangkat).
        if (Build.VERSION.SDK_INT >= 31) {
            lp.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
            lp.setBlurBehindRadius(dp(40));
        }
        panel = back;
        wm.addView(panel, lp);
        back.setAlpha(0f);
        back.animate().alpha(1f).setDuration(200).start();
        box.setTranslationY(-dp(60));
        box.setScaleX(0.94f);
        box.setScaleY(0.94f);
        box.animate().translationY(0).scaleX(1f).scaleY(1f).setDuration(320)
                .setInterpolator(new DecelerateInterpolator(2f)).start();
    }

    private void hidePanel() {
        if (panel == null) return;
        wm.removeView(panel);
        panel = null;
        torchTile = null;
    }

    private GridLayout grid(LinearLayout parent, int width, int gap) {
        GridLayout g = new GridLayout(this);
        g.setColumnCount(4);
        parent.addView(g, new LinearLayout.LayoutParams(width + gap, -2));
        return g;
    }

    private ImageView icon(int res, String label, int size, int padding) {
        ImageView i = new ImageView(this);
        i.setImageResource(res);
        i.setContentDescription(label);
        i.setPadding(padding, padding, padding, padding);
        i.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        press(i);
        return i;
    }

    private void toggle(GridLayout grid, int res, String label, int size, int gap, boolean on,
            int color, View.OnClickListener click) {
        ImageView i = action(grid, res, label, size, gap, click);
        paint(i, on, color);
    }

    private ImageView action(GridLayout grid, int res, String label, int size, int gap,
            View.OnClickListener click) {
        ImageView i = round(grid, res, label, size, gap, grid.getChildCount());
        i.setOnClickListener(click);
        return i;
    }

    private ImageView round(GridLayout grid, int res, String label, int size, int gap, int index) {
        ImageView i = icon(res, label, size, size / 4);
        paint(i, false, ACCENT);
        GridLayout.LayoutParams p = new GridLayout.LayoutParams();
        p.width = size;
        p.height = size;
        int columns = grid.getColumnCount();
        p.setMargins(0, 0, gap, columns == 4 || index < 2 ? gap : 0);
        grid.addView(i, p);
        return i;
    }

    /** Efek mengecil saat ditekan. */
    private void press(View view) {
        view.setOnTouchListener((v, e) -> {
            int a = e.getAction();
            if (a == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90).start();
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
            }
            return false;
        });
    }

    private void paint(View v, boolean on, int color) {
        GradientDrawable d = shape(on ? color : GLASS, 100);
        d.setStroke(dp(1), 0x45FFFFFF);
        v.setBackground(d);
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
        paint(v, auto, 0xFFFF5C6C);
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
        paint(v, on, 0xFF8B6CFF);
    }

    private void launch(Intent i) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) { toast("Aplikasi tidak ditemukan"); }
        hidePanel();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
