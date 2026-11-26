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
for g in 0 1; do
  adb shell am broadcast -n $P/.TestMusic --ei gaya $g; sleep 1
  for k in 50 85; do
    adb shell am broadcast -n $P/.TestMusic --ei bening $k; sleep 1
    adb shell am start -a android.settings.SETTINGS; sleep 3
    adb shell cmd notification post -t Budi p$g$k 'Popup_di_atas_layar_terang'; sleep 2; shot popup$g-$k; sleep 8
    adb shell input keyevent KEYCODE_HOME; sleep 2
  done
  adb shell am broadcast -n $P/.TestMusic; sleep 2
  buka 85 250; shot cc$g
  adb shell input swipe 912 1040 912 880 1600 & sleep 1.1; shot persen$g; wait; sleep 2
  adb shell input swipe 780 330 780 330 900; sleep 2; shot musikbesar$g
  adb shell input keyevent KEYCODE_BACK; sleep 2; shot tutupmusik$g; tutup
  buka 15 250; shot pusat$g; tutup
  adb shell dumpsys battery unplug; sleep 2; adb shell dumpsys battery set level 57; adb shell dumpsys battery set ac 1; sleep 1.2; shot cas$g; sleep 4; shot casusai$g
  adb shell dumpsys battery reset; sleep 2
done
adb shell am start -n $P/.MainActivity; sleep 4
for i in 1 2 3 4 5; do adb shell input swipe 540 1800 540 700 600; sleep 1; shot atur$i; done
echo "=== LOG ==="
adb logcat -d | grep -E "ControlCenter|AndroidRuntime|FATAL" | tail -60
exit 0
