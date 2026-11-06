package com.snaphop.ical.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.snaphop.ical.IcalParseException;
import com.snaphop.ical.IcalReader;
import java.time.ZoneId;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/// Calendar text with something wrong, and what each mode says about it, in
/// full: the message [IcalReader.Mode#STRICT] throws, with its line, and the
/// warnings of [IcalReader.Mode#LAX].
class ProblemTest {

  enum Problem {
    /// A sign-in page where a calendar's feed was: not iCalendar at all, so
    /// lax mode fails too.
    HTML("<html>Sign in to see this calendar</html>\n", 1, """
        Line 1: Not iCalendar: the text should start with BEGIN:VCALENDAR, but starts with "<html>Sign in to see this calendar</html>\"""",
        null),
    EMPTY("\n\n", 0, "Not iCalendar: the text is empty.", null),
    NO_UID(event("""
        DTSTART:20261121T090000
        SUMMARY:Practice
        """), 4, """
        Line 4: An event has no UID: "Practice" at 20261121T090000""", """
        Line 4: An event has no UID, so it is left out: "Practice" at 20261121T090000"""),
    BAD_START(event("""
        UID:a
        DTSTART;TZID=America/New_York:2026110T0900
        """), 7, """
        Line 7: DTSTART "2026110T0900" is not a DATE-TIME (YYYYMMDDTHHMMSS, with a Z for UTC): "DTSTART;TZID=America/New_York:2026110T0900\"""",
        """
        Line 7: DTSTART "2026110T0900" is not a DATE-TIME (YYYYMMDDTHHMMSS, with a Z for UTC), so the event is left out: "DTSTART;TZID=America/New_York:2026110T0900\""""),
    BAD_RULE(event("""
        UID:a
        DTSTART:20261102T090000
        RRULE:FREQ=SOMETIMES
        """), 8, """
        Line 8: RRULE: FREQ=SOMETIMES is not a frequency (such as DAILY or WEEKLY): "RRULE:FREQ=SOMETIMES\"""", """
        Line 8: RRULE: FREQ=SOMETIMES is not a frequency (such as DAILY or WEEKLY), so the event is left out: "RRULE:FREQ=SOMETIMES\""""),
    /// A part the RFC doesn't allow with the rule's frequency, which lax
    /// mode leaves out of the rule.
    RULE_PART(event("""
        UID:a
        DTSTART:20261102T090000
        RRULE:FREQ=MONTHLY;BYWEEKNO=2
        """), 8, """
        Line 8: RRULE: BYWEEKNO is only for FREQ=YEARLY: "RRULE:FREQ=MONTHLY;BYWEEKNO=2\"""", """
        Line 8: RRULE: BYWEEKNO is only for FREQ=YEARLY, so it is left out: "RRULE:FREQ=MONTHLY;BYWEEKNO=2\""""),
    TOO_OFTEN(event("""
        UID:a
        DTSTART:20261102T090000
        RRULE:FREQ=HOURLY;BYMINUTE=0,30
        SUMMARY:Check the oven
        """), 8, """
        Line 8: The event repeats more often than hourly: "Check the oven" at 20261102T090000""", """
        Line 8: The event repeats more often than hourly, so it is left out: "Check the oven" at 20261102T090000"""),
    END_BEFORE_START(event("""
        UID:a
        DTSTART:20261102T090000
        DTEND:20261102T080000
        """), 8, """
        Line 8: DTEND is before DTSTART: "DTEND:20261102T080000\"""", """
        Line 8: DTEND is before DTSTART, so DTEND is left out: "DTEND:20261102T080000\""""),
    DATE_AND_TIME(event("""
        UID:a
        DTSTART;VALUE=DATE:20261102
        DTEND:20261102T080000
        """), 8, """
        Line 8: DTEND is a floating date-time but DTSTART is a date: "DTEND:20261102T080000\"""", """
        Line 8: DTEND is a floating date-time but DTSTART is a date, so DTEND is left out: "DTEND:20261102T080000\""""),
    UNKNOWN_STATUS(event("""
        UID:a
        DTSTART:20261102T090000
        STATUS:MAYBE
        """), 8, """
        Line 8: STATUS is not TENTATIVE, CONFIRMED or CANCELLED: "STATUS:MAYBE\"""", """
        Line 8: STATUS is not TENTATIVE, CONFIRMED or CANCELLED, so the event has none: "STATUS:MAYBE\""""),
    UNKNOWN_ZONE(event("""
        UID:a
        DTSTART;TZID=Somewhere Else:20261102T090000
        """), 7, """
        Line 7: The time zone "Somewhere Else" is not a zone name this reader knows, nor a VTIMEZONE of the calendar: "DTSTART;TZID=Somewhere Else:20261102T090000\"""",
        """
        Line 7: The time zone "Somewhere Else" is not a zone name this reader knows, nor a VTIMEZONE of the calendar, so its times are read as floating: "DTSTART;TZID=Somewhere Else:20261102T090000\""""),
    SAME_UID("""
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//SnapHop//snaphop-ical test//EN
        BEGIN:VEVENT
        UID:a
        DTSTAMP:20261101T120000Z
        DTSTART:20261102T090000
        SUMMARY:First
        END:VEVENT
        BEGIN:VEVENT
        UID:a
        DTSTAMP:20261101T120000Z
        DTSTART:20261103T090000
        SUMMARY:Second
        END:VEVENT
        END:VCALENDAR
        """, 10, """
        Line 10: Another event before it has the UID "a" and no RECURRENCE-ID: "Second" at 20261103T090000""", """
        Line 10: Another event before it has the UID "a" and no RECURRENCE-ID, so it is left out: "Second" at 20261103T090000"""),
    NOT_A_LINE(event("""
        UID:a
        DTSTART:20261102T090000
        Bring snacks
        """), 8, """
        Line 8: Not a content line (NAME:value): "Bring snacks\"""", """
        Line 8: Not a content line (NAME:value), so it is left out: "Bring snacks\""""),
    NO_END("""
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//SnapHop//snaphop-ical test//EN
        BEGIN:VEVENT
        UID:a
        DTSTAMP:20261101T120000Z
        DTSTART:20261102T090000
        END:VCALENDAR
        """, 4, """
        Line 4: BEGIN:VEVENT has no END:VEVENT before line 8: "END:VCALENDAR\"""", """
        Line 4: BEGIN:VEVENT has no END:VEVENT before line 8, so it ends there: "END:VCALENDAR\""""),
    /// RFC 5545 requires these; lax mode reads the text as well without
    /// them, so says nothing.
    NO_DTSTAMP("""
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//SnapHop//snaphop-ical test//EN
        BEGIN:VEVENT
        UID:a
        DTSTART:20261102T090000
        SUMMARY:Practice
        END:VEVENT
        END:VCALENDAR
        """, 4, """
        Line 4: An event has no DTSTAMP, which RFC 5545 requires: "Practice" at 20261102T090000""", ""),
    NO_VERSION("""
        BEGIN:VCALENDAR
        PRODID:-//SnapHop//snaphop-ical test//EN
        END:VCALENDAR
        """, 1, """
        Line 1: The VCALENDAR has no VERSION, which RFC 5545 requires.""", "");

