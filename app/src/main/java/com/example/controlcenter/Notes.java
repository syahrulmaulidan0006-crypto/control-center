package com.example.controlcenter;

import android.app.ActivityOptions;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Pembantu untuk membaca dan menjalankan notifikasi. */
final class Notes {
    static String text(StatusBarNotification n, String key) {
        CharSequence v = n.getNotification().extras.getCharSequence(key);
        return v == null ? "" : v.toString();
    }

    static String title(StatusBarNotification n) { return text(n, Notification.EXTRA_TITLE); }

    static String body(Context c, StatusBarNotification n) {
        if (Prefs.is(c, "hideContent", false)) return "Isi disembunyikan";
        String big = text(n, Notification.EXTRA_BIG_TEXT);
        return big.isEmpty() ? text(n, Notification.EXTRA_TEXT) : big;
    }

    static String app(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) { return pkg; }
    }

    /** Ikon asli aplikasi pengirim. */
    static Drawable icon(Context c, StatusBarNotification n) {
        try { return c.getPackageManager().getApplicationIcon(n.getPackageName()); }
        catch (Exception e) {
            Icon small = n.getNotification().getSmallIcon();
            Drawable d = small == null ? null : small.loadDrawable(c);
            return d != null ? d : c.getDrawable(R.drawable.ic_notifications);
        }
    }

    /** Foto pengirim, bila disediakan aplikasinya. */
    static Drawable photo(Context c, StatusBarNotification n) {
        if (Prefs.is(c, "hideContent", false)) return null;
        try {
            Icon large = n.getNotification().getLargeIcon();
            return large == null ? null : large.loadDrawable(c);
        } catch (Exception e) { return null; }
    }

    static String age(long time) {
        long minutes = (System.currentTimeMillis() - time) / 60000;
        if (minutes < 1) return "sekarang";
        if (minutes < 60) return minutes + " mnt";
        if (minutes < 1440) return minutes / 60 + " jam";
        return minutes / 1440 + " hari";
    }

    static boolean send(Context c, PendingIntent intent, Intent fill) {
        if (intent == null) return false;
        try {
            Bundle options = null;
            if (Build.VERSION.SDK_INT >= 34) {
                options = ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(
                                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                        .toBundle();
            }
            intent.send(c, 0, fill, null, null, null, options);
            return true;
        } catch (Exception e) { return false; }
    }

    static void open(Context c, StatusBarNotification n) {
        if (!send(c, n.getNotification().contentIntent, null)) {
            Intent i = c.getPackageManager().getLaunchIntentForPackage(n.getPackageName());
            if (i != null) c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        if ((n.getNotification().flags & Notification.FLAG_AUTO_CANCEL) != 0) {
            MusicListener.dismiss(n.getKey());
        }
    }

    /** Aksi biasa ditambah aksi untuk jam tangan; sebagian aplikasi menaruhnya di sana. */
    private static List<Notification.Action> actions(StatusBarNotification n) {
        List<Notification.Action> all = new ArrayList<>();
        Notification v = n.getNotification();
        if (v.actions != null) all.addAll(Arrays.asList(v.actions));
        try { all.addAll(new Notification.WearableExtender(v).getActions()); }
        catch (Exception ignored) { }
        return all;
    }

    static Notification.Action replyAction(StatusBarNotification n) {
        for (Notification.Action a : actions(n)) {
            if (a.getRemoteInputs() == null) continue;
            for (RemoteInput r : a.getRemoteInputs()) {
                if (r.getAllowFreeFormInput()) return a;
            }
        }
        return null;
    }

    static Notification.Action readAction(StatusBarNotification n) {
        List<Notification.Action> all = actions(n);
        for (Notification.Action a : all) {
            if (a.getSemanticAction() == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ) {
                return a;
            }
        }
        for (Notification.Action a : all) {
            if (a.title == null || a.getRemoteInputs() != null) continue;
            String t = a.title.toString().toLowerCase(Locale.ROOT);
            if (t.contains("dibaca") || t.contains("mark as read") || t.contains("mark read")) {
                return a;
            }
        }
        return null;
    }

    static boolean reply(Context c, Notification.Action action, String message) {
        Intent fill = new Intent();
        Bundle results = new Bundle();
        for (RemoteInput r : action.getRemoteInputs()) {
            results.putCharSequence(r.getResultKey(), message);
        }
        RemoteInput.addResultsToIntent(action.getRemoteInputs(), fill, results);
        return send(c, action.actionIntent, fill);
    }
}
