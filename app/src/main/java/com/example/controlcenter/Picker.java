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
import android.media.AudioManager;
import android.provider.AlarmClock;
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

/** Jendela mengambang milik sebuah tombol, dibuka dengan tekan lama. */
class Picker extends FrameLayout {
    private final Controls controls;
    private final LinearLayout list;
    private final Runnable close;
    private final Item item;
    private int rows;
    private boolean first = true;

    Picker(Context context, Controls controls, Item item, int width, Runnable close) {
        super(context);
        this.controls = controls;
        this.close = close;
        this.item = item;
        setBackgroundColor(0x33000000);
        setOnClickListener(v -> close.run());

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClickable(true);
        card.setPadding(dp(18), dp(20), dp(18), dp(16));
        card.setBackground(new Glass(context, 40, 0x4D1B2440).reading());

        LinearLayout head = new LinearLayout(context);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView badge = new ImageView(context);
        badge.setImageResource(item == Item.AUTO_LIGHT ? R.drawable.ic_sun : item.icon);
        badge.setPadding(dp(11), dp(11), dp(11), dp(11));
        badge.setBackground(new Glass(context, 30,
                Theme.accent(context)));
        badge.setColorFilter(Theme.onAccent(context));
        head.addView(badge, new LinearLayout.LayoutParams(dp(48), dp(48)));
        badge.setScaleX(0);
        badge.setScaleY(0);
        badge.setRotation(-90);
        badge.animate().scaleX(1).scaleY(1).rotation(0).setStartDelay(90).setDuration(420)
                .setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start();
        TextView title = text(name(item), 20, Color.WHITE);
        title.setPadding(dp(14), 0, 0, 0);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        GlassSwitch toggle = null;
        if (switchable(item)) {
            GlassSwitch made = new GlassSwitch(context, Theme.accent(context),
                    controls.isOn(item));
            made.setContentDescription("Nyalakan atau matikan " + name(item));
            made.setOnClickListener(v -> {
                controls.click(item);
                made.postDelayed(this::render, 350);
            });
            Motion.press(made);
            head.addView(made, new LinearLayout.LayoutParams(dp(56), dp(32)));
            toggle = made;
        }
        card.addView(head);
        GlassSwitch live = toggle;
        if (live != null) {
            live.postDelayed(new Runnable() {
                @Override public void run() {
                    if (!live.isAttachedToWindow()) return;
                    live.setOn(controls.isOn(item));
                    live.postDelayed(this, 500);
                }
            }, 500);
        }

        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(6), 0, 0);
        ScrollView scroll = new ScrollView(context) {
            @Override protected void onMeasure(int w, int h) {
                int screen = getResources().getDisplayMetrics().heightPixels;
                // Layar mendatar lebih pendek, jadi sisakan ruang untuk judul dan tombol.
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(
                        screen * (screen < dp(500) ? 42 : 58) / 100, MeasureSpec.AT_MOST));
            }
        };
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(list);
        card.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

        render();
        first = false;

        Intent settings = settings(item);
        if (settings != null) {
            TextView more = text("Pengaturan lengkap", 14, Color.WHITE);
            more.setGravity(Gravity.CENTER);
            more.setPadding(0, dp(13), 0, dp(13));
            more.setBackground(new Glass(context, 30, 0x1FFFFFFF));
            more.setOnClickListener(v -> controls.launch(settings));
            Motion.press(more);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
            mp.topMargin = dp(14);
            card.addView(more, mp);
        }
        addView(card, new LayoutParams(width, -2, Gravity.CENTER));
    }

    private static String name(Item item) {
        switch (item) {
            case DND: return "Fokus";
            case AUTO_LIGHT: return "Kecerahan";
            case VOLUME: return "Volume";
            default: return item.label;
        }
    }

    private static boolean switchable(Item item) {
        return !item.leaves() && item != Item.AUTO_LIGHT;
    }

    private static Intent settings(Item item) {
        switch (item) {
            case WIFI: return new Intent(Settings.ACTION_WIFI_SETTINGS);
            case BLUETOOTH: return new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            case DATA: return new Intent(Settings.ACTION_DATA_ROAMING_SETTINGS);
            case AIRPLANE: return new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS);
            case LOCATION: return new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            case NFC: return new Intent(Settings.ACTION_NFC_SETTINGS);
            case DND: return new Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS);
            case AUTO_LIGHT: case DARK: return new Intent(Settings.ACTION_DISPLAY_SETTINGS);
            case VOLUME: case VIBRATE: case MUTE:
                return new Intent(Settings.ACTION_SOUND_SETTINGS);
            case HOTSPOT: return new Intent().setComponent(new android.content.ComponentName(
                    "com.android.settings", "com.android.settings.TetherSettings"));
            default: return null;
        }
    }

    /** Mengisi (ulang) bagian bawah jendela. */
    private void render() {
        list.removeAllViews();
        rows = 0;
        switch (item) {
            case WIFI: wifi(); break;
            case BLUETOOTH: bluetooth(); break;
            case DATA: data(); break;
            case DND: focus(); break;
            case HOTSPOT: hotspot(); break;
            case TORCH: torch(); break;
            case TIMER: case ALARM: timer(); break;
            case AUTO_LIGHT: light(); break;
            case VOLUME: case VIBRATE: case MUTE: volume(); break;
            case RECORD: record(); break;
            default: plain();
        }
    }

    private void record() {
        if (RecordService.active()) {
            row(R.drawable.ic_record, "Berhenti merekam", "Video disimpan ke galeri", true,
                    () -> {
                        RecordService.stop();
                        close.run();
                    });
            return;
        }
        sound(R.drawable.ic_record, "Tanpa suara", "Hanya gambar layar", Recorder.SILENT);
        sound(R.drawable.ic_mic, "Dengan mikrofon", "Suara Anda dan sekitar",
                Recorder.MICROPHONE);
        sound(R.drawable.ic_volume, "Dengan suara HP", "Musik dan game, bila diizinkan",
                Recorder.DEVICE);
    }

    private void sound(int icon, String label, String detail, int value) {
        boolean last = Prefs.of(getContext()).getInt("recordSound", Recorder.MICROPHONE) == value;
        row(icon, label, detail, last, () -> {
            Prefs.of(getContext()).edit().putInt("recordSound", value).commit();
            Context app = getContext().getApplicationContext();
            controls.closeFor(() -> RecordActivity.open(app, value));
        });
    }

    private void refreshSoon() { postDelayed(this::render, 350); }

    private void plain() {
        boolean on = controls.isOn(item);
        if (item.leaves()) {
            row(item.icon, "Jalankan", item.label, true, () -> controls.click(item));
        } else {
            row(item.icon, on ? "Aktif" : "Mati",
                    "Ketuk untuk " + (on ? "mematikan" : "menyalakan"), on, () -> {
                        controls.click(item);
                        refreshSoon();
                    });
        }
    }

    private void focus() {
        String mode = Focus.mode(getContext());
        mode(R.drawable.ic_moon, "Jangan Ganggu", "Popup dan suara diam", Focus.DND, mode);
        mode(R.drawable.ic_bed, "Tidur", "Alarm tetap bunyi, layar redup", Focus.SLEEP, mode);
        mode(R.drawable.ic_contacts, "Pribadi", "Isi pesan di popup disembunyikan",
                Focus.PERSONAL, mode);
        boolean auto = Prefs.is(getContext(), "sleepAuto", false);
        row(R.drawable.ic_timer, "Jadwal tidur", auto ? "Aktif, "
                + Focus.clock(Focus.start(getContext())) + " sampai "
                + Focus.clock(Focus.end(getContext())) : "Mati", auto, () -> {
                    Prefs.of(getContext()).edit().putBoolean("sleepAuto", !auto).commit();
                    Focus.check(getContext(), controls);
                    refreshSoon();
                });
        if (auto) {
            time("Mulai", "sleepStart", Focus.start(getContext()));
            time("Selesai", "sleepEnd", Focus.end(getContext()));
        }
    }

    private void mode(int icon, String label, String detail, String value, String current) {
        boolean on = value.equals(current);
        row(icon, label, on ? "Aktif. Ketuk untuk mematikan" : detail, on, () -> {
            Focus.set(getContext(), controls, on ? Focus.NONE : value);
            refreshSoon();
        });
    }

    /** Baris pengatur jam dengan langkah 30 menit. */
    private void time(String label, String key, int minutes) {
        LinearLayout line = new LinearLayout(getContext());
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(dp(14), dp(4), dp(4), dp(4));
        line.addView(text(label + "  " + Focus.clock(minutes), 15, Color.WHITE),
                new LinearLayout.LayoutParams(0, -2, 1));
        for (int step : new int[]{-30, 30}) {
            TextView b = text(step < 0 ? "\u2212" : "+", 20, Color.WHITE);
            b.setGravity(Gravity.CENTER);
            b.setContentDescription((step < 0 ? "Mundurkan " : "Majukan ") + label);
            b.setBackground(new Glass(getContext(), 20, 0x26FFFFFF));
            b.setOnClickListener(v -> {
                Prefs.of(getContext()).edit()
                        .putInt(key, (minutes + step + 1440) % 1440).commit();
                Focus.check(getContext(), controls);
                render();
            });
            Motion.press(b);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(44), dp(40));
            p.leftMargin = dp(8);
            line.addView(b, p);
        }
        add(line);
    }

    private void hotspot() {
        if (!Shell.modeB(getContext()) || !Shell.ready() || Build.VERSION.SDK_INT < 30) {
            note("Android tidak mengizinkan aplikasi biasa menyalakan hotspot. "
                    + "Gunakan pengaturan, atau aktifkan Mode B (Shizuku).");
            row(R.drawable.ic_hotspot, "Buka pengaturan hotspot", "Nyalakan dari sana", true,
                    () -> controls.click(Item.HOTSPOT));
            return;
        }
        ((OverlayService) getContext()).setKeyboard(true);
        note("Uji coba lewat Shizuku. Di sebagian HP gagal atau internet tidak terbagi.");
        EditText name = field("Nama hotspot", Prefs.of(getContext()).getString("apName", ""),
                InputType.TYPE_CLASS_TEXT);
        EditText pass = field("Kata sandi (minimal 8)",
                Prefs.of(getContext()).getString("apPass", ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        row(R.drawable.ic_hotspot, "Nyalakan", "Dengan nama dan sandi di atas", true, () -> {
            String n = name.getText().toString().trim(), w = pass.getText().toString();
            if (n.isEmpty() || w.length() < 8) {
                controls.toast("Isi nama dan sandi minimal 8 karakter");
                return;
            }
            Prefs.of(getContext()).edit().putString("apName", n).putString("apPass", w).apply();
            Shell.run(getContext(), shell -> shell.hotspot(true, n, w), ok -> controls.toast(
                    ok ? "Perintah terkirim, periksa hotspot" : "Tidak didukung di HP ini"));
        });
        row(R.drawable.ic_hotspot, "Matikan", "Menghentikan hotspot", false, () ->
                Shell.run(getContext(), shell -> shell.hotspot(false, "", ""), ok ->
                        controls.toast(ok ? "Hotspot dimatikan" : "Gagal mematikan")));
    }

    private EditText field(String hint, String value, int type) {
        EditText input = new EditText(getContext());
        input.setHint(hint);
        input.setText(value);
        input.setHintTextColor(0x99FFFFFF);
        input.setTextColor(Color.WHITE);
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setInputType(type);
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        input.setBackground(new Glass(getContext(), 22, 0x1FFFFFFF));
        add(input);
        return input;
    }

    private void torch() {
        if (controls.torchLevels() > 1) {
            slider(new VerticalSlider(getContext(), R.drawable.ic_flashlight,
                    controls.isOn(Item.TORCH) ? 1 : 0, controls::torchStrength, null));
            note("Geser untuk mengatur terang senter");
        } else {
            plain();
            note("HP ini tidak mendukung pengaturan terang senter");
        }
    }

    private void timer() {
        for (int minutes : new int[]{1, 5, 10, 30}) {
            row(R.drawable.ic_hourglass, minutes + " menit", "Mulai hitung mundur", false,
                    () -> {
                        controls.toast("Timer " + minutes + " menit dimulai");
                        // Dipakai kapsul melayang untuk menghitung mundur.
                        Prefs.of(getContext()).edit().putLong(Island.TIMER_END,
                                System.currentTimeMillis() + minutes * 60000L).apply();
                        controls.launch(new Intent(AlarmClock.ACTION_SET_TIMER)
                                .putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                                .putExtra(AlarmClock.EXTRA_SKIP_UI, true));
                    });
        }
        row(R.drawable.ic_timer, "Buka jam", "Alarm dan timer", false,
                () -> controls.click(Item.ALARM));
    }

    private void light() {
        float now = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, 128) / 255f;
        slider(new VerticalSlider(getContext(), R.drawable.ic_sun, Math.min(1, now), v -> {
            controls.put(Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
            controls.put(Settings.System.SCREEN_BRIGHTNESS, Math.max(1, Math.round(v * 255)));
        }, controls::canWrite));
        toggleRow(Item.AUTO_LIGHT, "Kecerahan otomatis");
        toggleRow(Item.DARK, "Mode gelap");
    }

    private void toggleRow(Item target, String label) {
        boolean on = controls.isOn(target);
        row(target.icon, label, on ? "Aktif" : "Mati", on, () -> {
            controls.click(target);
            refreshSoon();
        });
    }

    private void volume() {
        AudioManager audio = controls.audio;
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        slider(new VerticalSlider(getContext(), R.drawable.ic_volume,
                audio.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max, v -> {
                    try {
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(v * max), 0);
                    } catch (SecurityException e) {
                        controls.toast("Izin Jangan Ganggu diperlukan");
                    }
                }, null));
        ringer(R.drawable.ic_volume, "Dering", AudioManager.RINGER_MODE_NORMAL);
        ringer(R.drawable.ic_bell, "Getar", AudioManager.RINGER_MODE_VIBRATE);
        ringer(R.drawable.ic_mute, "Senyap", AudioManager.RINGER_MODE_SILENT);
    }

    private void ringer(int icon, String label, int mode) {
        boolean on = controls.audio.getRingerMode() == mode;
        row(icon, label, on ? "Dipakai" : "Ketuk untuk memakai", on, () -> {
            try { controls.audio.setRingerMode(mode); }
            catch (SecurityException e) { controls.toast("Izin Jangan Ganggu diperlukan"); }
            refreshSoon();
        });
    }

    private void slider(VerticalSlider view) {
        FrameLayout holder = new FrameLayout(getContext());
        boolean small = getResources().getDisplayMetrics().heightPixels < dp(500);
        holder.addView(view, new LayoutParams(dp(78), dp(small ? 110 : 180),
                Gravity.CENTER_HORIZONTAL));
        add(holder);
    }

    /** Menambah isi; saat jendela dibuka, isinya muncul berurutan. */
    private void add(android.view.View v) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(8);
        list.addView(v, p);
        if (first) Motion.enter(v, 120 + rows++ * 45L, dp(18));
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
        add(t);
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
        Motion.press(item);
        add(item);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
