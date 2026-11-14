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
#      SETTLE (seconds to wait after start before sampling, default 3),
#      GRID_TIMEOUT (seconds to poll for a populated contacts grid, default 15),
#      NAV (navigation cycles per workers trial, default 10),
#      LAUNCH (component to time, default $APP/.PhoneActivity).
# CSV columns: metric,median,iqr,valid_trials,"all valid values"[,extra]
set -euo pipefail
export LC_ALL=C   # stable decimal point/sort for EPOCHREALTIME, sort -n, awk

# One measurement run per host. flock where it exists (Linux); macOS has no
# flock(1), so fall back to a mkdir lock there.
if command -v flock > /dev/null 2>&1; then
  exec 8> /tmp/mama-baseline.lock
  flock 8
else
  LOCKD=/tmp/mama-baseline.lock.d
  mkdir "$LOCKD" 2> /dev/null || { echo "another measurement run is active ($LOCKD)" >&2; exit 1; }
  trap 'rmdir "$LOCKD"' EXIT
fi

ADB=(adb); TRIALS=5; CONTACTS="${CONTACTS:-unknown}"; SCENARIOS=()
while [ $# -gt 0 ]; do
  case "$1" in
    -s) [ $# -ge 2 ] || { echo "-s needs a value" >&2; exit 2; }; ADB+=( -s "$2" ); shift 2 ;;
    -t) [ $# -ge 2 ] || { echo "-t needs a value" >&2; exit 2; }; TRIALS="$2"; shift 2 ;;
    -c) [ $# -ge 2 ] || { echo "-c needs a value" >&2; exit 2; }; CONTACTS="$2"; shift 2 ;;
    *) SCENARIOS+=( "$1" ); shift ;;
  esac
done
[ ${#SCENARIOS[@]} -gt 0 ] || SCENARIOS=(all)
[ "${SCENARIOS[*]}" = "all" ] && SCENARIOS=(env cold warm frames heap workers)
case "$TRIALS" in '' | *[!0-9]* | 0) echo "-t must be a positive integer" >&2; exit 2 ;; esac
if [ "${SCENARIOS[*]}" != "env" ] && [ "$CONTACTS" = unknown ]; then
  echo "-c CONTACTS is required for measurement scenarios (rows must carry the seeded size)" >&2
  exit 2
fi

APP="${APP:-info.fadl.mamalauncher}"
OUT="${OUT:-baseline-results.csv}"
SETTLE="${SETTLE:-3}"
GRID_TIMEOUT="${GRID_TIMEOUT:-15}"
NAV="${NAV:-10}"
# PhoneActivity hosts the contacts grid - the workload T-06/T-07 change. It is
# exported ONLY in the profileable build type (app/src/profileable), so timed
# starts can target it here while production build types keep it unexported.
# Time the home screen separately with LAUNCH=$APP/.MainActivity.
LAUNCH="${LAUNCH:-$APP/.PhoneActivity}"
TS="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
COMMIT="$(git -C "$(dirname "$0")/.." rev-parse --short HEAD 2> /dev/null || echo unknown)"

row() { echo "$TS,$CONTACTS,$*" >> "$OUT"; }

prep() {
  # Fresh installs otherwise measure the permission/setup screen, not the grid.
  "${ADB[@]}" shell pm grant "$APP" android.permission.READ_CONTACTS 2> /dev/null || true
  "${ADB[@]}" shell pm grant "$APP" android.permission.READ_CALL_LOG 2> /dev/null || true
  # Page-cache drop needs root; Google Play emulator images have no su. Probe
  # once so cold numbers record whether the cache was actually dropped.
  if "${ADB[@]}" shell "su 0 id" > /dev/null 2>&1; then CACHE_DROP=yes; else CACHE_DROP=no; fi
}

reset() {
  "${ADB[@]}" shell am force-stop "$APP"
  # Move focus to a neutral screen instead of HOME: if Mama is the default
  # launcher, a HOME keyevent would relaunch it before the timed (cold) start.
  "${ADB[@]}" shell am start -a android.settings.SETTINGS > /dev/null
  sleep 1
  if [ "$CACHE_DROP" = yes ]; then
    "${ADB[@]}" shell "su 0 sh -c 'sync; echo 3 > /proc/sys/vm/drop_caches'" 2> /dev/null || true
  fi
  sleep 2
}

median() {  # average the two middle values on even counts; numeric args only
  printf '%s\n' "$@" | sort -n | awk '{a[NR]=$1} END{if(NR%2) print a[(NR+1)/2]; else printf "%.1f\n", (a[NR/2]+a[NR/2+1])/2}'
}

iqr() {  # interquartile range (median-of-halves, median excluded on odd n); empty below 4 values
  [ $# -ge 4 ] || return 0
  printf '%s\n' "$@" | sort -n | awk '{a[NR]=$1} END{
    n=NR; lo=int(n/2); hi=lo; off=n-hi
    if(lo%2) q1=a[(lo+1)/2]; else q1=(a[lo/2]+a[lo/2+1])/2
    if(hi%2) q3=a[off+(hi+1)/2]; else q3=(a[off+hi/2]+a[off+hi/2+1])/2
    printf "%.1f\n", q3-q1}'
}

nums() {  # keep only numeric values (NA and friends are excluded from stats)
  local v
  for v in "$@"; do case "$v" in '' | *[!0-9.]*) ;; *) printf '%s\n' "$v" ;; esac; done
}

