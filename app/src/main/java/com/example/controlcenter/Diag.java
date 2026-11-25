package com.example.controlcenter;

import android.content.Context;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Catatan hasil popup terakhir, untuk ditampilkan di halaman status. */
final class Diag {
    @SuppressWarnings("deprecation")
    static String read(Context c) {
        return c.getSharedPreferences("diag", Context.MODE_MULTI_PROCESS)
                .getString("popup", "belum ada notifikasi yang diterima");
    }

    static void popup(Context c, String result) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date());
        c.getSharedPreferences("diag", Context.MODE_PRIVATE).edit()
                .putString("popup", time + " " + result).commit();
    }
}
