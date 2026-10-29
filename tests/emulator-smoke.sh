#!/usr/bin/env bash

set -Eeuo pipefail

API_LEVEL="${1:-30}"
DIAG_DIR="dist/diagnostics"
mkdir -p "$DIAG_DIR"

capture_diagnostics() {
    local exit_code=$?
    echo "--- EXIT TRAP: Capturing diagnostics (Exit code: $exit_code) ---"
    timeout 10s adb -s emulator-5554 logcat -d > "$DIAG_DIR/logcat_full_api${API_LEVEL}.txt" || true
    timeout 10s adb -s emulator-5554 shell screencap -p /sdcard/screen.png || true
    timeout 10s adb -s emulator-5554 pull /sdcard/screen.png "$DIAG_DIR/screen_api${API_LEVEL}.png" || true
    timeout 10s adb -s emulator-5554 shell dumpsys window > "$DIAG_DIR/dumpsys_window_api${API_LEVEL}.txt" || true
    timeout 10s adb -s emulator-5554 shell dumpsys activity top > "$DIAG_DIR/dumpsys_activity_top_api${API_LEVEL}.txt" || true
    echo "Diagnostics capture complete."
}
trap capture_diagnostics EXIT ERR

if [ -n "${ANDROID_HOME:-}" ]; then
    export PATH=$PATH:$ANDROID_HOME/emulator
fi
echo "KVM check:"
emulator -accel-check || true

timeout 10s adb -s emulator-5554 shell input keyevent 82 || true
timeout 60s adb -s emulator-5554 install -r dist/Media-Organizer.apk

CRASH_FOUND=false

