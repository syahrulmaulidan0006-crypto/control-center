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
for G in 0 1; do
  kirim --ei gaya $G
  adb shell input keyevent KEYCODE_HOME; sleep 2; buka 85 250; besar rumah$G; tutup
  dinding; buka 85 250; besar panel$G
  adb shell input swipe 912 960 912 960 900; sleep 1.5; shot pilih$G; tutup; tutup
  dinding; pesan a$G Rina "Sampai ketemu nanti sore"; sleep 1.8; besar popup$G; sleep 7
  pesan b$G Budi "Jadi berangkat jam berapa"; sleep 8
  buka 15 250; besar pusat$G; tutup; tutup
  adb shell am start -a android.settings.SETTINGS >/dev/null; sleep 3; buka 85 250; shot terang$G; tutup; tutup
done
kirim --ei gaya 0
dinding; rekam rekbuka 4; adb shell input swipe 918 5 918 900 900; sleep 1; cetak rekbuka; tutup
bening 40; dinding; buka 85 250; shot panel40; tutup; bening 80
kirim --ei gaya 0
adb shell am start -n $P/.MainActivity; sleep 4; adb shell input tap 400 2027; sleep 2; shot setelan1; adb shell input swipe 540 1700 540 900 600; sleep 1; shot setelan2
adb root >/dev/null; adb wait-for-device; sleep 3
DEV=/dev/input/event1
kunci() { adb shell "sendevent $DEV 1 $1 $2; sendevent $DEV 0 0 0"; }
naik() { adb shell "sendevent $DEV 1 115 1; sendevent $DEV 0 0 0; sleep 0.08; sendevent $DEV 1 115 0; sendevent $DEV 0 0 0"; }
turun() { adb shell "sendevent $DEV 1 114 1; sendevent $DEV 0 0 0; sleep 0.08; sendevent $DEV 1 114 0; sendevent $DEV 0 0 0"; }
dinding; naik; sleep 0.8; besar popvol; sleep 3
echo "=== LOG ==="
adb logcat -d | grep -E "ControlCenter|AndroidRuntime|FATAL" | tail -80
exit 0
