# T-04a performance baseline protocol

Measure before optimizing. This document is the fixed protocol for the low-end
baseline that T-06 (contacts pipeline) and T-07 (async photo loading) must
beat. It covers the emulator-only part (T-04a). Running the same protocol on
the optional non-production A10s (T-04b) is a separate owner authorization
that is still an open decision - nothing here assumes it.

## Pinned build (all numbers must come from this build)

- Flavor: **noNotif** (the distributed flavor).
- Build type: **profileable** (`:app:assembleNoNotifProfileable`) - `initWith
  release`, signed with the **local debug key**. Never the release key, never
  distributed, never mixed with debuggable builds (a debuggable build
  exaggerates main-thread cost; mixing build types invalidates comparisons).
- The profileable manifest flag (`android:shell="true"`) lets Perfetto/
  simpleperf attach on API 29+.
- `PhoneActivity` is exported **in the profileable build type only**
  (`app/src/profileable/AndroidManifest.xml`) so timed starts can reach the
  contacts grid directly. Production build types keep it unexported; there is
  no release behavior change.
- The `env` row records the installed APK's versionName/versionCode and the
  repo commit, so every number is tied to an exact binary, not a flavor name.

## Synthetic data

`scripts/measure/seed-contacts.sh COUNT` seeds COUNT synthetic contacts marked
`account_type=measure.local` (cleanup deletes only that set; idempotent).
Mix: short and long Arabic names, and three phone-number cases:
- every **5th** contact: a **genuinely different** second number - real
  multi-number contacts for the confirmation workload, on any device region;
- every **7th**: a `+20` form, every **13th**: a `0020` form **of the first
  number** - equivalent-forms load for the T-17 dedup path. This case is
  region-dependent by design: with an Egyptian resolved region the forms
  merge, elsewhere they stay separate. The `env` row records the resolved
  region inputs (SIM country, network country, locale) so runs are comparable.

Standard sizes: **50, 500, 2000** contacts. Seeding is ~5 `content` calls per
contact, each a fresh on-device process: **2000 contacts takes roughly 30+
minutes**. Seeding fails loudly when exact account-scoped name/phone row
counts do not match the expected values (the on-device `content` CLI swallows
provider errors, so this count check is the real guard).

Photos, a slow/failing provider, and corrupt/rotated streams are **pending
selections** (see below).

## Protocol

1. Record the environment block: `scripts/measure/measure.sh env` (device
   model, API, build fingerprint, display size, region, cache-drop
   capability, APK identity, repo commit, host).
2. Seed the target size: `seed-contacts.sh 500` (etc.).
3. Install the pinned build. `measure.sh` grants `READ_CONTACTS` and
   `READ_CALL_LOG` before timing - a fresh install would otherwise measure
   the permission/setup screen, not the contacts grid.
4. Timed starts target **`.PhoneActivity`** (the contacts grid - the workload
   T-06/T-07 change), exported only in this build type. To also time the home
   screen, run with `LAUNCH=info.fadl.mamalauncher/.MainActivity`.
5. Every timed start records the platform-reported `LaunchState`
   (COLD/WARM/HOT). A cold trial that does not report `COLD` fails loudly -
   it means something (e.g. Mama as the default home) relaunched the app.
   Warm trials record the reported state as-is (raising a live singleTask
   entry reports HOT, not WARM).
6. Start latency is `TotalTime` (first frame), which does **not** include the
   background contacts load (`PhoneActivity.refresh()` runs on a worker
   thread). The script therefore also records `grid_ready_s`: a coarse (~1s)
   `uiautomator` poll from launch until a contact tile exists in the window
   (i.e. launch-to-populated-grid, including TotalTime). Precise
   fully-drawn timing would need a one-line `reportFullyDrawn()` in
   `PhoneActivity` - a production change and therefore an **owner decision**,
   not part of this kit.
7. For every scenario run **at least 5 trials** after the documented reset
   (force-stop, move focus to Settings - never HOME, which would relaunch
   Mama when it is the default launcher - and drop page caches when root is
   available; the probe result is recorded as `cache_drop=yes|no` on the `env`
   row, because Google Play emulator images have no `su`) and report the
   **median and IQR** with all valid trial values kept:
   `measure.sh -t 5 -c 500 all` (`-c` records the seeded size on every row).
   `SETTLE` (default 3s) is the extra wait after start before heap/worker
   sampling; raise it on slow emulators. Trials that cannot be measured
   (dead process, unparseable dump, process restart mid-scenario) are
   **excluded, never recorded as zero**,
   and the valid-trial count is recorded per row. The **warm** scenario is the
   one exception to the per-trial reset: by design it keeps the process alive
   and only moves focus away and back.
