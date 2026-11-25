# Control Center (Android 10+)

## Cara membuat APK
**Android Studio:** Open folder ini > tunggu Gradle sync > Build > Build APK(s).
Hasil: app/build/outputs/apk/debug/app-debug.apk

**Tanpa komputer kuat (GitHub):** buat repository, unggah SELURUH isi folder ini
(termasuk .github/workflows/build.yml), buka tab Actions > Build APK >
unduh artifact "ControlCenter-apk".

## Cara pakai
1. Pasang APK, buka aplikasi.
2. Tombol 1: aktifkan layanan Control Center di Aksesibilitas. Berikan izin 2 dan 3.
3. Geser ke bawah dari status bar bagian kanan. Bagian kiri tetap notifikasi.
4. Tutup: ketuk area kosong atau tombol kembali.

Jika layanan diblokir ("setelan terbatas", Android 13+): Info aplikasi >
menu titik tiga > Izinkan setelan terbatas.

## Batasan
- Panel bawaan tidak dihapus; geseran kanan atas dialihkan ke panel ini.
- Wi-Fi, internet, Bluetooth, mode pesawat membuka panel/pengaturan sistem.
- Blur hanya di Android 12+ pada perangkat yang mendukung.

## Ubah transparansi
OverlayService.java, konstanta BACKGROUND (dua digit pertama: 00 bening, FF pekat).
