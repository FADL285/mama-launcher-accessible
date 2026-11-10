# موبايل ماما | Mama Launcher

لانشر أندرويد عربي بسيط لكبار السن وضعاف القراءة: صور كبيرة لجهات الاتصال، أزرار واضحة، وإعدادات يحميها رمز PIN لمن يجهز الهاتف.

An Arabic-first Android home screen for older adults and people who find reading difficult. It keeps daily actions small in number, uses large contact photos and text, and separates family setup from everyday use.

## Current status

**0.14 is the stable handset handoff baseline.** Its internal version string is still `0.14-pilot-candidate` (version code `16`). This is a project handoff decision, not a certification of every Android device or accessibility service. No new runtime changes are part of the documentation/audit follow-up.

- Current source: merged [PR #10](https://github.com/FADL285/mama-launcher-accessible/pull/10), commit `46c966d16575edbfb3aa86cc99b559a4b6832145`.
- Fresh baseline checks on 2026-11-10: **206 app tests + 88 UI tests**, zero failures; Android Lint **0 errors / 11 warnings**; Semgrep **0 findings** in 36 production Java files with 88 applicable rules.
- Those checks are JVM/static/layout checks. They do not measure handset frame times, memory, carrier calling, audible pronunciation, TalkBack or Switch Access.
- Keep the stable installation while considering improvements. See the [ranked audit and proposed next steps](docs/audit-014.md). Nothing there is an approved code change.

## What it does

| Daily use | Family setup |
| --- | --- |
| Large 12-hour clock, date and chosen installed apps | Long-press the clock, then enter the four-digit PIN |
| Phone tile with photo contacts and favorites first | Choose Home, Apps page or hidden placement; reorder items |
| Contact confirmation with spoken name and explicit Call | Text size, high contrast, touch guard, vibration and speech settings |
| Dialpad with local saved-number suggestions | Permission and installed TTS-engine setup |
| Visible Back actions and optional Home after screen lock | Optional device-credential recovery when configured |

A contact with several numbers needs a visible number choice before calling. A direct dialpad call needs a deliberate green-button press. Granting call permission alone never places a call. Save Number opens the system contacts form or shows an existing match; the launcher does not silently write contacts.

In 0.14, Home centers the visible icon/name group inside equal-height cards. Long words fit down to a readable floor or are ellipsized, rather than split. Contacts permission has an explicit Retry and a separate optional Settings action; Android can still suppress its own prompt after repeated denial.

## Privacy and limits

- The manifest requests only `READ_CONTACTS` and `CALL_PHONE`. No internet permission, analytics SDK, accounts, SMS or call-log access.
- Speech chooses Arabic voices the installed TTS engine reports as offline. The engine is a separate app; this is not an independent guarantee about its implementation. No Egyptian speech model is bundled.
- Favorites and setup preferences are app-local. Backup is disabled in the manifest and backup rules exclude app data. Uninstalling or clearing storage loses that configuration.
- The settings PIN discourages accidental or casual changes. It is not device security, kiosk enforcement or protection against a rooted/compromised device.
- The system phone app owns the in-call screen and End Call. This launcher does not replace it.
- Portrait phone layouts are the current baseline. Tablet, landscape, foldable and calling support need a separate evaluation.
- Real contacts, photos, credentials and signing keys do not belong in this repository. Test fixtures are synthetic.

## Get started

- **تجهيز الهاتف والاستخدام وحل المشاكل:** [دليل الأسرة بالعربي](docs/family-guide.ar.md)
- **Develop/build/test:** [Build and test guide](docs/build-and-test.md)
- **Understand the code:** [Architecture and invariants](docs/architecture.md)
- **Review improvements before changing anything:** [0.14 audit](docs/audit-014.md)
- **Older changes:** [Documentation index and history](docs/README.md)

```bash
# JDK 11, Android SDK platform 33 and Build Tools 30.0.3 installed
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :ui:verifyPaparazziDebug :app:lintDebug
```

`verifyPaparazziDebug` checks committed golden images. Do not regenerate them just to hide a failure. `assembleRelease` produces an **unsigned** APK in this repository; a debug APK is not an update for the dedicated-key release installation. Read [release continuity](docs/build-and-test.md#release-continuity) before installing anything over a stable build.

## Project shape

- `app/`: activities, permissions, contacts/number logic, setup preferences, PIN/session gate and TTS coordination.
- `ui/`: shared layouts, rendering/binding, photo loading and Paparazzi tests.
- Runtime implementation: Java + Android framework Views. Test/build tools are not shipped as app runtime libraries.
- Configured minimum API 24; compile/target API 33. These settings are not a promise of validation on every supported OS/device.

## License and credits

No project-wide code license has been chosen or included. Public source visibility is not a license grant. The owner must choose one before offering broader reuse. Font/icon and test-tool notices are in [NOTICE.md](NOTICE.md).
