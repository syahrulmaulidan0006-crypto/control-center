package com.example.controlcenter;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
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
    private boolean on;
    private float fill;
    private ValueAnimator animator;
    private final Runnable wave1, wave2, wave3;

    TileView(Context context, Item item) {
        super(context);
        this.item = item;
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

    @Override protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float d = getResources().getDisplayMetrics().density;
        float r = Math.min(cx, cy) - d;
        RectF circle = new RectF(cx - r, cy - r, cx + r, cy + r);
        Glass.draw(c, paint, circle, r, d, 0x1FFFFFFF);
        int accent = Theme.accent(getContext());
        if (fill > 0) {
            paint.setColor(Theme.alpha(accent, 0xE0));
            c.drawCircle(cx, cy, r * fill, paint);
            Glass.shine(c, paint, circle, r, d);
        }
        int half = Math.round(r * 0.48f);
        icon.setBounds(Math.round(cx) - half, Math.round(cy) - half,
                Math.round(cx) + half, Math.round(cy) + half);
        int on = Theme.onAccent(getContext());
        icon.setTint(fill > 0.5f ? on : 0xFFFFFFFF);
        icon.draw(c);
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