for run_idx in {1..5}; do
   echo "--- Cold start run $run_idx ---"
   timeout 10s adb -s emulator-5554 logcat -c || true
   timeout 10s adb -s emulator-5554 shell am force-stop tw.mediaorganizer || true
   sleep 2

   timeout 30s adb -s emulator-5554 shell am start -W tw.mediaorganizer/.MainActivity
   sleep 30

   PID=$(timeout 10s adb -s emulator-5554 shell pidof tw.mediaorganizer | tr -d '\r\n' | awk '{print $1}' || true)
   if [ -z "$PID" ]; then
     echo "Process tw.mediaorganizer is not running on run $run_idx!"
     CRASH_FOUND=true
     break
   fi

   # Check Settings via UIAutomator
   timeout 10s adb -s emulator-5554 shell uiautomator dump /data/local/tmp/window_dump.xml
   timeout 10s adb -s emulator-5554 shell cat /data/local/tmp/window_dump.xml > window_dump_start.xml
   cp window_dump_start.xml "$DIAG_DIR/window_dump_start_run${run_idx}_api${API_LEVEL}.xml"
   timeout 10s adb -s emulator-5554 shell screencap -p /sdcard/screen.png || true
   timeout 10s adb -s emulator-5554 pull /sdcard/screen.png "$DIAG_DIR/screen_start_run${run_idx}_api${API_LEVEL}.png" || true

   if ! grep -q "search_input" window_dump_start.xml; then
       echo "ERROR: Gallery search control missing from home screen."
       exit 1
   fi
   if grep -q "btn_settings" window_dump_start.xml; then
       echo "PASS: Settings button found on screen."
       # Use uiautomator to get bounds of Settings instead of hardcoded coordinates
       BOUNDS=$(grep -oP 'resource-id="tw.mediaorganizer:id/btn_settings".*?bounds="\K[^"]+' window_dump_start.xml || echo "")
       if [ -n "$BOUNDS" ]; then
           # parse bounds [x1,y1][x2,y2]
           X1=$(echo $BOUNDS | grep -oP '^\[\K\d+')
           Y1=$(echo $BOUNDS | grep -oP '^\[\d+,\K\d+')
           X2=$(echo $BOUNDS | grep -oP '\]\[\K\d+')
           Y2=$(echo $BOUNDS | grep -oP '\]\[\d+,\K\d+')
           CX=$(( (X1 + X2) / 2 ))
           CY=$(( (Y1 + Y2) / 2 ))
           timeout 10s adb -s emulator-5554 shell input tap $CX $CY
       else
           echo "ERROR: Settings button bounds not found."
           exit 1
       fi
   else
       echo "ERROR: Settings button not found on screen."
       exit 1
   fi

   sleep 5

   # Verify inside Settings
   timeout 10s adb -s emulator-5554 shell uiautomator dump /data/local/tmp/window_dump.xml
   timeout 10s adb -s emulator-5554 shell cat /data/local/tmp/window_dump.xml > window_dump_settings.xml
   cp window_dump_settings.xml "$DIAG_DIR/window_dump_settings_run${run_idx}_api${API_LEVEL}.xml"
   timeout 10s adb -s emulator-5554 shell screencap -p /sdcard/screen.png || true
   timeout 10s adb -s emulator-5554 pull /sdcard/screen.png "$DIAG_DIR/screen_settings_run${run_idx}_api${API_LEVEL}.png" || true

   if grep -q "掃描範圍" window_dump_settings.xml || grep -q "分析選項" window_dump_settings.xml || grep -q "選擇來源資料夾" window_dump_settings.xml; then
        echo "PASS: Setting page confirmed open."
   else
        echo "ERROR: Setting page NOT open."
        exit 1
   fi

   if grep -q "btn_back" window_dump_settings.xml; then
       BOUNDS=$(grep -oP 'resource-id="tw.mediaorganizer:id/btn_back".*?bounds="\K[^"]+' window_dump_settings.xml || echo "")
       if [ -n "$BOUNDS" ]; then
           X1=$(echo $BOUNDS | grep -oP '^\[\K\d+')
           Y1=$(echo $BOUNDS | grep -oP '^\[\d+,\K\d+')
           X2=$(echo $BOUNDS | grep -oP '\]\[\K\d+')
           Y2=$(echo $BOUNDS | grep -oP '\]\[\d+,\K\d+')
           CX=$(( (X1 + X2) / 2 ))
           CY=$(( (Y1 + Y2) / 2 ))
           timeout 10s adb -s emulator-5554 shell input tap $CX $CY
       else
           echo "ERROR: Back button bounds not found."
           exit 1
       fi
   else
       echo "ERROR: Back button not found on screen."
       exit 1
   fi
   sleep 5

   timeout 10s adb -s emulator-5554 logcat -d --pid="$PID" > "$DIAG_DIR/logcat_run_${run_idx}_api${API_LEVEL}.txt" || true
   CRASH=$(timeout 10s adb -s emulator-5554 logcat -d | grep -E "FATAL EXCEPTION" || true)

   if [[ -n "$CRASH" ]]; then
      echo "Crash detected on run $run_idx: $CRASH"
      CRASH_FOUND=true
      break
   fi

   timeout 10s adb -s emulator-5554 shell input keyevent 3 || true # HOME
   sleep 5
   timeout 10s adb -s emulator-5554 shell input keyevent 4 || true # BACK to make sure we do not stick
   timeout 30s adb -s emulator-5554 shell am start -W tw.mediaorganizer/.MainActivity
   sleep 5

   timeout 10s adb -s emulator-5554 shell uiautomator dump /data/local/tmp/window_dump.xml
   timeout 10s adb -s emulator-5554 shell cat /data/local/tmp/window_dump.xml > window_dump_restore.xml
   cp window_dump_restore.xml "$DIAG_DIR/window_dump_restore_run${run_idx}_api${API_LEVEL}.xml"
   timeout 10s adb -s emulator-5554 shell screencap -p /sdcard/screen.png || true
   timeout 10s adb -s emulator-5554 pull /sdcard/screen.png "$DIAG_DIR/screen_restore_run${run_idx}_api${API_LEVEL}.png" || true
   if grep -q "btn_settings" window_dump_restore.xml; then
       echo "PASS: Main Activity survived background restore."
   else
       echo "ERROR: Main Activity NOT correctly restored."
       exit 1
   fi
done

if [[ "$CRASH_FOUND" == "true" ]]; then
   echo "Smoke test failed."
   exit 1
else
   echo "Smoke test passed."
fi
