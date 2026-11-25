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
    POWER(R.drawable.ic_power, "Menu daya");

    final int icon;
    final String label;

    Item(int icon, String label) {
        this.icon = icon;
        this.label = label;
    }
}
