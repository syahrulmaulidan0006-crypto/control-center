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
buka 15 80; shot pusat; tutup
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell am start -a android.settings.SETTINGS; sleep 3
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 4
adb shell dumpsys window windows | grep -A12 "AccessibilityOverlay\|ty=ACCESSIBILITY" | grep -iE "Window #|frame=|ty=" | head -30
adb shell input swipe 2200 2 2200 700 250; sleep 3; shot mendatar
echo "=== LOG ==="
adb logcat -d | grep -E "ControlCenter|AndroidRuntime|FATAL|com.example" | grep -vE "ActivityManager: Start proc" | tail -80
exit 0
