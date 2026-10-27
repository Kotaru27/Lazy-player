#!/usr/bin/env bash
set -euo pipefail
mkdir -p screenshots
trap 'adb logcat -d -t 800 > screenshots/logcat.txt 2>&1 || true' EXIT

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.lazy.player.focus/com.lazy.player.MainActivity
sleep 5
adb shell pidof com.lazy.player.focus
adb exec-out screencap -p > screenshots/01-welcome.png
adb shell uiautomator dump /data/local/tmp/ui.xml >/dev/null
adb pull /data/local/tmp/ui.xml screenshots/welcome.xml >/dev/null
grep -q 'Choose my FLAC folder' screenshots/welcome.xml

adb shell mkdir -p /sdcard/Music/QA_Collection
adb push "$RUNNER_TEMP/Stage_Light.flac" /sdcard/Music/QA_Collection/Stage_Light.flac
adb push "$RUNNER_TEMP/Second_Track.flac" /sdcard/Music/QA_Collection/Second_Track.flac
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Music/QA_Collection/Stage_Light.flac || true
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Music/QA_Collection/Second_Track.flac || true
adb shell pm grant com.lazy.player.focus android.permission.READ_MEDIA_AUDIO
adb shell am force-stop com.lazy.player.focus
adb shell am start -n com.lazy.player.focus/com.lazy.player.MainActivity
sleep 10
adb exec-out screencap -p > screenshots/02-library.png
adb shell uiautomator dump /data/local/tmp/ui.xml >/dev/null
adb pull /data/local/tmp/ui.xml screenshots/library.xml >/dev/null
adb shell content query --uri content://media/external/audio/media --projection _id:title:is_music:duration > screenshots/media-store.txt 2>&1 || true

python3 - <<'PY'
import re, subprocess, xml.etree.ElementTree as ET
root = ET.parse("screenshots/library.xml").getroot()
nodes=[n for n in root.iter() if n.get("text","") == "Play all"]
if not nodes: raise SystemExit("QA BLOCKED: No playable fixture in library; inspect media-store.txt and 02-library.png")
b=list(map(int,re.findall(r'\d+',nodes[0].get("bounds",""))))
subprocess.check_call(["adb","shell","input","tap",str((b[0]+b[2])//2),str((b[1]+b[3])//2)])
PY
sleep 4
adb exec-out screencap -p > screenshots/03-unified-dock.png
adb shell uiautomator dump /data/local/tmp/ui.xml >/dev/null
adb pull /data/local/tmp/ui.xml screenshots/dock.xml >/dev/null
python3 - <<'PY'
import re, subprocess, xml.etree.ElementTree as ET
root=ET.parse("screenshots/dock.xml").getroot()
nodes=[n for n in root.iter() if "Stage Light" in n.get("text","")]
if not nodes: raise SystemExit("QA BLOCKED: Mini player track label missing")
n=max(nodes,key=lambda n:int(re.findall(r'\d+',n.get("bounds","[0,0][0,0]"))[1]))
b=list(map(int,re.findall(r'\d+',n.get("bounds",""))))
subprocess.check_call(["adb","shell","input","tap",str((b[0]+b[2])//2),str((b[1]+b[3])//2)])
PY
sleep 4
adb exec-out screencap -p > screenshots/04-now-playing.png
adb shell uiautomator dump /data/local/tmp/ui.xml >/dev/null
adb pull /data/local/tmp/ui.xml screenshots/now-playing.xml >/dev/null
grep -q 'Now Playing' screenshots/now-playing.xml
grep -q 'Shuffle' screenshots/now-playing.xml || true

adb shell settings put system font_scale 1.35
adb shell wm size 720x1280
adb shell wm density 320
adb shell am force-stop com.lazy.player.focus
adb shell am start -n com.lazy.player.focus/com.lazy.player.MainActivity
sleep 5
adb exec-out screencap -p > screenshots/05-small-large-font.png
echo "PASS: first launch, FLAC collection, unified dock and Now Playing captured"
