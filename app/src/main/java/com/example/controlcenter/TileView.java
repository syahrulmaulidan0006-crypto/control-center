package com.example.controlcenter;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/** Tombol bulat kaca dengan animasi warna menyebar saat menyala. */
class TileView extends View {
    final Item item;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Drawable icon;
    private final int accent, onAccent;
    private boolean on;
    /** Tombol 2x2: ikon di atas, nama di bawah. */
    boolean large;
    private float fill;
    private ValueAnimator animator, motion;
    private float t = 1;
    private final Runnable wave1, wave2, wave3;

    TileView(Context context, Item item) {
        super(context);
        this.item = item;
        accent = Theme.accent(context);
        onAccent = Theme.onAccent(context);
        setIcon(item.icon);
        setContentDescription(item.label);
        wave1 = () -> setIcon(R.drawable.ic_wifi1);
        wave2 = () -> setIcon(R.drawable.ic_wifi2);
        wave3 = () -> setIcon(item.icon);
    }

    private void setIcon(int res) {
        icon = getContext().getDrawable(res).mutate();
        invalidate();
    }

    void setOn(boolean value, boolean animate) {
        if (value == on) return;
        on = value;
        if (animate) act();
        if (animator != null) animator.cancel();
        if (!animate) { fill = value ? 1 : 0; invalidate(); return; }
        animator = ValueAnimator.ofFloat(fill, value ? 1 : 0);
        animator.setDuration(380);
        animator.setInterpolator(new DecelerateInterpolator(1.6f));
        animator.addUpdateListener(a -> { fill = (float) a.getAnimatedValue(); invalidate(); });
        animator.start();
        animate().cancel();
        setScaleX(0.84f);
        setScaleY(0.84f);
        animate().scaleX(1).scaleY(1).setDuration(420)
                .setInterpolator(new OvershootInterpolator(3f)).start();
        // Garis sinyal Wi-Fi menyala satu per satu.
        if (value && item == Item.WIFI) {
            wave1.run();
            postDelayed(wave2, 160);
            postDelayed(wave3, 320);
        }
    }

    private int face;

    /** Mengganti gambar ikon, misalnya sesuai mode fokus yang aktif. */
    void face(int res) {
        int now = face == 0 ? item.icon : face;
        if (res == now) return;
        face = res;
        setIcon(res);
        if (isAttachedToWindow()) act();
    }

    /** Gerakan khas tiap ikon. */
    void act() {
        if (motion != null && motion.isRunning()) return;
        motion = ValueAnimator.ofFloat(0, 1);
        motion.setDuration(560);
        motion.setInterpolator(new android.view.animation.LinearInterpolator());
        motion.addUpdateListener(a -> { t = (float) a.getAnimatedValue(); invalidate(); });
        motion.start();
    }

    @Override public boolean performClick() {
        Haptic.click(getContext());
        act();
        return super.performClick();
    }

