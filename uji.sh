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
adb shell input swipe $((W*85/100)) 5 $((W*85/100)) 900 250
sleep 3; shot panel0
adb logcat -d | grep -E "ControlCenter|AndroidRuntime"
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell input swipe $((W*85/100)) 5 $((W*85/100)) 900 60
sleep 3; shot panel
for n in 1 2 3 4 5 6; do
  adb shell input keyevent KEYCODE_HOME; sleep 3
  echo "--- geser ke-$n"
  adb logcat -c
  adb shell dumpsys window windows | grep -E "Window #|mCurrentFocus" | head -12
  adb shell input swipe $((W*85/100)) 5 $((W*85/100)) 900 $((n*60))
  sleep 3
  adb logcat -d | grep -E "ControlCenter|InputDispatcher|pilfer|Pilfer"
  shot g$n
  adb shell input keyevent KEYCODE_BACK; sleep 2
done
adb shell am start -a android.settings.SETTINGS; sleep 3
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 4
adb shell dumpsys window windows | grep -A12 "AccessibilityOverlay\|ty=ACCESSIBILITY" | grep -iE "Window #|frame=|ty=" | head -30
adb shell input swipe 2200 2 2200 700 250; sleep 3; shot mendatar
echo "=== LOG ==="
adb logcat -d | grep -E "ControlCenter|AndroidRuntime|FATAL|com.example" | grep -vE "ActivityManager: Start proc" | tail -80
exit 0
