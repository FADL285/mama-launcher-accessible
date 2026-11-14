# Contributing

Thanks for helping make phones easier for mothers and seniors. This file is
the short version of how work lands here.

## Ground rules

- **Users first.** Every change is judged against one question: does this
  make the phone simpler or safer for an older adult, or for the family
  member who sets it up? Features that add daily-use complexity need a very
  good reason.
- **No real personal data, ever.** Real contacts, photos, names, numbers,
  credentials and signing keys do not belong in this repository - not in
  tests, fixtures, docs or screenshots. Fixtures are synthetic
  (`FixtureContactsProvider` is the model).
- **English for code, commits and developer docs.** User-facing strings stay
  Arabic, and the family guide stays in Arabic (`docs/family-guide.ar.md`).
- **Small, reviewable PRs.** One task per PR, one commit per task where
  practical. The owner merges; nobody else pushes to `main`.

## Before you push

All gates must be green on your branch:

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:testNoNotifDebugUnitTest :app:testFullDebugUnitTest
./gradlew :ui:testDebugUnitTest :ui:verifyPaparazziDebug
./gradlew :app:lintNoNotifDebug :app:lintFullDebug :ui:lintDebug
```

- The app has two flavors: `full` (notification dots via special
  notification access) and `noNotif` (no notification listener). Changes
  usually need to pass for both.
- `verifyPaparazziDebug` compares committed golden screenshots. If a golden
  fails, either the change is a bug (fix the code) or an intended visual
  change (say so explicitly in the PR and regenerate deliberately). Never
  regenerate goldens just to make a failure disappear.
- Robolectric tests run on the JVM; they do not prove handset behavior.
  Device-affecting claims (audio, TalkBack, frame times) need a device pass,
  not just green JVM tests.

## Conventions that bite

- **Failure is never "empty".** A failed contacts/call-log read must surface
  as a retryable error, never as an empty list. See `ContactsLoader` and
  `CallLogRepository` for the pattern (CancellationSignal + named deadline).
- **Identity over position.** Adapter positions are not identity - grid rows
  recycle. Photos and async results bind by contact id + URI + size
  (`ContactPhotoLoader`).
- **Deliberate actions only.** Placing a call requires a visible, deliberate
  tap. No implicit calls, no silent contact writes.
- **Reviewer bots are suggestions, not authority.** Verify every automated
  review claim against the source before changing code for it.

## Where things live

- `app/`: activities, permissions, contacts/dial logic, setup, PIN gate, TTS.
- `ui/`: shared layouts, binding, photo loading, Paparazzi tests.
- `docs/`: architecture snapshot, build/test guide, family guide (Arabic),
  dated audits and validation records.
- `docs/performance-baseline.md`: how to measure before/after performance
  claims (never on a personal phone).

## License

By contributing you agree your contribution is licensed under the project's
[Apache License 2.0](LICENSE).
