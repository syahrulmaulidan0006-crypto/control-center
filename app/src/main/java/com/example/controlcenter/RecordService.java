package com.example.controlcenter;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.Display;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Layanan latar depan yang menjalankan perekaman layar dan menyimpannya ke galeri. */
public class RecordService extends Service {
    private static final String STOP = "berhenti";
    private static RecordService instance;
    /** Waktu mulai (elapsedRealtime); 0 bila tidak sedang merekam. */
    static volatile long since;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaProjection projection;
    private Recorder recorder;
    private ParcelFileDescriptor file;
    private Uri entry;
    private boolean finishing;

    static boolean active() { return instance != null; }

    static void stop() { if (instance != null) instance.finish(); }

    static void start(Context c, int result, Intent data, int sound) {
        c.startForegroundService(new Intent(c, RecordService.class)
                .putExtra("result", result).putExtra("data", data).putExtra("sound", sound));
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent == null || STOP.equals(intent.getAction())) {
            if (instance != null) finish(); else stopSelf();
            return START_NOT_STICKY;
        }
        if (instance != null) return START_NOT_STICKY;
        instance = this;
        int sound = intent.getIntExtra("sound", Recorder.SILENT);
        try {
            // Wajib tampil sebagai layanan latar depan sebelum meminta proyeksi layar.
            notice(sound);
            projection = getSystemService(MediaProjectionManager.class).getMediaProjection(
                    intent.getIntExtra("result", Activity.RESULT_CANCELED),
                    intent.getParcelableExtra("data"));
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { finish(); }
            }, handler);
        } catch (RuntimeException e) {
            fail(e);
            return START_NOT_STICKY;
        }
        // Jeda sebentar agar jendela persetujuan tidak ikut terekam.
        handler.postDelayed(() -> begin(sound), 600);
        return START_NOT_STICKY;
    }

    private void notice(int sound) {
        getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel("rekam", "Perekaman layar",
                        NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, RecordService.class).setAction(STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, "rekam")
                .setContentTitle("Sedang merekam layar")
                .setContentText("Ketuk untuk berhenti")
                .setSmallIcon(R.drawable.ic_record)
                .setOngoing(true)
                .setUsesChronometer(true)
                .setContentIntent(stop)
                .addAction(new Notification.Action.Builder(null, "Berhenti", stop).build())
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
            if (Build.VERSION.SDK_INT >= 30 && sound == Recorder.MICROPHONE) {
                type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            }
            startForeground(2, n, type);
        } else {
            startForeground(2, n);
        }
    }

    private void begin(int sound) {
        if (finishing || projection == null) return;
        try {
            DisplayMetrics m = new DisplayMetrics();
            getSystemService(android.hardware.display.DisplayManager.class)
                    .getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(m);
            ContentValues v = new ContentValues();
            v.put(MediaStore.Video.Media.DISPLAY_NAME, "Rekaman_"
                    + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())
                    + ".mp4");
            v.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            v.put(MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/ControlCenter");
            v.put(MediaStore.Video.Media.IS_PENDING, 1);
            entry = getContentResolver().insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v);
            // Sisi terpanjang paling besar 1920, lalu 1280 bila HP menolak.
            for (int longest : new int[]{1920, 1280}) {
                float scale = Math.min(1f,
                        longest / (float) Math.max(m.widthPixels, m.heightPixels));
                int w = Math.round(m.widthPixels * scale / 16) * 16;
                int h = Math.round(m.heightPixels * scale / 16) * 16;
                file = getContentResolver().openFileDescriptor(entry, "rwt");
                try {
                    recorder = new Recorder(projection, file.getFileDescriptor(), w, h,
                            Math.round(m.densityDpi * scale), sound);
                    android.util.Log.i("ControlCenter", "merekam " + w + "x" + h);
                    break;
                } catch (Exception e) {
                    file.close();
                    if (longest == 1280) throw e;
                    android.util.Log.w("ControlCenter", "ukuran ditolak: " + e);
                }
            }
            if (recorder.soundFailed) {
                toast("Suara tidak bisa direkam, merekam tanpa suara");
            }
            since = SystemClock.elapsedRealtime();
            OverlayService.recordingChanged();
        } catch (Exception e) {
            fail(e);
        }
    }

    private void fail(Exception e) {
        android.util.Log.w("ControlCenter", "rekam gagal: " + e);
        toast("Perekaman gagal dimulai");
        finish();
    }

    private void finish() {
        if (finishing) return;
        finishing = true;
        handler.removeCallbacksAndMessages(null);
        since = 0;
        OverlayService.recordingChanged();
        Recorder done = recorder;
        recorder = null;
        // Menutup berkas bisa beberapa saat, jadi tidak di utas utama.
        new Thread(() -> {
            boolean good = done != null && done.stop();
            try { if (file != null) file.close(); }
            catch (Exception ignored) { }
            if (entry != null) {
                try {
                    if (good) {
                        ContentValues v = new ContentValues();
                        v.put(MediaStore.Video.Media.IS_PENDING, 0);
                        getContentResolver().update(entry, v, null, null);
                    } else {
                        getContentResolver().delete(entry, null, null);
                    }
                } catch (RuntimeException e) {
                    android.util.Log.w("ControlCenter", "galeri: " + e);
                }
            }
            handler.post(() -> {
                if (good) {
                    android.util.Log.i("ControlCenter", "rekaman tersimpan");
                    toast("Tersimpan di galeri, folder Movies/ControlCenter");
                }
                if (projection != null) projection.stop();
                projection = null;
                stopForeground(true);
                stopSelf();
                instance = null;
            });
        }, "rekam-simpan").start();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override public void onDestroy() {
        if (instance == this && !finishing) finish();
        super.onDestroy();
    }
}
