package com.example.controlcenter;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Sakelar kaca dengan animasi geser. */
class GlassSwitch extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int color;
    private boolean on;
    private float position;

    GlassSwitch(Context context, int color, boolean start) {
        super(context);
        this.color = color;
        on = start;
        position = start ? 1 : 0;
    }

    void setOn(boolean value) {
        if (value == on) return;
        on = value;
        ValueAnimator a = ValueAnimator.ofFloat(position, value ? 1 : 0);
        a.setDuration(260);
        a.addUpdateListener(v -> { position = (float) v.getAnimatedValue(); invalidate(); });
        a.start();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), d = getResources().getDisplayMetrics().density;
        RectF track = new RectF(0, 0, w, h);
        Glass.draw(c, paint, track, h / 2, d, 0x33FFFFFF);
        paint.setColor((color & 0x00FFFFFF) | (Math.round(230 * position) << 24));
        c.drawRoundRect(track, h / 2, h / 2, paint);
        paint.setColor(0xFFFFFFFF);
        float r = h / 2 - 3 * d;
        c.drawCircle(h / 2 + (w - h) * position, h / 2, r, paint);
    }
}
