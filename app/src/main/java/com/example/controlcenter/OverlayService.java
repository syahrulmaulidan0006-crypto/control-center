package com.example.controlcenter;

import android.accessibilityservice.AccessibilityService;
import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
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
    private View handle;
    private Panel panel;
    private WindowManager.LayoutParams panelParams;
    private Controls controls;
    private ValueAnimator animator;
    private int blur;
    private boolean closing;

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override public void onInterrupt() { }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        if (handle != null) return;
        wm = getSystemService(WindowManager.class);
        controls = new Controls(this);
        showHandle();
        keepAlive();
    }

    /** Notifikasi latar depan agar sistem tidak mudah mematikan layanan. */
    private void keepAlive() {
        try {
            getSystemService(NotificationManager.class).createNotificationChannel(
                    new NotificationChannel("cc", "Control Center",
                            NotificationManager.IMPORTANCE_MIN));
            Notification n = new Notification.Builder(this, "cc")
                    .setContentTitle("Control Center aktif")
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
        removePanel();
        if (handle != null) wm.removeView(handle);
        handle = null;
        if (controls != null) controls.release();
        super.onDestroy();
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if (handle == null) return;
        // Perubahan mode gelap tidak perlu menutup panel.
        if (handle.getTag().equals(config.orientation)) return;
        removePanel();
        wm.removeView(handle);
        showHandle();
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

    /** Area transparan di status bar kanan; panel mengikuti geseran jari. */
    private void showHandle() {
        View area = new View(this);
        area.setTag(getResources().getConfiguration().orientation);
        int width = getResources().getDisplayMetrics().widthPixels * 2 / 5;
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        int height = Math.max(dp(32), id > 0 ? getResources().getDimensionPixelSize(id) : 0);
        WindowManager.LayoutParams lp = params(width, height,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        lp.gravity = Gravity.TOP | Gravity.RIGHT;

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
                    addPanel();
                }
                if (active[0]) drag(Math.min(1f, dy / dp(280)));
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                tracker[0].computeCurrentVelocity(1000);
                boolean open = tracker[0].getYVelocity() > dp(400) || dy > dp(130);
                if (tracker[0].getYVelocity() < -dp(400)) open = false;
                tracker[0].recycle();
                tracker[0] = null;
                if (active[0]) { if (open) openFully(); else closePanel(); }
            }
            return true;
        });
        handle = area;
        wm.addView(handle, lp);
    }

    private void addPanel() {
        removePanel();
        panel = new Panel(this, controls);
        panel.setBackground(new ColorDrawable(BACKGROUND));
        panelParams = params(-1, -1, WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        // Efek blur kaca hanya didukung Android 12+ (dan tergantung perangkat).
        if (Build.VERSION.SDK_INT >= 31) {
            panelParams.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
            panelParams.setBlurBehindRadius(1);
        }
        blur = 1;
        panel.setProgress(0);
        wm.addView(panel, panelParams);
    }

    /** Izinkan keyboard muncul untuk kolom kata sandi. */
    void setKeyboard(boolean enabled) {
        if (panel == null) return;
        int flag = WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        boolean has = (panelParams.flags & flag) != 0;
        if (has == !enabled) return;
        panelParams.flags ^= flag;
        panelParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
        wm.updateViewLayout(panel, panelParams);
    }

    void drag(float progress) {
        if (panel == null) return;
        panel.setProgress(progress);
        if (Build.VERSION.SDK_INT >= 31) {
            int target = Math.max(1, Math.round(dp(40) * Math.max(0, Math.min(1, progress))));
            if (Math.abs(target - blur) >= dp(3) || progress >= 1 || progress <= 0) {
                blur = target;
                panelParams.setBlurBehindRadius(blur);
                wm.updateViewLayout(panel, panelParams);
            }
        }
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
        animateTo(1, null);
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
        wm.removeView(panel);
        panel = null;
    }
}
