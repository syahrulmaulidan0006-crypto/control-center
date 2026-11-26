package com.example.controlcenter;

/** Daftar tombol panel. */
enum Item {
    AIRPLANE(R.drawable.ic_airplane, "Mode pesawat"),
    WIFI(R.drawable.ic_wifi, "Wi-Fi"),
    DATA(R.drawable.ic_data, "Data seluler"),
    BLUETOOTH(R.drawable.ic_bluetooth, "Bluetooth"),
    HOTSPOT(R.drawable.ic_hotspot, "Hotspot"),
    LOCATION(R.drawable.ic_location, "Lokasi"),

    ROTATION(R.drawable.ic_rotation, "Kunci rotasi"),
    DND(R.drawable.ic_moon, "Jangan ganggu"),
    TORCH(R.drawable.ic_flashlight, "Senter"),
    VIBRATE(R.drawable.ic_bell, "Mode getar"),

    CALCULATOR(R.drawable.ic_calculator, "Kalkulator"),
    CAMERA(R.drawable.ic_camera, "Kamera"),
    ALARM(R.drawable.ic_timer, "Alarm"),
    SCREENSHOT(R.drawable.ic_screenshot, "Tangkapan layar"),
    RECORD(R.drawable.ic_record, "Perekam layar"),
    QR(R.drawable.ic_qr, "Pemindai QR"),
    DARK(R.drawable.ic_dark, "Mode gelap"),
    AUTO_LIGHT(R.drawable.ic_autolight, "Kecerahan otomatis"),
    NFC(R.drawable.ic_nfc, "NFC"),
    SYNC(R.drawable.ic_sync, "Sinkronisasi"),
    BATTERY(R.drawable.ic_battery, "Penghemat baterai"),
    SETTINGS(R.drawable.ic_settings, "Pengaturan"),
    NOTIFICATIONS(R.drawable.ic_notifications, "Notifikasi"),
    RECENTS(R.drawable.ic_recents, "Aplikasi terbaru"),
    LOCK(R.drawable.ic_lock, "Kunci layar"),
    POWER(R.drawable.ic_power, "Menu daya"),

    // Tambahan: tersedia di layar atur tombol.
    AWAKE(R.drawable.ic_awake, "Layar menyala"),
    MUTE(R.drawable.ic_mute, "Bisukan media"),
    VOLUME(R.drawable.ic_mixer, "Atur volume"),
    TIMER(R.drawable.ic_hourglass, "Hitung mundur"),
    CALENDAR(R.drawable.ic_calendar, "Kalender"),
    GALLERY(R.drawable.ic_gallery, "Galeri"),
    MUSIC_APP(R.drawable.ic_music, "Musik"),
    BROWSER(R.drawable.ic_browser, "Peramban"),
    MESSAGES(R.drawable.ic_message, "Pesan"),
    PHONE(R.drawable.ic_phone, "Telepon"),
    CONTACTS(R.drawable.ic_contacts, "Kontak"),
    MAPS(R.drawable.ic_map, "Peta"),
    EMAIL(R.drawable.ic_mail, "Email"),
    FILES(R.drawable.ic_folder, "Berkas"),
    VOICE(R.drawable.ic_mic, "Perekam suara"),
    VIDEO(R.drawable.ic_video, "Rekam video"),
    SPLIT(R.drawable.ic_split, "Layar terbagi"),
    HOME(R.drawable.ic_home, "Layar utama"),
    CAST(R.drawable.ic_cast, "Transmisikan"),
    VPN(R.drawable.ic_vpn, "VPN"),
    USAGE(R.drawable.ic_usage, "Pemakaian data");

    final int icon;
    final String label;

    Item(int icon, String label) {
        this.icon = icon;
        this.label = label;
    }

    /** Tombol yang berpindah dari panel (membuka aplikasi atau menu). */
    boolean leaves() {
        switch (this) {
            case AIRPLANE: case WIFI: case DATA: case BLUETOOTH: case LOCATION:
            case ROTATION: case DND: case TORCH: case VIBRATE: case DARK:
            case AUTO_LIGHT: case NFC: case SYNC: case AWAKE: case MUTE:
                return false;
            default:
                return true;
        }
    }

    /** Boleh dipasang dan disingkirkan; empat tombol koneksi tetap di kotaknya. */
    boolean movable() { return ordinal() > BLUETOOTH.ordinal(); }

    static java.util.List<Item> standard() {
        java.util.List<Item> list = new java.util.ArrayList<>();
        for (int i = ROTATION.ordinal(); i <= POWER.ordinal(); i++) list.add(values()[i]);
        return list;
    }

    static java.util.List<Item> load(android.content.Context c) {
        String saved = Prefs.of(c).getString("tiles", null);
        if (saved == null) return standard();
        java.util.List<Item> list = new java.util.ArrayList<>();
        for (String name : saved.split(",")) {
            for (Item item : values()) {
                if (item.name().equals(name) && item.movable() && !list.contains(item)) {
                    list.add(item);
                }
            }
        }
        return list;
    }

    static void save(android.content.Context c, java.util.List<Item> list) {
        StringBuilder out = new StringBuilder();
        for (Item item : list) out.append(item.name()).append(',');
        Prefs.of(c).edit().putString("tiles", out.toString()).apply();
    }
}
