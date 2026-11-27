package com.example.controlcenter;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import java.util.Date;

/**
 * Animasi pengisian daya selayar penuh yang menutupi animasi bawaan: tabung kaca terisi cairan
 * sampai persen baterai. Ketuk untuk menutup.
 */
class ChargeView extends View {
    private static final long SHOWN = 5000;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path(), bolt = new Path();
    private final Liquid.Wave wave = new Liquid.Wave();
    private final Liquid liquid;
    private final int level, color;
    private final float d;
    private float grow;
    private String remaining = "";
    private long asked;
    private ValueAnimator run;
    private Runnable done;
    private boolean closing;
    private final Runnable timeout = this::close;

    ChargeView(Context context, int level) {
        super(context);
        this.level = level;
        liquid = level <= 20 ? Liquid.amber() : Liquid.green();
        color = level <= 20 ? 0xFFFFB534 : 0xFF37D98A;
        d = context.getResources().getDisplayMetrics().density;
        setContentDescription("Mengisi daya " + level + " persen");
        setOnClickListener(v -> close());
    }

    void play(Runnable finished) {
        done = finished;
        setAlpha(0);
        animate().alpha(1).setDuration(260).start();
        run = ValueAnimator.ofFloat(0, 1);
        run.setDuration(1500);
        run.setStartDelay(120);
        run.setInterpolator(new DecelerateInterpolator(1.8f));
        run.addUpdateListener(a -> {
            float next = (float) a.getAnimatedValue();
            wave.stir((next - grow) * level / 100f);
            grow = next;
            invalidate();
        });
        run.start();
        postDelayed(timeout, SHOWN);
    }

    void close() {
        if (closing) return;
        closing = true;
        removeCallbacks(timeout);
        animate().alpha(0).setStartDelay(0).setDuration(320)
                .setInterpolator(new LinearInterpolator()).start();
        // Dihapus dengan penjadwalan sendiri, tidak bergantung pada akhir animasi.
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(done, 340);
    }

    @Override protected void onDetachedFromWindow() {
        if (run != null) run.cancel();
        removeCallbacks(timeout);
        super.onDetachedFromWindow();
    }

    /** Perkiraan waktu sampai penuh dari sistem; kosong bila belum diketahui. */
    private void estimate() {
        long now = SystemClock.uptimeMillis();
        if (asked != 0 && now - asked < 1000) return;
        asked = now;
        long left = getContext().getSystemService(BatteryManager.class)
                .computeChargeTimeRemaining();
        if (level >= 100) remaining = "Baterai penuh";
        else if (left <= 0) remaining = "";
        else {
            long minutes = Math.max(1, left / 60000);
            remaining = "Penuh dalam " + (minutes >= 60 ? minutes / 60 + " jam " : "")
                    + minutes % 60 + " menit";
        }
    }

    @Override protected void onDraw(Canvas c) {
        estimate();
        float w = getWidth(), h = getHeight(), cx = w / 2;
        // Hampir pekat agar animasi bawaan di belakangnya tidak terlihat.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        paint.setShader(new LinearGradient(0, 0, 0, h, 0xFA0B1226, 0xFA03050B,
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);

        float tall = Math.min(h * 0.44f, 340 * d), wide = tall * 0.48f;
        float middle = h * 0.5f;
        RectF tube = new RectF(cx - wide / 2, middle - tall / 2, cx + wide / 2,
                middle + tall / 2);
        // Cahaya lembut sewarna cairan di belakang tabung.
        paint.setShader(new RadialGradient(cx, middle, tall * 0.85f,
                Theme.alpha(color, Math.round(70 * grow)), Theme.alpha(color, 0),
                Shader.TileMode.CLAMP));
        c.drawCircle(cx, middle, tall * 0.85f, paint);
        paint.setShader(null);

        float r = wide / 2;
        Glass.draw(c, paint, tube, r, d, 0x1FFFFFFF);
        clip.reset();
        clip.addRoundRect(tube, r, r, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        c.translate(tube.left, tube.top);
        liquid.draw(c, wave, wide, tall, level / 100f * grow, d);
        c.restore();
        Glass.shine(c, paint, tube, r, d);

        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setShadowLayer(6 * d, 0, d, 0x80000000);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(wide * 0.34f);
        c.drawText(Math.round(level * grow) + "%", cx, middle + wide * 0.1f, paint);
        bolt(c, cx, middle + tall * 0.28f, 22 * d);

        paint.setTextSize(48 * d);
        paint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        c.drawText(DateFormat.getTimeFormat(getContext()).format(new Date()), cx,
                tube.top - 56 * d, paint);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(18 * d);
        c.drawText("Mengisi daya", cx, tube.bottom + 46 * d, paint);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(14 * d);
        paint.setColor(0xB3FFFFFF);
        c.drawText(remaining, cx, tube.bottom + 72 * d, paint);
        paint.setTextSize(12 * d);
        paint.setColor(0x66FFFFFF);
        c.drawText("Ketuk untuk menutup", cx, h - 56 * d, paint);
        paint.clearShadowLayer();
        // Cairan terus beriak selama tampil.
        if (!closing) {
            wave.stir(0);
            postInvalidateOnAnimation();
        }
    }

    private void bolt(Canvas c, float x, float y, float u) {
        float beat = 1 + 0.08f * (float) Math.sin(SystemClock.uptimeMillis() / 180.0);
        u *= beat;
        bolt.reset();
        bolt.moveTo(x + 0.18f * u, y - u);
        bolt.lineTo(x - 0.62f * u, y + 0.14f * u);
        bolt.lineTo(x - 0.04f * u, y + 0.14f * u);
        bolt.lineTo(x - 0.18f * u, y + u);
        bolt.lineTo(x + 0.62f * u, y - 0.14f * u);
        bolt.lineTo(x + 0.04f * u, y - 0.14f * u);
        bolt.close();
        c.drawPath(bolt, paint);
    }
}
