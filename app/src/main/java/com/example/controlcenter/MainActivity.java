package com.example.controlcenter;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(p, p * 2, p, p);

        TextView info = new TextView(this);
        info.setText("Berikan izin di bawah. Setelah layanan aktif, geser ke bawah "
                + "dari status bar bagian kanan.");
        info.setTextSize(16);
        root.addView(info);

        add(root, "1. Aktifkan/matikan layanan (Aksesibilitas)",
                () -> open(Settings.ACTION_ACCESSIBILITY_SETTINGS, false));
        add(root, "2. Izin ubah pengaturan (kecerahan, rotasi)",
                () -> open(Settings.ACTION_MANAGE_WRITE_SETTINGS, true));
        add(root, "3. Izin Jangan Ganggu",
                () -> open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, false));

        add(root, "4. Izin akses notifikasi (sinkron musik)",
                () -> open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, false));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
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
