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
sudo apt-get -qq update >/dev/null; sudo apt-get -qq install -y ffmpeg imagemagick >/dev/null
adb logcat -c
buka() { adb shell input swipe $((1080*$1/100)) 5 $((1080*$1/100)) 900 $2; sleep 2; }
tutup() { adb shell input keyevent KEYCODE_BACK; sleep 1.5; }
pesan() { adb shell cmd notification post -S bigtext -t "'$2'" $1 "'$3'"; }
bening() { adb shell am broadcast -n $P/.TestMusic --ei bening $1; sleep 1; }
kirim() { adb shell am broadcast -n $P/.TestMusic "$@" >/dev/null; sleep 1; }
dinding() { adb shell am start -n $P/.TestWall >/dev/null; sleep 2.5; }
rekam() { adb shell screenrecord --time-limit $2 --size 540x1140 /sdcard/$1.mp4 & REK=$!; sleep 1; }
cetak() {
  wait $REK; adb pull /sdcard/$1.mp4 . >/dev/null
  ffmpeg -v error -reinit_filter 0 -i $1.mp4 -vf "mpdecimate,scale=180:380,tile=8x4" -vsync vfr -frames:v 1 $1.png
  convert $1.png -quality 55 $1.jpg
  echo "GAMBAR-$1-AWAL"; base64 -w 200 $1.jpg; echo "GAMBAR-$1-AKHIR"
}
besar() { adb exec-out screencap -p > $1.png; convert $1.png -resize 720x -quality 70 $1.jpg; echo "GAMBAR-$1-AWAL"; base64 -w 200 $1.jpg; echo "GAMBAR-$1-AKHIR"; }
bening 80; kirim --ei henti 0
adb shell input keyevent KEYCODE_HOME; sleep 5
adb shell wm 2>&1 | grep -i blur; adb shell getprop ro.surface_flinger.supports_background_blur
# Pembukaan pertama setelah layanan hidup diperiksa tersendiri.
buka 85 250; shot awal1; sleep 2; shot awal1b; tutup
buka 85 250; shot awal2; tutup
setelan() { adb shell am start -a android.settings.SETTINGS >/dev/null; sleep 2.5; }
for G in 0 1; do
  kirim --ei gaya $G
  adb shell input keyevent KEYCODE_HOME; sleep 2; buka 85 250; besar rumah$G; tutup
  dinding; buka 85 250; besar panel$G; tutup
  setelan; buka 85 250; shot terang$G; tutup; tutup
  # Popup memakai kaca yang sama; saat layar berganti ia harus jadi tembus.
  dinding; pesan a$G Rina "Sampai ketemu nanti sore"; sleep 1.5; besar popup$G
  adb shell am start -a android.settings.SETTINGS >/dev/null; sleep 1.5; besar popupganti$G; sleep 7
  dinding; pesan b$G Budi "Jadi berangkat jam berapa"; sleep 1; pesan c$G Sari "Oke siap"; sleep 1.5; shot popupdua$G; sleep 7
  buka 15 250; besar pusat$G; tutup; tutup
done
adb root >/dev/null; adb wait-for-device; sleep 3
DEV=/dev/input/event1
naik() { adb shell "sendevent $DEV 1 115 1; sendevent $DEV 0 0 0; sleep 0.08; sendevent $DEV 1 115 0; sendevent $DEV 0 0 0"; }
for G in 0 1; do
  kirim --ei gaya $G; dinding; rekam rekvol$G 4; naik; sleep 0.5; naik; sleep 0.4; besar popvol$G; cetak rekvol$G; sleep 2.5
done
# Animasi cas: dicabut lalu dicolok, saat layar menyala dan saat layar terkunci.
lepas() { adb shell dumpsys battery unplug; adb shell dumpsys battery set level 63; sleep 1.5; }
colok() { adb shell dumpsys battery set ac 1; }
# Emulator mematikan animasi; dinyalakan lagi agar gerak naik cairan terlihat.
for S in window_animation_scale transition_animation_scale animator_duration_scale; do adb shell settings put global $S 1; done
dinding; lepas; rekam reccas 8; colok; sleep 2.2; besar cas; sleep 5; cetak reccas; shot cashilang
lepas; colok; sleep 1.5; adb shell input tap 540 1100; sleep 1; shot casketuk
lepas; colok; sleep 1.5; adb shell dumpsys battery unplug; sleep 1; shot cascabut
adb shell locksettings set-disabled false
lepas; adb shell input keyevent KEYCODE_SLEEP; sleep 2; colok; sleep 2; adb shell input keyevent KEYCODE_WAKEUP; sleep 2; besar caskunci; sleep 6; shot caskunci2
adb shell dumpsys battery reset; adb shell wm dismiss-keyguard
echo "=== LOG ==="
adb logcat -d | grep -E "panel dibuka|kaca bening|Choreographer.*Skipped" | head -12
adb logcat -d | grep -E "ControlCenter|AndroidRuntime|FATAL" | tail -80
exit 0
