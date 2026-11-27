package com.example.controlcenter;

import android.app.Dialog;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;

/**
 * Jendela tipis di bawah popup yang memburamkan layar tepat di belakangnya, secara langsung.
 * Hanya jalan di HP Android 12+ yang mengizinkan buram antar jendela.
 */
final class BlurPane implements ViewTreeObserver.OnPreDrawListener {
    private static boolean broken;

    static boolean supported(Context c) {
        return Build.VERSION.SDK_INT >= 31 && !broken
                && c.getSystemService(WindowManager.class).isCrossWindowBlurEnabled();
    }

    private final Dialog dialog;
    private final int[] at = new int[2];
    private View target;
    private int x, y, w = 1, h = 1;
    private float alpha;

    /**
     * Harus dibuat sebelum jendela popupnya, agar berada di bawahnya. Mengembalikan null bila
     * gayanya bening atau HP tidak mendukung.
     */
    static BlurPane make(Context c, float corner) {
        if (Glass.style != PopupGlass.FROSTED || !supported(c)) return null;
        try {
            return new BlurPane(c, corner);
        } catch (RuntimeException e) {
            broken = true;
            android.util.Log.w("ControlCenter", "jendela buram ditolak: " + e);
            return null;
        }
    }

    private BlurPane(Context c, float corner) {
        dialog = new Dialog(c, android.R.style.Theme_Translucent_NoTitleBar);
        Window window = dialog.getWindow();
        window.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        window.setFormat(PixelFormat.TRANSLUCENT);
        // Bentuk buramnya mengikuti sudut latar jendela.
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0x01000000);
        shape.setCornerRadius(corner);
        window.setBackgroundDrawable(shape);
        dialog.setContentView(new View(c));
        WindowManager.LayoutParams p = window.getAttributes();
        p.gravity = Gravity.TOP | Gravity.LEFT;
        p.width = p.height = 1;
        p.alpha = 0;
        p.windowAnimations = 0;
        if (Build.VERSION.SDK_INT >= 30) {
            p.setFitInsetsTypes(0);
            p.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }
        window.setAttributes(p);
        dialog.show();
        window.setLayout(1, 1);
        window.setBackgroundBlurRadius(
                Math.round(22 * c.getResources().getDisplayMetrics().density));
    }

    /** Mengikuti letak, ukuran, dan kemunculan benda yang dilatarinya. */
    void follow(View view) {
        target = view;
        view.getViewTreeObserver().addOnPreDrawListener(this);
    }

    @Override public boolean onPreDraw() {
        if (target == null) return true;
        target.getLocationOnScreen(at);
        float shown = target.isShown() ? target.getAlpha() : 0;
        int width = Math.max(1, Math.round(target.getWidth() * target.getScaleX()));
        int height = Math.max(1, Math.round(target.getHeight() * target.getScaleY()));
        shown = Math.max(0, Math.min(1, shown));
        if (at[0] != x || at[1] != y || width != w || height != h
                || Math.abs(shown - alpha) > 0.02f) {
            x = at[0];
            y = at[1];
            w = width;
            h = height;
            alpha = shown;
            Window window = dialog.getWindow();
            WindowManager.LayoutParams p = window.getAttributes();
            p.x = x;
            p.y = y;
            p.width = w;
            p.height = h;
            p.alpha = alpha;
            window.setAttributes(p);
        }
        return true;
    }

    void close() {
        if (target != null && target.getViewTreeObserver().isAlive()) {
            target.getViewTreeObserver().removeOnPreDrawListener(this);
        }
        target = null;
        try { dialog.dismiss(); }
        catch (RuntimeException ignored) { }
    }
}
