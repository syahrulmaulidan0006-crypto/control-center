package com.example.controlcenter;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Wadah dua halaman: pusat notifikasi di kiri dan Control Center di kanan.
 * Halaman mengikuti jari saat digeser ke samping.
 */
class Deck extends FrameLayout {
    /** ready: halaman sebelah yang sudah disiapkan tetapi belum terlihat. */
    private Sheet current, next, ready;
    private Consumer<Sheet> chosen;
    private VelocityTracker velocity;
    private float grabX, grabMoved;
    private final Dots dots;
    private ValueAnimator slide;
    private float moved;

    Deck(Context context, Sheet first) {
        super(context);
        current = first;
        addView(first, new LayoutParams(-1, -1));
        dots = new Dots(context);
        dots.position = place(first);
        float d = getResources().getDisplayMetrics().density;
        LayoutParams p = new LayoutParams(Math.round(40 * d), Math.round(8 * d),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        p.bottomMargin = Math.round(10 * d);
        addView(dots, p);
    }

    private static float place(Sheet s) { return s instanceof Center ? 0 : 1; }

    /** Latar diam di tempat; hanya isi halaman yang bergerak. */
    private float shownNow;

    /** Mengganti latar tanpa mengubah tingkat kemunculannya. */
    void shade(android.graphics.drawable.Drawable background) {
        setBackground(background);
        fade(shownNow);
    }

    void fade(float shown) {
        shownNow = shown;
        dots.setAlpha(shown);
        if (getBackground() != null) {
            getBackground().setAlpha(Math.round(255 * Math.max(0, Math.min(1, shown * 1.4f))));
        }
    }

    /** Titik disembunyikan selagi ada jendela di atas halaman. */
    void dots(boolean visible) {
        dots.animate().scaleX(visible ? 1 : 0).scaleY(visible ? 1 : 0).setDuration(220).start();
    }

    boolean paging() { return next != null; }

    boolean prepared() { return ready != null; }

    /** Halaman sebelah dibuat lebih dulu agar geseran pertama tidak tersendat. */
    void prepare(Sheet other) {
        ready = other;
        other.setProgress(1);
        other.setVisibility(INVISIBLE);
        addView(other, indexOfChild(dots), new LayoutParams(-1, -1));
    }

    /** other hanya dipakai bila belum ada halaman yang disiapkan. */
    void begin(Supplier<Sheet> other) {
        if (ready == null) prepare(other.get());
        next = ready;
        ready = null;
        next.setVisibility(VISIBLE);
        // Selama meluncur, tiap halaman cukup digambar sekali.
        current.setLayerType(LAYER_TYPE_HARDWARE, null);
        next.setLayerType(LAYER_TYPE_HARDWARE, null);
        move(0);
    }

    /** Jarak geser jari dalam piksel sejak halaman mulai berpindah. */
    void drag(float dx) {
        if (next == null) return;
        move(Math.max(0, Math.min(1, dx * current.pageDirection() / Math.max(1, getWidth()))));
    }

    private void move(float f) {
        moved = f;
        int way = current.pageDirection(), w = getResources().getDisplayMetrics().widthPixels;
        if (getWidth() > 0) w = getWidth();
        page(current, way * f * w, f);
        page(next, way * (f - 1) * w, 1 - f);
        dots.position = place(current) + (place(next) - place(current)) * f;
        dots.invalidate();
    }

    /**
     * away: 0 = di tengah layar, 1 = sudah keluar. Halaman hanya bergeser sedikit sambil
     * melebur, sehingga tidak ada tepi halaman yang terlihat memotong isi.
     */
    private void page(Sheet v, float x, float away) {
        v.parallax(Math.signum(x) * away);
        float seen = Math.max(0, 1 - away * 1.5f);
        v.setTranslationX(x * 0.28f);
        v.setAlpha(seen * seen * (3 - 2 * seen));
        v.setScaleX(1 - 0.07f * away);
        v.setScaleY(1 - 0.07f * away);
    }

    /** Meluncur ke halaman tujuan, melanjutkan kecepatan jari. */
    void release(float speedX, boolean lifted, Consumer<Sheet> done) {
        if (next == null) return;
        chosen = done;
        float speed = speedX * current.pageDirection();
        float flick = 350 * getResources().getDisplayMetrics().density;
        boolean go = lifted && (speed > flick || speed > -flick && moved > 0.35f);
        float end = go ? 1 : 0;
        float distance = Math.abs(end - moved), toward = go ? speed : -speed;
        // Kurva kubik mulai dengan kecepatan 3 x jarak / durasi, sama dengan jari.
        float millis = toward > flick
                ? 3000 * distance * Math.max(1, getWidth()) / toward : 300 + 240 * distance;
        slide = ValueAnimator.ofFloat(moved, end);
        slide.setDuration((long) Math.max(240, Math.min(520, millis)));
        slide.setInterpolator(t -> 1 - (1 - t) * (1 - t) * (1 - t));
        slide.addUpdateListener(a -> move((float) a.getAnimatedValue()));
        slide.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { settle(go); }
        });
        slide.start();
    }

    private void settle(boolean go) {
        Sheet gone = go ? current : next;
        if (go) current = next;
        next = null;
        slide = null;
        current.setLayerType(LAYER_TYPE_NONE, null);
        gone.setLayerType(LAYER_TYPE_NONE, null);
        page(current, 0, 0);
        dots.position = place(current);
        dots.invalidate();
        if (go) {
            Haptic.tick(getContext());
            removeView(gone);
            chosen.accept(current);
        } else {
            // Batal pindah: halaman sebelah disimpan untuk geseran berikutnya.
            gone.setVisibility(INVISIBLE);
            ready = gone;
        }
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        // Halaman yang masih meluncur bisa ditangkap lagi dengan jari.
        return slide != null;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            if (slide == null) return false;
            slide.removeAllListeners();
            slide.cancel();
            slide = null;
            grabX = e.getRawX();
            grabMoved = moved;
            velocity = VelocityTracker.obtain();
        }
        if (velocity == null) return false;
        MotionEvent raw = MotionEvent.obtain(e);
        raw.setLocation(e.getRawX(), e.getRawY());
        velocity.addMovement(raw);
        raw.recycle();
        if (a == MotionEvent.ACTION_MOVE) {
            float f = grabMoved + (e.getRawX() - grabX) * current.pageDirection()
                    / Math.max(1, getWidth());
            move(Math.max(0, Math.min(1, f)));
        } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            velocity.computeCurrentVelocity(1000);
            float speed = velocity.getXVelocity();
            velocity.recycle();
            velocity = null;
            release(speed, a == MotionEvent.ACTION_UP, chosen);
        }
        return true;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) { return current.dispatchKeyEvent(e); }

    @Override protected void onDetachedFromWindow() {
        if (slide != null) { slide.removeAllListeners(); slide.cancel(); }
        super.onDetachedFromWindow();
    }

    /** Dua titik penanda; titik aktif memanjang. */
    private static class Dots extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float position;

        Dots(Context context) { super(context); }

        @Override protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            float first = 1 - position, wide = 7 * d, extra = 11 * d, gap = 7 * d;
            float x = (getWidth() - 2 * wide - extra - gap) / 2f;
            for (int i = 0; i < 2; i++) {
                float active = i == 0 ? first : position;
                float w = wide + extra * active;
                paint.setColor(((int) (90 + 165 * active) << 24) | 0xFFFFFF);
                c.drawRoundRect(x, 0.5f * d, x + w, 7.5f * d, 4 * d, 4 * d, paint);
                x += w + gap;
            }
        }
    }
}
