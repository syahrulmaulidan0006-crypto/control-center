package com.example.controlcenter;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/** Animasi singkat saat pengisi daya dicolok: cincin terisi sampai persen baterai. */
class ChargeView extends View {
    private static final int GREEN = 0xFF37D98A;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int level;
    private final float d;
    private float time;
    private ValueAnimator run;

    ChargeView(Context context, int level) {
        super(context);
        this.level = level;
        d = context.getResources().getDisplayMetrics().density;
        setContentDescription("Mengisi daya " + level + " persen");
    }

    void play(Runnable done) {
        setAlpha(0);
        setScaleX(0.6f);
        setScaleY(0.6f);
        animate().alpha(1).scaleX(1).scaleY(1).setDuration(420)
                .setInterpolator(new OvershootInterpolator(1.6f)).start();
        run = ValueAnimator.ofFloat(0, 1);
        run.setDuration(2400);
        run.setInterpolator(new android.view.animation.LinearInterpolator());
        run.addUpdateListener(a -> { time = (float) a.getAnimatedValue(); invalidate(); });
        run.start();
        postDelayed(() -> animate().alpha(0).scaleX(0.82f).scaleY(0.82f).setDuration(300)
                .setInterpolator(new DecelerateInterpolator()).withEndAction(done).start(), 2400);
    }

    @Override protected void onDetachedFromWindow() {
        if (run != null) run.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas c) {
        float size = Math.min(getWidth(), getHeight()) - 8 * d;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        RectF card = new RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
        paint.setShader(null);
        Glass.draw(c, paint, card, 44 * d, d, 0xB30C1228);

        float grow = Math.min(1, time / 0.5f);
        grow = 1 - (1 - grow) * (1 - grow) * (1 - grow);
        float ringY = cy - 14 * d, radius = size * 0.28f;
        RectF ring = new RectF(cx - radius, ringY - radius, cx + radius, ringY + radius);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(8 * d);
        paint.setColor(0x33FFFFFF);
        c.drawOval(ring, paint);
        paint.setColor(GREEN);
        c.drawArc(ring, -90, 3.6f * level * grow, false, paint);
        // Gelombang cahaya yang menyebar keluar dari cincin.
        float wave = (time * 2.4f) % 1;
        paint.setStrokeWidth(2 * d);
        paint.setColor(Theme.alpha(GREEN, Math.round(110 * (1 - wave))));
        c.drawCircle(cx, ringY, radius + 5 * d + wave * size * 0.12f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeCap(Paint.Cap.BUTT);

        // Petir berdenyut di tengah cincin.
        float beat = 1 + 0.08f * (float) Math.sin(time * Math.PI * 6);
        float u = radius * 0.5f * beat;
        Path bolt = new Path();
        bolt.moveTo(cx + 0.18f * u, ringY - u);
        bolt.lineTo(cx - 0.62f * u, ringY + 0.14f * u);
        bolt.lineTo(cx - 0.04f * u, ringY + 0.14f * u);
        bolt.lineTo(cx - 0.18f * u, ringY + u);
        bolt.lineTo(cx + 0.62f * u, ringY - 0.14f * u);
        bolt.lineTo(cx + 0.04f * u, ringY - 0.14f * u);
        bolt.close();
        paint.setColor(0xFFFFFFFF);
        c.drawPath(bolt, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(size * 0.14f);
        c.drawText(Math.round(level * grow) + "%", cx, card.bottom - size * 0.13f, paint);
        paint.setTypeface(Typeface.DEFAULT);
    }
}
