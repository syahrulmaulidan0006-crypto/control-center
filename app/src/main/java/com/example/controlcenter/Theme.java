package com.example.controlcenter;

import android.content.Context;

/** Satu warna untuk semua tombol aktif. */
final class Theme {
    static final String[] NAMES = {"Biru", "Merah", "Hitam", "Abu-abu", "Putih kaca",
            "Kuning", "Ungu"};
    static final int[] COLORS = {0xFF2F8CFF, 0xFFE5394A, 0xFF101014, 0xFF7C8290, 0xE6FFFFFF,
            0xFFFFC93C, 0xFF8B5CF6};

    private static int index(Context c) {
        int i = Prefs.of(c).getInt("color", 0);
        return i >= 0 && i < COLORS.length ? i : 0;
    }

    static int accent(Context c) { return COLORS[index(c)]; }

    /** Warna ikon di atas warna aktif. */
    static int onAccent(Context c) {
        int i = index(c);
        return i == 4 || i == 5 ? 0xFF1A1D29 : 0xFFFFFFFF;
    }

    /** Warna ikon slider ketika tertutup isian putih. */
    static int onWhite(Context c) {
        int i = index(c);
        return i == 4 ? 0xFF4A5568 : i == 5 ? 0xFFB57F00 : COLORS[i];
    }

    static int alpha(int color, int alpha) { return (color & 0x00FFFFFF) | (alpha << 24); }
}
