# Handset feedback review: 0.13 candidate

Date: 2026-11-10. Base: `c239668040c7231c10752bf01b034b0fae2d1a09` (merged 0.12 source).

## Changes

- F01: top-align short icon labels within the existing equal two-line tile area. This shortens the visible icon/name gap without moving neighboring tiles.
- F02: split the clock into two views, with the smaller Arabic period marker physically left of the digits. Keep a single full clock accessibility description.
- F03: reset Home scroll on Home reselect and on return from the Apps folder. Ordinary resumes do not reset Home.
- F04: add 24dp start/end padding to confirmation Call and Back actions.
- F05: balance the measured Back width with an equal invisible side. On narrow/large-text layouts, Back and a full-width centered title use separate rows.
- F06: add a persistent Home-after-screen-lock switch. It applies to Apps, Phone and Dialpad, waits for an interactive/unlocked screen, preserves pending navigation over saved-state recreation, and ignores screen-off while already paused in another app.

F06 is **ON by default**, as chosen by the owner on 2026-11-10. The family setter can turn it OFF in accessibility settings. Call confirmation keeps its prior behavior. Protected settings/PIN already lock and return Home on screen-off independently of this preference; this change does not weaken that behavior.

No speakerphone feature, Egyptian TTS integration, new external service, real call, device mutation, or private contact fixture is included.

## Validation

- 186 app tests pass, including 16 new feedback-navigation/preference cases.
- 84 UI test cases pass. New coverage includes physical clock ordering in LTR/RTL, button padding and minimum action heights, viewport title centering in both directions, and the settings switch.
- Paparazzi snapshot recording followed by a separate verification pass. Synthetic screenshots inspected: the new cases, all 66 changed baseline renders, large text, high contrast, short dialpad windows, selected/unselected multi-number calls and setup/error paths.
- Debug build and Android Lint pass. Lint: 0 errors, 11 warnings. See `audit-013.md` for the warning breakdown and limits.
- `git diff --check` passes.

These are local JVM/layout tests, not an Android emulator or Samsung handset run. OEM lifecycle behavior, TalkBack, real TTS output, permissions and calls still require supervised device checks. No calls were made.

## Before distribution or merge

1. F06 default ON is confirmed. Review the setting and test both choices on the handset.
2. Review the PR and merge manually. No automatic merge.
3. The old pilot key could not be recovered. The owner approved a fresh install. A dedicated key now signs the 0.13 release candidate, with a private, download-verified backup; the APK remains held for delivery.
4. Before the approved reinstall, record launcher preferences, selected apps/order, favorites and TTS/UI choices on the handset, then set a new PIN after reinstall. Contacts and the separate TTS engine are not launcher app storage.
5. Supervised handset checklist: Home reselect from a scrolled page; Apps Back and Home; screen off/unlock on Apps, Phone and Dialpad with the switch both ways; permission/system-screen and call-confirmation flows; PIN/settings screen-off lock; largest text; TalkBack clock/header; and existing contact/TTS flows. Verify no auto-call after granting permission.

## Reproduce locally

Use Java 11 and Android SDK 33. Set `ANDROID_HOME` to the SDK location.

```sh
./gradlew :app:testDebugUnitTest :ui:verifyPaparazziDebug :app:lintDebug :app:assembleDebug
```

Snapshot regeneration is a deliberate review operation, not a replacement for verification:

```sh
./gradlew :ui:recordPaparazziDebug
./gradlew :ui:verifyPaparazziDebug
```