    /** Mengubah kanvas sesuai waktu t (0..1) dan mengembalikan kepekatan ikon. */
    private float pose(Canvas c, float cx, float cy, float r) {
        if (t >= 1) return 1;
        float wave = (float) Math.sin(Math.PI * t), left = 1 - t;
        float ease = 1 - left * left * left;
        switch (item) {
            case AIRPLANE: {
                // Terbang keluar ke kanan atas, lalu masuk lagi dari kiri bawah.
                float p = t < 0.5f ? t * 2 : (1 - t) * 2;
                float way = (t < 0.5f ? 1 : -1) * p * p * r;
                c.translate(way, -way);
                return 1 - p;
            }
            case ROTATION: case SYNC: case AUTO_LIGHT: case SETTINGS: case DARK:
                c.rotate(360 * ease, cx, cy);
                break;
            case DND:
                c.rotate((float) Math.sin(t * Math.PI * 3) * 40 * left, cx, cy);
                break;
            case VIBRATE: case NOTIFICATIONS: case ALARM: case PHONE:
                c.rotate((float) Math.sin(t * Math.PI * 6) * 28 * left, cx, cy - r * 0.4f);
                break;
            case TIMER:
                c.rotate(180 * ease, cx, cy);
                break;
            case TORCH:
                c.scale(1 + 0.25f * wave, 1 + 0.25f * wave, cx, cy);
                return t < 0.5f && (int) (t * 12) % 2 == 0 ? 0.3f : 1;
            case DATA: case USAGE: case VOLUME:
                c.scale(1, 0.2f + 0.8f * ease + 0.2f * wave, cx, cy + r * 0.45f);
                break;
            case LOCATION: case LOCK: case MAPS: case HOME:
                c.translate(0, -(float) Math.abs(Math.sin(t * Math.PI * 2)) * r * 0.35f * left);
                break;
            case CAMERA: case SCREENSHOT: case QR: case VIDEO: case RECORD:
                c.scale(1 - 0.4f * wave, 1 - 0.4f * wave, cx, cy);
                break;
            default: {
                float s = 1 + (float) Math.sin(t * Math.PI * 3) * 0.3f * left;
                c.scale(s, s, cx, cy);
            }
        }
        return 1;
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float d = getResources().getDisplayMetrics().density;
        float cx = w / 2, cy = h / 2, r = Math.min(cx, cy) - d;
        // Bentuk dikenali dari ukurannya: persegi, lebar, atau besar.
        boolean wide = w > h * 1.5f;
        if (wide) {
            Glass.draw(c, paint, new RectF(d, d, w - d, h - d), h / 2 - d, d, 0x1FFFFFFF);
            r = h / 2 - Math.max(6 * d, h * 0.11f);
            cx = h / 2;
        } else if (large) {
            Glass.draw(c, paint, new RectF(d, d, w - d, h - d), 36 * d, d, 0x1FFFFFFF);
            r = Math.min(w, h) * 0.24f;
            cy = h * 0.37f;
        }
        RectF circle = new RectF(cx - r, cy - r, cx + r, cy + r);
        Glass.draw(c, paint, circle, r, d, 0x1FFFFFFF);
        if (fill > 0) {
            paint.setColor(Theme.alpha(accent, 0xE0));
            c.drawCircle(cx, cy, r * fill, paint);
            Glass.shine(c, paint, circle, r, d);
        }
        if (wide) {
            float x = h + 2 * d;
            words(c, x, h / 2, w - x - 12 * d, Layout.Alignment.ALIGN_NORMAL);
        } else if (large) {
            words(c, 10 * d, h * 0.77f, w - 20 * d, Layout.Alignment.ALIGN_CENTER);
        }
        int half = Math.round(r * 0.48f);
        icon.setBounds(Math.round(cx) - half, Math.round(cy) - half,
                Math.round(cx) + half, Math.round(cy) + half);
        icon.setTint(fill > 0.5f ? onAccent : 0xFFFFFFFF);
        c.save();
        float alpha = pose(c, cx, cy, r);
        icon.setAlpha(Math.round(255 * alpha));
        icon.draw(c);
        c.restore();
    }

    /** Nama (paling banyak dua baris) dan keadaan, dipusatkan pada tinggi middle. */
    private void words(Canvas c, float x, float middle, float width,
            Layout.Alignment align) {
        if (width <= 0) return;
        float sp = getResources().getDisplayMetrics().scaledDensity;
        // Huruf sistem yang sangat besar dibatasi agar tetap muat di dalam tombol.
        sp = Math.min(sp, getResources().getDisplayMetrics().density * 1.15f);
        StaticLayout name = block(item.label, 13 * sp, 0xFFFFFFFF, true, 2, width, align);
        StaticLayout state = item.leaves() ? null
                : block(on ? "Aktif" : "Mati", 11 * sp, 0xB3FFFFFF, false, 1, width, align);
        float total = name.getHeight() + (state == null ? 0 : state.getHeight());
        c.save();
        c.translate(x, middle - total / 2);
        name.draw(c);
        if (state != null) {
            c.translate(0, name.getHeight());
            state.draw(c);
        }
        c.restore();
    }

    private static StaticLayout block(String value, float size, int color, boolean bold,
            int lines, float width, Layout.Alignment align) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size);
        p.setColor(color);
        if (bold) p.setTypeface(Typeface.DEFAULT_BOLD);
        return StaticLayout.Builder.obtain(value, 0, value.length(), p, (int) width)
                .setAlignment(align).setMaxLines(lines)
                .setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).build();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            animate().scaleX(0.88f).scaleY(0.88f).setDuration(110).start();
        } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            animate().scaleX(1).scaleY(1).setDuration(180).start();
        }
        return super.onTouchEvent(e);
    }
}
