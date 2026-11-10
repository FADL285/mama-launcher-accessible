package info.fadl.mamalauncher;

/** Immutable read-only provider record. No voicemail data or system writes. */
final class CallLogEntry {
  final long id, date, duration;
  final int type, presentation;
  final String number, name;

  CallLogEntry(
      long id, long date, long duration, int type, int presentation, String number, String name) {
    this.id = id;
    this.date = date;
    this.duration = duration;
    this.type = type;
    this.presentation = presentation;
    this.number = number == null ? "" : number;
    this.name = name == null ? "" : name;
  }

  String identity() {
    return id + ":" + date + ":" + type;
  }

  boolean callable() {
    return presentation == android.provider.CallLog.Calls.PRESENTATION_ALLOWED
        && !number.equals("-1")
        && !number.equals("-2")
        && !number.equals("-3")
        && number.matches("[+0-9 ()\\-]{1,80}")
        && android.telephony.PhoneNumberUtils.isGlobalPhoneNumber(
            android.telephony.PhoneNumberUtils.stripSeparators(number));
  }
}
