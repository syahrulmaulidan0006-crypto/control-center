package com.example.controlcenter;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Akses notifikasi: dipakai untuk sinkron musik, popup, pusat notifikasi, dan suara.
 * Data hanya diproses di perangkat; aplikasi tidak memiliki izin internet.
 */
public class MusicListener extends NotificationListenerService {
    private static MusicListener instance;
    private static Runnable observer;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, String> seen = new HashMap<>();
    private final Map<String, StatusBarNotification> held = new LinkedHashMap<>();
    private SoundPool sounds;
    private int sound;
    private long lastSound;

    @Override public void onCreate() {
        super.onCreate();
        sounds = new SoundPool.Builder().setMaxStreams(1)
                .setAudioAttributes(new AudioAttributes.Builder()
                        // Bukan USAGE_NOTIFICATION: petunjuk pembisu suara bawaan
                        // ikut membisukan semua suara dengan jenis tersebut.
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .build();
        sound = sounds.load(this, R.raw.notif, 1);
    }

    @Override public void onDestroy() {
        sounds.release();
        instance = null;
        super.onDestroy();
    }

    @Override public void onListenerConnected() {
        instance = this;
        for (StatusBarNotification n : active()) seen.put(n.getKey(), signature(n));
        applyHints();
        changed();
    }

    @Override public void onListenerDisconnected() {
        instance = null;
        // Minta sistem menyambungkan lagi bila terputus.
        requestRebind(new ComponentName(this, MusicListener.class));
    }

    @Override public void onNotificationPosted(StatusBarNotification n) {
        changed();
        if (!visible(n)) return;
        String now = signature(n);
        String before = seen.put(n.getKey(), now);
        if (now.equals(before)) return;
        // Pesan baru memperbarui notifikasi yang sama, jadi tetap harus muncul.
        boolean once = (n.getNotification().flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0;
        if (before != null && once && !message(n)) return;
        if (Prefs.blocked(this).contains(n.getPackageName())) {
            Diag.popup(this, "tidak tampil, aplikasi dimatikan di daftar");
            return;
        }
        Ranking ranking = new Ranking();
        if (getCurrentRanking().getRanking(n.getKey(), ranking)
                && ranking.getImportance() < NotificationManager.IMPORTANCE_LOW) {
            Diag.popup(this, "tidak tampil, notifikasi diset sangat senyap");
            return;
        }
        int filter = getCurrentInterruptionFilter();
        if (filter != INTERRUPTION_FILTER_ALL && filter != INTERRUPTION_FILTER_UNKNOWN) {
            Diag.popup(this, "tidak tampil, Jangan Ganggu aktif");
            return;
        }
        applyHints();
        playSound();
        main.post(() -> {
            // Popup bawaan disingkirkan hanya bila popup kita benar-benar tampil.
            if (!OverlayService.running()) {
                Diag.popup(this, "tidak tampil, layanan Aksesibilitas tidak berjalan");
            }
            if (OverlayService.banner(n) && Prefs.is(this, "removeNative", true)
                    && n.isClearable() && !urgent(n)) {
                held.put(n.getKey(), n);
                dismissNative(n.getKey());
                changed();
            }
        });
    }

    @Override public void onNotificationRemoved(StatusBarNotification n, RankingMap map,
            int reason) {
        seen.remove(n.getKey());
        // Dibatalkan aplikasinya (misalnya pesan sudah dibaca): hapus juga salinan kita.
        if (reason != REASON_LISTENER_CANCEL) held.remove(n.getKey());
        changed();
    }

    private static boolean urgent(StatusBarNotification n) {
        String c = n.getNotification().category;
        return Notification.CATEGORY_CALL.equals(c) || Notification.CATEGORY_ALARM.equals(c)
                || n.getNotification().fullScreenIntent != null;
    }

    private void dismissNative(String key) {
        try { cancelNotification(key); } catch (Exception ignored) { }
    }

    /** Minta sistem membisukan suara bawaan agar tidak bertabrakan dengan suara kita. */
    private void hints() {
        try { requestListenerHints(muting() ? HINT_HOST_DISABLE_NOTIFICATION_EFFECTS : 0); }
        catch (Exception ignored) { }
    }

    /** Efek bawaan dibisukan bila diganti suara kita, atau bila getaran dimatikan. */
    private boolean muting() {
        return (Prefs.is(this, "sound", true) && Prefs.is(this, "muteNative", true))
                || !Prefs.is(this, "vibrate", true);
    }

    static void applyHints() {
        if (instance != null) instance.hints();
    }

    private void playSound() {
        boolean tone = Prefs.is(this, "sound", true);
        boolean muted = muting();
        if (!tone && !muted) return;
        int mode = getSystemService(AudioManager.class).getRingerMode();
        if (mode == AudioManager.RINGER_MODE_SILENT) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastSound < 2500) return;
        lastSound = now;
        // Getar bawaan ikut dibisukan, jadi digantikan di sini bila masih diinginkan.
        if (muted && Prefs.is(this, "vibrate", true)) {
            getSystemService(Vibrator.class).vibrate(
                    VibrationEffect.createWaveform(new long[]{0, 120, 80, 120}, -1));
        }
        if (!tone || mode != AudioManager.RINGER_MODE_NORMAL) return;
        int stream = sounds.play(sound, 1, 1, 1, 0, 1);
        android.util.Log.i("ControlCenter", "suara notifikasi diputar: " + stream);
    }

    private static String signature(StatusBarNotification n) {
        Notification v = n.getNotification();
        Object[] messages = (Object[]) v.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        return Notes.title(n) + "|" + Notes.text(n, Notification.EXTRA_TEXT) + "|"
                + (messages == null ? 0 : messages.length) + "|" + (message(n) ? v.when : 0);
    }

    private static boolean message(StatusBarNotification n) {
        Notification v = n.getNotification();
        return Notification.CATEGORY_MESSAGE.equals(v.category)
                || v.extras.containsKey(Notification.EXTRA_MESSAGES)
                || Notes.replyAction(n) != null;
    }

    /** Hanya notifikasi biasa: bukan musik, proses berjalan, atau ringkasan grup. */
    private boolean visible(StatusBarNotification n) {
        Notification v = n.getNotification();
        if (n.getPackageName().equals(getPackageName())
                && !v.extras.getBoolean("cc.tes")) return false;
        if (n.isOngoing() || (v.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return false;
        if (v.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return false;
        return !Notes.title(n).isEmpty()
                || !Notes.text(n, Notification.EXTRA_TEXT).isEmpty();
    }

    private StatusBarNotification[] active() {
        try {
            StatusBarNotification[] all = getActiveNotifications();
            return all == null ? new StatusBarNotification[0] : all;
        } catch (Exception e) { return new StatusBarNotification[0]; }
    }

    private void changed() {
        main.post(() -> { if (observer != null) observer.run(); });
    }

    private static long hiddenCountdown;

    /** Waktu selesai hitung mundur dari notifikasi aplikasi jam, atau 0 bila tidak ada. */
    static long countdown() {
        if (instance == null) return 0;
        long now = System.currentTimeMillis(), nearest = 0;
        for (StatusBarNotification n : instance.active()) {
            Notification v = n.getNotification();
            if (!v.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
                    || !v.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)) continue;
            if (v.when > now && v.when != hiddenCountdown && (nearest == 0 || v.when < nearest)) {
                nearest = v.when;
            }
        }
        return nearest;
    }

    static void hideCountdown() { hiddenCountdown = countdown(); }

    static void observe(Runnable r) { observer = r; }

    static boolean connected() { return instance != null; }

    /** Notifikasi yang tampil, terbaru dahulu. */
    static List<StatusBarNotification> list() {
        List<StatusBarNotification> out = new ArrayList<>();
        if (instance == null) return out;
        Map<String, StatusBarNotification> all = new LinkedHashMap<>(instance.held);
        for (StatusBarNotification n : instance.active()) {
            if (instance.visible(n)) all.put(n.getKey(), n);
        }
        out.addAll(all.values());
        out.sort((a, b) -> Long.compare(b.getPostTime(), a.getPostTime()));
        return out;
    }

    static void dismiss(String key) {
        if (instance == null) return;
        if (instance.held.remove(key) != null) instance.changed();
        instance.dismissNative(key);
    }
}
