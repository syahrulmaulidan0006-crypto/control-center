package com.example.controlcenter;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.ApplicationExitInfo;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.res.ColorStateList;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
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
    private static final int MUTED = 0xFFC4CBE8, GOOD = 0xFF37D98A, BAD = 0xFFFF6B5E;
    private static final String[] TABS = {"Beranda", "Tampilan", "Notifikasi", "Lanjutan"};
    private static final int[] TAB_ICONS = {R.drawable.ic_home, R.drawable.ic_dark,
            R.drawable.ic_notifications, R.drawable.ic_settings};
    private LinearLayout root, bar;
    private ScrollView scroll;
    private int tab;
    /** Benar saat berpindah tab, sehingga kartunya muncul berurutan. */
    private boolean entering = true;
    private final Shizuku.OnRequestPermissionResultListener result = (code, grant) -> build();
    private final Shizuku.OnBinderReceivedListener binder = this::build;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) tab = state.getInt("tab");
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackground(new Backdrop());
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(28), dp(16), dp(24));
        root.setClipChildren(false);
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(root);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        bar = new LinearLayout(this);
        bar.setPadding(dp(6), dp(6), dp(6), dp(6));
        LinearLayout.LayoutParams under = new LinearLayout.LayoutParams(-1, -2);
        under.setMargins(dp(14), dp(6), dp(14), dp(12));
        page.addView(bar, under);
        setContentView(page);
        getWindow().setStatusBarColor(0xFF17275C);
        getWindow().setNavigationBarColor(0xFF0B0F24);
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

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
    }

    @Override protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(result);
        Shizuku.removeBinderReceivedListener(binder);
        super.onDestroy();
    }

    private void build() {
        if (root == null) return;
        root.removeAllViews();
        Glass.load(this);
        tabs();
        TextView head = label(tab == 0 ? "Control Center" : TABS[tab], 30, Color.WHITE);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setPadding(dp(4), 0, 0, dp(6));
        root.addView(head);
        if (tab == 0) home();
        else if (tab == 1) looks();
        else if (tab == 2) notices();
        else advanced();
        if (!entering) return;
        entering = false;
        for (int i = 0; i < root.getChildCount() && i < 12; i++) {
            Motion.enter(root.getChildAt(i), i * 35L, dp(26));
        }
    }

    private void tabs() {
        bar.removeAllViews();
        bar.setBackground(new Glass(this, 32, 0x2EFFFFFF));
        for (int i = 0; i < TABS.length; i++) {
            int index = i;
            boolean on = i == tab;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, dp(8), 0, dp(7));
            if (on) item.setBackground(new Glass(this, 26, Theme.alpha(Theme.accent(this), 0xD9)));
            ImageView icon = new ImageView(this);
            icon.setImageResource(TAB_ICONS[i]);
            icon.setColorFilter(on ? Theme.onAccent(this) : Color.WHITE);
            item.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));
            TextView name = label(TABS[i], 11, on ? Theme.onAccent(this) : Color.WHITE);
            name.setSingleLine(true);
            name.setGravity(Gravity.CENTER);
            item.addView(name);
            item.setContentDescription("Tab " + TABS[i]);
            item.setOnClickListener(v -> {
                if (tab == index) { scroll.smoothScrollTo(0, 0); return; }
                Haptic.tick(this);
                tab = index;
                entering = true;
                build();
                scroll.scrollTo(0, 0);
            });
            Motion.press(item);
            bar.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
        }
    }

    private void home() {
        List<Object[]> checks = new ArrayList<>();
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        ComponentName service = new ComponentName(this, OverlayService.class);
        boolean accessible = enabled != null && (enabled.contains(service.flattenToString())
                || enabled.contains(service.flattenToShortString()));
        check(checks, "Aksesibilitas", accessible,
                () -> open(Settings.ACTION_ACCESSIBILITY_SETTINGS, false));
        check(checks, "Layanan sedang berjalan", running(), () -> {
            Toast.makeText(this, "Matikan lalu nyalakan lagi layanan Control Center",
                    Toast.LENGTH_LONG).show();
            open(Settings.ACTION_ACCESSIBILITY_SETTINGS, false);
        });
        NotificationManager nm = getSystemService(NotificationManager.class);
        check(checks, "Akses notifikasi", nm.isNotificationListenerAccessGranted(
                        new ComponentName(this, MusicListener.class)),
                () -> open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, false));
        check(checks, "Bebas penghemat baterai", getSystemService(PowerManager.class)
                        .isIgnoringBatteryOptimizations(getPackageName()),
                () -> open(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, true));
        check(checks, "Ubah pengaturan (kecerahan, rotasi)", Settings.System.canWrite(this),
                () -> open(Settings.ACTION_MANAGE_WRITE_SETTINGS, true));
        check(checks, "Jangan Ganggu", nm.isNotificationPolicyAccessGranted(),
                () -> open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, false));
        check(checks, "Lokasi (daftar Wi-Fi)", checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION) == 0, this::permissions);
        check(checks, "Notifikasi \"Control Center aktif\"", nm.areNotificationsEnabled(),
                () -> startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())));

        int broken = 0;
        for (Object[] c : checks) if (!(Boolean) c[1]) broken++;
        row(broken == 0 ? "Semua siap" : broken + " hal perlu diperbaiki", broken == 0
                ? "Geser dari atas layar untuk memakai Control Center"
                : "Ketuk kartu bertitik merah di bawah", broken == 0 ? GOOD : BAD, () -> { });

        // Yang bermasalah diletakkan paling atas.
        title("Status layanan", 18);
        for (boolean ok : new boolean[]{false, true}) {
            for (Object[] c : checks) {
                if ((Boolean) c[1] == ok) status((String) c[0], ok, (Runnable) c[2]);
            }
        }

        title("Khusus Xiaomi", 18);
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
        note("Buka layar aplikasi terbaru, tekan lama kartu Control Center, lalu ketuk "
                + "ikon gembok agar tidak ikut dibersihkan.");
        if (Build.VERSION.SDK_INT >= 30) history();
    }

    private void check(List<Object[]> list, String name, boolean ok, Runnable fix) {
        list.add(new Object[]{name, ok, fix});
    }

    private void looks() {
        title("Warna tombol", 18);
        colors();
        title("Gaya kaca", 18);
        popupStyles();
        title("Kaca dan cairan", 18);
        toggle("Kaca bening asli (Android 11 ke atas)", "frost", true);
        note("Layar dipotret sesaat saat panel atau popup muncul, hanya untuk latar kaca. "
                + "Tidak disimpan atau dikirim.");
        toggle("Isian cairan pada volume dan kecerahan", "liquid", true);
        toggle("Popup volume cairan saat tombol volume ditekan", "volumeHud", true);
        note("Mengatur volume media. Saat panggilan dan di layar kunci tetap memakai bawaan. "
                + "Matikan bila tombol volume ingin dipakai memotret.");
        title("Gerak dan sentuhan", 18);
        toggle("Getar halus saat disentuh", Haptic.KEY, true);
        toggle("Animasi saat mengisi daya", "chargeAnim", true);
        title("Kapsul melayang", 18);
        toggle("Tampilkan kapsul", Island.ON, true);
        note("Muncul di atas layar saat ada lagu, hitung mundur, atau baru mengisi daya. "
                + "Ketuk untuk membesarkan.");
        position();
    }

    private void notices() {
        note("Popup terakhir: " + Diag.read(this));
        button("Tes popup sekarang", this::test);
        title("Popup dan suara", 18);
        toggle("Popup notifikasi", "popup", true);
        toggle("Suara notifikasi", "sound", true);
        toggle("Getar saat ada notifikasi", "vibrate", true);
        note("Suara mengikuti volume dering dan diam saat HP dalam mode getar atau senyap. "
                + "Bila getar dimatikan, suara bawaan aplikasi lain juga dibisukan.");
        toggle("Bisukan suara bawaan (agar tidak dobel)", "muteNative", true);
        button("Pilih aplikasi yang boleh muncul", this::apps);
        title("Privasi dan permainan", 18);
        toggle("Sembunyikan isi pesan", "hideContent", false);
        toggle("Sembunyikan popup saat layar penuh", "hideFullscreen", true);
    }

    private void advanced() {
        title("Gerakan geser", 18);
        toggle("Geser kiri atas untuk pusat notifikasi", "leftSwipe", true);
        toggle("Matikan area geser saat layar mendatar (game)", "noSwipeLand", false);
        boolean heads = Settings.Global.getInt(getContentResolver(),
                "heads_up_notifications_enabled", 1) == 1;
        title("Popup bawaan HP", 18);
        button(heads ? "Matikan popup bawaan semua aplikasi (Shizuku)"
                : "Nyalakan kembali popup bawaan (Shizuku)", () -> headsUp(!heads));
        note(heads ? "Cara terbersih. Memerlukan Shizuku aktif. Sebagian HP mengabaikannya."
                : "Popup bawaan sedang dimatikan. Nyalakan kembali sebelum menghapus aplikasi.");
        toggle("Ganti popup bawaan: hapus notifikasi bawaan saat popup kita tampil",
                "removeNative", true);
        note("Popup bawaan bisa terlihat sekejap, dan notifikasinya hilang dari panel "
                + "bawaan. Salinannya ada di pusat notifikasi kita.");

        title("Mode koneksi", 18);
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

    /** Jarak kapsul dari tepi atas, disesuaikan dengan letak kamera tiap HP. */
    private void position() {
        LinearLayout c = card();
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.NO_GRAVITY);
        int now = Prefs.of(this).getInt(Island.TOP, 2);
        TextView name = label("Jarak kapsul dari atas: " + now, 15, Color.WHITE);
        c.addView(name);
        slider(c, "Jarak kapsul dari atas", 60, now,
                value -> name.setText("Jarak kapsul dari atas: " + value),
                value -> {
                    Prefs.of(this).edit().putInt(Island.TOP, value).commit();
                    refreshService();
                });
    }

    private void slider(LinearLayout into, String label, int max, int now,
            java.util.function.IntConsumer moving, java.util.function.IntConsumer done) {
        SeekBar bar = new SeekBar(this);
        bar.setMax(max);
        bar.setProgress(now);
        bar.setContentDescription(label);
        bar.setPadding(dp(8), dp(14), dp(8), dp(8));
        bar.setProgressTintList(ColorStateList.valueOf(0xFFFFFFFF));
        bar.setThumbTintList(ColorStateList.valueOf(0xFFFFFFFF));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int value, boolean user) {
                moving.accept(value);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { done.accept(s.getProgress()); }
        });
        into.addView(bar, new LinearLayout.LayoutParams(-1, -2));
    }

    private void refreshService() {
        sendBroadcast(new Intent(OverlayService.REFRESH).setPackage(getPackageName()));
    }

    /** Membangun ulang tanpa memindahkan posisi gulir. */
    private void rebuildHere() {
        int y = scroll.getScrollY();
        build();
        scroll.post(() -> scroll.scrollTo(0, y));
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

    private void test() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (!nm.areNotificationsEnabled()) {
            Toast.makeText(this, "Izinkan notifikasi aplikasi ini dahulu", Toast.LENGTH_LONG).show();
            return;
        }
        nm.createNotificationChannel(new NotificationChannel("tes", "Tes popup",
                NotificationManager.IMPORTANCE_DEFAULT));
        Bundle extra = new Bundle();
        extra.putBoolean("cc.tes", true);
        nm.notify((int) (System.currentTimeMillis() % 100000), new Notification.Builder(this, "tes")
                .setSmallIcon(R.drawable.ic_bell).setContentTitle("Tes popup")
                .setContentText("Popup Control Center berfungsi").addExtras(extra).build());
        root.postDelayed(this::build, 1200);
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
                Haptic.click(this);
                rebuildHere();
            });
            row.addView(item, new LinearLayout.LayoutParams(
                    (getResources().getDisplayMetrics().widthPixels - dp(32)) / 4, -2));
        }
    }

    /** Dua contoh popup di atas latar berwarna; ketuk untuk memilih. */
    private void popupStyles() {
        note("Dipakai di semua bagian: control center, pusat notifikasi, popup, dan volume. "
                + "Contoh di bawah hanya gambaran.");
        int chosen = PopupGlass.chosen(this);
        View[] samples = new View[PopupGlass.NAMES.length];
        for (int i = 0; i < PopupGlass.NAMES.length; i++) {
            int index = i;
            LinearLayout frame = new LinearLayout(this);
            frame.setOrientation(LinearLayout.VERTICAL);
            frame.setPadding(dp(14), dp(14), dp(14), dp(12));
            GradientDrawable back = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF2F8CFF, 0xFF8B5CF6, 0xFFFF8A5B});
            back.setCornerRadius(dp(22));
            back.setStroke(dp(i == chosen ? 4 : 1), i == chosen ? 0xFF37D98A : 0x40FFFFFF);
            frame.setBackground(back);
            LinearLayout sample = new LinearLayout(this);
            sample.setOrientation(LinearLayout.VERTICAL);
            sample.setPadding(dp(18), dp(14), dp(18), dp(14));
            sample.setBackground(new PopupGlass(this, i));
            samples[i] = sample;
            TextView head = label("Rina", 15, Color.WHITE);
            head.setTypeface(Typeface.DEFAULT_BOLD);
            sample.addView(head);
            sample.addView(label("Sampai ketemu nanti sore", 14, Color.WHITE));
            frame.addView(sample, new LinearLayout.LayoutParams(-1, -2));
            TextView name = label((i == chosen ? "Dipakai: " : "") + PopupGlass.NAMES[i], 14,
                    Color.WHITE);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setPadding(dp(4), dp(10), 0, 0);
            frame.addView(name);
            frame.setContentDescription("Gaya kaca " + PopupGlass.NAMES[i]);
            frame.setOnClickListener(v -> {
                Prefs.of(this).edit().putInt(PopupGlass.KEY, index).commit();
                Haptic.click(this);
                rebuildHere();
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
            p.topMargin = dp(8);
            root.addView(frame, p);
        }
        clearness(samples);
    }

    /** Penggeser kebeningan popup; contoh di atasnya ikut berubah saat digeser. */
    private void clearness(View[] samples) {
        LinearLayout c = card();
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.NO_GRAVITY);
        int now = PopupGlass.clearness(this);
        TextView name = label("Kebeningan kaca: " + now + "%", 15, Color.WHITE);
        c.addView(name);
        slider(c, "Kebeningan kaca", 100, now, value -> {
            name.setText("Kebeningan kaca: " + value + "%");
            for (int i = 0; i < samples.length; i++) {
                samples[i].setBackground(new PopupGlass(this, i, value));
            }
        }, value -> {
            Prefs.of(this).edit().putInt(PopupGlass.CLEAR, value).commit();
            // Kartu di halaman ini langsung memakai kebeningan baru.
            Glass.load(this);
            root.getRootView().invalidate();
            rebuildHere();
        });
        TextView hint = label("Berlaku untuk kedua gaya. 0% pekat, 100% sebening kaca. "
                + "Makin bening, tulisan makin sulit dibaca di layar terang.", 13, MUTED);
        c.addView(hint);
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
        t.setPadding(dp(4), dp(18), 0, dp(4));
        root.addView(t);
    }

    private void note(String value) {
        TextView t = label(value, 13, MUTED);
        t.setPadding(dp(4), dp(6), dp(4), dp(2));
        root.addView(t);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(new Glass(this, 26, 0x1FFFFFFF));
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
        Motion.press(c);
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
        Motion.press(c);
    }

    private void toggle(String name, String key, boolean fallback) {
        LinearLayout c = card();
        c.addView(label(name, 15, Color.WHITE), new LinearLayout.LayoutParams(0, -2, 1));
        GlassSwitch s = new GlassSwitch(this, Theme.accent(this), Prefs.is(this, key, fallback));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(54), dp(31));
        p.leftMargin = dp(12);
        c.addView(s, p);
        c.setContentDescription(name + (Prefs.is(this, key, fallback) ? ", nyala" : ", mati"));
        c.setOnClickListener(v -> {
            boolean on = !Prefs.is(this, key, fallback);
            Prefs.of(this).edit().putBoolean(key, on).commit();
            Haptic.click(this);
            s.setOn(on);
            c.setContentDescription(name + (on ? ", nyala" : ", mati"));
            if (key.equals("leftSwipe") || key.equals("noSwipeLand") || key.equals("sound")
                    || key.equals("muteNative") || key.equals("vibrate")
                    || key.equals(Island.ON)) {
                refreshService();
            }
        });
    }

    private void open(String action, boolean forPackage) {
        Intent i = new Intent(action);
        if (forPackage) i.setData(Uri.parse("package:" + getPackageName()));
        try { startActivity(i); }
        catch (Exception e) { Toast.makeText(this, "Menu tidak tersedia", Toast.LENGTH_SHORT).show(); }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
