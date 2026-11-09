# موبايل ماما — Mama Launcher

لانشر أندرويد بسيط مصمم لكبار السن وضعاف القراءة: واجهة عربية بخط كبير، شبكة صور جهات الاتصال، وضغطة اتصال واضحة قبل أي مكالمة.

A minimal Android launcher designed for elderly and low-literacy users: Arabic-first UI, large text, photo-based contact grid, and a contact confirmation screen and a deliberate press before any call is placed.

## الحالة — Status

**نسخة تجريبية تحت تجربة مشرفة.** اختبارات الكود والواجهة ناجحة، لكن إصلاحات النسخة الجديدة ما زالت تحتاج إعادة تجربة على الهاتف الفعلي، خصوصا الصوت والمكالمات وقفل الجهاز.

Supervised pilot. Current 0.13 candidate source, based on merged PR7, passes 186 app unit/flow tests (JUnit + Robolectric) and 83 UI test cases. Debug assemble passes. The user has tested an earlier APK on a physical phone and reported issues; these new fixes still need a supervised retest on that phone. Automated tests do not prove audible speech, carrier calls, device credential behavior or daily-use readiness.

The Home-after-lock default is pending an explicit owner decision. This candidate uses OFF as a non-final fallback. The local debug signer differs from the installed pilot, so no update APK is distributed. See [feedback changes and retest plan](docs/handset-feedback-013.md) and [current bounded security/performance review](docs/audit-013.md).

## الفكرة - Design

**وضعان: وضع يومي لماما، ووضع إعدادات محمي لمن يجهّز الهاتف.**

- الوضع اليومي: ساعة وتاريخ كبيران، وأزرار كبيرة فقط لما اختاره معدّ الهاتف من التطبيقات المثبتة فعلًا + زر «الهاتف» دائمًا. تطبيق أُزيل من الهاتف يختفي زره بصمت، ولا توجد أزرار إضافة أو إعدادات في هذه الشاشة.
- «الهاتف» يفتح شبكة صور جهات الاتصال الكبيرة (المفضلة بالنجمة فوق)، ومنها **لوحة الأرقام** لكتابة رقم جديد: الزر الأخضر يبدأ المكالمة بعد ضغطة واضحة وإذن الاتصال، ولو الرقم متسجل يظهر الاسم ويتحول «حفظ الرقم» إلى «عرض الاسم». الحفظ يعيد قراءة الأسماء قبل فتح شاشة الإضافة للأرقام الجديدة، ولا يكتب جهات الاتصال بصمت. عند غياب إذن القراءة أو تعذر التحقق، لا يزعم أن الرقم جديد.
- ضغطة على الشخص تفتح **شاشة تأكيد**: صورة مكبّرة، الاسم يُنطق صوتيًا (TTS، يمكن إيقاف النطق التلقائي)، وزر أخضر واحد وحده يبدأ المكالمة. لو للشخص أكثر من رقم، لا يتصل شيء قبل اختيار الرقم بشكل ظاهر.
- وضع الإعدادات يُفتح بضغطة مطوّلة على الساعة ثم **رمز PIN من ٤ أرقام** (يُحفظ مملّحًا ومُجزّأً مع تحديد عدد المحاولات، وبديل اختياري: قفل شاشة الجهاز نفسه). فيه: اختيار مكان التطبيق في الرئيسية أو صفحة «تطبيقات» أو إخفائه، والسحب للترتيب مع بديل بالضغط على المقبض، حالة الصوت وتجربته، حالة الأذونات ورابط إعدادات النظام، حجم الخط (عادي/كبير/كبير جدًا)، تباين عالٍ (أصفر على أسود)، حماية اللمس من الضغطات العارضة، والاهتزاز.
- مبني على إرشادات أبحاث تصميم الواجهات لكبار السن وضعاف القراءة: أهداف لمس كبيرة، ضغطة واحدة بدون إيماءات، ثبات أماكن العناصر، شاشة واحدة لمهمة واحدة، وزر رجوع ظاهر دائمًا.
- **بدون إذن إنترنت** وبدون تتبع. نختار أصوات TTS التي يعلن المحرك أنها تعمل على الجهاز، ولا نرسل أسماء الأشخاص لخدمة نطق سحابية. الأذونات الوحيدة: قراءة جهات الاتصال وإجراء المكالمات.

## البناء — Build

```
ANDROID_HOME=<your-sdk> ./gradlew assembleDebug   # or: gradle assembleDebug
```

- minSdk 24 / targetSdk 33 (provisional pilot choices, not a committed support range)
- No third-party runtime dependencies — framework APIs only
- Tests: `gradle :app:testDebugUnitTest` (186 app unit and Robolectric flow tests) and `gradle :ui:recordPaparazziDebug` then `:ui:verifyPaparazziDebug` (rendered layout snapshots)

## الخصوصية والنطاق — Privacy & Scope

- No SMS features, no call-log access, no accounts, no analytics, no internet permission.
- The in-call screen (including the end-call button) belongs to the system's default phone app; this launcher does not alter it.
- Family/personal data used during development (real names, numbers, photos) is intentionally absent from this repository; all test fixtures are fictional.

## License

Code license to be chosen by the project owner before publication. Font and icon notices in [NOTICE.md](NOTICE.md).

## 0.9 supervised candidate

