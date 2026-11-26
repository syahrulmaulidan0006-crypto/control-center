package com.example.controlcenter;

import android.accessibilityservice.AccessibilityService;
import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.app.KeyguardManager;
import android.os.PowerManager;
import android.service.notification.StatusBarNotification;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.DecelerateInterpolator;

public class OverlayService extends AccessibilityService {
    /** Gelap latar: 00 = bening penuh, FF = pekat. */
    private static final int BACKGROUND = 0x59000000;

    private WindowManager wm;
    private static OverlayService instance;
    private View handle, leftHandle;
    private Sheet panel;
    private Deck deck;
    private Banner banner;
    private WindowManager.LayoutParams bannerParams;
    private WindowManager.LayoutParams panelParams;
    private Controls controls;
    private ValueAnimator animator;
    private int blur;
    private boolean closing;

    /** Dikirim layar pengaturan saat area geser perlu dibuat ulang. */
    static final String REFRESH = "com.example.controlcenter.REFRESH";
    private final BroadcastReceiver refresh = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            MusicListener.applyHints();
            removePanel();
            removeHandles();
            showHandles();
        }
    };

    /** Memeriksa jadwal tidur tiap menit dan saat layar menyala. */
    private final BroadcastReceiver ticker = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            Focus.check(OverlayService.this, controls);
        }
    };

    private ChargeView charging;
    private final BroadcastReceiver power = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { showCharging(); }
    };

    private void showCharging() {
        if (!Prefs.is(this, "chargeAnim", true) || charging != null
                || !getSystemService(PowerManager.class).isInteractive()) return;
        if (Prefs.is(this, "hideFullscreen", true) && fullscreen()) return;
        // Sumber yang sama dengan angka baterai di status bar.
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) return;
        int level = battery.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0) * 100
                / Math.max(1, battery.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100));
        Glass.load(this);
        ChargeView view = new ChargeView(this, Math.max(0, Math.min(100, level)));
        // Tidak menerima sentuhan, jadi layar di bawahnya tetap bisa dipakai.
        WindowManager.LayoutParams lp = params(dp(220), dp(220),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        lp.gravity = Gravity.CENTER;
        try { wm.addView(view, lp); }
        catch (RuntimeException e) { return; }
        charging = view;
        android.util.Log.i("ControlCenter", "animasi cas: " + level);
        view.play(this::removeCharging);
    }

    private void removeCharging() {
        if (charging != null && charging.isAttachedToWindow()) wm.removeView(charging);
        charging = null;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override public void onInterrupt() { }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        if (handle != null) return;
        wm = getSystemService(WindowManager.class);
        controls = new Controls(this);
        instance = this;
        showHandles();
        keepAlive();
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(refresh, new IntentFilter(REFRESH), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(refresh, new IntentFilter(REFRESH));
        }
        IntentFilter time = new IntentFilter(Intent.ACTION_TIME_TICK);
        time.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(ticker, time, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(ticker, time);
        }
        Focus.check(this, controls);
        IntentFilter plugged = new IntentFilter(Intent.ACTION_POWER_CONNECTED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(power, plugged, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(power, plugged);
        }
        android.service.notification.NotificationListenerService.requestRebind(
                new android.content.ComponentName(this, MusicListener.class));
    }

    /** Notifikasi latar depan agar sistem tidak mudah mematikan layanan. */
    private void keepAlive() {
        try {
            getSystemService(NotificationManager.class).createNotificationChannel(
                    new NotificationChannel("aktif", "Control Center aktif",
                            NotificationManager.IMPORTANCE_LOW));
            Notification n = new Notification.Builder(this, "aktif")
                    .setContentTitle("Control Center aktif")
                    .setOngoing(true)
                    .setSmallIcon(R.drawable.ic_settings)
                    .setContentIntent(PendingIntent.getActivity(this, 0,
                            new Intent(this, MainActivity.class),
                            PendingIntent.FLAG_IMMUTABLE))
                    .build();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(1, n);
            }
        } catch (Exception ignored) {
            // Panel tetap bekerja tanpa notifikasi.
        }
    }

    @Override public void onDestroy() {
        if (instance != null) {
            unregisterReceiver(refresh);
            unregisterReceiver(ticker);
            unregisterReceiver(power);
        }
        instance = null;
        removePanel();
        removeCharging();
        if (banner != null) removeBanner(banner);
        removeHandles();
        if (controls != null) controls.release();
        super.onDestroy();
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if (handle == null) return;
        // Perubahan mode gelap tidak perlu menutup panel.
        if (handle.getTag().equals(config.orientation)) return;
        removePanel();
        removeHandles();
        showHandles();
    }

    private void showHandles() {
        handle = showHandle(false);
        leftHandle = showHandle(true);
    }

    private void removeHandles() {
        if (handle != null) wm.removeView(handle);
        if (leftHandle != null) wm.removeView(leftHandle);
        handle = null;
        leftHandle = null;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private WindowManager.LayoutParams params(int w, int h, int flags) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                flags | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        if (Build.VERSION.SDK_INT >= 30) lp.setFitInsetsTypes(0);
        lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        return lp;
    }

    /** Area transparan di status bar: kanan Control Center, kiri pusat notifikasi. */
    private View showHandle(boolean left) {
        if (left && !Prefs.is(this, "leftSwipe", true)) return null;
        boolean land = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        // Saat mendatar (game): "kecil" (bawaan) atau mati, agar tombol game tetap bisa disentuh.
        boolean offInLand = Prefs.is(this, "noSwipeLand", false);
        View area = new View(this);
        area.setTag(getResources().getConfiguration().orientation);
        int width = getResources().getDisplayMetrics().widthPixels * 2 / 5;
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        int height = id > 0 ? getResources().getDimensionPixelSize(id) : dp(28);
        if (land) {
            width = dp(56);
            height = dp(10);
        }
        WindowManager.LayoutParams lp = params(width, height,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        lp.gravity = Gravity.TOP | (left ? Gravity.LEFT : Gravity.RIGHT);
        if (land && offInLand) lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

        final float[] start = new float[1];
        final boolean[] active = new boolean[1];
        final VelocityTracker[] tracker = new VelocityTracker[1];
        area.setOnTouchListener((v, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                start[0] = e.getRawY();
                active[0] = false;
                tracker[0] = VelocityTracker.obtain();
            }
            if (tracker[0] == null) return true;
            MotionEvent raw = MotionEvent.obtain(e);
            raw.setLocation(e.getRawX(), e.getRawY());
            tracker[0].addMovement(raw);
            raw.recycle();
            float dy = e.getRawY() - start[0];
            if (a == MotionEvent.ACTION_MOVE) {
                if (!active[0] && dy > dp(8)) {
                    active[0] = true;
                    addPanel(left);
                }
                if (active[0]) drag(Math.min(1f, dy / dp(280)));
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                tracker[0].computeCurrentVelocity(1000);
                float speed = tracker[0].getYVelocity();
                tracker[0].recycle();
                tracker[0] = null;
                if (a == MotionEvent.ACTION_CANCEL && dy > dp(16)) {
                    // Sistem merebut geseran untuk membuka panel bawaan: ambil alih kembali.
                    if (!active[0]) addPanel(left);
                    openFully();
                    dismissNative();
                    return true;
                }
                boolean open = speed > dp(400) || dy > dp(130);
                if (speed < -dp(400)) open = false;
                if (active[0]) {
                    if (open) openFully(); else closePanel();
                } else if (a == MotionEvent.ACTION_UP && dy > dp(40)) {
                    // Geseran sangat cepat bisa tiba tanpa kejadian gerak sama sekali.
                    addPanel(left);
                    openFully();
                }
            }
            return true;
        });
        wm.addView(area, lp);
        return area;
    }

    private void addPanel(boolean notifications) {
        removePanel();
        if (banner != null) banner.leave();
        Glass.load(this);
        panel = notifications ? new Center(this, controls) : new Panel(this, controls);
        android.util.Log.i("ControlCenter", "panel dibuka, pusat=" + notifications);
        panel.setBackground(new ColorDrawable(BACKGROUND));
        panelParams = params(-1, -1, WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        // Efek blur kaca hanya didukung Android 12+ (dan tergantung perangkat).
        if (Build.VERSION.SDK_INT >= 31) {
            panelParams.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
            panelParams.setBlurBehindRadius(1);
        }
        blur = 1;
        deck = new Deck(this, panel);
        panel.setProgress(0);
        deck.fade(0);
        wm.addView(deck, panelParams);
    }

    /** Menutup panel notifikasi bawaan yang ikut terbuka (Android 12+). */
    private void dismissNative() {
        if (Build.VERSION.SDK_INT < 31 || handle == null) return;
        Runnable dismiss = () -> performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE);
        dismiss.run();
        handle.postDelayed(dismiss, 120);
        handle.postDelayed(dismiss, 400);
    }

    /** Izinkan keyboard muncul untuk kolom kata sandi. */
    void setKeyboard(boolean enabled) {
        if (panel == null) return;
        int flag = WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        boolean has = (panelParams.flags & flag) != 0;
        if (has == !enabled) return;
        panelParams.flags ^= flag;
        panelParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
        wm.updateViewLayout(deck, panelParams);
    }

    /** Tampilkan popup notifikasi bila keadaan layar memungkinkan. */
    static boolean running() { return instance != null; }

    static boolean banner(StatusBarNotification note) {
        return instance != null && instance.showBanner(note);
    }

    private boolean showBanner(StatusBarNotification note) {
        if (!Prefs.is(this, "popup", true)) return skip("popup dimatikan di pengaturan");
        if (panel != null) return skip("panel sedang terbuka");
        if (!getSystemService(PowerManager.class).isInteractive()
                || getSystemService(KeyguardManager.class).isKeyguardLocked()) {
            return skip("layar mati atau terkunci");
        }
        if (Prefs.is(this, "hideFullscreen", true) && fullscreen()) {
            return skip("layar mendatar (mode game)");
        }
        // Jangan ganti popup yang sedang dipakai mengetik balasan.
        if (banner != null) {
            if ((bannerParams.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0) {
                return skip("sedang mengetik balasan");
            }
            // Popup lama keluar dengan animasi selagi yang baru masuk.
            banner.leave();
        }
        Glass.load(this);
        Banner next = new Banner(this, note);
        bannerParams = params(Math.min(getResources().getDisplayMetrics().widthPixels, dp(440)),
                -2, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        bannerParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        bannerParams.y = id > 0 ? getResources().getDimensionPixelSize(id) : dp(28);
        try { wm.addView(next, bannerParams); }
        catch (RuntimeException e) { return skip("gagal menampilkan: " + e.getMessage()); }
        banner = next;
        next.enter();
        Diag.popup(this, "tampil");
        android.util.Log.i("ControlCenter", "popup tampil: " + note.getPackageName());
        return true;
    }

    private boolean skip(String why) {
        Diag.popup(this, "tidak tampil, " + why);
        android.util.Log.i("ControlCenter", "popup tidak tampil: " + why);
        return false;
    }

    /** Mode game dikenali dari layar mendatar. */
    private boolean fullscreen() {
        return getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
    }

    void bannerKeyboard(Banner target) {
        if (banner != target) return;
        bannerParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        bannerParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        wm.updateViewLayout(banner, bannerParams);
    }

    void removeBanner(Banner target) {
        if (target.isAttachedToWindow()) wm.removeView(target);
        if (banner == target) banner = null;
    }

    void drag(float progress) {
        if (panel == null) return;
        panel.setProgress(progress);
        deck.fade(Math.max(0, Math.min(1, progress)));
        if (Build.VERSION.SDK_INT >= 31) {
            int target = Math.max(1, Math.round(dp(40) * Math.max(0, Math.min(1, progress))));
            if (Math.abs(target - blur) >= dp(3) || progress >= 1 || progress <= 0) {
                blur = target;
                panelParams.setBlurBehindRadius(blur);
                wm.updateViewLayout(deck, panelParams);
            }
        }
    }

    /** Mulai berpindah dari satu halaman ke halaman sebelahnya. */
    boolean startPage(Sheet from) {
        if (panel != from || closing || deck.paging()) return false;
        stopAnimation();
        drag(1);
        deck.begin(() -> neighbor(from));
        return true;
    }

    private Sheet neighbor(Sheet of) {
        Sheet other = of instanceof Center ? new Panel(this, controls)
                : new Center(this, controls);
        other.setBackground(new ColorDrawable(BACKGROUND));
        return other;
    }

    /** Dipanggil setelah panel diam agar pembuatannya tidak mengganggu gerakan. */
    private void prepareNeighbor() {
        Deck target = deck;
        if (target == null) return;
        target.postDelayed(() -> {
            if (deck != target || closing || target.paging() || target.prepared()) return;
            target.prepare(neighbor(panel));
        }, 80);
    }

    void dots(boolean visible) { if (deck != null) deck.dots(visible); }

    void page(float dx) { if (deck != null) deck.drag(dx); }

    void endPage(float velocity, boolean lifted) {
        if (deck == null) return;
        deck.release(velocity, lifted, chosen -> {
            setKeyboard(false);
            panel = chosen;
            prepareNeighbor();
        });
    }

    void stopAnimation() {
        closing = false;
        if (animator == null) return;
        animator.removeAllListeners();
        animator.cancel();
        animator = null;
    }

    private void animateTo(float target, Runnable end) {
        stopAnimation();
        animator = ValueAnimator.ofFloat(panel.progress(), target);
        animator.setDuration(target > 0 ? 380 : 220);
        animator.setInterpolator(target > 0
                ? new android.view.animation.PathInterpolator(0.2f, 0.9f, 0.25f, 1.04f)
                : new DecelerateInterpolator(1.4f));
        animator.addUpdateListener(a -> drag((float) a.getAnimatedValue()));
        if (end != null) {
            animator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator a) { end.run(); }
            });
        }
        animator.start();
    }

    void openFully() {
        if (panel == null) return;
        closing = false;
        animateTo(1, this::prepareNeighbor);
    }

    void closePanel() {
        if (panel == null || closing) return;
        animateTo(0, this::removePanel);
        closing = true;
    }

    private void removePanel() {
        stopAnimation();
        closing = false;
        if (panel == null) return;
        wm.removeView(deck);
        deck = null;
        panel = null;
    }
}
