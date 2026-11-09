# موبايل ماما — Mama Launcher

لانشر أندرويد بسيط مصمم لكبار السن وضعاف القراءة: واجهة عربية بخط كبير، شبكة صور جهات الاتصال، وتأكيد واضح قبل أي مكالمة.

A minimal Android launcher designed for elderly and low-literacy users: Arabic-first UI, large text, photo-based contact grid, and an explicit confirmation screen before any call is placed.

## الحالة — Status

**نسخة تجريبية مبكرة (pilot).** الكود يُبنى وتنجح اختباراته (وحدة + لقطات شاشة معروضة عبر layoutlib)، لكنه **لم يُختبر بعد على جهاز حقيقي**. التوافق الفعلي يتأكد بالتجربة على الأجهزة.

Early pilot. Builds cleanly, unit tests and layout-rendered snapshot tests pass (see `ui/src/test/snapshots/images/`), **but it has not been tested on a real device yet**.

## الفكرة — Design

- الشاشة الرئيسية: شبكة جهات اتصال كبيرة بالصور؛ المفضلة (المعلَّمة بنجمة في جهات الاتصال) مثبتة فوق، ومن لا صورة له يظهر باسمه الكبير على بلاطة ملوّنة.
- ضغطة على الشخص تفتح **شاشة تأكيد**: صورة مكبّرة، الاسم يُنطق صوتيًا (TTS)، اسم كبير، وزر أخضر كبير واحد هو وحده الذي يبدأ المكالمة. لو للشخص أكثر من رقم، لا يتصل شيء قبل اختيار الرقم بشكل ظاهر.
- أزرار كبيرة لفتح واتساب وماسنجر ويوتيوب بأيقوناتها الحقيقية كما هي مثبتة على الجهاز.
- مبني على إرشادات أبحاث تصميم الواجهات لكبار السن وضعاف القراءة: أهداف لمس كبيرة، ضغطة واحدة بدون إيماءات، ثبات أماكن العناصر، شاشة واحدة لمهمة واحدة، وزر رجوع ظاهر دائمًا.
- خصوصية كاملة: **بدون إذن إنترنت أصلًا**، بدون تتبع، كل البيانات محلية على الجهاز. الأذونات الوحيدة: قراءة جهات الاتصال وإجراء المكالمات.

## البناء — Build

```
ANDROID_HOME=<your-sdk> ./gradlew assembleDebug   # or: gradle assembleDebug
```

- minSdk 24 / targetSdk 33 (provisional pilot choices, not a committed support range)
- No third-party runtime dependencies — framework APIs only
- Tests: `gradle :app:testDebugUnitTest` (unit) and `gradle :ui:recordPaparazziDebug` then `:ui:verifyPaparazziDebug` (rendered layout snapshots)

## الخصوصية والنطاق — Privacy & Scope

- No SMS features, no call-log access, no accounts, no analytics, no internet permission.
- The in-call screen (including the end-call button) belongs to the system's default phone app; this launcher does not alter it.
- Family/personal data used during development (real names, numbers, photos) is intentionally absent from this repository; all test fixtures are fictional.

## License

Code license to be chosen by the project owner before publication. Font and icon notices in [NOTICE.md](NOTICE.md).
