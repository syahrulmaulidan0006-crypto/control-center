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
buka() { adb shell input swipe $((1080*$1/100)) 5 $((1080*$1/100)) 900 $2; sleep 2; }
tutup() { adb shell input keyevent KEYCODE_BACK; sleep 1.5; }
pesan() { adb shell cmd notification post -S bigtext -t "'$2'" $1 "'$3'"; }
bening() { adb shell am broadcast -n $P/.TestMusic --ei bening $1; sleep 1; }
adb shell am broadcast -n $P/.TestMusic --ei gaya 0; sleep 1
buka 85 250; shot panel80; tutup
bening 100; buka 85 250; shot panel100; tutup
bening 0; buka 85 250; shot panel0; tutup
bening 80
pesan a Rina "Sampai ketemu nanti sore"; sleep 1.5; shot popup80; sleep 7
pesan b Budi "Jadi berangkat jam berapa"; sleep 8
pesan c Sari "Fotonya sudah saya kirim"; sleep 8
buka 85 250
adb shell input swipe 150 1750 850 1750 6000 & sleep 2.2; shot tengah1; sleep 0.8; shot tengah2; wait; sleep 1.5; shot pusat
adb shell input swipe 850 1750 150 1750 5000 & sleep 2.6; shot tengahbalik; wait; sleep 1.5; shot balik
adb shell input swipe 150 1750 850 1750 150; sleep 2
adb shell input swipe 150 760 650 760 5000 & sleep 3.3; shot geserkartu; wait; shot lepaskartu; sleep 2; shot sesudah
adb shell input tap 540 760; sleep 1.5; shot buka
adb shell input swipe 150 1000 900 1000 120; sleep 0.1; shot hapussatu; sleep 2; shot hapussatu2
adb shell input tap 940 590; sleep 0.2; shot semua1; sleep 2.5; shot semua2
tutup
pesan d Rina "Halo lagi"; sleep 8; buka 15 250; shot pusatbaru
adb shell input swipe 150 760 950 760 150; sleep 3; shot pusatkosong
tutup
adb shell am start -n $P/.MainActivity; sleep 4; adb shell input tap 400 2027; sleep 2; adb shell input swipe 540 1700 540 500 500; sleep 1; adb shell input swipe 540 1700 540 900 500; sleep 1; shot setelan
echo "=== LOG ==="
adb logcat -d | grep -E "ControlCenter|AndroidRuntime|FATAL" | tail -80
exit 0
