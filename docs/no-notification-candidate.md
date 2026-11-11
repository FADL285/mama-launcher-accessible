# Notification-free candidate, version20

The delivered candidate is `noNotifRelease`: versionCode20, versionName `0.15-no-notif-footer-candidate`, package `info.fadl.mamalauncher`, minSdk24 and targetSdk33. It uses the same dedicated release certificate as18/19. Build with `:app:assembleNoNotifRelease`; a generic full-flavor build is not this candidate.

Version19 can be reproduced from commit `55e9901e45d1ecec54e3deb3f5e8fd62c99397af`; current branch commands produce20. Version20 removes inherited Button elevation animation and sets elevation/translationZ to zero for styled phone controls. This fixes their clipped bottom shadows without changing footer layout, spacing, fills or pressed/focus feedback.

## Capability boundary

The noNotif flavor removes `LauncherNotificationListener` from the merged manifest entirely. No `BIND_NOTIFICATION_LISTENER_SERVICE` or `NotificationListenerService` action remains. Runtime capability guards refuse stale grants, prevent opening notification-access settings and hide both the grant control and the inapplicable restricted-settings instructions. An Arabic explanation replaces them. App notification dots are absent; read-only call history, its permission flow and the separate locally acknowledged missed-call count are unchanged. No stock flags are written.

The full flavor preserves the original listener and behavior, with version metadata inherited from main. This is a real capability reduction in noNotif, not a hidden listener or installer-source workaround. `READ_CALL_LOG` remains declared and may still be restricted or denied by Android, installer or OEM. There is no claim of Play distribution eligibility or guaranteed Play Protect acceptance.

## Phone/history UI

- Decorative direction symbols accompany existing call-type words. Outgoing, incoming, missed and rejected/blocked have distinct shapes; unknown types have no guessed icon. Row speech labels are unchanged and icons are excluded from accessibility focus.
- Filters have a10dp gap. History has12dp top/bottom padding and10dp transparent card separators instead of default grey stripes.
- Phone controls use18dp corners, pale-blue secondary fills and explicit pressed/focus states. Cards have a light1dp edge and restrained card elevation in normal mode; phone controls are flat. High contrast keeps explicit yellow boundaries rather than relying on shadows.
- All and Missed remain the only filters. Footer navigation, permission behavior and call actions are unchanged.

## Validation and limits

Local checks on the delivered production source: noNotif278passed/12full-listener-only skipped, full281passed/9noNotif-only skipped, UI106passed. App lint has0errors/12warnings; UI lint has0errors/156warnings. Normal,1.5font RTL normal/contrast, international-number and recycled-row renders were inspected using synthetic names/numbers only. Automated row tests cover API24/25/33.

The delivered signed APK is257829bytes; SHA256 `eb8e9725b6706de3aff3ef7522432d8b872204b1864f7958fb5fb9c6aa470857`. Its64 archive entries match the unsigned build output byte-for-byte. Signatures v2/v3 verify; certificate SHA256 `5c77e65030a4d8c0a69398a3e56ec2e5be529ef54f25adada442f2a6154582d8`. No signing material is committed.

Version20 has not yet been verified on a handset, with live TalkBack or against provider/OEM behavior. Synthetic renders and local tests are not handset compatibility evidence. The repository currently has no GitHub Actions workflow; local validation is not a remote CI pass.
