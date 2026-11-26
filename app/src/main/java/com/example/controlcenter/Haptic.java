package com.example.controlcenter;

import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Getar halus saat disentuh. Bisa dimatikan di pengaturan. */
final class Haptic {
    static final String KEY = "haptic";

    /** Ketukan tombol. */
    static void click(Context c) { play(c, VibrationEffect.EFFECT_CLICK); }

    /** Sentuhan paling ringan: slider mentok atau pindah halaman. */
    static void tick(Context c) { play(c, VibrationEffect.EFFECT_TICK); }

    private static void play(Context c, int effect) {
        if (!Prefs.is(c, KEY, true)) return;
        try {
            c.getSystemService(Vibrator.class).vibrate(VibrationEffect.createPredefined(effect));
        } catch (Exception ignored) {
            // HP tanpa motor getar.
        }
    }
}
