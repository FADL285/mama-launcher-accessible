#!/usr/bin/env bash
# T-04a: run the low-end baseline protocol against an installed
# noNotifProfileable build and append results rows to a CSV.
# Protocol rules live in docs/performance-baseline.md - read it before running.
#
# Usage: measure.sh [-s SERIAL] [-t TRIALS] SCENARIO [SCENARIO...]
#   SCENARIO: cold | warm | frames | heap | workers | env | all
# Env: APP (default info.fadl.mamalauncher), OUT (default baseline-results.csv)
set -euo pipefail

ADB=(adb); TRIALS=5; SCENARIOS=()
while [ $# -gt 0 ]; do
  case "$1" in
    -s) ADB+=( -s "$2" ); shift 2 ;;
    -t) TRIALS="$2"; shift 2 ;;
    *) SCENARIOS+=( "$1" ); shift ;;
  esac
done
[ ${#SCENARIOS[@]} -gt 0 ] || SCENARIOS=(all)
[ "${SCENARIOS[*]}" = "all" ] && SCENARIOS=(env cold warm frames heap workers)

APP="${APP:-info.fadl.mamalauncher}"
OUT="${OUT:-baseline-results.csv}"
LAUNCH="$APP/.PhoneActivity"
TS="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

row() { echo "$TS,$*" >> "$OUT"; }

reset() {
  "${ADB[@]}" shell am force-stop "$APP"
  "${ADB[@]}" shell input keyevent 3   # HOME
  sleep 1
  # drop page cache when root is available (emulator); harmless otherwise
  "${ADB[@]}" shell "su 0 sh -c 'sync; echo 3 > /proc/sys/vm/drop_caches'" 2>/dev/null || true
  sleep 2
}

median() { printf '%s\n' "$@" | sort -n | awk '{a[NR]=$1} END{print a[int((NR+1)/2)]}'; }

env_row() {
  FP=$("${ADB[@]}" shell getprop ro.build.fingerprint | tr -d '\r')
  API=$("${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r')
  MODEL=$("${ADB[@]}" shell getprop ro.product.model | tr -d '\r')
  echo "env: model=$MODEL api=$API fingerprint=$FP host=$(uname -srm)"
  row "env,\"$MODEL\",$API,\"$FP\",\"$(uname -srm)\""
}

cold() {
  local vals=()
  for t in $(seq 1 "$TRIALS"); do
    reset
    MS=$("${ADB[@]}" shell am start -W -n "$LAUNCH" | grep '^TotalTime:' | awk '{print $2}')
    vals+=( "$MS" ); echo "  cold trial $t: ${MS}ms"
  done
  row "cold_start_ms,$(median "${vals[@]}"),$TRIALS,\"${vals[*]}\""
}

warm() {
  "${ADB[@]}" shell am start -W -n "$LAUNCH" > /dev/null   # ensure process exists
  local vals=()
  for t in $(seq 1 "$TRIALS"); do
    "${ADB[@]}" shell input keyevent 3; sleep 1
    MS=$("${ADB[@]}" shell am start -W -n "$LAUNCH" | grep '^TotalTime:' | awk '{print $2}')
    vals+=( "$MS" ); echo "  warm trial $t: ${MS}ms"
  done
  row "warm_start_ms,$(median "${vals[@]}"),$TRIALS,\"${vals[*]}\""
}

frames() {
  "${ADB[@]}" shell am start -W -n "$LAUNCH" > /dev/null; sleep 2
  "${ADB[@]}" shell dumpsys gfxinfo "$APP" reset > /dev/null
  # deterministic scroll: 12 full-screen swipes up, 6 down
  for t in $(seq 1 12); do "${ADB[@]}" shell input swipe 540 1400 540 400 250; sleep 0.3; done
  for t in $(seq 1 6);  do "${ADB[@]}" shell input swipe 540 400 540 1400 250; sleep 0.3; done
  "${ADB[@]}" shell dumpsys gfxinfo "$APP" > /tmp/mama-gfx.txt
  JANKY=$(grep -o 'Janky frames: [0-9]* ([0-9.]*%)' /tmp/mama-gfx.txt || echo "Janky frames: n/a")
  P90=$(grep -o '90th percentile: [0-9]*ms' /tmp/mama-gfx.txt || echo "90th percentile: n/a")
  P99=$(grep -o '99th percentile: [0-9]*ms' /tmp/mama-gfx.txt || echo "99th percentile: n/a")
  echo "  frames: $JANKY / $P90 / $P99"
  row "frames,\"$JANKY\",\"$P90\",\"$P99\""
}

heap() {
  "${ADB[@]}" shell am start -W -n "$LAUNCH" > /dev/null; sleep 3
  "${ADB[@]}" shell dumpsys meminfo "$APP" > /tmp/mama-mem.txt
  TOTAL=$(awk '/TOTAL PSS/{print $3; exit}' /tmp/mama-mem.txt)
  NATIVE=$(awk '/Native Heap/{print $3; exit}' /tmp/mama-mem.txt)
  JAVA=$(awk '/Java Heap/{print $3; exit}' /tmp/mama-mem.txt)
  echo "  heap KB: total=$TOTAL native=$NATIVE java=$JAVA"
  row "heap_kb,$TOTAL,$NATIVE,$JAVA"
}

workers() {
  PID=$("${ADB[@]}" shell pidof "$APP" | tr -d ' ')
  [ -n "$PID" ] || { echo "  app not running; start it first"; return 0; }
  THREADS=$("${ADB[@]}" shell "cat /proc/$PID/status" | awk '/^Threads:/{print $2}')
  JOBS=$("${ADB[@]}" shell dumpsys jobscheduler | grep -c "u0a[0-9]*.*$APP" || true)
  echo "  threads=$THREADS scheduled_jobs=$JOBS"
  row "workers,$THREADS,$JOBS"
}

echo "# mama-launcher T-04a baseline, app=$APP, trials=$TRIALS" >> "$OUT"
for S in "${SCENARIOS[@]}"; do
  echo "== $S"
  case "$S" in
    env) env_row ;; cold) cold ;; warm) warm ;;
    frames) frames ;; heap) heap ;; workers) workers ;;
    *) echo "unknown scenario $S" >&2; exit 1 ;;
  esac
done
echo "rows appended to $OUT"
