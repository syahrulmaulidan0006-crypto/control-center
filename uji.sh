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
buka 85 250
adb shell input swipe 912 1100 912 780 2500 & sleep 1.6; shot volgerak; wait; sleep 0.3; shot voldiam
adb shell input swipe 663 1100 663 850 2500 & sleep 1.6; shot terang; wait
rekam rekcair 4; adb shell input swipe 912 1050 912 850 350; sleep 1; adb shell input swipe 912 850 912 1050 350; cetak rekcair
sleep 1; adb shell input swipe 912 820 912 820 900; sleep 1; shot voltekan
tutup
dinding
rekam rekvol 5; adb shell input keyevent 24; sleep 0.4; adb shell input keyevent 24; sleep 0.4; adb shell input keyevent 25; cetak rekvol
adb shell input keyevent 24; sleep 0.7; shot popvol; sleep 3; shot popvolhilang
pesan a Rina "Sampai ketemu nanti sore"; sleep 1.8; shot popup80; sleep 7
pesan b Budi "Jadi berangkat jam berapa"; sleep 8
buka 15 250; shot pusat
rekam rekgeser 4; adb shell input swipe 150 1750 850 1750 500; cetak rekgeser
tutup; tutup
dinding; buka 85 250
adb shell input tap 160 1575; sleep 1.5; shot pilihrekam
tutup; tutup
dinding; kirim --ei rekam 0; sleep 2; shot izin
tutup
adb shell appops set $P PROJECT_MEDIA allow
dinding; kirim --ei rekam 0; sleep 4; shot merekam
adb shell input tap 540 50; sleep 1.5; shot kapsulbuka
sleep 4; buka 85 250; shot panelrekam; adb shell input tap 160 1575; sleep 4; shot selesai
tutup; tutup
for M in 1 2; do
  dinding; kirim --ei rekam $M; sleep 6
  adb shell input tap 540 50; sleep 1.2
  [ $M = 1 ] && shot kapsulmik
  adb shell input swipe 540 1200 540 700 300; sleep 2
  buka 85 250; adb shell input tap 160 1575; sleep 4; tutup
done
echo "=== BERKAS ==="
adb shell ls -l /sdcard/Movies/ControlCenter/
mkdir -p hasil; adb pull /sdcard/Movies/ControlCenter/. hasil/ >/dev/null
n=0; for f in hasil/*; do n=$((n+1)); echo "$f"; ffprobe -v error -show_entries stream=codec_name,width,height,nb_frames,duration -of compact "$f"
  ffmpeg -v error -i "$f" -vf "fps=1,scale=180:380,tile=6x2" -frames:v 1 video$n.png; convert video$n.png -quality 55 video$n.jpg
  echo "GAMBAR-video$n-AWAL"; base64 -w 200 video$n.jpg; echo "GAMBAR-video$n-AKHIR"; done
adb shell am start -n $P/.MainActivity; sleep 4; adb shell input tap 400 2027; sleep 2; shot setelan1; adb shell input swipe 540 1700 540 700 600; sleep 1; shot setelan2; adb shell input swipe 540 1700 540 700 600; sleep 1; shot setelan3
echo "=== LOG ==="
adb logcat -d | grep -E "ControlCenter|AndroidRuntime|FATAL" | tail -80
exit 0