emit() {  # emit METRIC VALUES... -> row metric,median,iqr,valid_n,"values"
  local m="$1"; shift
  local valid
  valid=$(nums "$@")
  if [ -n "$valid" ]; then
    # intentional word splitting: valid is one number per line, all numeric
    row "$m,$(median $valid),$(iqr $valid),$(printf '%s\n' "$valid" | wc -l | tr -d ' '),\"$(printf '%s ' $valid | sed 's/ $//')\""
  else
    row "$m,NA,,0,\"no valid samples\""
    ZERO_VALID=1   # the run as a whole must fail when a scenario produced nothing
  fi
}

start_ms() {  # sets MS + LAUNCH_STATE; fails loudly when TotalTime is missing
  local OUT_START
  OUT_START=$("${ADB[@]}" shell am start -W -n "$LAUNCH" | tr -d '\r')
  MS=$(printf '%s' "$OUT_START" | awk '/^TotalTime:/{print $2; exit}')
  LAUNCH_STATE=$(printf '%s' "$OUT_START" | awk -F'[ :]+' '/^LaunchState:/{print $2; exit}')
  [ -n "$MS" ] || { echo "am start failed: $OUT_START" >&2; exit 1; }
  echo "$MS"
}

now_ms() {  # wall clock in ms; EPOCHREALTIME (bash 5) or whole seconds (macOS bash 3.2)
  if [ -n "${EPOCHREALTIME:-}" ]; then
    local s=${EPOCHREALTIME%.*} us=${EPOCHREALTIME#*.}
    echo $(( s * 1000 + 10#${us:0:3} ))
  else
    echo $(( SECONDS * 1000 ))
  fi
}

grid_ready_ms() {  # coarse populated-grid GATE without production changes.
  # Honest precision: the uiautomator dump itself costs hundreds of ms, so
  # this is a pass/fail readiness gate, NOT a sub-second T-06 timer (see doc).
  # $1 = optional epoch (now_ms) to measure from; pass the pre-launch value
  # so the number covers launch-to-populated-grid, not just post-first-frame.
  local start=${1:-$(now_ms)} UI
  while :; do
    # Remove any stale dump first: a failed dump must not match an earlier UI.
    "${ADB[@]}" shell rm -f /sdcard/mama-ui.xml > /dev/null 2>&1 || true
    "${ADB[@]}" shell uiautomator dump /sdcard/mama-ui.xml > /dev/null 2>&1 || true
    # Capture before matching: piping `cat` into `grep -q` under pipefail lets
    # adb die with SIGPIPE and misreport a populated grid as not ready.
    UI=$("${ADB[@]}" shell cat /sdcard/mama-ui.xml 2> /dev/null || true)
    case "$UI" in
      *":id/name"*)
        echo $(( $(now_ms) - start )); return 0 ;;
    esac
    [ $(( $(now_ms) - start )) -ge $(( GRID_TIMEOUT * 1000 )) ] && { echo NA; return 1; }
    sleep 0.5
  done
}

env_row() {
  FP=$("${ADB[@]}" shell getprop ro.build.fingerprint | tr -d '\r')
  API=$("${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r')
  MODEL=$("${ADB[@]}" shell getprop ro.product.model | tr -d '\r')
  DISP=$("${ADB[@]}" shell wm size | tr -d '\r' | tr '\n' ' ')
  # Resolved-number region changes T-17 dedup behaviour, so record its inputs.
  SIM=$("${ADB[@]}" shell getprop gsm.sim.operator.iso-country | tr -d '\r ')
  NET=$("${ADB[@]}" shell getprop gsm.operator.iso-country | tr -d '\r ')
  LOC=$("${ADB[@]}" shell getprop persist.sys.locale | tr -d '\r ')
  # Identify the exact installed binary, not just flavor/build-type names.
  # Capture once: piping dumpsys into an awk that exits early can SIGPIPE adb
  # under pipefail and abort the env scenario.
  PKG=$("${ADB[@]}" shell dumpsys package "$APP" 2> /dev/null | tr -d '\r' || true)
  VER=$(awk -F= '/versionName=/{print $2; exit}' <<< "$PKG")
  VC=$(awk '{for(i=1;i<=NF;i++) if($i ~ /^versionCode=/) {print $i; exit}}' <<< "$PKG")
  UPD=$(awk -F= '/lastUpdateTime=/{print $2; exit}' <<< "$PKG")
  echo "env: model=$MODEL api=$API fingerprint=$FP display=$DISP region(sim=$SIM net=$NET locale=$LOC) cache_drop=$CACHE_DROP apk=\"$VER $VC\" apk_updated=\"$UPD\" checkout=$COMMIT launch=$LAUNCH host=$(uname -srm)"
  row "env,\"$MODEL\",$API,\"$FP\",\"$DISP\",\"sim=$SIM net=$NET locale=$LOC\",cache_drop=$CACHE_DROP,\"$VER $VC\",apk_updated=\"$UPD\",checkout=$COMMIT,$LAUNCH,\"$(uname -srm)\""
}

cold() {
  local vals=() states=() ready=() invalid=0
  local t GR T0
  for t in $(seq 1 "$TRIALS"); do
    reset
    T0=$(now_ms)
    start_ms > /dev/null
    # A "cold" trial that the platform reports as WARM/HOT is not a cold
    # start (e.g. Mama relaunched as the default home). Fail loudly.
    if [ "$LAUNCH_STATE" != COLD ]; then
      echo "  cold trial $t: LaunchState=${LAUNCH_STATE:-unknown}, expected COLD - scenario invalid" >&2
      row "cold_start_ms,NA,,0,\"invalid: LaunchState=${LAUNCH_STATE:-unknown}\""
      return 1
    fi
    GR=$(grid_ready_ms "$T0" || true)
    if [ "$GR" = NA ]; then
      # The grid never populated (permission, provider or load failure): this
      # trial is not a valid sample of the contacts workload at any count.
      echo "  cold trial $t: grid never populated within ${GRID_TIMEOUT}s - trial invalid, excluded" >&2
      invalid=$((invalid+1)); continue
    fi
    vals+=( "$MS" ); states+=( "$LAUNCH_STATE" ); ready+=( "$GR" )
    echo "  cold trial $t: ${MS}ms state=$LAUNCH_STATE grid_ready=${GR}ms"
  done
  [ "$invalid" -eq 0 ] || echo "  cold: $invalid invalid trial(s) excluded from statistics" >&2
  emit cold_start_ms ${vals[@]+"${vals[@]}"}
  emit grid_ready_ms ${ready[@]+"${ready[@]}"}
  row "cold_launch_states,NA,,${#states[@]},\"${states[*]:-}\""
}

warm() {
  start_ms > /dev/null   # process + activity exist before "warm" trials
  sleep "$SETTLE"
  local vals=() states=() t
  for t in $(seq 1 "$TRIALS"); do
    "${ADB[@]}" shell am start -a android.settings.SETTINGS > /dev/null; sleep 1
    start_ms > /dev/null
    vals+=( "$MS" ); states+=( "${LAUNCH_STATE:-unknown}" )
    # Note: raising a live singleTask entry reports HOT, not WARM; the
    # reported state is recorded, not assumed.
    echo "  warm trial $t: ${MS}ms state=${LAUNCH_STATE:-unknown}"
  done
  emit warm_start_ms ${vals[@]+"${vals[@]}"}
  row "warm_launch_states,NA,,${#states[@]},\"${states[*]:-}\""
}

frames() {
  # display-relative swipes so any emulator size works; when an override size
  # is set, `wm size` prints Physical AND Override lines - use the last one.
  read -r W H <<< "$("${ADB[@]}" shell wm size | grep -o '[0-9]*x[0-9]*' | tail -n1 | tr 'x' ' ')"
  local X=$(( W / 2 )) Y1=$(( H * 73 / 100 )) Y2=$(( H * 21 / 100 ))
  local jankys=() p90s=() p99s=() invalid=0
  local t s GFX J P9 P99
  for t in $(seq 1 "$TRIALS"); do
    reset; start_ms > /dev/null
    grid_ready_ms > /dev/null || {
      # Scrolling an unpopulated grid is not the S3 workload; exclude it.
      echo "  frames trial $t: grid not ready within ${GRID_TIMEOUT}s - trial invalid, excluded" >&2
      invalid=$((invalid+1)); continue; }
    "${ADB[@]}" shell dumpsys gfxinfo "$APP" reset > /dev/null
    for s in $(seq 1 12); do "${ADB[@]}" shell input swipe "$X" "$Y1" "$X" "$Y2" 250; sleep 0.3; done
    for s in $(seq 1 6);  do "${ADB[@]}" shell input swipe "$X" "$Y2" "$X" "$Y1" 250; sleep 0.3; done
    GFX="$(mktemp)"
    "${ADB[@]}" shell dumpsys gfxinfo "$APP" > "$GFX"
    # Exactly one numeric value per metric: anchor on the CPU percentile lines
    # ("  90th percentile: Nms"); the "GPU 90th percentile" line must NOT match,
    # and the label digits (90/99) must not leak into the sample. A missed
    # match yields NA (excluded from medians), never 0, and never aborts.
    J=$(sed -n 's/^ *Janky frames: [0-9]* (\([0-9.]*\)%).*/\1/p' "$GFX" | head -n1 || true)
    P9=$(awk '/^ *90th percentile:/{gsub(/ms/,"",$3); print $3; exit}' "$GFX" || true)
    P99=$(awk '/^ *99th percentile:/{gsub(/ms/,"",$3); print $3; exit}' "$GFX" || true)
    rm -f "$GFX"
    jankys+=( "${J:-NA}" ); p90s+=( "${P9:-NA}" ); p99s+=( "${P99:-NA}" )
    echo "  frames trial $t: janky=${J:-NA}% p90=${P9:-NA}ms p99=${P99:-NA}ms"
  done
  [ "$invalid" -eq 0 ] || echo "  frames: $invalid invalid trial(s) excluded from statistics" >&2
  emit frames_janky_pct ${jankys[@]+"${jankys[@]}"}
  emit frames_p90_ms ${p90s[@]+"${p90s[@]}"}
  emit frames_p99_ms ${p99s[@]+"${p99s[@]}"}
}

heap() {
  local tots=() nats=() javas=() invalid=0
  local t PID MEM T N J
  for t in $(seq 1 "$TRIALS"); do
    reset; start_ms > /dev/null
    grid_ready_ms > /dev/null || {
      # Heap of an app stuck before the grid is not the S4 workload.
      echo "  heap trial $t: grid not ready within ${GRID_TIMEOUT}s - trial invalid, excluded" >&2
      invalid=$((invalid+1)); continue; }
    sleep "$SETTLE"
    PID=$("${ADB[@]}" shell pidof "$APP" | tr -d ' \r' || true)
    # A dead/missing process or an unsupported dump layout must fail the trial,
    # never record a successful zero sample (which fakes a memory improvement).
    if [ -z "$PID" ]; then
      echo "  heap trial $t: app not running - trial invalid, excluded" >&2
      invalid=$((invalid+1)); continue
    fi
    MEM="$(mktemp)"
    "${ADB[@]}" shell dumpsys meminfo "$APP" > "$MEM"
    # App Summary values are plain digits, but some API 30+ layouts use
    # comma group separators; strip them. Absent fields stay empty and the
    # trial is invalidated below (NA, never zero).
    T=$(awk '/TOTAL PSS/{gsub(/,/,"",$3); print $3; exit}' "$MEM" || true)
    N=$(awk '/Native Heap/{gsub(/,/,"",$3); print $3; exit}' "$MEM" || true)
    J=$(awk '/Java Heap/{gsub(/,/,"",$3); print $3; exit}' "$MEM" || true)
    rm -f "$MEM"
    case "${T:-x}.${N:-x}.${J:-x}" in
      *[!0-9.]*)
        echo "  heap trial $t: unparseable meminfo (T=${T:--} N=${N:--} J=${J:--}) - trial invalid, excluded" >&2
        invalid=$((invalid+1)); continue ;;
    esac
    tots+=( "$T" ); nats+=( "$N" ); javas+=( "$J" )
    echo "  heap trial $t: total=${T}KB native=${N}KB java=${J}KB"
  done
  [ "$invalid" -eq 0 ] || echo "  heap: $invalid invalid trial(s) excluded from statistics" >&2
  emit heap_total_kb ${tots[@]+"${tots[@]}"}
  emit heap_native_kb ${nats[@]+"${nats[@]}"}
  emit heap_java_kb ${javas[@]+"${javas[@]}"}
}

workers() {
  # Retention across navigation (T-06's repeated-visit workload): each trial
  # starts the app once, then runs NAV in-process cycles of PhoneActivity ->
  # back -> MainActivity so workers retained per visit can accumulate, then
  # waits for bounded work to quiesce and samples again. A fresh-process
  # sample (force-stop per measurement) cannot detect per-visit leaks.
  local before=() after=() jobs_=() invalid=0
  local t c PID PID0 TH0 TH1 JB
  for t in $(seq 1 "$TRIALS"); do
    reset; start_ms > /dev/null
    grid_ready_ms > /dev/null || {
      # Worker retention is only meaningful for the real contacts workload.
      echo "  workers trial $t: grid not ready within ${GRID_TIMEOUT}s - trial invalid, excluded" >&2
      invalid=$((invalid+1)); continue; }
    sleep "$SETTLE"
    PID=$("${ADB[@]}" shell pidof "$APP" | tr -d ' \r' || true)
    if [ -z "$PID" ]; then
      echo "  workers trial $t: app not running - trial invalid, excluded" >&2
      invalid=$((invalid+1)); continue
    fi
    PID0=$PID
    TH0=$("${ADB[@]}" shell "cat /proc/$PID/status" | tr -d '\r' | awk '/^Threads:/{print $2}' || true)
    for c in $(seq 1 "$NAV"); do
      "${ADB[@]}" shell am start -n "$LAUNCH" > /dev/null
      sleep 1
      "${ADB[@]}" shell input keyevent KEYCODE_BACK
      "${ADB[@]}" shell am start -n "$APP/.MainActivity" > /dev/null
      sleep 1
    done
    sleep "$SETTLE"   # let bounded async work quiesce before sampling
    PID=$("${ADB[@]}" shell pidof "$APP" | tr -d ' \r' || true)
    if [ -z "$PID" ]; then
      echo "  workers trial $t: process died during navigation - trial invalid, excluded" >&2
      invalid=$((invalid+1)); continue
    fi
    if [ "$PID" != "$PID0" ]; then
      # A crash/kill + relaunch gives a fresh process: before/after thread
      # counts from two processes say nothing about retained workers.
      echo "  workers trial $t: process restarted during navigation (pid $PID0 -> $PID) - trial invalid, excluded" >&2
      invalid=$((invalid+1)); continue
    fi
    TH1=$("${ADB[@]}" shell "cat /proc/$PID/status" | tr -d '\r' | awk '/^Threads:/{print $2}' || true)
    # Registered jobs only (JOB # lines), not every history/stats line that
    # happens to mention the package.
    JB=$("${ADB[@]}" shell dumpsys jobscheduler | grep -E '^ *JOB #' | grep -c "$APP" || true)
    case "${TH0:-x}.${TH1:-x}" in
      *[!0-9.]*)
        echo "  workers trial $t: unparseable thread count - trial invalid, excluded" >&2
        invalid=$((invalid+1)); continue ;;
    esac
    before+=( "$TH0" ); after+=( "$TH1" ); jobs_+=( "$JB" )
    echo "  workers trial $t: threads $TH0 -> $TH1 after $NAV nav cycles, registered_jobs=$JB"
  done
  [ "$invalid" -eq 0 ] || echo "  workers: $invalid invalid trial(s) excluded from statistics" >&2
  emit workers_threads_before_nav ${before[@]+"${before[@]}"}
  emit workers_threads_after_nav ${after[@]+"${after[@]}"}
  emit workers_registered_jobs ${jobs_[@]+"${jobs_[@]}"}
  row "workers_nav_cycles_per_trial,NA,,${#after[@]},\"$NAV\""
}

verify_seed_count() {
  # The -c label must match reality: an interrupted or partial seed would
  # otherwise be measured and recorded under the wrong contact count.
  local n
  n=$("${ADB[@]}" shell "content query --uri content://com.android.contacts/raw_contacts --projection _id --where \"account_type='measure.local' AND deleted=0\"" | grep -c 'Row:' || true)
  if [ "$n" != "$CONTACTS" ]; then
    echo "seed check failed: device holds $n synthetic contacts but this run is labeled -c $CONTACTS - reseed with scripts/measure/seed-contacts.sh $CONTACTS before measuring" >&2
    exit 1
  fi
}

echo "# mama-launcher T-04a baseline, app=$APP, checkout=$COMMIT (installed APK identity is on the env row), launch=$LAUNCH, trials=$TRIALS, contacts=$CONTACTS, nav=$NAV, cache_drop_probe_at_env" >> "$OUT"
ZERO_VALID=0
prep
for S in "${SCENARIOS[@]}"; do
  echo "== $S"
  [ "$S" = env ] || verify_seed_count
  case "$S" in
    env) env_row ;; cold) cold ;; warm) warm ;;
    frames) frames ;; heap) heap ;; workers) workers ;;
    *) echo "unknown scenario $S" >&2; exit 1 ;;
  esac
done
echo "rows appended to $OUT"
if [ "$ZERO_VALID" != 0 ]; then
  echo "FAILED: at least one scenario produced no valid samples - see NA rows in $OUT" >&2
  exit 1
fi
