# T-04a performance baseline protocol

Measure before optimizing. This document is the fixed protocol for the low-end
baseline that T-06 (contacts pipeline) and T-07 (async photo loading) must
beat. It covers the emulator-only part (T-04a); running the same protocol on
the optional non-production A10s (T-04b) is separately authorized by the owner.

## Pinned build (all numbers must come from this build)

- Flavor: **noNotif** (the distributed flavor).
- Build type: **profileable** (`:app:assembleNoNotifProfileable`) - `initWith
  release`, signed with the **local debug key**. Never the release key, never
  distributed, never mixed with debuggable builds (a debuggable build
  exaggerates main-thread cost; mixing build types invalidates comparisons).
- The profileable manifest flag (`android:shell="true"`) lets Perfetto/
  simpleperf attach on API 29+.

## Synthetic data

`scripts/measure/seed-contacts.sh COUNT` seeds COUNT synthetic contacts marked
`account_type=measure.local` (cleanup deletes only that set; idempotent).
Mix: short and long Arabic names, 1-3 numbers per contact, national / `+20` /
`0020` forms of the same number. Standard sizes: **50, 500, 2000** contacts.
Photos, a slow/failing provider, and corrupt/rotated streams are **pending
selections** (see below).

## Protocol

1. Record the environment block: `scripts/measure/measure.sh env` (device
   model, API, build fingerprint, display size, host).
2. Seed the target size: `seed-contacts.sh 500` (etc.).
3. Install the pinned build. `measure.sh` grants `READ_CONTACTS` and
   `READ_CALL_LOG` before timing - a fresh install would otherwise measure
   the permission/setup screen, not the contacts grid.
4. Timed starts target **`.MainActivity`** (the exported launcher/home entry,
   singleTask); `.PhoneActivity` is not exported.
5. For every scenario run **at least 5 trials** after the documented reset
   (force-stop, move focus to Settings - never HOME, which would relaunch
   Mama when it is the default launcher - and drop page caches when root is
   available) and report the **median** with all trial values kept:
   `measure.sh -t 5 -c 500 all` (`-c` records the seeded size on every row).
   `SETTLE` (default 3s) is the wait for the async contacts load before
   sampling frames/heap; raise it on slow emulators.

| # | Scenario | Metric | Command |
|---|----------|--------|---------|
| S1 | Cold start, main grid visible | TotalTime (ms) | `measure.sh cold` |
| S2 | Warm start from recents | TotalTime (ms) | `measure.sh warm` |
| S3 | Grid scroll (12 up / 6 down, display-relative swipes) | janky %, p90, p99 frame ms | `measure.sh frames` |
| S4 | Settled heap after start | total / native / java PSS KB | `measure.sh heap` |
| S5 | Retained workers after settle | thread count, scheduled jobs | `measure.sh workers` |

Manual-path scenarios (scripted taps are too fragile to trust): open a
multi-number contact confirmation, open History, open the dialpad, largest
system text scale pass. Run each after the same reset and record heap +
observed jank in the results table. Optional deep trace: `perfetto -c - --txt
-o /data/misc/perfetto-traces/t.pftrace` with `gfx`, `am`, `sched`, `dalvik`
data sources during S1/S3; keep the trace file next to the results.

## Results and target-to-beat

Baseline numbers land in the table below (one row block per environment).
The **median becomes the reference**; the target column is what T-06/T-07 must
beat, re-measured with the identical protocol and build pinning.

| Scenario | 50 contacts | 500 contacts | 2000 contacts | Target to beat |
|----------|-------------|--------------|---------------|----------------|
| S1 cold start (ms) | _pending_ | _pending_ | _pending_ | baseline -0% (no regression) |
| S2 warm start (ms) | _pending_ | _pending_ | _pending_ | baseline -0% |
| S3 janky frames (%) | _pending_ | _pending_ | _pending_ | strictly lower at 500/2000 |
| S4 settled total PSS (KB) | _pending_ | _pending_ | _pending_ | no growth > 10% at 2000 |
| S5 threads after settle | _pending_ | _pending_ | _pending_ | no unbounded growth |
| Main-thread photo decode (ms) | _pending_ (needs fixture harness) | | | **near zero** (T-07's core win) |

## Known limits

- Emulator numbers are not handset certification; they exist to make
  T-06/T-07 before/after comparisons objective. T-04b (optional
  non-production A10s) is the handset-shaped check and is separately
  authorized.
- A host without KVM runs the emulator slowly; absolute values shift, so
  comparisons must stay within one environment block.

## Pending selections (not in this PR, per the plan)

- **Test-only fixture/harness provisioning** (slow/failing contacts provider,
  arbitrary corrupt/rotated photo streams, photo seeding): separately selected
  prerequisite PR. Without it the photo and degraded-provider scenarios stay
  `_pending_`.
- **T-04b** non-production A10s run: separately authorized by the owner.
