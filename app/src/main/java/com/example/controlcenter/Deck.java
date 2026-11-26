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
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import java.util.function.Consumer;

/**
 * Wadah dua halaman: pusat notifikasi di kiri dan Control Center di kanan.
 * Halaman mengikuti jari saat digeser ke samping.
 */
class Deck extends FrameLayout {
    private Sheet current, next;
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

    void fade(float shown) { dots.setAlpha(shown); }

    /** Titik disembunyikan selagi ada jendela di atas halaman. */
    void dots(boolean visible) {
        dots.animate().scaleX(visible ? 1 : 0).scaleY(visible ? 1 : 0).setDuration(220).start();
    }

    boolean paging() { return next != null; }

    void begin(Sheet other) {
        next = other;
        other.setProgress(1);
        addView(other, indexOfChild(dots), new LayoutParams(-1, -1));
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

    /** away: 0 = di tengah layar, 1 = sudah keluar. */
    private void page(View v, float x, float away) {
        v.setTranslationX(x);
        v.setAlpha(1 - 0.55f * away);
        v.setScaleX(1 - 0.08f * away);
        v.setScaleY(1 - 0.08f * away);
    }

    void release(float velocity, boolean lifted, Consumer<Sheet> chosen) {
        if (next == null) return;
        float speed = velocity * current.pageDirection();
        float fast = 600 * getResources().getDisplayMetrics().density;
        boolean go = lifted && speed > -fast && (moved > 0.3f || speed > fast);
        slide = ValueAnimator.ofFloat(moved, go ? 1 : 0);
        slide.setDuration(300);
        slide.setInterpolator(new DecelerateInterpolator(1.8f));
        slide.addUpdateListener(a -> move((float) a.getAnimatedValue()));
        slide.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                Sheet gone = go ? current : next;
                if (go) current = next;
                next = null;
                slide = null;
                removeView(gone);
                page(current, 0, 0);
                dots.position = place(current);
                dots.invalidate();
                if (go) chosen.accept(current);
            }
        });
        slide.start();
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        // Abaikan sentuhan baru selagi halaman masih meluncur.
        return slide != null;
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
