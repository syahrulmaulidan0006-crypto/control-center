package com.example.controlcenter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import java.util.function.BooleanSupplier;

/** Slider kapsul vertikal, terisi dari bawah. */
class VerticalSlider extends View {
    interface Listener { void onChange(float value); }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private Drawable icon;
    private int shownIcon, zeroIcon, lowIcon;
    private final int baseIcon;
    private boolean touching;
    private final Listener listener;
    private final BooleanSupplier allowed;
    private float value, target = -1;
    private android.animation.ValueAnimator animator;
    private boolean active;
    private final int covered;

    VerticalSlider(Context context, int iconRes, float start, Listener listener,
            BooleanSupplier allowed) {
        super(context);
        this.icon = context.getDrawable(iconRes).mutate();
        this.baseIcon = iconRes;
        this.shownIcon = iconRes;
        this.value = start;
        this.listener = listener;
        this.allowed = allowed;
        this.covered = Theme.onWhite(context);
    }

    /** Ikon berbeda untuk nilai nol dan rendah. */
    void levelIcons(int zero, int low) {
        zeroIcon = zero;
        lowIcon = low;
        invalidate();
    }

    /** Ikuti perubahan dari luar, misalnya tombol volume fisik. */
    void sync(float next) {
        if (touching || Math.abs(next - value) < 0.004f) return;
        if (animator != null) animator.cancel();
        target = next;
        animator = android.animation.ValueAnimator.ofFloat(value, next);
        animator.setDuration(160);
        animator.addUpdateListener(a -> { value = (float) a.getAnimatedValue(); invalidate(); });
        animator.start();
    }

    @Override protected void onDraw(Canvas c) {
        if (zeroIcon != 0) {
            int want = value <= 0.001f ? zeroIcon : value < 0.5f ? lowIcon : baseIcon;
            if (want != shownIcon) {
                shownIcon = want;
                icon = getContext().getDrawable(want).mutate();
            }
        }
        float w = getWidth(), h = getHeight(), r = w / 2;
        clip.reset();
        clip.addRoundRect(new RectF(0, 0, w, h), r, r, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        float d = getResources().getDisplayMetrics().density;
        Glass.draw(c, paint, new RectF(0, 0, w, h), r, d, 0x1FFFFFFF);
        paint.setColor(0xEBFFFFFF);
        c.drawRect(0, h * (1 - value), w, h, paint);
        c.restore();
        Glass.shine(c, paint, new RectF(0, 0, w, h), r, d);

        int size = Math.round(w * 0.46f);
        int left = Math.round((w - size) / 2);
        int top = Math.round(h - size - w * 0.28f);
        icon.setBounds(left, top, left + size, top + size);
        // Ikon menjadi gelap bila tertutup isian terang.
        icon.setTint(h * (1 - value) < top + size / 2f ? covered : 0xFFFFFFFF);
        icon.draw(c);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            active = allowed == null || allowed.getAsBoolean();
            getParent().requestDisallowInterceptTouchEvent(true);
            touching = true;
            if (animator != null) animator.cancel();
        }
        if (e.getAction() == MotionEvent.ACTION_UP
                || e.getAction() == MotionEvent.ACTION_CANCEL) {
            postDelayed(() -> touching = false, 250);
        }
        if (active && e.getAction() != MotionEvent.ACTION_CANCEL) {
            value = Math.max(0f, Math.min(1f, 1 - e.getY() / getHeight()));
            listener.onChange(value);
            invalidate();
        }
        return true;
    }
}
