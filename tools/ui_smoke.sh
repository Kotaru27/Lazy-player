#!/usr/bin/env bash
set -euo pipefail
mkdir -p screenshots
trap 'adb logcat -d -t 700 > screenshots/logcat-tail.txt 2>&1 || true' EXIT

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.lazy.player android.permission.READ_MEDIA_AUDIO
adb shell am start -n com.lazy.player/.MainActivity
sleep 8
adb shell pidof com.lazy.player
adb exec-out screencap -p > screenshots/00-first-launch.png

ready=0
for i in $(seq 1 40); do
    if adb shell test -d /sdcard >/dev/null 2>&1; then
        ready=1
        break
    fi
    sleep 3
done
if [[ "$ready" != 1 ]]; then
    adb shell ls -la /storage > screenshots/storage-diagnostics.txt 2>&1 || true
    echo "TEST ENVIRONMENT FAILURE: emulated shared media storage is not mounted"
    exit 1
fi

adb shell mkdir -p /sdcard/Music
adb push "$RUNNER_TEMP/Stage_Light.wav" /sdcard/Music/Stage_Light.wav
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Music/Stage_Light.wav || true
sleep 7
adb shell am force-stop com.lazy.player
adb shell am start -n com.lazy.player/.MainActivity
sleep 7
adb exec-out screencap -p > screenshots/01-library.png
adb shell uiautomator dump /sdcard/window.xml >/dev/null
adb pull /sdcard/window.xml screenshots/library.xml >/dev/null

function find_tap () {
  python3 - "$1" "$2" "$3" <<'PY'
import re, sys, xml.etree.ElementTree as ET
filename, needle, attribute = sys.argv[1:4]
root=ET.parse(filename).getroot()
nodes=[n for n in root.iter() if needle.lower() in n.attrib.get(attribute,'').lower()]
if not nodes:
    raise SystemExit(f"TEST FAILURE: missing UI node with {attribute} containing {needle} in {filename}")
n=max(nodes,key=lambda n:int(re.findall(r'\d+',n.attrib.get('bounds','[0,0][0,0]'))[1]))
b=list(map(int,re.findall(r'\d+',n.attrib['bounds'])))
print((b[0]+b[2])//2,(b[1]+b[3])//2)
PY
}

tap=$(find_tap screenshots/library.xml Stage text)
adb shell input tap $tap
sleep 4
adb exec-out screencap -p > screenshots/02-mini-player.png
adb shell uiautomator dump /sdcard/window.xml >/dev/null
adb pull /sdcard/window.xml screenshots/mini.xml >/dev/null

tap=$(find_tap screenshots/mini.xml Stage text)
adb shell input tap $tap
sleep 4
adb exec-out screencap -p > screenshots/03-now-playing.png
adb shell uiautomator dump /sdcard/window.xml >/dev/null
adb pull /sdcard/window.xml screenshots/now-playing.xml >/dev/null
grep -q 'NOW PLAYING' screenshots/now-playing.xml || { echo "TEST FAILURE: Now Playing did not open"; exit 1; }

tap=$(find_tap screenshots/now-playing.xml "Open playback queue" content-desc)
adb shell input tap $tap
sleep 3
adb exec-out screencap -p > screenshots/04-queue.png

adb shell settings put system font_scale 1.3
adb shell wm size 720x1280
adb shell wm density 320
adb shell am force-stop com.lazy.player
adb shell am start -n com.lazy.player/.MainActivity
sleep 5
adb exec-out screencap -p > screenshots/05-360dp-font130.png
echo "UI smoke captures completed."