8. CSV columns: `timestamp,contacts,metric,median,iqr,valid_trials,"all valid
   values"[,extra]`.

| # | Scenario | Metric | Command |
|---|----------|--------|---------|
| S1 | Cold start to populated contacts grid | TotalTime (ms) + grid_ready (s), LaunchState-checked | `measure.sh cold` |
| S2 | Warm start from recents | TotalTime (ms), reported LaunchState recorded | `measure.sh warm` |
| S3 | Grid scroll (12 up / 6 down, display-relative swipes) | janky %, p90, p99 frame ms | `measure.sh frames` |
| S4 | Settled heap after start | total / native / java PSS KB | `measure.sh heap` |
| S5 | Retained workers across repeated navigation (10 in-process Phone->Home cycles per trial) | threads before vs after, registered jobs | `measure.sh workers` |

S3's percentile parsing anchors on the CPU `90th/99th percentile:` lines
exactly (the GPU lines and the label digits must not leak into samples).
S5 deliberately keeps one process alive across navigation cycles: a
force-stop-per-sample design cannot detect per-visit worker leaks, and the
jobs metric counts only registered `JOB #` lines, not every dump line that
mentions the package.

Manual-path scenarios (scripted taps are too fragile to trust): open a
multi-number contact confirmation (guaranteed by the every-5th fixture),
open History, open the dialpad, largest system text scale pass. Run each
after the same reset and record heap + observed jank in the results table.
Optional deep trace: `perfetto -c - --txt
-o /data/misc/perfetto-traces/t.pftrace` with `gfx`, `am`, `sched`, `dalvik`
data sources during S1/S3; keep the trace file next to the results.

## Results and target-to-beat

Baseline numbers land in the table below (one row block per environment).
The **median becomes the reference**, recorded with its IQR. Because medians
of 5 emulator trials typically drift 5-10% between sessions, a bare "no
regression" target would pass or fail on noise. Rules for T-06/T-07:

- Regression limit: the candidate median must not exceed the baseline median
  by more than **max(5%, 2 x baseline IQR)**.
- Candidates are re-measured **A/B in the same session** as a fresh baseline
  run (same emulator, same seed), never against numbers recorded days
  earlier.

| Scenario | 50 contacts | 500 contacts | 2000 contacts | Target to beat |
|----------|-------------|--------------|---------------|----------------|
| S1 cold start (ms) | _pending_ | _pending_ | _pending_ | no regression beyond noise band |
| S1 grid ready (s) | _pending_ | _pending_ | _pending_ | strictly lower at 500/2000 (T-06's core win) |
| S2 warm start (ms) | _pending_ | _pending_ | _pending_ | no regression beyond noise band |
| S3 janky frames (%) | _pending_ | _pending_ | _pending_ | strictly lower at 500/2000 |
| S4 settled total PSS (KB) | _pending_ | _pending_ | _pending_ | no growth > 10% at 2000 |
| S5 threads after nav (before -> after) | _pending_ | _pending_ | _pending_ | no unbounded growth across cycles |
| Main-thread photo decode (ms) | _pending_ (blocked on the fixture-harness owner decision below) | | | **near zero** (T-07's core win) |

## Known limits

- Emulator numbers are not handset certification; they exist to make
  T-06/T-07 before/after comparisons objective. T-04b (optional
  non-production A10s) is the handset-shaped check and awaits a separate
  owner authorization.
- A host without KVM runs the emulator slowly; absolute values shift, so
  comparisons must stay within one environment block.
- `grid_ready_s` is a coarse poll (~1s resolution), not a fully-drawn
  timestamp; see protocol step 6 for the precise option.

## Pending selections (not in this PR, per the plan)

- **Test-only fixture/harness provisioning** (slow/failing contacts provider,
  arbitrary corrupt/rotated photo streams, photo seeding): separately selected
  prerequisite PR. This is an **owner decision that blocks the T-07 photo
  baseline row above**; without it the photo and degraded-provider scenarios
  stay `_pending_`.
- **`reportFullyDrawn()` one-liner** in `PhoneActivity` for precise
  fully-drawn start timing: production change, owner decision.
- **T-04b** non-production A10s run: separate owner authorization, still an
  open decision.
