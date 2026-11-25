package com.example.controlcenter;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.InputType;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.LinkedHashMap;
import java.util.Map;

/** Jendela mengambang untuk Wi-Fi, data seluler, dan Bluetooth. */
class Picker extends FrameLayout {
    private final Controls controls;
    private final LinearLayout list;
    private final Runnable close;

    Picker(Context context, Controls controls, Item item, int width, Runnable close) {
        super(context);
        this.controls = controls;
        this.close = close;
        setBackgroundColor(0x99000000);
        setOnClickListener(v -> close.run());

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClickable(true);
        card.setPadding(dp(20), dp(20), dp(20), dp(14));
        GradientDrawable back = new GradientDrawable();
        back.setColor(0xE6232838);
        back.setCornerRadius(dp(34));
        back.setStroke(dp(1), 0x40FFFFFF);
        card.setBackground(back);

        TextView title = text(item.label, 20, Color.WHITE);
        title.setPadding(dp(4), 0, 0, dp(10));
        card.addView(title);

        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(context) {
            @Override protected void onMeasure(int w, int h) {
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(
                        getResources().getDisplayMetrics().heightPixels * 2 / 5,
                        MeasureSpec.AT_MOST));
            }
        };
        scroll.addView(list);
        card.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

        if (item == Item.WIFI) wifi();
        else if (item == Item.BLUETOOTH) bluetooth();
        else data();

        Intent settings = item == Item.WIFI ? new Intent(Settings.ACTION_WIFI_SETTINGS)
                : item == Item.BLUETOOTH ? new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                : new Intent(Settings.ACTION_DATA_ROAMING_SETTINGS);
        TextView more = row(card, "Pengaturan lengkap", null, () -> controls.launch(settings));
        more.setGravity(Gravity.CENTER);
        addView(card, new LayoutParams(width, -2, Gravity.CENTER));
    }

    private boolean granted(String permission) {
        return getContext().checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void wifi() {
        row(list, controls.isOn(Item.WIFI) ? "Matikan Wi-Fi" : "Nyalakan Wi-Fi", null, () -> {
            controls.click(Item.WIFI);
            close.run();
        });
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            note("Beri izin lokasi di aplikasi untuk melihat daftar jaringan");
            return;
        }
        WifiManager wifi = getContext().getApplicationContext()
                .getSystemService(WifiManager.class);
        try {
            wifi.startScan();
            Map<String, ScanResult> found = new LinkedHashMap<>();
            wifi.getScanResults().stream()
                    .sorted((a, b) -> b.level - a.level)
                    .filter(r -> r.SSID != null && !r.SSID.isEmpty())
                    .forEach(r -> found.putIfAbsent(r.SSID, r));
            String current = wifi.getConnectionInfo().getSSID();
            for (ScanResult r : found.values()) {
                String type = security(r.capabilities);
                boolean here = ("\"" + r.SSID + "\"").equals(current);
                int bars = WifiManager.calculateSignalLevel(r.level, 4) + 1;
                String detail = (here ? "Tersambung, " : "") + "sinyal " + bars + "/4"
                        + ("open".equals(type) ? ", terbuka" : ", terkunci");
                row(list, r.SSID, detail, () -> choose(r.SSID, type));
            }
            if (found.isEmpty()) note("Jaringan belum terlihat. Pastikan Wi-Fi dan lokasi aktif");
        } catch (SecurityException e) {
            note("Daftar jaringan tidak diizinkan oleh sistem");
        }
    }

    private static String security(String c) {
        if (c.contains("EAP")) return "other";
        if (c.contains("PSK")) return "wpa2";
        if (c.contains("SAE")) return "wpa3";
        if (c.contains("WEP") || c.contains("OWE")) return "other";
        return "open";
    }

    private void choose(String ssid, String type) {
        if (!Shell.modeB(getContext()) || !Shell.ready() || Build.VERSION.SDK_INT < 30
                || "other".equals(type)) {
            controls.launch(new Intent(Settings.Panel.ACTION_WIFI));
            return;
        }
        if ("open".equals(type)) { connect(ssid, type, ""); return; }
        list.removeAllViews();
        ((OverlayService) getContext()).setKeyboard(true);
        note("Kata sandi untuk " + ssid);
        EditText input = new EditText(getContext());
        input.setTextColor(Color.WHITE);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        list.addView(input, new LinearLayout.LayoutParams(-1, -2));
        row(list, "Sambungkan", null, () -> {
            String password = input.getText().toString();
            if (password.length() < 8) { controls.toast("Minimal 8 karakter"); return; }
            connect(ssid, type, password);
        });
        input.requestFocus();
        input.postDelayed(() -> getContext().getSystemService(InputMethodManager.class)
                .showSoftInput(input, 0), 200);
    }

    private void connect(String ssid, String type, String password) {
        Shell.run(getContext(), shell -> shell.connectWifi(ssid, type, password), ok -> {
            controls.toast(ok ? "Mencoba menyambungkan" : "Gagal menyambungkan");
            if (ok) close.run();
        });
    }

    private void bluetooth() {
        row(list, controls.isOn(Item.BLUETOOTH) ? "Matikan Bluetooth" : "Nyalakan Bluetooth",
                null, () -> { controls.click(Item.BLUETOOTH); close.run(); });
        if (Build.VERSION.SDK_INT >= 31 && !granted(Manifest.permission.BLUETOOTH_CONNECT)) {
            note("Beri izin Bluetooth di aplikasi untuk melihat perangkat");
            return;
        }
        try {
            int count = 0;
            for (BluetoothDevice d : getContext().getSystemService(BluetoothManager.class)
                    .getAdapter().getBondedDevices()) {
                row(list, d.getName(), "Tersimpan",
                        () -> controls.launch(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
                count++;
            }
            if (count == 0) note("Belum ada perangkat tersimpan, atau Bluetooth mati");
        } catch (Exception e) {
            note("Daftar perangkat tidak tersedia");
        }
    }

    private void data() {
        TelephonyManager phone = getContext().getSystemService(TelephonyManager.class);
        ConnectivityManager net = getContext().getSystemService(ConnectivityManager.class);
        NetworkCapabilities c = net.getNetworkCapabilities(net.getActiveNetwork());
        boolean using = c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
        String name = phone.getNetworkOperatorName();
        note("Operator: " + (name.isEmpty() ? "tidak terdeteksi" : name));
        note(using ? "Internet sedang memakai data seluler"
                : "Internet tidak sedang memakai data seluler");
        row(list, controls.isOn(Item.DATA) ? "Matikan data seluler" : "Nyalakan data seluler",
                null, () -> { controls.click(Item.DATA); close.run(); });
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(getContext());
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private void note(String value) {
        TextView t = text(value, 13, 0xB3FFFFFF);
        t.setPadding(dp(4), dp(8), dp(4), dp(8));
        list.addView(t);
    }

    private TextView row(LinearLayout parent, String name, String detail, Runnable click) {
        TextView t = text(detail == null ? name : name + "\n" + detail, 15, Color.WHITE);
        t.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x24FFFFFF);
        d.setCornerRadius(dp(18));
        t.setBackground(d);
        t.setOnClickListener(v -> click.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(8);
        parent.addView(t, p);
        return t;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
