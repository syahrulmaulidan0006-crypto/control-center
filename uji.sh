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
kirim --ei gaya 0
dinding; shot dinding
buka 85 250; shot panel80; tutup
bening 100; buka 85 250; shot panel100; tutup
bening 0; buka 85 250; shot panel0; tutup
bening 80
kirim --ei henti 0
# Tombol lewat perangkat keras tiruan: tombol suntikan tidak melewati saringan aksesibilitas.
naik() { adb emu event send EV_KEY:KEY_VOLUMEUP:1 EV_SYN:0:0; sleep 0.08; adb emu event send EV_KEY:KEY_VOLUMEUP:0 EV_SYN:0:0; }
turun() { adb emu event send EV_KEY:KEY_VOLUMEDOWN:1 EV_SYN:0:0; sleep 0.08; adb emu event send EV_KEY:KEY_VOLUMEDOWN:0 EV_SYN:0:0; }
isi() { echo "VOLUME $1: $(adb shell cmd media_session volume --stream 3 --get | grep 'volume is')"; }
adb shell cmd media_session volume --stream 3 --set 5 >/dev/null
adb shell getevent -lp | grep -E "add device|name:|VOLUME"
dinding; isi awal
rekam rekvol 6; naik; sleep 0.5; naik; sleep 0.5; naik; sleep 0.5; turun; cetak rekvol; isi "setelah +3 -1"
sleep 3; dinding
naik; sleep 0.6; shot popvol; naik; sleep 0.9; shot popvol2; sleep 3.5; shot popvolhilang; isi "setelah +2"
adb emu event send EV_KEY:KEY_VOLUMEUP:1 EV_SYN:0:0; sleep 1.5; shot popvoltahan; adb emu event send EV_KEY:KEY_VOLUMEUP:0 EV_SYN:0:0; isi "ditahan"
sleep 0.2; adb shell input swipe 80 1000 80 1250 700; sleep 0.4; shot popvolgeser; isi "digeser"; sleep 3.5
buka 85 250; naik; sleep 0.8; shot voldipanel; tutup
echo "=== LOG ==="
adb logcat -d | grep -E "ControlCenter|AndroidRuntime|FATAL" | tail -80
exit 0
