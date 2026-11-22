#!/bin/bash
# Seeds (or with "clean", removes) 2 lakh SMS per folder on an EMULATOR for performance testing.
# The test itself refuses to run on a physical phone.
# Usage: tools/perf/seed.sh [clean] [perSection] [threads]      (defaults: 200000, 1000)
set -e
SERIAL=${SERIAL:-emulator-5554}
ADB=${ADB:-adb}
cd "$(dirname "$0")/../.."
./gradlew -q :app:assembleDebug :app:assembleDebugAndroidTest
$ADB -s "$SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
$ADB -s "$SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
$ADB -s "$SERIAL" shell cmd role add-role-holder android.app.role.SMS com.dns.sms 0
if [ "$1" = "clean" ]; then
  $ADB -s "$SERIAL" shell am instrument -w -e perfClean true -e class com.dns.sms.perf.LargeInboxSeeder#clean \
    com.dns.sms.test/androidx.test.runner.AndroidJUnitRunner
else
  $ADB -s "$SERIAL" shell am instrument -w -e perfSeed true -e perfPerSection "${1:-200000}" -e perfThreads "${2:-1000}" \
    -e class com.dns.sms.perf.LargeInboxSeeder#seed com.dns.sms.test/androidx.test.runner.AndroidJUnitRunner
fi
