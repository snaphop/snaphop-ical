package com.snaphop.ical.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.snaphop.ical.IcalRecurrence;
import com.snaphop.ical.IcalReader;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/// What a reader's settings change, and what it refuses, and why, in full.
class ReaderConfigTest {

  static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

  @Test
  void aReaderNeedsAZone() {
    var e = assertThrows(IllegalStateException.class, () -> IcalReader.builder().build());
    assertEquals("""
        An IcalReader needs a zone: call zone(ZoneId) with the zone the calendar is read for.""",
        e.getMessage());
  }

  @Test
  void aWindowEndsAfterItStarts() {
    var calendar = IcalReader.builder().zone(NEW_YORK).build().read(SampleCalendarTest.Sample.FAMILY.text());
    var day = LocalDate.of(2026, 11, 1);
    var e = assertThrows(IllegalArgumentException.class, () -> calendar.occurrences(day, day));
    assertEquals("""
        A window must end after it starts. from: 2026-11-01, until: 2026-11-01""", e.getMessage());
  }

  /// Cancelled events and times are left out unless asked for: Apple's
  /// cancelled lesson on November 23, and the family's cancelled dentist.
  @Test
  void cancelledTimesWhenAskedFor() {
    var reader = IcalReader.builder().zone(NEW_YORK).cancelled(IcalReader.Cancelled.INCLUDE).build();
    var apple = reader.read(SampleCalendarTest.Sample.APPLE.text());
    assertEquals("""
        name: Gent Family
        2026-11-02 17:00-17:30 Piano lesson @ Music school [piano-1@2026-11-02T22:00:00Z]
        2026-11-17 18:00-18:30 Piano lesson (moved) [piano-1@2026-11-16T22:00:00Z]
        2026-11-23 17:00-17:30 Piano lesson [piano-1@2026-11-23T22:00:00Z]
        2026-11-26..2026-11-28 all day Grandma visits [grandma] Bring the pie, and the good chairs
        2026-11-30 17:00-17:30 Piano lesson @ Music school [piano-1@2026-11-30T22:00:00Z]
        """, SampleCalendarTest.render(apple, SampleCalendarTest.NOVEMBER, SampleCalendarTest.DECEMBER));
    var family = reader.read(SampleCalendarTest.Sample.FAMILY.text());
    var dentist = family.occurrences(SampleCalendarTest.NOVEMBER, SampleCalendarTest.DECEMBER).stream()
        .filter(o -> o.event().cancelled()).map(o -> o.event().summary() + " " + o.startDay()).toList();
    assertEquals("[Dentist 2026-11-10]", dentist.toString());
  }

  /// An event that repeats more often than the reader allows is left out;
  /// allowed, the sports app's every minute event is every minute.
  @Test
  void moreFrequentWhenAllowed() {
    var reader = IcalReader.builder().zone(NEW_YORK).mostFrequent(IcalRecurrence.Frequency.MINUTELY).build();
    var calendar = reader.read(SampleCalendarTest.Sample.SPORTS.text());
    var minutes = calendar.occurrences(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2)).stream()
        .filter(o -> o.event().uid().equals("spam")).count();
    assertEquals(24 * 60, minutes);
    assertEquals("""
        Line 19: An event has no UID, so it is left out: "Practice with no UID" at 20261121T090000""",
        String.join("\n", calendar.warnings()));
  }

  /// Rules made to keep a reader busy, as a feed from elsewhere might have:
  /// each is worked out in well under a second.
  enum Busy {
    /// February 30th never comes.
    NEVER("FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=30", "19000101T090000"),
    /// Hourly from the year 1, counting, so no skipping ahead: day by day.
    HOURLY_SINCE_YEAR_ONE("FREQ=HOURLY;BYMONTH=2;BYMONTHDAY=30;COUNT=5", "00010101T090000"),
    /// Every hour of every day for centuries before the window.
    HOURLY_COUNTED("FREQ=HOURLY;COUNT=100000000", "16000101T000000"),
    /// Many parts multiplied.
    MANY_PARTS("FREQ=DAILY;BYHOUR=0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23", "20260101T000000");

    final String rule;
    final String start;

    Busy(String rule, String start) {
      this.rule = rule;
      this.start = start;
    }
  }

  @ParameterizedTest
  @EnumSource(Busy.class)
  void busyRulesFinish(Busy busy) {
    var text = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//SnapHop//snaphop-ical test//EN
        BEGIN:VEVENT
        UID:busy
        DTSTAMP:20261101T120000Z
        DTSTART:%s
        RRULE:%s
        END:VEVENT
        END:VCALENDAR
        """.formatted(busy.start, busy.rule);
    var calendar = IcalReader.builder().zone(NEW_YORK).build().read(text);
    assertTimeoutPreemptively(Duration.ofSeconds(5), () -> calendar.occurrences(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)));
  }
}
