# موبايل ماما — Mama Launcher

لانشر أندرويد بسيط مصمم لكبار السن وضعاف القراءة: واجهة عربية بخط كبير، شبكة صور جهات الاتصال، وتأكيد واضح قبل أي مكالمة.

A minimal Android launcher designed for elderly and low-literacy users: Arabic-first UI, large text, photo-based contact grid, and an explicit confirmation screen before any call is placed.

## الحالة — Status

**نسخة تجريبية تحت تجربة مشرفة.** اختبارات الكود والواجهة ناجحة، لكن إصلاحات النسخة الجديدة ما زالت تحتاج إعادة تجربة على الهاتف الفعلي، خصوصا الصوت والمكالمات وقفل الجهاز.

Supervised pilot. Current PR4 source, including merged PR3, passes 110 app unit/flow tests (JUnit + Robolectric) and 40 layout-rendered snapshots. Debug assemble passes. The user has tested an earlier APK on a physical phone and reported issues; these new fixes still need a supervised retest on that phone. Automated tests do not prove audible speech, carrier calls, device credential behavior or daily-use readiness.

## الفكرة — Design

**وضعان: وضع يومي لماما، ووضع إعدادات محمي لمن يجهّز الهاتف.**

- الوضع اليومي: ساعة وتاريخ كبيران، وأزرار كبيرة فقط لما اختاره معدّ الهاتف من التطبيقات المثبتة فعلًا + زر «الهاتف» دائمًا. تطبيق أُزيل من الهاتف يختفي زره بصمت، ولا توجد أزرار إضافة أو إعدادات في هذه الشاشة.
- «الهاتف» يفتح شبكة صور جهات الاتصال الكبيرة (المفضلة بالنجمة فوق)، ومنها **لوحة الأرقام** لكتابة رقم جديد: الزر الأخضر يبدأ المكالمة بعد ضغطة واضحة وإذن الاتصال، و«حفظ الرقم» يفتح شاشة إضافة جهة اتصال الأصلية بالرقم جاهزًا - بدون كتابة جهات الاتصال بصمت.
- ضغطة على الشخص تفتح **شاشة تأكيد**: صورة مكبّرة، الاسم يُنطق صوتيًا (TTS، يمكن إيقاف النطق التلقائي)، وزر أخضر واحد وحده يبدأ المكالمة. لو للشخص أكثر من رقم، لا يتصل شيء قبل اختيار الرقم بشكل ظاهر.
- وضع الإعدادات يُفتح بضغطة مطوّلة على الساعة ثم **رمز PIN من ٤ أرقام** (يُحفظ مملّحًا ومُجزّأً مع تحديد عدد المحاولات، وبديل اختياري: قفل شاشة الجهاز نفسه). فيه: اختيار وترتيب أزرار الشاشة، حالة الصوت وتجربته، حالة الأذونات ورابط إعدادات النظام، حجم الخط (عادي/كبير/كبير جدًا)، تباين عالٍ (أصفر على أسود)، حماية اللمس من الضغطات العارضة، والاهتزاز.
- مبني على إرشادات أبحاث تصميم الواجهات لكبار السن وضعاف القراءة: أهداف لمس كبيرة، ضغطة واحدة بدون إيماءات، ثبات أماكن العناصر، شاشة واحدة لمهمة واحدة، وزر رجوع ظاهر دائمًا.
- خصوصية كاملة: **بدون إذن إنترنت أصلًا**، بدون تتبع، كل البيانات محلية على الجهاز. الأذونات الوحيدة: قراءة جهات الاتصال وإجراء المكالمات.

## البناء — Build

```
ANDROID_HOME=<your-sdk> ./gradlew assembleDebug   # or: gradle assembleDebug
```

- minSdk 24 / targetSdk 33 (provisional pilot choices, not a committed support range)
- No third-party runtime dependencies — framework APIs only
- Tests: `gradle :app:testDebugUnitTest` (110 app unit and Robolectric flow tests) and `gradle :ui:recordPaparazziDebug` then `:ui:verifyPaparazziDebug` (rendered layout snapshots)

## الخصوصية والنطاق — Privacy & Scope

- No SMS features, no call-log access, no accounts, no analytics, no internet permission.
- The in-call screen (including the end-call button) belongs to the system's default phone app; this launcher does not alter it.
- Family/personal data used during development (real names, numbers, photos) is intentionally absent from this repository; all test fixtures are fictional.

## License

Code license to be chosen by the project owner before publication. Font and icon notices in [NOTICE.md](NOTICE.md).
