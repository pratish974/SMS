#!/bin/bash
# Scroll/fling performance of the conversation list, per folder, using Android's frame stats.
# Prints only numbers (frame counts, jank %, frame-time percentiles, Perf timing logs): never message text.
# Usage: SERIAL=<device> tools/perf/scroll.sh [flings-per-folder]   (default 40)
SERIAL=${SERIAL:-emulator-5554}
ADB=${ADB:-adb}
PKG=com.dns.sms
FLINGS=${1:-40}
DIR="$(cd "$(dirname "$0")" && pwd)"
a() { $ADB -s "$SERIAL" "$@"; }

read W H < <(a shell wm size | awk -F'[ x]' '/Physical/{print $3, $4}')
X=$((W / 2)); Y1=$((H * 75 / 100)); Y2=$((H * 25 / 100))

# Bounds of a bottom-bar label (Personal/Transactions/...). The dump is parsed locally; only coordinates are used.
center_of() {
  a shell uiautomator dump /sdcard/perf-ui.xml >/dev/null 2>&1
  a shell cat /sdcard/perf-ui.xml | python3 "$DIR/center.py" "$1"
  a shell rm -f /sdcard/perf-ui.xml
}

a shell am start -n $PKG/.MainActivity >/dev/null 2>&1
sleep 3
a logcat -c
for folder in Personal Transactions Promotions Starred; do
  C=$(center_of "$folder"); [ -z "$C" ] && { echo "$folder: tab not found"; continue; }
  a shell input tap $C
  sleep 3
  a shell dumpsys gfxinfo $PKG reset >/dev/null
  for i in $(seq 1 "$FLINGS"); do a shell input swipe $X $Y1 $X $Y2 60; done   # fling down
  for i in $(seq 1 $((FLINGS / 4))); do a shell input swipe $X $Y2 $X $Y1 60; done  # and back up a bit
  sleep 1
  echo "== $folder"
  a shell dumpsys gfxinfo $PKG | grep -E "Total frames rendered|Janky frames:|50th percentile|90th percentile|95th percentile|99th percentile" | sed 's/^ *//'
done
echo "== Perf timings (debug build)"
a logcat -d -s Perf | sed 's/^.*Perf *: //' | grep -E "folder|loadThreads|unreadCounts|folders:" | tail -20
