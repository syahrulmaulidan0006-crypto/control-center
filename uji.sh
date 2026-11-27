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
# Mulai dengan volume rendah agar perubahan terlihat.
adb shell cmd media_session volume --stream 3 --set 5 >/dev/null
dinding
rekam rekvol 6; adb shell input keyevent 24; sleep 0.5; adb shell input keyevent 24; sleep 0.5; adb shell input keyevent 24; sleep 0.5; adb shell input keyevent 25; cetak rekvol
sleep 3; dinding
adb shell input keyevent 24; sleep 0.5; shot popvol; adb shell input keyevent 24; sleep 0.9; shot popvol2; sleep 3.5; shot popvolhilang
adb shell input keyevent 24; sleep 0.8; adb shell input swipe 90 1100 90 800 700; sleep 0.4; shot popvolgeser; sleep 3.5
adb shell cmd media_session volume --stream 3 --get
dinding; pesan a Rina "Sampai ketemu nanti sore"; sleep 1.8; shot popup80; sleep 7
rekam rekpopup 5; pesan b Budi "Jadi berangkat jam berapa"; cetak rekpopup; sleep 5
dinding; buka 15 250; shot pusat
echo "=== LOG ==="
adb logcat -d | grep -E "ControlCenter|AndroidRuntime|FATAL" | tail -80
exit 0
