package com.example.controlcenter;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private TextView status;
    private final Shizuku.OnRequestPermissionResultListener result = (code, grant) -> update();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(p, p * 2, p, p);

        text(root, "Berikan izin di bawah. Setelah layanan aktif, geser ke bawah "
                + "dari status bar bagian kanan.");
        add(root, "1. Aktifkan/matikan layanan (Aksesibilitas)",
                () -> open(Settings.ACTION_ACCESSIBILITY_SETTINGS, false));
        add(root, "2. Izin ubah pengaturan (kecerahan, rotasi)",
                () -> open(Settings.ACTION_MANAGE_WRITE_SETTINGS, true));
        add(root, "3. Izin Jangan Ganggu",
                () -> open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, false));
        add(root, "4. Izin akses notifikasi (sinkron musik)",
                () -> open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, false));
        add(root, "5. Izin lokasi dan Bluetooth (daftar Wi-Fi/perangkat)", () -> {
            if (Build.VERSION.SDK_INT >= 31) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.BLUETOOTH_CONNECT}, 1);
            } else {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
            }
        });

        text(root, "\nAgar tetap aktif setelah aplikasi ditutup");
        add(root, "6. Bebaskan dari penghemat baterai", () -> {
            if (getSystemService(PowerManager.class)
                    .isIgnoringBatteryOptimizations(getPackageName())) {
                Toast.makeText(this, "Sudah dibebaskan", Toast.LENGTH_SHORT).show();
            } else {
                open(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, true);
            }
        });
        add(root, "7. Mulai otomatis (Xiaomi/Redmi/POCO)", () -> {
            try {
                startActivity(new Intent().setComponent(new ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity")));
            } catch (Exception e) {
                open(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true);
            }
        });

        text(root, "\nPilih mode");
        RadioGroup group = new RadioGroup(this);
        RadioButton a = radio(group, "Mode A: biasa. Wi-Fi, data, dan lainnya membuka menu sistem");
        RadioButton b = radio(group, "Mode B: Shizuku. Mencoba menyalakan/mematikan langsung");
        (Shell.modeB(this) ? b : a).setChecked(true);
        group.setOnCheckedChangeListener((g, id) -> {
            getSharedPreferences("cc", MODE_PRIVATE).edit()
                    .putBoolean("modeB", id == b.getId()).apply();
            update();
        });
        root.addView(group);
        status = text(root, "");
        add(root, "Izinkan Shizuku (khusus Mode B)", () -> {
            try {
                if (!Shizuku.pingBinder()) {
                    Toast.makeText(this, "Pasang dan jalankan aplikasi Shizuku dahulu",
                            Toast.LENGTH_LONG).show();
                } else if (!Shell.ready()) {
                    Shizuku.requestPermission(2);
                }
            } catch (Throwable e) {
                Toast.makeText(this, "Shizuku tidak tersedia", Toast.LENGTH_SHORT).show();
            }
            update();
        });

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        Shizuku.addRequestPermissionResultListener(result);
    }

    @Override protected void onResume() {
        super.onResume();
        update();
    }

    @Override protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(result);
        super.onDestroy();
    }

    private void update() {
        if (status == null) return;
        status.setText(!Shell.modeB(this) ? "Mode A aktif"
                : Shell.ready() ? "Mode B aktif, Shizuku siap"
                : "Mode B dipilih, tetapi Shizuku belum siap");
    }

    private TextView text(LinearLayout root, String value) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(16);
        root.addView(t);
        return t;
    }

    private RadioButton radio(RadioGroup group, String value) {
        RadioButton r = new RadioButton(this);
        r.setId(android.view.View.generateViewId());
        r.setText(value);
        group.addView(r);
        return r;
    }

    private void add(LinearLayout root, String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(v -> action.run());
        root.addView(b);
    }

    private void open(String action, boolean forPackage) {
        Intent i = new Intent(action);
        if (forPackage) i.setData(Uri.parse("package:" + getPackageName()));
        try { startActivity(i); }
        catch (Exception e) { Toast.makeText(this, "Menu tidak tersedia", Toast.LENGTH_SHORT).show(); }
    }
}
