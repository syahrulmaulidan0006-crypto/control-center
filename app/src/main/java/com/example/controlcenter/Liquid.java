package com.example.controlcenter;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;

/**
 * Isian cairan mengkilap: dua lapis gelombang, pita kilau yang mengalir, dan gelembung.
 * Gelombangnya membesar saat nilai berubah cepat, lalu tenang sendiri.
 */
class Liquid {
    static Liquid blue() {
        return new Liquid(0xFFB8F1FF, 0xFF22B4F5, 0xFF0A63D8, 0xFF06358F);
    }

    static Liquid amber() {
        return new Liquid(0xFFFFF1C2, 0xFFFFB534, 0xFFF0701C, 0xFFA83A12);
    }

    private final int foam, bright, deep, floor;

    private Liquid(int foam, int bright, int deep, int floor) {
        this.foam = foam;
        this.bright = bright;
        this.deep = deep;
        this.floor = floor;
    }

    /** Keadaan gerak satu wadah cairan. */
    static class Wave {
        private float energy, phase;
        private long last;

        /** change: seberapa jauh permukaan baru saja berpindah, 0 sampai 1. */
        void stir(float change) {
            energy = Math.min(1f, energy + Math.abs(change) * 5f + 0.04f);
        }

        /** Maju satu langkah; true bila masih perlu digambar ulang. */
        private boolean step() {
            long now = SystemClock.uptimeMillis();
            float seconds = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1000f);
            last = now;
            phase += seconds * (2.2f + 7f * energy);
            energy *= (float) Math.exp(-seconds * 1.6f);
            if (energy < 0.015f) {
                energy = 0;
                last = 0;
            }
            return energy > 0;
        }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    /**
     * Menggambar di dalam bidang w x h yang sudah dipotong sesuai bentuk wadah.
     * Mengembalikan true bila cairan masih bergerak.
     */
    boolean draw(Canvas c, Wave wave, float w, float h, float level, float d) {
        boolean moving = wave.step();
        if (level <= 0.001f) return moving;
        float surface = h * (1 - level);
        // Gelombang mengecil saat wadah hampir penuh atau hampir kosong.
        float room = Math.min(1f, Math.min(level, 1 - level) * 8);
        float tall = (1.6f + 9f * wave.energy) * d * room;
        float t = wave.phase;

        // Lapisan belakang, lebih pekat dan bergerak berlawanan.
        shape(w, h, surface - 2.5f * d * room, tall * 0.8f, -t * 0.8f + 2.1f, 1.25f);
        fill(surface, h, Theme.alpha(deep, 0xB0), Theme.alpha(floor, 0xD0));
        c.drawPath(path, paint);

        shape(w, h, surface, tall, t, 1f);
        paint.setShader(new LinearGradient(0, surface - tall, 0,
                Math.max(surface + 1, h),
                new int[]{Theme.alpha(bright, 0xF2), Theme.alpha(deep, 0xF2),
                        Theme.alpha(floor, 0xFA)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        c.drawPath(path, paint);

        c.save();
        c.clipPath(path);
        // Pita kilau melengkung seperti pantulan di dalam air.
        streak(c, w, surface, h, t * 0.55f, 0.28f, 0.13f * w, 0x66);
        streak(c, w, surface, h, -t * 0.4f + 2.6f, 0.72f, 0.07f * w, 0x55);
        streak(c, w, surface, h, t * 0.3f + 4.4f, 0.55f, 0.025f * w, 0x8C);
        // Cahaya lembut di dekat permukaan.
        float cx = w * (0.5f + 0.25f * (float) Math.sin(t * 0.7f));
        paint.setShader(new RadialGradient(cx, surface + w * 0.12f, w * 0.65f,
                Theme.alpha(foam, 0x80), Theme.alpha(foam, 0), Shader.TileMode.CLAMP));
        c.drawRect(0, surface - tall, w, surface + w, paint);
        paint.setShader(null);
        // Gelembung naik selagi cairan bergolak.
        if (wave.energy > 0.05f) {
            for (int i = 0; i < 6; i++) {
                float rise = (t * (0.11f + i * 0.021f) + i * 0.37f) % 1f;
                float x = w * (0.18f + 0.64f * ((i * 0.618f) % 1f))
                        + (float) Math.sin(t + i) * 2 * d;
                float y = h - rise * (h - surface);
                float size = (1.2f + (i % 3)) * d;
                paint.setColor(Theme.alpha(foam, Math.round(180 * wave.energy)));
                c.drawCircle(x, y, size, paint);
            }
        }
        c.restore();

        // Garis terang tepat di permukaan.
        crest(w, surface, tall, t);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.6f * d);
        paint.setColor(Theme.alpha(foam, 0xE6));
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        return moving;
    }

    private static float height(float x, float w, float tall, float t, float turns) {
        float u = x / w * (float) Math.PI * 2 * turns;
        return (float) (Math.sin(u * 0.85f + t) * 0.65 + Math.sin(u * 1.7f - t * 1.3f) * 0.35)
                * tall;
    }

    private void crest(float w, float surface, float tall, float t) {
        path.reset();
        path.moveTo(0, surface + height(0, w, tall, t, 1));
        for (int i = 1; i <= 24; i++) {
            float x = w * i / 24f;
            path.lineTo(x, surface + height(x, w, tall, t, 1));
        }
    }

    private void shape(float w, float h, float surface, float tall, float t, float turns) {
        path.reset();
        path.moveTo(0, surface + height(0, w, tall, t, turns));
        for (int i = 1; i <= 24; i++) {
            float x = w * i / 24f;
            path.lineTo(x, surface + height(x, w, tall, t, turns));
        }
        path.lineTo(w, h);
        path.lineTo(0, h);
        path.close();
    }

    private void fill(float top, float bottom, int from, int to) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        paint.setShader(new LinearGradient(0, top, 0, Math.max(top + 1, bottom), from, to,
                Shader.TileMode.CLAMP));
    }

    private void streak(Canvas c, float w, float top, float bottom, float t, float at,
            float width, int alpha) {
        float x = w * at, sway = w * 0.32f * (float) Math.sin(t);
        float span = Math.max(1, bottom - top);
        Path line = new Path();
        line.moveTo(x + sway * 0.4f, top - width);
        line.cubicTo(x - sway, top + span * 0.33f, x + sway, top + span * 0.66f,
                x - sway * 0.5f, bottom + width);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xFFFFFFFF);
        paint.setShader(new LinearGradient(0, top, 0, bottom,
                new int[]{Theme.alpha(foam, alpha), Theme.alpha(foam, alpha / 4),
                        Theme.alpha(foam, alpha / 2)},
                null, Shader.TileMode.CLAMP));
        // Tiga kali dengan lebar menyusut agar pinggirnya lembut.
        for (int i = 3; i >= 1; i--) {
            paint.setStrokeWidth(width * i / 2f);
            paint.setAlpha(i == 1 ? 255 : 80);
            c.drawPath(line, paint);
        }
        paint.setAlpha(255);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
    }
}
