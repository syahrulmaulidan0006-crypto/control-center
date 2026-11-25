package com.example.controlcenter;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
import android.widget.ImageView;
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
        setBackgroundColor(0x33000000);
        setOnClickListener(v -> close.run());

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClickable(true);
        card.setPadding(dp(18), dp(20), dp(18), dp(16));
        card.setBackground(new Glass(context, 40, 0x4D1B2440));

        LinearLayout head = new LinearLayout(context);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView badge = new ImageView(context);
        badge.setImageResource(item.icon);
        badge.setPadding(dp(11), dp(11), dp(11), dp(11));
        badge.setBackground(new Glass(context, 30,
                Theme.accent(context)));
        badge.setColorFilter(Theme.onAccent(context));
        head.addView(badge, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = text(item.label, 20, Color.WHITE);
        title.setPadding(dp(14), 0, 0, 0);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        GlassSwitch toggle = new GlassSwitch(context, Theme.accent(context), controls.isOn(item));
        toggle.setContentDescription("Nyalakan atau matikan " + item.label);
        toggle.setOnClickListener(v -> controls.click(item));
        head.addView(toggle, new LinearLayout.LayoutParams(dp(56), dp(32)));
        card.addView(head);
        toggle.postDelayed(new Runnable() {
            @Override public void run() {
                if (!toggle.isAttachedToWindow()) return;
                toggle.setOn(controls.isOn(item));
                toggle.postDelayed(this, 500);
            }
        }, 500);

        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(6), 0, 0);
        ScrollView scroll = new ScrollView(context) {
            @Override protected void onMeasure(int w, int h) {
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(
                        getResources().getDisplayMetrics().heightPixels
                                * 2 / (getResources().getDisplayMetrics().heightPixels
                                < dp(500) ? 6 : 5),
                        MeasureSpec.AT_MOST));
            }
        };
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(list);
        card.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

        if (item == Item.WIFI) wifi();
        else if (item == Item.BLUETOOTH) bluetooth();
        else data();

        Intent settings = item == Item.WIFI ? new Intent(Settings.ACTION_WIFI_SETTINGS)
                : item == Item.BLUETOOTH ? new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                : new Intent(Settings.ACTION_DATA_ROAMING_SETTINGS);
        TextView more = text("Pengaturan lengkap", 14, Color.WHITE);
        more.setGravity(Gravity.CENTER);
        more.setPadding(0, dp(13), 0, dp(13));
        more.setBackground(new Glass(context, 30, 0x1FFFFFFF));
        more.setOnClickListener(v -> controls.launch(settings));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
        mp.topMargin = dp(14);
        card.addView(more, mp);
        addView(card, new LayoutParams(width, -2, Gravity.CENTER));
    }

    private boolean granted(String permission) {
        return getContext().checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void wifi() {
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
                row(R.drawable.ic_wifi, r.SSID, detail, here, () -> choose(r.SSID, type));
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
        row(R.drawable.ic_wifi, "Sambungkan", "Ketuk setelah mengisi sandi", true, () -> {
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
        if (Build.VERSION.SDK_INT >= 31 && !granted(Manifest.permission.BLUETOOTH_CONNECT)) {
            note("Beri izin Bluetooth di aplikasi untuk melihat perangkat");
            return;
        }
        try {
            int count = 0;
            for (BluetoothDevice d : getContext().getSystemService(BluetoothManager.class)
                    .getAdapter().getBondedDevices()) {
                row(R.drawable.ic_bluetooth, d.getName(), "Tersimpan", false,
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
        row(R.drawable.ic_data, name.isEmpty() ? "Operator tidak terdeteksi" : name,
                using ? "Sedang dipakai untuk internet" : "Tidak sedang dipakai", using,
                () -> controls.launch(new Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)));
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

    private void row(int icon, String name, String detail, boolean active, Runnable click) {
        LinearLayout item = new LinearLayout(getContext());
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(12), dp(10), dp(12), dp(10));
        item.setBackground(new Glass(getContext(), 22, active
                ? Theme.alpha(Theme.accent(getContext()), 0x66) : 0x14FFFFFF));
        item.setOnClickListener(v -> click.run());
        ImageView image = new ImageView(getContext());
        image.setImageResource(icon);
        image.setPadding(dp(7), dp(7), dp(7), dp(7));
        item.addView(image, new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout words = new LinearLayout(getContext());
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(10), 0, 0, 0);
        TextView first = text(name, 15, Color.WHITE);
        first.setSingleLine(true);
        first.setEllipsize(android.text.TextUtils.TruncateAt.END);
        words.addView(first);
        words.addView(text(detail, 12, 0xB3FFFFFF));
        item.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(8);
        list.addView(item, p);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
