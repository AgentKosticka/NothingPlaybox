#!/usr/bin/env bash
set -euo pipefail

APK_DIR="${CI_APK_DIR:-ci-apks}"
OUTPUT="${GITHUB_WORKSPACE:-.}/instrumentation-output.txt"

APP_APK="$(find "$APK_DIR" -type f -name 'app-debug.apk' -print -quit)"
TEST_APK="$(find "$APK_DIR" -type f -name 'app-debug-androidTest.apk' -print -quit)"

test -n "$APP_APK" || { echo "Debug app APK not found under $APK_DIR" >&2; exit 1; }
test -n "$TEST_APK" || { echo "Instrumentation APK not found under $APK_DIR" >&2; exit 1; }

adb wait-for-device
adb install -r -t "$APP_APK"
adb install -r -t "$TEST_APK"

RUNNER="$(adb shell pm list instrumentation \
  | tr -d '\r' \
  | grep '(target=com.agentkosticka.playbox)' \
  | head -n 1 \
  | sed -E 's/^instrumentation:([^ ]+).*/\1/')"

test -n "$RUNNER" || { echo "NothingPlaybox instrumentation runner not found" >&2; exit 1; }

echo "Running $RUNNER"
set +e
adb shell am instrument -w -r "$RUNNER" 2>&1 | tee "$OUTPUT"
status=${PIPESTATUS[0]}
set -e

if (( status != 0 )); then
  echo "am instrument exited with status $status" >&2
  exit "$status"
fi

if grep -Eq 'FAILURES!!!|INSTRUMENTATION_(FAILED|ABORTED)|Process crashed|shortMsg=' "$OUTPUT"; then
  echo "Instrumentation reported a failure" >&2
  exit 1
fi

if ! grep -Eq 'OK \([0-9]+ tests?\)' "$OUTPUT"; then
  echo "Instrumentation did not report a successful JUnit completion" >&2
  exit 1
fi
