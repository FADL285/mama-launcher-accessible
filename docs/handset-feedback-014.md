# 0.14 handset follow-up

## Causes and repairs

- Home cards: the fixed two-line TextView area had text at its top. A single-line label left an invisible empty line below the icon/name group. Cards now reserve equal height in a measurement pass, then center the actual one- or two-line content. The 6dp icon/name gap, two-line cap, ellipsis, accessible full name and long-press full-name dialog are retained.
- Word wrapping: fit the longest word from24sp down to an18sp floor, respecting the selected font scale. If it still cannot fit, show one ellipsized line rather than split the word. Exact الاستوديو largest-text regression added; full name remains available.
- Contacts setup: a fixed110dp button with30sp text clipped a wrapped Arabic label on the trial handset. The button now uses content height,110dp minimum and20dp padding. The setup region scrolls independently of the bottom dialpad action.
- Permission retry: the old `contacts_asked` flag was written before a result, then combined with a false rationale to jump to Settings. That does not distinguish a completed denial from an interrupted request. The Allow/Retry action now always requests the permission explicitly. A completed denial with no rationale offers a separate labelled Settings action and explains the retry/OS boundary. Empty callbacks do not record a denial; grants clear recovery state. Existing legacy flags do not force a redirect.

## Android limit

Android can suppress the system permission dialog after repeated denial or a fixed policy. The app cannot force another dialog in that state. Settings is a separate optional action, never the hidden meaning of Retry. Dialpad remains available without contacts access.

Official guidance: https://developer.android.com/training/permissions/requesting

## Verification

- Regression tests reproduced the legacy flag redirect, cancellation retry failure and off-center one-line group before the fixes.
- App:206 tests pass, including21 contacts-permission cases across API24,30 (Android11) and33: legacy migration, denial, cancellation, explicit Settings, dialpad without permission, unrelated callback and granted state.
- UI:88 tests pass; separate Paparazzi golden verification passes. Checked Arabic A10s largest-font renders, one-/two-line card peers, permission setup and short-viewport recovery scroll, standard and high contrast existing renders. All new fixtures are synthetic.
- Release assembly passes. Android lint:0 errors,11 warnings (same count as0.13). Diff whitespace check passes.
- Not verified on a physical Samsung A10s in this change. JVM permission tests do not emulate Samsung's actual permission dialog. No real call placed and no device permissions changed.

## Handset acceptance checks

1. Update the dedicated-key0.13 installation to0.14 without uninstalling; confirm saved settings remain.
2. Compare single-line and wrapped app labels: equal peer-card bounds, icon+name centered, small gap retained. Test biggest text and high contrast.
3. Fresh contacts permission: full Allow label, deny once, tap Retry. When the OS permits, it should show its dialog again. If Android suppresses it, Retry must not unexpectedly open Settings.
4. After blocked denial, open the separate Settings action, grant contacts, return. The grid should load. With no grant, dialpad stays reachable.
5. Use a short viewport and biggest text: scroll recovery content to its end without losing the bottom dialpad action.
