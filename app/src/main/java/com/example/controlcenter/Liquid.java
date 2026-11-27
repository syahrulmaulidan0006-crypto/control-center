package com.example.controlcenter;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.SystemClock;

/**
 * Isian cairan berwarna polos bergradasi, dua lapis gelombang tanpa garis atau kilau putih.
 * Gelombangnya membesar saat nilai berubah cepat, lalu tenang sendiri.
 */
class Liquid {
    static Liquid blue() {
        return new Liquid(0xFF1E9BEF, 0xFF0A63D8, 0xFF06358F);
    }

    static Liquid green() {
        return new Liquid(0xFF3BE08F, 0xFF14B36B, 0xFF0A6B45);
    }

    static Liquid amber() {
        return new Liquid(0xFFF89B26, 0xFFE9681A, 0xFFA83A12);
    }

    private final int bright, deep, floor;

    private Liquid(int bright, int deep, int floor) {
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
        fill(surface, h, Theme.alpha(deep, 0xA0), Theme.alpha(floor, 0xD0));
        c.drawPath(path, paint);

        shape(w, h, surface, tall, t, 1f);
        paint.setShader(new LinearGradient(0, surface - tall, 0,
                Math.max(surface + 1, h),
                new int[]{Theme.alpha(bright, 0xF2), Theme.alpha(deep, 0xF2),
                        Theme.alpha(floor, 0xFA)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        paint.setColor(0xFFFFFFFF);
        c.drawPath(path, paint);
        paint.setShader(null);
        return moving;
    }

    private static float height(float x, float w, float tall, float t, float turns) {
        float u = x / w * (float) Math.PI * 2 * turns;
        return (float) (Math.sin(u * 0.85f + t) * 0.65 + Math.sin(u * 1.7f - t * 1.3f) * 0.35)
                * tall;
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
}
