# Notification dots and read-only history: review validation

2026-11-10. Feature branch based on `ae90b17fee08592bde4df2aac2104cd54049d506` (main after the docs-only PR11). This is not a packaged release. Version remains code16 / 0.14-pilot-candidate; no application APK, signing, upload, install, merge or stable-handset change is part of this work.

## Implemented contract

- App tiles on Home and Apps get a small outward red existence dot, white boundary, no app numeral. Phone independently gets a locale-aware missed-record count capped at 99+. The normal icon/name measurement contract is preserved.
- Optional protected notification setup explains Android's broad access honestly. Current-profile configured packages only, minimal in-memory key/package/group/summary metadata, system lifecycle/reconnect reconciliation and API26+ canShowBadge. Unknown invalidates stale marks. No text/extras parsing, notification cancellation, PendingIntent execution or new automatic speech.
- Read-only call history: Contacts by default on fresh entry/new intent, ordinary return preserves the tab, fixed full-width dialpad and Contacts/History below. The Phone-only back label collapses to an accessible back icon on short large-text windows so content retains scrolling room. Retry/recovery labels remain readable.
- Newest-first records, All/Missed filters, separate call-type labels, local date/time, optional current contact resolution, exact clicked number passed to existing call confirmation. Ambiguous/currently inaccessible contacts fall back to the number. Invalid/private values have no call path. Synthetic unknown contact IDs cannot use favorites.
- READ_CALL_LOG only. P is the complete current pending missed set (TYPE=MISSED, NEW=1, IS_READ IS NOT1), separate from 200-row display batches. First successful pending snapshot baselines old records. P minus local acknowledgements is the Phone count. Stock flag changes/deletion flow into Mama; successful foreground history display acknowledges only its corresponding earlier pending snapshot locally.
- Provider null/error/malformed/cancel/timeout/resource-limit states are not empty. A pending set over10,000 distinct identities is deliberately unavailable, not partially counted. Query reads have a5-second budget and the UI owner has a6-second timeout. An uncooperative provider may keep a worker blocked until it returns, but cannot publish after cancellation or timeout. Older queries cannot prune a newer acknowledgement.
- Local acknowledgement uses an immediate in-memory update and asynchronous preferences persistence; abrupt process termination before disk persistence may resurface a badge. It never silently clears the stock dialer. First baseline is committed off-main and errors cannot report success. All preferences remain excluded from backup.

## Completed automated checks

JDK11, platform33, Build Tools30.0.3, existing Gradle7.6.4/AGP7.4.2/Paparazzi1.2.0/Robolectric4.11.1. No wrapper, runtime dependency, framework, CI or version migration.

| Check | Result |
| --- | --- |
| App/UI Java + resources compilation | Pass |
| Full app JVM/Robolectric suite | 272 tests, 0 failures (206 existing +66 new executions) |
| UI/Paparazzi verification | 95 tests, 0 failures (88 existing +7 new) |
| App Android Lint | 0 errors,12 warnings |
| Standalone UI Android Lint | Fails:1 existing WrongConstant error,158 warnings |
| Same standalone UI lint on unchanged main | Same WrongConstant error,136 warnings |
| Semgrep public Java rules, metrics disabled | 53 production Java files,60 rules,0 findings,0 parse errors |
| Whitespace / privacy boundary source review | Pass, details below |

UI lint's error is the unchanged HomeLabel `Layout.BREAK_STRATEGY_SIMPLE` line. It was reproduced on main using the same SDK/JDK/toolchain, not suppressed or changed here. The22 additional warnings are20 library-only unused-resource reports (resources used by the app module) and2 ButtonStyle suggestions; lint is not claimed fully green. Existing min-API attribute warnings remain. App lint's warnings are not a proof of handset behavior.

One combined lint/Robolectric invocation exhausted the2GB runner (exit137). Sequential, single-worker full runs passed. The local formatter/security scanner are development-only and do not alter project dependencies.

### New regression coverage

- First baseline, later arrival, equal timestamps/distinct identities, reused identity discriminator, deletion/system clear, suppression pruning, restart-equivalent context and revoke/regrant baseline.
- Permission callback cancellation/empty result, denial/grant hint versus actual grant, late revoked state rejection, Contacts-first/no startup call-log query, Not now, ordinary resume versus new intent, pause/cache removal and provider failure not acknowledging.
- Complete pending versus200-row page, explicit next batch, successful empty/null cursor, malformed flags, canceled snapshot, bound missed filter, over10,000 pending unknown, older query versus newer acknowledgement, worker timeout and stale callback cancellation.
- Exact number opens confirmation only, unknown ID has no favorite; hidden/invalid/sentinel/service-like numbers cannot call. Separate incoming/outgoing/missed/rejected/blocked/other labels.
- API24/25/33 notification service tests: empty ready versus disconnected, missing ranking unknown on26+ versus guarded24/25 fallback, configured-package filtering, ongoing exclusion and access revocation. Store duplicate-key updates, summary-only/child existence, invalidation and tile accessibility ownership. Standard service/media/navigation/progress filtering.

