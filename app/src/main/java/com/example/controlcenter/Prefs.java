package com.example.controlcenter;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

/** Pengaturan yang dibaca oleh layar pengaturan dan layanan (beda proses). */
final class Prefs {
    @SuppressWarnings("deprecation")
    static SharedPreferences of(Context c) {
        return c.getSharedPreferences("cc", Context.MODE_MULTI_PROCESS);
    }

    static boolean is(Context c, String key, boolean fallback) {
        return of(c).getBoolean(key, fallback);
    }

    static Set<String> blocked(Context c) {
        return new HashSet<>(of(c).getStringSet("blocked", new HashSet<>()));
    }
}
