#!/usr/bin/env bash
# T-04a: run the low-end baseline protocol against an installed
# noNotifProfileable build and append result rows to a CSV.
# Protocol rules live in docs/performance-baseline.md - read it before running.
#
# Usage: measure.sh [-s SERIAL] [-t TRIALS] [-c CONTACTS] SCENARIO [SCENARIO...]
#   SCENARIO: env | cold | warm | frames | heap | workers | all
#   -c CONTACTS: seeded contact count, recorded on every row (required so
#   50/500/2000 runs stay distinguishable in one CSV).
# Env: APP (default info.fadl.mamalauncher), OUT (default baseline-results.csv),
#      SETTLE (seconds to wait for async grid load before sampling, default 3)
set -euo pipefail
exec 8> /tmp/mama-measure.lock && flock 8   # one measurement run per host

ADB=(adb); TRIALS=5; CONTACTS="${CONTACTS:-unknown}"; SCENARIOS=()
while [ $# -gt 0 ]; do
  case "$1" in
    -s) ADB+=( -s "$2" ); shift 2 ;;
    -t) TRIALS="$2"; shift 2 ;;
    -c) CONTACTS="$2"; shift 2 ;;
    *) SCENARIOS+=( "$1" ); shift ;;
  esac
done
[ ${#SCENARIOS[@]} -gt 0 ] || SCENARIOS=(all)
[ "${SCENARIOS[*]}" = "all" ] && SCENARIOS=(env cold warm frames heap workers)

APP="${APP:-info.fadl.mamalauncher}"
OUT="${OUT:-baseline-results.csv}"
SETTLE="${SETTLE:-3}"
# MainActivity is the exported launcher/home entry (singleTask); PhoneActivity
# is not exported, so component starts must target MainActivity.
LAUNCH="$APP/.MainActivity"
TS="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

row() { echo "$TS,$CONTACTS,$*" >> "$OUT"; }

prep() {
  # Fresh installs otherwise measure the permission/setup screen, not the grid.
  "${ADB[@]}" shell pm grant "$APP" android.permission.READ_CONTACTS 2>/dev/null || true
  "${ADB[@]}" shell pm grant "$APP" android.permission.READ_CALL_LOG 2>/dev/null || true
}

reset() {
  "${ADB[@]}" shell am force-stop "$APP"
  # Move focus to a neutral screen instead of HOME: if Mama is the default
  # launcher, a HOME keyevent would relaunch it before the timed (cold) start.
  "${ADB[@]}" shell am start -a android.settings.SETTINGS > /dev/null
  sleep 1
  # drop page cache when root is available (emulator); harmless otherwise
  "${ADB[@]}" shell "su 0 sh -c 'sync; echo 3 > /proc/sys/vm/drop_caches'" 2>/dev/null || true
  sleep 2
}

median() {  # average the two middle values on even counts
  printf '%s\n' "$@" | sort -n | awk '{a[NR]=$1} END{if(NR%2) print a[(NR+1)/2]; else printf "%.1f\n", (a[NR/2]+a[NR/2+1])/2}'
}

start_ms() {  # fails loudly when the start does not report a TotalTime
  local OUT_START MS
  OUT_START=$("${ADB[@]}" shell am start -W -n "$LAUNCH")
  MS=$(printf '%s' "$OUT_START" | grep '^TotalTime:' | awk '{print $2}')
  [ -n "$MS" ] || { echo "am start failed: $OUT_START" >&2; exit 1; }
  echo "$MS"
}

env_row() {
  FP=$("${ADB[@]}" shell getprop ro.build.fingerprint | tr -d '\r')
  API=$("${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r')
  MODEL=$("${ADB[@]}" shell getprop ro.product.model | tr -d '\r')
  DISP=$("${ADB[@]}" shell wm size | tr -d '\r')
  echo "env: model=$MODEL api=$API fingerprint=$FP display=$DISP host=$(uname -srm)"
  row "env,\"$MODEL\",$API,\"$FP\",\"$DISP\",\"$(uname -srm)\""
}

cold() {
  local vals=()
  for t in $(seq 1 "$TRIALS"); do
    reset
    MS=$(start_ms); vals+=( "$MS" ); echo "  cold trial $t: ${MS}ms"
  done
  row "cold_start_ms,$(median "${vals[@]}"),$TRIALS,\"${vals[*]}\""
}

warm() {
  start_ms > /dev/null   # process + activity exist (singleTask: re-starts raise the task)
  sleep "$SETTLE"
  local vals=()
  for t in $(seq 1 "$TRIALS"); do
    "${ADB[@]}" shell am start -a android.settings.SETTINGS > /dev/null; sleep 1
    MS=$(start_ms); vals+=( "$MS" ); echo "  warm trial $t: ${MS}ms"
  done
  row "warm_start_ms,$(median "${vals[@]}"),$TRIALS,\"${vals[*]}\""
}

frames() {
  # display-relative swipes so any emulator size works
  read -r W H <<< "$("${ADB[@]}" shell wm size | grep -o '[0-9]*x[0-9]*' | tr 'x' ' ')"
  local X=$(( W / 2 )) Y1=$(( H * 73 / 100 )) Y2=$(( H * 21 / 100 ))
  local jankys=() p90s=() p99s=()
  for t in $(seq 1 "$TRIALS"); do
    reset; start_ms > /dev/null; sleep "$SETTLE"
    "${ADB[@]}" shell dumpsys gfxinfo "$APP" reset > /dev/null
    for s in $(seq 1 12); do "${ADB[@]}" shell input swipe "$X" "$Y1" "$X" "$Y2" 250; sleep 0.3; done
    for s in $(seq 1 6);  do "${ADB[@]}" shell input swipe "$X" "$Y2" "$X" "$Y1" 250; sleep 0.3; done
    local GFX; GFX="$(mktemp)"
    "${ADB[@]}" shell dumpsys gfxinfo "$APP" > "$GFX"
    local J P9 P99
    J=$(grep -o 'Janky frames: [0-9]* (\([0-9.]*\)%)' "$GFX" | grep -o '[0-9.]*%' | tr -d '%')
    P9=$(grep -o '90th percentile: [0-9]*ms' "$GFX" | grep -o '[0-9]*')
    P99=$(grep -o '99th percentile: [0-9]*ms' "$GFX" | grep -o '[0-9]*')
    rm -f "$GFX"
    jankys+=( "${J:-0}" ); p90s+=( "${P9:-0}" ); p99s+=( "${P99:-0}" )
    echo "  frames trial $t: janky=${J:-n/a}% p90=${P9:-n/a}ms p99=${P99:-n/a}ms"
  done
  row "frames_janky_pct,$(median "${jankys[@]}"),$TRIALS,\"${jankys[*]}\""
  row "frames_p90_ms,$(median "${p90s[@]}"),$TRIALS,\"${p90s[*]}\""
  row "frames_p99_ms,$(median "${p99s[@]}"),$TRIALS,\"${p99s[*]}\""
}

heap() {
  local tots=() nats=() javas=()
  for t in $(seq 1 "$TRIALS"); do
    reset; start_ms > /dev/null; sleep "$SETTLE"
    local MEM; MEM="$(mktemp)"
    "${ADB[@]}" shell dumpsys meminfo "$APP" > "$MEM"
    local T N J
    T=$(awk '/TOTAL PSS/{print $3; exit}' "$MEM")
    N=$(awk '/Native Heap/{print $3; exit}' "$MEM")
    J=$(awk '/Java Heap/{print $3; exit}' "$MEM")
    rm -f "$MEM"
    tots+=( "${T:-0}" ); nats+=( "${N:-0}" ); javas+=( "${J:-0}" )
    echo "  heap trial $t: total=${T:-n/a}KB native=${N:-n/a}KB java=${J:-n/a}KB"
  done
  row "heap_total_kb,$(median "${tots[@]}"),$TRIALS,\"${tots[*]}\""
  row "heap_native_kb,$(median "${nats[@]}"),$TRIALS,\"${nats[*]}\""
  row "heap_java_kb,$(median "${javas[@]}"),$TRIALS,\"${javas[*]}\""
}

workers() {
  local ths=() jobs_=()
  for t in $(seq 1 "$TRIALS"); do
    reset; start_ms > /dev/null; sleep "$SETTLE"
    local PID; PID=$("${ADB[@]}" shell pidof "$APP" | tr -d ' ' || true)
    [ -n "$PID" ] || { echo "  workers trial $t: app not running"; continue; }
    local TH JB
    TH=$("${ADB[@]}" shell "cat /proc/$PID/status" | awk '/^Threads:/{print $2}')
    JB=$("${ADB[@]}" shell dumpsys jobscheduler | grep -c "$APP" || true)
    ths+=( "${TH:-0}" ); jobs_+=( "${JB:-0}" )
    echo "  workers trial $t: threads=$TH jobs=$JB"
  done
  [ ${#ths[@]} -gt 0 ] || { echo "no worker samples"; return 0; }
  row "workers_threads,$(median "${ths[@]}"),$TRIALS,\"${ths[*]}\""
  row "workers_jobs,$(median "${jobs_[@]}"),$TRIALS,\"${jobs_[*]}\""
}

echo "# mama-launcher T-04a baseline, app=$APP, trials=$TRIALS, contacts=$CONTACTS" >> "$OUT"
prep
for S in "${SCENARIOS[@]}"; do
  echo "== $S"
  case "$S" in
    env) env_row ;; cold) cold ;; warm) warm ;;
    frames) frames ;; heap) heap ;; workers) workers ;;
    *) echo "unknown scenario $S" >&2; exit 1 ;;
  esac
done
echo "rows appended to $OUT"