    final String text;
    final int line;
    final String strict;
    final @Nullable String lax;

    /// `lax` is the warnings, one a line; null when lax mode fails too,
    /// with the same message.
    Problem(String text, int line, String strict, @Nullable String lax) {
      this.text = text;
      this.line = line;
      this.strict = strict;
      this.lax = lax;
    }
  }

  /// A calendar with one event: its `BEGIN:VEVENT` on line 4, and `body`
  /// from line 6.
  static String event(String body) {
    return """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//SnapHop//snaphop-ical test//EN
        BEGIN:VEVENT
        DTSTAMP:20261101T120000Z
        %sEND:VEVENT
        END:VCALENDAR
        """.formatted(body);
  }

  static IcalReader reader(IcalReader.Mode mode) {
    return IcalReader.builder().zone(ZoneId.of("America/New_York")).mode(mode).build();
  }

  @ParameterizedTest
  @EnumSource(Problem.class)
  void strictSaysWhatIsWrongAndWhere(Problem p) {
    var e = assertThrows(IcalParseException.class, () -> reader(IcalReader.Mode.STRICT).read(p.text));
    assertEquals(p.strict, e.getMessage());
    assertEquals(p.line, e.line());
  }

  @ParameterizedTest
  @EnumSource(Problem.class)
  void laxSaysWhatItDidAboutIt(Problem p) {
    var lax = reader(IcalReader.Mode.LAX);
    var expected = p.lax;
    if (expected == null) {
      var e = assertThrows(IcalParseException.class, () -> lax.read(p.text));
      assertEquals(p.strict, e.getMessage());
      return;
    }
    assertEquals(expected, String.join("\n", lax.read(p.text).warnings()));
  }
}
