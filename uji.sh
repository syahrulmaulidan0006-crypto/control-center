P=com.example.controlcenter
adb install -g app/build/outputs/apk/debug/app-debug.apk
adb shell appops set $P WRITE_SETTINGS allow
adb shell cmd notification allow_listener $P/$P.MusicListener
adb shell settings put secure enabled_accessibility_services $P/$P.OverlayService
adb shell settings put secure accessibility_enabled 1
adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard
adb shell input keyevent KEYCODE_HOME
sleep 20
shot() {
  adb exec-out screencap -p > $1.png
  python3 -c "import sys" 
  sudo apt-get -qq install -y imagemagick >/dev/null 2>&1
  convert $1.png -resize 360x -quality 55 $1.jpg
  echo "GAMBAR-$1-AWAL"; base64 -w 200 $1.jpg; echo "GAMBAR-$1-AKHIR"
}
adb logcat -c
adb shell cmd notification post -t Rina pesan1 'Pesan_pertama'
sleep 2; shot popup1
sleep 8
adb shell cmd notification post -t Rina pesan1 'Pesan_kedua'
sleep 2; shot popup2
echo "--- audio"
adb shell dumpsys audio | grep -iE "ASSISTANCE_SONIFICATION|muted|PlaybackActivity|player piid" | tail -25
adb shell dumpsys notification | grep -iE "suppress|hints" | head -10
sleep 8
W=$(adb shell wm size | grep -o '[0-9]*x' | tr -d x)
buka() {
  adb shell input keyevent KEYCODE_HOME; sleep 3
  adb shell input swipe $((W*$1/100)) 5 $((W*$1/100)) 900 $2
  sleep 3
}
tutup() { adb shell input keyevent KEYCODE_BACK; sleep 2; }
n=1
for ms in 40 80 150 250 400 600; do
  buka 85 $ms; shot g$n; tutup; n=$((n+1))
done
buka 85 250; shot panel
adb shell input tap 100 150; sleep 2; shot atur
adb shell input draganddrop 173 460 650 460 900; sleep 2; shot geser
adb shell input tap 173 700; sleep 2; shot hapus
tutup; shot panel3
tutup
adb shell cmd notification post -t Budi pesan2 'Halo_satu'; sleep 3
adb shell cmd notification post -t Sari pesan3 'Halo_dua'; sleep 8
buka 15 80; shot pusat; tutup
adb shell input keyevent KEYCODE_HOME; sleep 2
# --- versi 16
echo "--- popup beruntun"
adb shell cmd notification post -t Andi cepat1 'Satu'; sleep 1
adb shell cmd notification post -t Andi cepat2 'Dua'; sleep 1; shot ganti
sleep 10
echo "jendela sisa: $(adb shell dumpsys window windows | grep -c 'Window #.*controlcenter')"
adb shell cmd notification allow_dnd com.example.controlcenter
mode() { echo "$1: $(adb shell dumpsys notification | grep -m1 -oE 'mZenMode=[A-Z_]+') terang=$(adb shell settings get system screen_brightness)"; }
adb shell settings put system screen_brightness 150
buka 85 250
mode awal
adb shell input swipe 163 840 163 840 800; sleep 2; shot fokus
adb shell input tap 540 1085; sleep 2; shot tidur; mode sesudah-pilih-tidur
tutup; shot ikontidur
adb shell input tap 163 840; sleep 2; mode sesudah-ketuk-lagi
adb shell input swipe 163 840 163 840 800; sleep 2
adb shell input tap 540 1416; sleep 2; shot jadwal; mode jadwal-aktif
tutup; tutup
buka 85 250
adb shell input swipe 411 1335 411 1335 800; sleep 2; shot timer; tutup
adb shell input swipe 663 963 663 963 800; sleep 2; shot kecerahan; tutup
adb shell input swipe 225 470 225 470 100; sleep 2; shot kotak
adb shell input swipe 300 1470 300 1470 800; sleep 2; shot hotspot; tutup; tutup
adb shell input tap 100 150; sleep 2; shot atur5; tutup; tutup
# jadwal tidur: pindahkan jam emulator ke dalam lalu ke luar jadwal
adb root; sleep 5
adb shell settings put global auto_time 0
bangun() { adb shell input keyevent KEYCODE_SLEEP; sleep 3; adb shell input keyevent KEYCODE_WAKEUP; sleep 3; adb shell wm dismiss-keyguard; sleep 2; }
adb shell date 112623302026.00; bangun; mode jadwal-malam
adb shell date 112706002026.00; bangun; mode jadwal-pagi
adb logcat -d | grep -E "AndroidRuntime|ControlCenter" | tail -30
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell am start -a android.settings.SETTINGS; sleep 3
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 4
adb shell dumpsys window windows | grep -A12 "AccessibilityOverlay\|ty=ACCESSIBILITY" | grep -iE "Window #|frame=|ty=" | head -30
adb shell input swipe 2200 2 2200 700 250; sleep 3; shot mendatar
# --- versi 18
adb shell settings put system user_rotation 0; sleep 4
adb shell input keyevent KEYCODE_HOME; sleep 2
buka 85 250; shot kosong
adb shell input swipe 912 1040 912 860 1600 & sleep 1.1; shot tekanvolume; wait; sleep 2; shot lepasvolume
adb shell input swipe 150 1750 850 1750 2500 & sleep 1.4; shot tengah; wait; sleep 2; shot sampai
adb shell input swipe 850 1750 300 1750 60; sleep 2; shot balik
adb shell input swipe 150 1750 330 1750 700; sleep 2; shot batal
adb shell input swipe 150 1750 400 1750 50; sleep 2; shot cepat
# geser lalu langsung geser lagi selagi masih meluncur
adb shell "input swipe 850 1750 300 1750 60; input swipe 300 1750 800 1750 80"; sleep 2; shot tangkap
tutup
adb shell am broadcast -n $P/.TestMusic; sleep 3
buka 85 250; shot musik
adb shell input swipe 150 1750 850 1750 100; sleep 2; adb shell input swipe 850 1750 150 1750 100; sleep 2; shot musik2
tutup
echo "=== LOG ==="
adb logcat -d | grep -E "ControlCenter|AndroidRuntime|FATAL|com.example" | grep -vE "ActivityManager: Start proc" | tail -80
exit 0
