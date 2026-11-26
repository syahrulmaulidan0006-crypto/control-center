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
adb shell am broadcast -n $P/.TestMusic --ei gaya 0; sleep 1
adb shell am broadcast -n $P/.TestMusic; sleep 1
adb shell input keyevent KEYCODE_HOME; sleep 3; shot kapsullagu
adb shell input tap 540 45; sleep 1.5; shot besarlagu
adb shell input tap 540 1500; sleep 1.5; shot kecillagi
adb shell am broadcast -n $P/.TestMusic --ei timer 95; sleep 3; shot kapsultimer
adb shell input tap 540 45; sleep 1.5; shot besartimer
adb shell input tap 540 1500; sleep 1
adb shell am broadcast -n $P/.TestMusic --ei timer 0; adb shell am broadcast -n $P/.TestMusic --ez henti true; sleep 3; shot kosong
adb shell dumpsys battery unplug; sleep 1; adb shell dumpsys battery set level 57; adb shell dumpsys battery set ac 1; sleep 4; shot kapsulcas
adb shell input tap 540 45; sleep 1.5; shot besarcas
adb shell dumpsys battery reset; sleep 9
buka 85 250; shot panel
adb shell input tap 99 150; sleep 2; shot atur
tutup; tutup
adb shell am broadcast -n $P/.TestMusic --ei ukuran 1; sleep 1; adb shell am broadcast -n $P/.TestMusic; sleep 1
buka 85 250; shot panelukuran
adb shell input swipe 150 1750 850 1750 100; sleep 2; shot geserpusat
adb shell input swipe 850 1750 150 1750 100; sleep 2; shot geserbalik
adb shell input tap 99 150; sleep 2; shot aturukuran
adb shell input tap 190 560; sleep 1.5; shot aturketuk
tutup; tutup
adb shell settings put system accelerometer_rotation 0; adb shell settings put system user_rotation 1; sleep 4
adb shell input swipe 1900 5 1900 700 300; sleep 2; shot mendatar; tutup
adb shell settings put system user_rotation 0; sleep 4
adb shell am start -n $P/.MainActivity; sleep 4; shot beranda
adb shell input tap 400 2027; sleep 2; shot tampilan
adb shell input swipe 540 1700 540 500 500; sleep 1; shot tampilan2
adb shell input swipe 540 1700 540 400 500; sleep 1; shot tampilan3
adb shell input tap 660 2027; sleep 2; shot notif
adb shell input tap 950 610; sleep 1; shot sakelar
adb shell input tap 910 2027; sleep 2; shot lanjut
echo "=== LOG ==="
adb logcat -d | grep -E "ControlCenter|AndroidRuntime|FATAL" | tail -80
exit 0