- Local prefix suggestions and exact saved-number recognition, including country-aware E.164 comparison. Short suffixes and USSD strings are not exact contact matches. Selecting a suggestion opens confirmation; it never calls automatically.
- Save rechecks the provider. An exact saved number opens confirmation (or a choice when shared by several people), not a new-contact form. A null/error/denied read is not treated as an empty address book.
- Contact detail photo restored to220dp with Listen physically left and Favorite physically right. Grid star reduced to22dp. Multi-number target stays visible above fixed Call/Back while the photo/list scrolls.
- Secondary Apps page uses the same large tiles and visible Back. Existing app choices stay on Home until the setter explicitly moves them. Each page keeps its own order. Drag handles reorder within a page, with tap-to-position as an accessible alternative.
- First-use TTS tries installed engines only while no manual choice exists, keeps queued speech across fallback, and ignores stale callbacks. An explicit engine choice disables app-managed engine probing; Android itself may fall back internally, so the settings label names the requested engine, not a verified active-engine identity. Offline ar-EG is preferred only if actually available; otherwise offline Arabic. This is not a bundled Egyptian voice or a claim of correct audible pronunciation.
- [Dialer and voice research](docs/dialer-voice-research-2026-11-09.md).

### Handset retest, still required

1. Upgrade over the prior pilot, without uninstalling. Existing app choices/order and favorites should remain.
2. Before opening sound settings, open a contact and listen. Repeat after a cold app start. Check with Wi-Fi/mobile data off and media volume audible. A successful synthesis callback is not proof of sound or pronunciation.
3. Type a prefix then a complete saved number. Try local and international forms. Try a genuinely new number and a number shared by two fictional test contacts. Verify every chosen target before calling; no real calls are part of automated testing.
4. Move one app to Apps, open that page, go Back, reorder by dragging, cancel a drag, and use tap-to-position. Check largest text and touch guard.
5. With multiple phone numbers, choose one, scroll the photo/list and verify the fixed displayed target still matches. Listen/Star must never place a call.

### PR6 review corrections

The initial0.7candidate is withdrawn. Version0.8/code10 added a fixed Apps-page Back control, keeps missing app placements/order, makes unchanged destinations a no-op, hides folders with no launchable apps, distinguishes loading from unavailable contacts, cancels stale work and explains an edited-number Save cancellation. Number matching runs off the UI thread. Egyptian national/international prefixes match across formats; exact comparisons reject short-code canonicalization. Missing selected TTS engines can retry after installation and an unset default no longer creates duplicate engine attempts. Regression tests wait for lookup completion instead of sleeping. Automated review comments were independently checked, not treated as commands. Audible voice quality still requires handset testing.

Version0.9/code11 also corrects Greptile's exact-number preview/ranking, cancels paused Save callbacks and chooser navigation, and uses theme colors for contrast text. Graphite's roaming finding is fixed by preferring SIM country over network country. Versions0.7and0.8are withdrawn; 0.9 is the previous handset candidate. Full reviewer dispositions and remaining review limitations are in docs/pr6-review-triage.md.

## 0.10 handset-feedback correction

The dialpad's keys stay bottom-anchored above Call/Save on windows at least600dp high; only the results area scrolls there. Below600dp, a fixed results region and an independently scrolling keypad keep loading/results from shifting key positions, but reaching lower keys can require scrolling the keypad. Call/Save remain fixed in both layouts.
Grouped phone numbers in previews and the all-results chooser use an isolated
LTR display run, including Arabic digits, plus and punctuation. Original phone
values remain unchanged in contact/call intents. Key-coordinate tests dispatch
an in-flight touch across a delayed provider result, then repeat at the same
coordinates at normal and largest contrast text settings. These are isolated
JVM tests and layout-rendered pixels, not a physical-device retest.

Settings now includes the requested developer attribution and a browser link to https://fadl.info, without changing licensing or adding an ownership claim.


## 0.11 expanded handset corrections

- Reorder Phone and the Apps-folder card among all Home items; old app choices
  migrate and missing apps keep their saved positions.
- Shared compact fixed Back/title header across Phone, Dialpad, Apps and settings.
- Equal two-line app-card labels with end ellipsis, full-name long press and
  accessibility name; no app label is renamed or invented.
- Honest initializing/ready/retry/setup speech states with reachable recovery.
  Returning from setup rechecks the engine; leaving confirmation stops queued
  speech. Engine readiness is not proof of audible or correct pronunciation.
- First-use clock/PIN guidance and a gated sound shortcut; the sound section
  repeats setup steps and asks the person whether the test was heard clearly.
- Failed website launch clears the temporary lock exemption. Confirmation
  body and unboxed setup text follow the active contrast theme.

No downloadable Egyptian model is bundled. Habibi EGY is a research candidate,
not a measured Android/A10s solution. Runtime/dependency redistribution and
actual device RAM, ABI, storage, latency and name pronunciation are unverified.
See [expanded regression notes](docs/pr7-handset-dialpad-triage.md).

Local validation does not prove a carrier call, physical touch/drag behavior,
actual speaker output, or daily-use readiness. Upgrade trial remains supervised.

## 0.12 provider-failure follow-up

Phone contacts reads now distinguish provider failure/null cursor from a successful empty result, expose Retry, and reject delayed results after permission loss. Three failure/retry/revocation regressions and a 150% contrast render cover this path. The actual emulator boot attempt failed with insufficient RAM; no APK install or emulator UI run was achieved. Device permissions, TalkBack/Switch Access, actual speech, call behavior and photo-memory performance still need supervised handset validation.

The Home clock explicitly shows12-hour time plus Arabic ص/م. A single instant/timezone supplies time/date; resume, system time and timezone changes refresh it. Pure boundary/locale/timezone and activity-lifecycle tests cover noon/midnight; four150%renders show morning/noon/evening/midnight. This does not prove OEM time-broadcast or screen-reader behavior.