### Visual review

Inspected actual layout-renderer pixels, not HTML concepts: normal/high-contrast dots on synthetic real-icon/chip forms, dot absent/present, Phone1/9/10/99+, long Arabic labels, ordinary history record number/type/date, permission/error states, short320dp large-text footer and recovery scrolling, LTR/RTL header. Only8 affected old golden images were updated after review;7 new images are synthetic. The no-badge Home/App renders remained unchanged. On the most constrained windows, long history rows and explanations scroll within bounded content; fixed controls remain reachable. This does not certify touch target feel, actual OEM font shaping or a screen reader.

### Privacy / safety review

Manifest adds READ_CALL_LOG and a non-exported system-bound listener protected by BIND_NOTIFICATION_LISTENER_SERVICE. No INTERNET, SMS, WRITE_CALL_LOG, microphone, READ_PHONE_STATE, accessibility service, default Phone role or boot receiver was added. Reads use ordinary call-log URI only. No writes, notification cancellation/content access, media downloads, PendingIntent sends, analytics, notification database, logging of private contents, real contacts/photos or secrets were added. DirectCallAction, TTS/session/PIN and dialpad execution code are unchanged. Notification descriptions belong to the tile and do not invoke Mama audio. Existing touch guard wraps history/tab/setup actions.

## Not yet verified, not passed

- No APK packaging, emulator or real-handset run/install was authorized or performed for this feature.
- Actual Samsung A10s Android11, Pixel/AOSP, Xiaomi/HyperOS, OPPO/realme/ColorOS, Huawei-compatible Android, dualSIM/carrier/provider pending-flag behavior and installer allowlisting remain untested.
- API28/29/30/31/32/34/35/36 runtime compatibility, low-RAM/policy/work-profile/no-telephony behavior, restricted-settings UI, real provider latency/revocation and notification reconnect delivery need device/runtime testing. Compiling and guarded Robolectric paths do not certify them.
- A true canShowBadge=false versus true channel/ranking transition, current-profile cross-user delivery and OEM notification grouping remain physical/system-integration checks. Missing ranking is covered, not full live ranking parity.
- TalkBack, Switch Access, keyboard/touch guard feel, actual font choices/system-largest text, short-height gesture/three-button inset variants, frame time/memory and audible pronunciation need the planned hands-on matrix. Synthetic normal/large/contrast/LTR/RTL pixels are narrower evidence.
- No automatic bidirectional stock-badge clearing, exact unread-message count or universal OEM support is promised. No carrier calls or contact edits were performed.

Next gate: owner reviews/merges the feature PR if satisfied. Any candidate version bump, APK creation/distribution or device installation needs a separate decision and the established same-key release checks. The installed0.14 baseline stays untouched.


## PR12 review follow-up

Reviewed the live inline findings on the initial91c34d4 head, not merely the check badges. Code fixes cover history position retention (identity plus pixel offset, saved list state and pre-draw restoration), country-aware exact contact matching with ambiguity refusal, explicit LTR number-only titles with recycled-name reset, Arabic plural forms for the Phone badge and exact versionName in build instructions. A shared NumberRegion helper preserves the existing dialpad region policy rather than duplicating it. No raw number is replaced for call confirmation.

Twelve new regression tests exercise focused draw acknowledgement, absent focus and later focus, tab/pause before posted acknowledgement, new call after snapshot, ordinary-return/load-more/provider-insertion scroll retention and filter reset, full national/international contact equivalence and ambiguity, recycled text direction, Arabic quantity categories and an unobserved revoke/regrant limitation.

Two findings are dispositioned without changing the approved behavior:

- Graphite discussion4528139022 asks to acknowledge only pending records within the displayed200-row page. The approved v5 plan explicitly says to query the COMPLETE CURRENT pending set independently of200-row display batches and acknowledge the completed corresponding pending snapshot on successful foreground history display, in either filter. Applying page-only acknowledgement would change that product decision. A draw/focus flow test now verifies240 pending records acknowledged after a200-row successful display and leaves a later concurrent call pending. This action is local only, not a claim that each record was individually read.
- Greptile discussion4528146895 correctly identifies a limit of current-state permissions: revoke and regrant while the app process is absent cannot be inferred from the later granted state. Baseline reset is guaranteed only after CONFIRMED/observed permission loss, as the approved plan specifies. A regression documents the unobserved path. No unsupported permission-history API, constant background service or broader reset-on-every-restart rule was added. If the family revokes and regrants entirely while Mama is not running, calls from that period can appear in its badge. A later process restart alone does not mean permission was revoked.

CodeRabbit's overall check passed but its optional documentation-coverage heuristic warned (5.06% against80% on79 touched functions, including tests). This is not a failed compile/test/security check; critical boundaries have class/method documentation and this record. No automated bulk docstring generation, paid review or CI/config change was run to satisfy a percentage. Sourcery initially skipped due review-budget limits; that skip is not an approval. Subsequent reviewer/check state is reported separately for the actual pushed head.
