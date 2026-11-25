package com.example.controlcenter;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ApplicationExitInfo;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import rikka.shizuku.Shizuku;

/** Halaman status dan pengaturan. */
public class MainActivity extends Activity {
    private static final int CARD = 0xFF1C2450, MUTED = 0xFFC4CBE8;
    private LinearLayout root;
    private ScrollView scroll;
    private final Shizuku.OnRequestPermissionResultListener result = (code, grant) -> build();
    private final Shizuku.OnBinderReceivedListener binder = this::build;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(40), dp(18), dp(28));
        scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF0F1536);
        scroll.addView(root);
        setContentView(scroll);
        Shizuku.addRequestPermissionResultListener(result);
        Shizuku.addBinderReceivedListenerSticky(binder);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS) != 0) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 3);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        build();
    }

    @Override protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(result);
        Shizuku.removeBinderReceivedListener(binder);
        super.onDestroy();
    }

    private void build() {
        if (root == null) return;
        root.removeAllViews();
        title("Status layanan", 28);
        note("Titik hijau berarti siap. Merah perlu diperbaiki agar tetap berjalan.");

        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        ComponentName service = new ComponentName(this, OverlayService.class);
        boolean accessible = enabled != null && (enabled.contains(service.flattenToString())
                || enabled.contains(service.flattenToShortString()));
        status("Aksesibilitas", accessible,
                () -> open(Settings.ACTION_ACCESSIBILITY_SETTINGS, false));
        status("Layanan sedang berjalan", running(), () -> {
            Toast.makeText(this, "Matikan lalu nyalakan lagi layanan Control Center",
                    Toast.LENGTH_LONG).show();
            open(Settings.ACTION_ACCESSIBILITY_SETTINGS, false);
        });
        NotificationManager nm = getSystemService(NotificationManager.class);
        status("Akses notifikasi", nm.isNotificationListenerAccessGranted(
                        new ComponentName(this, MusicListener.class)),
                () -> open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, false));
        status("Bebas penghemat baterai", getSystemService(PowerManager.class)
                        .isIgnoringBatteryOptimizations(getPackageName()),
                () -> open(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, true));
        status("Ubah pengaturan (kecerahan, rotasi)", Settings.System.canWrite(this),
                () -> open(Settings.ACTION_MANAGE_WRITE_SETTINGS, true));
        status("Jangan Ganggu", nm.isNotificationPolicyAccessGranted(),
                () -> open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, false));
        status("Lokasi (daftar Wi-Fi)", checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION) == 0, this::permissions);
        manual("Mulai otomatis (Xiaomi)", () -> {
            try {
                startActivity(new Intent().setClassName("com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"));
            } catch (Exception e) {
                open(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true);
            }
        });
        manual("Pop-up saat di latar belakang (Xiaomi)", () -> {
            try {
                startActivity(new Intent("miui.intent.action.APP_PERM_EDITOR")
                        .setClassName("com.miui.securitycenter",
                                "com.miui.permcenter.permissions.PermissionsEditorActivity")
                        .putExtra("extra_pkgname", getPackageName()));
            } catch (Exception e) {
                open(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true);
            }
        });

        status("Notifikasi \"Control Center aktif\"",
                getSystemService(NotificationManager.class).areNotificationsEnabled(),
                () -> startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())));
        note("Penting di Xiaomi: buka layar aplikasi terbaru, tekan lama kartu Control "
                + "Center, lalu ketuk ikon gembok agar tidak ikut dibersihkan.");
        if (Build.VERSION.SDK_INT >= 30) history();

        title("Warna tombol", 20);
        colors();

        title("Notifikasi", 20);
        toggle("Popup notifikasi", "popup", true);
        toggle("Suara notifikasi", "sound", true);
        toggle("Sembunyikan isi pesan", "hideContent", false);
        toggle("Sembunyikan popup saat layar penuh", "hideFullscreen", true);
        toggle("Geser kiri atas untuk pusat notifikasi", "leftSwipe", true);
        button("Pilih aplikasi yang boleh muncul", this::apps);
        toggle("Bisukan suara bawaan (agar tidak dobel)", "muteNative", true);
        boolean heads = Settings.Global.getInt(getContentResolver(),
                "heads_up_notifications_enabled", 1) == 1;
        title("Popup bawaan HP", 20);
        button(heads ? "Matikan popup bawaan semua aplikasi (Shizuku)"
                : "Nyalakan kembali popup bawaan (Shizuku)", () -> headsUp(!heads));
        note(heads ? "Cara terbersih. Memerlukan Shizuku aktif. Sebagian HP mengabaikannya."
                : "Popup bawaan sedang dimatikan. Nyalakan kembali sebelum menghapus aplikasi.");
        toggle("Tanpa Shizuku: hapus notifikasi bawaan saat popup kita tampil",
                "removeNative", false);
        note("Popup bawaan bisa terlihat sekejap, dan notifikasinya hilang dari panel "
                + "bawaan. Salinannya ada di pusat notifikasi kita.");

        title("Mode koneksi", 20);
        boolean b = Prefs.is(this, "modeB", false);
        button((b ? "" : "Dipakai: ") + "Mode A, membuka menu sistem", () -> mode(false));
        button((b ? "Dipakai: " : "") + "Mode B, Shizuku", () -> mode(true));
        if (b) status("Shizuku siap", Shell.ready(), () -> {
            try {
                if (!Shizuku.pingBinder()) {
                    Toast.makeText(this, "Jalankan aplikasi Shizuku dahulu",
                            Toast.LENGTH_LONG).show();
                } else {
                    Shizuku.requestPermission(2);
                }
            } catch (Throwable e) {
                Toast.makeText(this, "Shizuku tidak tersedia", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @SuppressWarnings("deprecation")
    private boolean running() {
        for (ActivityManager.RunningServiceInfo s : getSystemService(ActivityManager.class)
                .getRunningServices(50)) {
            if (OverlayService.class.getName().equals(s.service.getClassName())) return true;
        }
        return false;
    }

    /** Catatan dari sistem tentang mengapa proses layanan terakhir dihentikan. */
    @android.annotation.TargetApi(30)
    private void history() {
        title("Riwayat layanan berhenti", 20);
        int shown = 0;
        for (ApplicationExitInfo e : getSystemService(ActivityManager.class)
                .getHistoricalProcessExitReasons(getPackageName(), 0, 30)) {
            if (!getPackageName().equals(e.getProcessName())) continue;
            String when = new SimpleDateFormat("d MMM HH:mm", new Locale("id"))
                    .format(new Date(e.getTimestamp()));
            note(when + ": " + reason(e.getReason()) + " (kode " + e.getReason() + ")"
                    + (e.getDescription() == null ? "" : "\n" + e.getDescription()));
            if (++shown == 3) break;
        }
        if (shown == 0) note("Belum ada catatan");
    }

    @android.annotation.TargetApi(30)
    private String reason(int code) {
        switch (code) {
            case ApplicationExitInfo.REASON_USER_REQUESTED: return "Dihentikan pengguna/sistem";
            case ApplicationExitInfo.REASON_USER_STOPPED: return "Pengguna dihentikan";
            case ApplicationExitInfo.REASON_LOW_MEMORY: return "Memori penuh";
            case ApplicationExitInfo.REASON_CRASH:
            case ApplicationExitInfo.REASON_CRASH_NATIVE: return "Aplikasi error";
            case ApplicationExitInfo.REASON_ANR: return "Aplikasi tidak merespons";
            case ApplicationExitInfo.REASON_SIGNALED: return "Dimatikan sistem";
            case ApplicationExitInfo.REASON_PERMISSION_CHANGE: return "Izin berubah";
            default: return "Lainnya";
        }
    }

    private void permissions() {
        List<String> p = new ArrayList<>();
        p.add(Manifest.permission.ACCESS_FINE_LOCATION);
        p.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= 31) p.add(Manifest.permission.BLUETOOTH_CONNECT);
        if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS);
        requestPermissions(p.toArray(new String[0]), 1);
    }

    private void headsUp(boolean on) {
        if (!Shell.ready()) {
            Toast.makeText(this, "Jalankan dan izinkan Shizuku dahulu", Toast.LENGTH_LONG).show();
            try {
                if (Shizuku.pingBinder()) Shizuku.requestPermission(2);
            } catch (Throwable ignored) { }
            return;
        }
        Shell.run(this, shell -> shell.toggle("headsup", on), ok -> {
            if (!ok) Toast.makeText(this, "Tidak didukung di HP ini", Toast.LENGTH_SHORT).show();
            root.postDelayed(this::build, 400);
        });
    }

    private void mode(boolean b) {
        Prefs.of(this).edit().putBoolean("modeB", b).commit();
        build();
    }

    private void apps() {
        Map<String, String> apps = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo r : getPackageManager().queryIntentActivities(launcher, 0)) {
            String pkg = r.activityInfo.packageName;
            if (!pkg.equals(getPackageName())) {
                apps.put(r.loadLabel(getPackageManager()) + "\n" + pkg, pkg);
            }
        }
        String[] labels = apps.keySet().toArray(new String[0]);
        Set<String> blocked = Prefs.blocked(this);
        boolean[] checked = new boolean[labels.length];
        for (int i = 0; i < labels.length; i++) {
            checked[i] = !blocked.contains(apps.get(labels[i]));
        }
        String[] names = new String[labels.length];
        for (int i = 0; i < labels.length; i++) names[i] = labels[i].split("\n")[0];
        new AlertDialog.Builder(this).setTitle("Popup dan suara untuk")
                .setMultiChoiceItems(names, checked, (d, i, on) -> {
                    if (on) blocked.remove(apps.get(labels[i]));
                    else blocked.add(apps.get(labels[i]));
                })
                .setPositiveButton("Simpan", (d, w) ->
                        Prefs.of(this).edit().putStringSet("blocked", blocked).commit())
                .setNegativeButton("Batal", null).show();
    }

    private void colors() {
        int chosen = Prefs.of(this).getInt("color", 0);
        LinearLayout row = null;
        for (int i = 0; i < Theme.COLORS.length; i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(this);
                root.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, dp(8), 0, dp(4));
            View dot = new View(this);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(Theme.COLORS[i]);
            d.setStroke(dp(i == chosen ? 4 : 1), i == chosen ? 0xFF37D98A : 0x80FFFFFF);
            dot.setBackground(d);
            item.addView(dot, new LinearLayout.LayoutParams(dp(48), dp(48)));
            TextView name = label(Theme.NAMES[i], 12, MUTED);
            name.setGravity(Gravity.CENTER);
            item.addView(name);
            int index = i;
            item.setContentDescription("Warna " + Theme.NAMES[i]);
            item.setOnClickListener(v -> {
                Prefs.of(this).edit().putInt("color", index).commit();
                int y = scroll.getScrollY();
                build();
                scroll.post(() -> scroll.scrollTo(0, y));
            });
            row.addView(item, new LinearLayout.LayoutParams(
                    (getResources().getDisplayMetrics().widthPixels - dp(36)) / 4, -2));
        }
    }

    private TextView label(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private void title(String value, int size) {
        TextView t = label(value, size, Color.WHITE);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(16), 0, dp(6));
        root.addView(t);
    }

    private void note(String value) {
        TextView t = label(value, 13, MUTED);
        t.setPadding(0, dp(4), 0, dp(4));
        root.addView(t);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable d = new GradientDrawable();
        d.setColor(CARD);
        d.setCornerRadius(dp(22));
        c.setBackground(d);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(8);
        root.addView(c, p);
        return c;
    }

    private void row(String name, String detail, int dot, Runnable click) {
        LinearLayout c = card();
        View v = new View(this);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(dot);
        v.setBackground(d);
        c.addView(v, new LinearLayout.LayoutParams(dp(12), dp(12)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(14), 0, 0, 0);
        words.addView(label(name, 15, Color.WHITE));
        words.addView(label(detail, 13, MUTED));
        c.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        c.setOnClickListener(x -> click.run());
    }

    private void status(String name, boolean ok, Runnable click) {
        row(name, ok ? "Siap" : "Ketuk untuk memperbaiki", ok ? 0xFF37D98A : 0xFFFF6B5E, click);
    }

    private void manual(String name, Runnable click) {
        row(name, "Ketuk, lalu periksa secara manual", 0xFF8B93B8, click);
    }

    private void button(String name, Runnable click) {
        LinearLayout c = card();
        c.addView(label(name, 15, Color.WHITE));
        c.setOnClickListener(v -> click.run());
    }

    private void toggle(String name, String key, boolean fallback) {
        LinearLayout c = card();
        Switch s = new Switch(this);
        s.setText(name);
        s.setTextColor(Color.WHITE);
        s.setTextSize(15);
        s.setChecked(Prefs.is(this, key, fallback));
        s.setOnCheckedChangeListener((v, on) -> {
            Prefs.of(this).edit().putBoolean(key, on).commit();
            if (key.equals("leftSwipe") || key.equals("sound") || key.equals("muteNative")) {
                sendBroadcast(new Intent(OverlayService.REFRESH).setPackage(getPackageName()));
            }
        });
        c.addView(s, new LinearLayout.LayoutParams(-1, -2));
    }

    private void open(String action, boolean forPackage) {
        Intent i = new Intent(action);
        if (forPackage) i.setData(Uri.parse("package:" + getPackageName()));
        try { startActivity(i); }
        catch (Exception e) { Toast.makeText(this, "Menu tidak tersedia", Toast.LENGTH_SHORT).show(); }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
