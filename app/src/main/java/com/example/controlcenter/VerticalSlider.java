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
    private float startY, startValue;
    /** Isian cairan untuk volume dan kecerahan; null berarti isian putih biasa. */
    private final Liquid liquid;
    private final Liquid.Wave wave = new Liquid.Wave();
    /** Kepekatan angka persen. */
    private float label;
    private android.animation.ValueAnimator labelFade;
    private final Runnable hideLabel = () -> label(0);
    /** Angka juga muncul bila nilainya diubah dari luar, misalnya tombol volume. */
    boolean announce;
    private boolean moved, held;
    /** Dijalankan saat slider ditekan lama tanpa digeser. */
    Runnable onHold;
    private final Runnable hold = () -> {
        held = true;
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        onHold.run();
    };

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
        boolean wet = Prefs.is(context, "liquid", true);
        // Objek sendiri tiap slider agar kuas dan jalurnya tidak dipakai bersama.
        this.liquid = !wet ? null : iconRes == R.drawable.ic_volume ? Liquid.blue()
                : iconRes == R.drawable.ic_sun ? Liquid.amber() : null;
    }

    /** Ikon berbeda untuk nilai nol dan rendah. */
    void levelIcons(int zero, int low) {
        zeroIcon = zero;
        lowIcon = low;
        invalidate();
    }

    /** Ikuti perubahan dari luar, misalnya tombol volume fisik. */
    void sync(float next) {
        if (touching || Math.abs(next - value) < 0.004f || next == target) return;
        if (announce && isShown()) {
            label(1);
            removeCallbacks(hideLabel);
            postDelayed(hideLabel, 1100);
        }
        if (animator != null) animator.cancel();
        target = next;
        animator = android.animation.ValueAnimator.ofFloat(value, next);
        animator.setDuration(160);
        animator.addUpdateListener(a -> {
            float now = (float) a.getAnimatedValue();
            wave.stir(now - value);
            value = now;
            invalidate();
        });
        animator.start();
    }

    private void label(float to) {
        if (labelFade != null) labelFade.cancel();
        labelFade = android.animation.ValueAnimator.ofFloat(label, to);
        labelFade.setDuration(to > 0 ? 130 : 320);
        labelFade.addUpdateListener(a -> { label = (float) a.getAnimatedValue(); invalidate(); });
        labelFade.start();
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
        Frost.begin(this);
        Glass.draw(c, paint, new RectF(0, 0, w, h), r, d, 0x1FFFFFFF);
        boolean moving = false;
        if (liquid != null) {
            moving = liquid.draw(c, wave, w, h, value, d);
        } else {
            paint.setShader(null);
            paint.setColor(0xEBFFFFFF);
            c.drawRect(0, h * (1 - value), w, h, paint);
        }
        c.restore();
        Glass.shine(c, paint, new RectF(0, 0, w, h), r, d);
        Frost.end();
        if (moving) postInvalidateOnAnimation();

        int size = Math.round(w * 0.46f);
        int left = Math.round((w - size) / 2);
        int top = Math.round(h - size - w * 0.28f);
        icon.setBounds(left, top, left + size, top + size);
        // Ikon menjadi gelap bila tertutup isian terang.
        icon.setTint(liquid == null && h * (1 - value) < top + size / 2f
                ? covered : 0xFFFFFFFF);
        icon.draw(c);
        if (label > 0) {
            float y = w * 0.66f;
            paint.setColor(liquid == null && h * (1 - value) < y - 5 * d
                    ? covered : 0xFFFFFFFF);
            if (liquid != null) paint.setShadowLayer(4 * d, 0, d, 0x99000000);
            paint.setAlpha(Math.round(paint.getAlpha() * label));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(Math.min(w * 0.25f, 17 * d));
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            c.drawText(Math.round(value * 100) + "%", r, y, paint);
            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            paint.clearShadowLayer();
        }
    }

    /** Nilai sekarang, 0 sampai 1. */
    float value() { return value; }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            active = allowed == null || allowed.getAsBoolean();
            getParent().requestDisallowInterceptTouchEvent(true);
            touching = true;
            moved = held = false;
            startY = e.getY();
            startValue = value;
            if (animator != null) animator.cancel();
            if (onHold != null) postDelayed(hold, 450);
            if (active) {
                removeCallbacks(hideLabel);
                label(1);
            }
            animate().scaleX(1.05f).scaleY(1.03f).setDuration(160).start();
            return true;
        }
        if (a == MotionEvent.ACTION_MOVE && !moved && Math.abs(e.getY() - startY)
                > android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
            moved = true;
            removeCallbacks(hold);
        }
        boolean up = a == MotionEvent.ACTION_UP;
        if (active && !held && (a == MotionEvent.ACTION_MOVE && moved || up)) {
            // Digeser: mengikuti jarak jari. Diketuk: langsung ke titik sentuh.
            float next = moved ? startValue + (startY - e.getY()) / getHeight()
                    : 1 - e.getY() / getHeight();
            float before = value;
            value = Math.max(0f, Math.min(1f, next));
            // Terasa saat mentok di bawah atau di atas.
            if (value != before && (value == 0 || value == 1)) Haptic.tick(getContext());
            wave.stir(value - before);
            listener.onChange(value);
            invalidate();
        }
        if (up || a == MotionEvent.ACTION_CANCEL) {
            removeCallbacks(hold);
            postDelayed(() -> touching = false, 250);
            removeCallbacks(hideLabel);
            postDelayed(hideLabel, 700);
            animate().scaleX(1).scaleY(1).setDuration(280)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(2.5f))
                    .start();
        }
        return true;
    }
}
