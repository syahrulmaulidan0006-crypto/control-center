package com.example.controlcenter;

import android.app.NotificationManager;
import android.app.UiModeManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import java.util.Calendar;

/** Mode fokus: Jangan Ganggu, Tidur, dan Pribadi, termasuk jadwal tidur. */
final class Focus {
    static final String NONE = "", DND = "dnd", SLEEP = "tidur", PERSONAL = "pribadi";

    static String mode(Context c) { return Prefs.of(c).getString("focus", NONE); }

    private static boolean systemDnd(Context c) {
        int f = c.getSystemService(NotificationManager.class).getCurrentInterruptionFilter();
        return f != NotificationManager.INTERRUPTION_FILTER_ALL
                && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN;
    }

    static boolean on(Context c) { return !mode(c).equals(NONE) || systemDnd(c); }

    static int icon(Context c) {
        String m = mode(c);
        return m.equals(SLEEP) ? R.drawable.ic_bed
                : m.equals(PERSONAL) ? R.drawable.ic_contacts : R.drawable.ic_moon;
    }

    /** Ketuk tombol: matikan bila ada yang aktif, selain itu pakai mode terakhir. */
    static void toggle(Context c, Controls controls) {
        if (on(c)) set(c, controls, NONE);
        else set(c, controls, Prefs.of(c).getString("focusLast", DND));
    }

    static void set(Context c, Controls controls, String next) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        boolean silent = next.equals(DND) || next.equals(SLEEP);
        if ((silent || systemDnd(c)) && !nm.isNotificationPolicyAccessGranted()) {
            controls.toast("Buka aplikasi dan berikan izin Jangan Ganggu");
            return;
        }
        restore(c, controls);
        if (silent) {
            nm.setInterruptionFilter(next.equals(SLEEP)
                    ? NotificationManager.INTERRUPTION_FILTER_ALARMS
                    : NotificationManager.INTERRUPTION_FILTER_PRIORITY);
        } else if (systemDnd(c)) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
        }
        SharedPreferences.Editor e = Prefs.of(c).edit().putString("focus", next)
                .putLong("focusAt", System.currentTimeMillis()).putBoolean("focusAuto", false);
        if (!next.equals(NONE)) e.putString("focusLast", next);
        if (next.equals(PERSONAL)) {
            e.putBoolean("oldHide", Prefs.is(c, "hideContent", false))
                    .putBoolean("hideContent", true);
        }
        if (next.equals(SLEEP)) dim(c, controls, e);
        e.commit();
    }

    private static void dim(Context c, Controls controls, SharedPreferences.Editor e) {
        if (Settings.System.canWrite(c)) {
            e.putInt("oldLight", Settings.System.getInt(c.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, 128));
            e.putInt("oldAuto", Settings.System.getInt(c.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE, 0));
            controls.put(Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
            controls.put(Settings.System.SCREEN_BRIGHTNESS, 4);
        }
        // Mode gelap hanya bisa diubah lewat Shizuku.
        if (Shell.ready() && c.getSystemService(UiModeManager.class).getNightMode()
                != UiModeManager.MODE_NIGHT_YES) {
            e.putBoolean("oldDark", true);
            Shell.run(c, shell -> shell.toggle("dark", true), ok -> { });
        }
    }

    /** Mengembalikan pengaturan yang diubah mode sebelumnya. */
    private static void restore(Context c, Controls controls) {
        SharedPreferences p = Prefs.of(c);
        SharedPreferences.Editor e = p.edit();
        if (p.contains("oldLight") && Settings.System.canWrite(c)) {
            controls.put(Settings.System.SCREEN_BRIGHTNESS, p.getInt("oldLight", 128));
            controls.put(Settings.System.SCREEN_BRIGHTNESS_MODE, p.getInt("oldAuto", 0));
        }
        if (p.contains("oldDark")) {
            Shell.run(c, shell -> shell.toggle("dark", false), ok -> { });
        }
        if (p.contains("oldHide")) e.putBoolean("hideContent", p.getBoolean("oldHide", false));
        e.remove("oldLight").remove("oldAuto").remove("oldDark").remove("oldHide").commit();
    }

    static int start(Context c) { return Prefs.of(c).getInt("sleepStart", 22 * 60); }

    static int end(Context c) { return Prefs.of(c).getInt("sleepEnd", 5 * 60); }

    static String clock(int minutes) {
        return String.format(java.util.Locale.US, "%02d.%02d", minutes / 60, minutes % 60);
    }

    /** Dipanggil tiap menit dan saat layar menyala. */
    static void check(Context c, Controls controls) {
        SharedPreferences p = Prefs.of(c);
        String m = mode(c);
        // Jangan Ganggu dimatikan dari luar: pulihkan juga pengaturan kita.
        if ((m.equals(DND) || m.equals(SLEEP)) && !systemDnd(c)
                && System.currentTimeMillis() - p.getLong("focusAt", 0) > 3000) {
            set(c, controls, NONE);
        }
        Calendar now = Calendar.getInstance();
        int minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int from = start(c), to = end(c);
        boolean inside = p.getBoolean("sleepAuto", false) && from != to
                && (from < to ? minute >= from && minute < to : minute >= from || minute < to);
        // Hanya bertindak saat masuk atau keluar jadwal, agar pilihan manual tetap dihormati.
        if (inside == p.getBoolean("sleepInside", false)) return;
        p.edit().putBoolean("sleepInside", inside).commit();
        if (inside && !mode(c).equals(SLEEP)) {
            set(c, controls, SLEEP);
            p.edit().putBoolean("focusAuto", true).commit();
        } else if (!inside && mode(c).equals(SLEEP) && p.getBoolean("focusAuto", false)) {
            set(c, controls, NONE);
        }
    }
}
