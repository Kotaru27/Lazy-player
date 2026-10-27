#!/usr/bin/env bash
set -euo pipefail
mkdir -p evidence/normal evidence/compact evidence/large-font
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell settings put system font_scale 1.0
adb shell wm size reset
adb shell wm density reset
adb shell dumpsys gfxinfo com.lazy.player.preview reset >/dev/null 2>&1 || true

# Record the actual emulator while Android instrumentation changes screens.
adb shell screenrecord --time-limit 65 --bit-rate 1800000 /sdcard/lazy-preview-interaction.mp4 >/dev/null 2>&1 &
record_pid=$!
sleep 2
set +e
adb shell am instrument -w -r com.lazy.player.preview.test/androidx.test.runner.AndroidJUnitRunner | tee evidence/normal/instrumentation.txt
first=${PIPESTATUS[0]}
set -e
for name in 01-library.png 02-now-playing.png 03-populated-library.png 04-albums.png; do
  adb exec-out run-as com.lazy.player.preview cat files/"$name" > evidence/normal/"$name" || true
done
adb shell kill -2 "$(adb shell pidof screenrecord | tr -d '\r')" >/dev/null 2>&1 || true
wait "$record_pid" || true
adb pull /sdcard/lazy-preview-interaction.mp4 evidence/normal/gesture-interaction.mp4 || true

adb shell settings put system font_scale 1.35
adb shell wm size 720x1280
adb shell wm density 320
set +e
adb shell am instrument -w -r com.lazy.player.preview.test/androidx.test.runner.AndroidJUnitRunner | tee evidence/compact/instrumentation.txt
second=${PIPESTATUS[0]}
set -e
for name in 01-library.png 02-now-playing.png 03-populated-library.png 04-albums.png; do
  adb exec-out run-as com.lazy.player.preview cat files/"$name" > evidence/compact/"$name" || true
done
# Targeted 200% font check on 320dp width, independent of normal-size run.
adb shell settings put system font_scale 2.0
adb shell wm size 640x1136
adb shell wm density 320
set +e
adb shell am instrument -w -r -e class 'com.lazy.player.ui.LazyPlayerUiTest#hundredTrackLibraryKeepsDockAndAccessibleLastSong' \
  com.lazy.player.preview.test/androidx.test.runner.AndroidJUnitRunner | tee evidence/large-font/instrumentation.txt
third=${PIPESTATUS[0]}
set -e
for name in 03-populated-library.png 04-albums.png; do
  adb exec-out run-as com.lazy.player.preview cat files/"$name" > evidence/large-font/"$name" || true
done

adb shell dumpsys gfxinfo com.lazy.player.preview framestats > evidence/compact/gfxinfo-debug.txt || true
adb logcat -d -t 600 > evidence/logcat-tail.txt || true
adb shell wm size reset
adb shell wm density reset
adb shell settings put system font_scale 1.0
if [ "$first" -ne 0 ] || [ "$second" -ne 0 ] || [ "$third" -ne 0 ]; then
  echo "Instrumented UI test failed: normal=$first compact=$second largeFont=$third"
  exit 1
fi
grep -q 'OK (' evidence/normal/instrumentation.txt
grep -q 'OK (' evidence/compact/instrumentation.txt
grep -q 'OK (' evidence/large-font/instrumentation.txt
test -s evidence/normal/01-library.png
test -s evidence/normal/02-now-playing.png
echo 'Instrumented UI and rendering tests passed at normal, 135%, and 200% font sizes'
