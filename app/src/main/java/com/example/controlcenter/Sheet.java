package com.example.controlcenter;

import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.ScrollView;

/** Dasar panel layar penuh yang mengikuti jari. */
abstract class Sheet extends FrameLayout {
    final OverlayService service;
    ScrollView scroll;
    private float downX, downY, progress;
    private boolean dragging;
    private VelocityTracker velocity;

    Sheet(OverlayService service) {
        super(service);
        this.service = service;
    }

    /** 0 = tertutup, 1 = terbuka penuh. */
    final void setProgress(float value) {
        progress = value;
        float shown = Math.max(0, Math.min(1, value));
        setAlpha(Math.min(1, shown * 1.4f));
        move(Math.min(1.06f, value), shown);
    }

    final float progress() { return progress; }

    abstract void move(float value, float shown);

    /** Tombol kembali. Mengembalikan true bila lapisan di dalam yang ditutup. */
    boolean back() { return false; }

    boolean canDrag() { return true; }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP && !back()) service.closePanel();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    /** Geser ke atas saat sudah di bagian bawah: panel mengikuti jari. */
    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        if (!canDrag()) return false;
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = e.getRawX();
            downY = e.getRawY();
            dragging = false;
        } else if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float dy = e.getRawY() - downY;
            if (-dy > ViewConfiguration.get(getContext()).getScaledTouchSlop()
                    && -dy > Math.abs(e.getRawX() - downX) * 1.5f
                    && !scroll.canScrollVertically(1)) {
                dragging = true;
                downY = e.getRawY();
                service.stopAnimation();
                velocity = VelocityTracker.obtain();
                return true;
            }
        }
        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!dragging) return super.onTouchEvent(e);
        MotionEvent raw = MotionEvent.obtain(e);
        raw.setLocation(e.getRawX(), e.getRawY());
        velocity.addMovement(raw);
        raw.recycle();
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_MOVE) {
            service.drag(Math.max(0, 1 + Math.min(0, e.getRawY() - downY) / dp(360)));
        } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            velocity.computeCurrentVelocity(1000);
            boolean close = velocity.getYVelocity() < -dp(500) || progress < 0.6f;
            velocity.recycle();
            dragging = false;
            if (close) service.closePanel(); else service.openFully();
        }
        return true;
    }

    final int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
