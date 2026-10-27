#!/usr/bin/env bash
set -euo pipefail
mkdir -p screenshots
trap 'adb logcat -d -t 500 > screenshots/logcat.txt 2>&1 || true' EXIT
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.lazy.player.refined/com.lazy.player.MainActivity
sleep 6
adb shell pidof com.lazy.player.refined
adb exec-out screencap -p > screenshots/01-welcome.png
adb shell uiautomator dump /data/local/tmp/window.xml >/dev/null
adb pull /data/local/tmp/window.xml screenshots/welcome.xml >/dev/null
grep -q 'Choose my FLAC folder' screenshots/welcome.xml || { echo 'ONBOARDING CHECK FAILED'; exit 1; }
echo 'Onboarding controls verified'
# The virtual shared volume must be mounted to test MediaStore music.
if ! adb shell test -d /sdcard; then echo 'EMULATOR STORAGE UNAVAILABLE'; exit 1; fi
adb shell mkdir -p /sdcard/Music
adb push "$RUNNER_TEMP/Stage_Light.wav" /sdcard/Music/Stage_Light.wav
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Music/Stage_Light.wav || true
adb shell pm grant com.lazy.player.refined android.permission.READ_MEDIA_AUDIO
adb shell am force-stop com.lazy.player.refined
adb shell am start -n com.lazy.player.refined/com.lazy.player.MainActivity
sleep 8
adb exec-out screencap -p > screenshots/02-library.png
adb shell uiautomator dump /data/local/tmp/library.xml >/dev/null
adb pull /data/local/tmp/library.xml screenshots/library.xml >/dev/null
python3 - <<'PY'
import re, subprocess, xml.etree.ElementTree as ET
tree=ET.parse("screenshots/library.xml")
nodes=[x for x in tree.getroot().iter() if "Stage" in x.get("text","")]
if not nodes: raise SystemExit("NO TEST TRACK IN LIBRARY; screenshot saved")
n=nodes[0]
bounds=list(map(int,re.findall(r"\d+",n.get("bounds"))))
subprocess.check_call(["adb","shell","input","tap",str((bounds[0]+bounds[2])//2),str((bounds[1]+bounds[3])//2)])
PY
sleep 4
adb exec-out screencap -p > screenshots/03-mini-player.png
adb shell uiautomator dump /data/local/tmp/mini.xml >/dev/null
adb pull /data/local/tmp/mini.xml screenshots/mini.xml >/dev/null
python3 - <<'PY'
import re, subprocess, xml.etree.ElementTree as ET
root=ET.parse("screenshots/mini.xml").getroot()
nodes=[x for x in root.iter() if "Stage" in x.get("text","")]
if not nodes: raise SystemExit("NO MINI PLAYER TRACK LABEL")
# lower match belongs to mini-player
n=max(nodes,key=lambda n:int(re.findall(r"\d+",n.get("bounds","[0,0][0,0]"))[1]))
b=list(map(int,re.findall(r"\d+",n.get("bounds"))))
subprocess.check_call(["adb","shell","input","tap",str((b[0]+b[2])//2),str((b[1]+b[3])//2)])
PY
sleep 4
adb exec-out screencap -p > screenshots/04-now-playing.png
adb shell uiautomator dump /data/local/tmp/now.xml >/dev/null
adb pull /data/local/tmp/now.xml screenshots/now-playing.xml >/dev/null
grep -q 'Now Playing' screenshots/now-playing.xml || { echo 'NOW PLAYING MISSING'; exit 1; }
echo 'Library, mini-player and Now Playing capture passed'
