package com.example.controlcenter;

/** Daftar tombol panel. */
enum Item {
    AIRPLANE(R.drawable.ic_airplane, "Mode pesawat", 0xFFFF9F43),
    WIFI(R.drawable.ic_wifi, "Wi-Fi", 0xFF2F8CFF),
    DATA(R.drawable.ic_data, "Data seluler", 0xFF2ECC71),
    BLUETOOTH(R.drawable.ic_bluetooth, "Bluetooth", 0xFF2F8CFF),
    HOTSPOT(R.drawable.ic_hotspot, "Hotspot", 0xFF2ECC71),
    LOCATION(R.drawable.ic_location, "Lokasi", 0xFF2F8CFF),

    ROTATION(R.drawable.ic_rotation, "Kunci rotasi", 0xFFFF5C6C),
    DND(R.drawable.ic_moon, "Jangan ganggu", 0xFF8B6CFF),
    TORCH(R.drawable.ic_flashlight, "Senter", 0xFFFFB300),
    VIBRATE(R.drawable.ic_bell, "Mode getar", 0xFFFF5C6C),

    CALCULATOR(R.drawable.ic_calculator, "Kalkulator", 0),
    CAMERA(R.drawable.ic_camera, "Kamera", 0),
    ALARM(R.drawable.ic_timer, "Alarm", 0),
    SCREENSHOT(R.drawable.ic_screenshot, "Tangkapan layar", 0),
    RECORD(R.drawable.ic_record, "Perekam layar", 0),
    QR(R.drawable.ic_qr, "Pemindai QR", 0),
    DARK(R.drawable.ic_dark, "Mode gelap", 0xFF5C6BC0),
    AUTO_LIGHT(R.drawable.ic_autolight, "Kecerahan otomatis", 0xFFFFB300),
    NFC(R.drawable.ic_nfc, "NFC", 0xFF2F8CFF),
    SYNC(R.drawable.ic_sync, "Sinkronisasi", 0xFF2ECC71),
    BATTERY(R.drawable.ic_battery, "Penghemat baterai", 0xFFFFB300),
    SETTINGS(R.drawable.ic_settings, "Pengaturan", 0),
    NOTIFICATIONS(R.drawable.ic_notifications, "Notifikasi", 0),
    RECENTS(R.drawable.ic_recents, "Aplikasi terbaru", 0),
    LOCK(R.drawable.ic_lock, "Kunci layar", 0),
    POWER(R.drawable.ic_power, "Menu daya", 0);

    final int icon, color;
    final String label;

    Item(int icon, String label, int color) {
        this.icon = icon;
        this.label = label;
        this.color = color;
    }
}
