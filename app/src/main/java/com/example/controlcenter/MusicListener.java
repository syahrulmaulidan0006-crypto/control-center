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
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.ArrayList;
import java.util.HashMap;
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
    private SoundPool sounds;
    private int sound;
    private long lastSound;

    @Override public void onCreate() {
        super.onCreate();
        sounds = new SoundPool.Builder().setMaxStreams(1)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
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
        if (Prefs.blocked(this).contains(n.getPackageName())) return;
        Ranking ranking = new Ranking();
        if (getCurrentRanking().getRanking(n.getKey(), ranking)
                && ranking.getImportance() < NotificationManager.IMPORTANCE_LOW) return;
        if (getCurrentInterruptionFilter() != INTERRUPTION_FILTER_ALL) return;
        playSound();
        main.post(() -> OverlayService.banner(n));
    }

    @Override public void onNotificationRemoved(StatusBarNotification n) {
        seen.remove(n.getKey());
        changed();
    }

    private void playSound() {
        if (!Prefs.is(this, "sound", true)) return;
        if (getSystemService(AudioManager.class).getRingerMode()
                != AudioManager.RINGER_MODE_NORMAL) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastSound < 2500) return;
        lastSound = now;
        sounds.play(sound, 1, 1, 1, 0, 1);
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
        if (n.getPackageName().equals(getPackageName())) return false;
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

    static void observe(Runnable r) { observer = r; }

    static boolean connected() { return instance != null; }

    /** Notifikasi yang tampil, terbaru dahulu. */
    static List<StatusBarNotification> list() {
        List<StatusBarNotification> out = new ArrayList<>();
        if (instance == null) return out;
        for (StatusBarNotification n : instance.active()) {
            if (instance.visible(n)) out.add(n);
        }
        out.sort((a, b) -> Long.compare(b.getPostTime(), a.getPostTime()));
        return out;
    }

    static void dismiss(String key) {
        if (instance == null) return;
        try { instance.cancelNotification(key); } catch (Exception ignored) { }
    }
}
