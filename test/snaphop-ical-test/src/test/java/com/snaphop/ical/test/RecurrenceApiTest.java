package com.snaphop.ical.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.snaphop.ical.IcalParseException;
import com.snaphop.ical.IcalRecurrence;
import com.snaphop.ical.IcalRecurrence.Frequency;
import com.snaphop.ical.IcalRecurrence.Weekday;
import com.snaphop.ical.IcalTime;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/// [IcalRecurrence] on its own, as a todo app repeats tasks with it: rules
/// read and made, in words, and the next date.
class RecurrenceApiTest {

  static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

  /// Tuesday, November 3, 2026.
  static final IcalTime.Date NOV_3 = new IcalTime.Date(LocalDate.of(2026, 11, 3));

  /// 09:00 on Tuesday, November 3, 2026, in New York.
  static final IcalTime.DateTime NOV_3_9AM = new IcalTime.DateTime(ZonedDateTime.of(2026, 11, 3, 9, 0, 0, 0, NEW_YORK));

  /// Rules in words: the presets of a todo app's Repeat menu first, then
  /// what a calendar writes. Null where words wouldn't be clear.
  enum Words {
    DAILY("FREQ=DAILY", NOV_3, "Every day"),
    WEEKDAYS("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", NOV_3, "Every weekday"),
    WEEKLY_ON("FREQ=WEEKLY;BYDAY=TU,FR", NOV_3, "Every week on Tuesday and Friday"),
    WEEKLY_FROM_START("FREQ=WEEKLY", NOV_3, "Every week on Tuesday"),
    EVERY_2_WEEKS("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,TH", NOV_3, "Every 2 weeks on Monday and Thursday"),
    MONTHLY_ON_DAY("FREQ=MONTHLY;BYMONTHDAY=15", NOV_3, "Every month on the 15th"),
    MONTHLY_FROM_START("FREQ=MONTHLY", NOV_3, "Every month on the 3rd"),
    MONTHLY_NTH_WEEKDAY("FREQ=MONTHLY;BYDAY=2TU", NOV_3, "Every month on the second Tuesday"),
    YEARLY("FREQ=YEARLY", NOV_3, "Every year on November 3"),
    EVERY_3_MONTHS("FREQ=MONTHLY;INTERVAL=3;BYMONTHDAY=15", NOV_3, "Every 3 months on the 15th"),
    EVERY_2_DAYS("FREQ=DAILY;INTERVAL=2", NOV_3, "Every 2 days"),
    LAST_DAY("FREQ=MONTHLY;BYMONTHDAY=-1", NOV_3, "Every month on the last day"),
    FIRST_AND_15TH("FREQ=MONTHLY;BYMONTHDAY=1,15", NOV_3, "Every month on the 1st and 15th"),
    LAST_FRIDAY_COUNTED("FREQ=MONTHLY;BYDAY=-1FR;COUNT=10", NOV_3, "Every month on the last Friday, 10 times"),
    THANKSGIVING("FREQ=YEARLY;BYMONTH=11;BYDAY=4TH", NOV_3, "Every year on the fourth Thursday of November"),
    TWICE_A_YEAR("FREQ=YEARLY;BYMONTH=3,9;BYMONTHDAY=1", NOV_3, "Every year on the 1st of March and September"),
    SUMMER_MONDAYS("FREQ=WEEKLY;BYDAY=MO;BYMONTH=6,7,8", NOV_3, "Every week on Monday in June, July and August"),
    AT_A_TIME("FREQ=WEEKLY;BYDAY=TU", NOV_3_9AM, "Every week on Tuesday at 9:00 AM"),
    AT_AN_HOUR("FREQ=DAILY;BYHOUR=17;BYMINUTE=30", NOV_3_9AM, "Every day at 5:30 PM"),
    UNTIL_A_DAY("FREQ=WEEKLY;UNTIL=20261130", NOV_3, "Every week on Tuesday, until November 30, 2026"),
    UNTIL_IN_UTC("FREQ=DAILY;UNTIL=20261201T035959Z", NOV_3_9AM, "Every day at 9:00 AM, until November 30, 2026"),
    ONCE("FREQ=DAILY;COUNT=1", NOV_3, "Every day, once"),
    SET_POS("FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-1", NOV_3, null),
    TWO_TIMES("FREQ=DAILY;BYHOUR=9,17", NOV_3_9AM, null),
    HOURLY("FREQ=HOURLY", NOV_3_9AM, null),
    WEEK_NUMBER("FREQ=YEARLY;BYWEEKNO=20", NOV_3, null);

    final String rule;
    @SuppressWarnings("ImmutableEnumChecker") // IcalTime's records are immutable.
    final IcalTime start;
    final @Nullable String words;

    Words(String rule, IcalTime start, @Nullable String words) {
      this.rule = rule;
      this.start = start;
      this.words = words;
    }
  }

  @ParameterizedTest
  @EnumSource(Words.class)
  void inWords(Words w) {
    assertEquals(w.words, IcalRecurrence.parse(w.rule).describeOrNull(w.start));
  }

  /// Rules made from parts, and the text they are written as.
  enum Built {
    WEEKDAYS(() -> IcalRecurrence.builder(Frequency.WEEKLY)
        .byDay(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY).build(),
        "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"),
    SECOND_TUESDAY(() -> IcalRecurrence.builder(Frequency.MONTHLY).byDay(new Weekday(2, DayOfWeek.TUESDAY)).build(),
        "FREQ=MONTHLY;BYDAY=2TU"),
    EVERY_3_MONTHS_10_TIMES(() -> IcalRecurrence.builder(Frequency.MONTHLY).interval(3).byMonthDay(15).count(10).build(),
        "FREQ=MONTHLY;COUNT=10;INTERVAL=3;BYMONTHDAY=15"),
    UNTIL_IN_A_ZONE(() -> IcalRecurrence.builder(Frequency.DAILY).until(NOV_3_9AM).build(),
        "FREQ=DAILY;UNTIL=20261103T140000Z"),
    UNTIL_A_DAY(() -> IcalRecurrence.builder(Frequency.WEEKLY).until(NOV_3).weekStart(DayOfWeek.SUNDAY).build(),
        "FREQ=WEEKLY;UNTIL=20261103;WKST=SU"),
    LAST_WEEKDAY(() -> IcalRecurrence.builder(Frequency.MONTHLY)
        .byDay(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY).bySetPos(-1).build(),
        "FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-1");

    @SuppressWarnings("ImmutableEnumChecker") // Each makes a new, immutable rule.
    final Supplier<IcalRecurrence> rule;
    final String text;

    Built(Supplier<IcalRecurrence> rule, String text) {
      this.rule = rule;
      this.text = text;
    }
  }

  @ParameterizedTest
  @EnumSource(Built.class)
  void builtAsWritten(Built b) {
    var rule = b.rule.get();
    assertEquals(b.text, rule.text());
    assertEquals(rule.text(), IcalRecurrence.parse(rule.text()).text());
  }

  /// Rules that are not RFC 5545's, and why, in full: what a person typing
  /// a custom rule, or an agent writing one, is told.
  enum Bad {
    NO_FREQ("BYDAY=MO", """
        The rule has no FREQ: "BYDAY=MO\""""),
    NOT_A_FREQUENCY("RRULE:FREQ=FORTNIGHTLY", """
        FREQ=FORTNIGHTLY is not a frequency (such as DAILY or WEEKLY): "FREQ=FORTNIGHTLY\""""),
    NOT_A_DAY("FREQ=WEEKLY;BYDAY=MO,TUES", """
        BYDAY=MO,TUES has "TUES", which is not a day (MO, TU, WE, TH, FR, SA or SU): "FREQ=WEEKLY;BYDAY=MO,TUES\""""),
    DAY_OUT_OF_RANGE("FREQ=MONTHLY;BYMONTHDAY=32", """
        BYMONTHDAY=32 has 32, which is not from 1 to 31, or -31 to -1: "FREQ=MONTHLY;BYMONTHDAY=32\""""),
    COUNT_AND_UNTIL("FREQ=DAILY;COUNT=3;UNTIL=20261130", """
        The rule has both COUNT and UNTIL, which RFC 5545 does not allow: "FREQ=DAILY;COUNT=3;UNTIL=20261130\""""),
    WRONG_FREQUENCY("FREQ=MONTHLY;BYWEEKNO=2", """
        BYWEEKNO is only for FREQ=YEARLY: "FREQ=MONTHLY;BYWEEKNO=2\""""),
    TWICE("FREQ=DAILY;INTERVAL=2;INTERVAL=3", """
        The rule has INTERVAL twice: "FREQ=DAILY;INTERVAL=2;INTERVAL=3\""""),
    UNKNOWN_PART("FREQ=DAILY;EVERY=2", """
        The rule part "EVERY=2" is not one RFC 5545 has: "FREQ=DAILY;EVERY=2\"""");

    final String rule;
    final String message;

    Bad(String rule, String message) {
      this.rule = rule;
      this.message = message;
    }
  }

  @ParameterizedTest
  @EnumSource(Bad.class)
  void badRulesSayWhy(Bad b) {
    var e = assertThrows(IcalParseException.class, () -> IcalRecurrence.parse(b.rule));
    assertEquals(b.message, e.getMessage());
    assertEquals(0, e.line());
  }

  @Test
  void badPartsSayWhy() {
    var e = assertThrows(IllegalArgumentException.class, () -> IcalRecurrence.builder(Frequency.WEEKLY).interval(0).build());
    assertEquals("""
        INTERVAL=0 is not a number from 1 up: "FREQ=WEEKLY;INTERVAL=0\"""", e.getMessage());
    e = assertThrows(IllegalArgumentException.class, () -> new Weekday(54, DayOfWeek.MONDAY));
    assertEquals("A BYDAY number is from 1 to 53, or -53 to -1, or 0 for every one: 54", e.getMessage());
  }

  /// A task due every Tuesday and Friday from November 3: its next five,
  /// and when it is due next after it was last due, or after today.
  @Test
  void nextDates() {
    var rule = IcalRecurrence.parse("FREQ=WEEKLY;BYDAY=TU,FR");
    assertEquals(List.of("2026-11-03", "2026-11-06", "2026-11-10", "2026-11-13", "2026-11-17"),
        rule.starts(NOV_3).limit(5).map(t -> ((IcalTime.Date) t).date().toString()).toList());
    assertEquals(new IcalTime.Date(LocalDate.of(2026, 11, 6)), rule.nextOrNull(NOV_3, NOV_3));
    // Several weeks overdue: the first after today, not the next missed.
    assertEquals(new IcalTime.Date(LocalDate.of(2027, 1, 8)), rule.nextOrNull(NOV_3, new IcalTime.Date(LocalDate.of(2027, 1, 5))));
  }

  /// A rule ended by a count has no next after its last.
  @Test
  void noNextAfterTheLast() {
    var rule = IcalRecurrence.parse("FREQ=DAILY;COUNT=3");
    assertEquals(null, rule.nextOrNull(NOV_3, new IcalTime.Date(LocalDate.of(2026, 11, 5))));
    assertEquals(3, rule.starts(NOV_3).count());
  }

  /// Daylight saving time ends on November 1, 2026 and starts on March 14,
  /// 2027: a 09:00 rule stays at 09:00 on the wall clock.
  @Test
  void sameWallClockAcrossDaylightSaving() {
    var start = new IcalTime.DateTime(ZonedDateTime.of(2026, 10, 27, 9, 0, 0, 0, NEW_YORK));
    var rule = IcalRecurrence.parse("FREQ=MONTHLY;BYDAY=-1TU");
    assertEquals(List.of("2026-10-27T09:00-04:00[America/New_York]", "2026-11-24T09:00-05:00[America/New_York]",
            "2026-12-29T09:00-05:00[America/New_York]", "2027-01-26T09:00-05:00[America/New_York]",
            "2027-02-23T09:00-05:00[America/New_York]", "2027-03-30T09:00-04:00[America/New_York]"),
        rule.starts(start).limit(6).map(t -> ((IcalTime.DateTime) t).dateTime().toString()).toList());
    var after = new IcalTime.DateTime(ZonedDateTime.of(2027, 3, 1, 0, 0, 0, 0, ZoneId.of("Europe/London")));
    assertEquals("2027-03-30T09:00-04:00[America/New_York]", ((IcalTime.DateTime) rule.nextOrNull(start, after)).dateTime().toString());
  }

  @Test
  void afterMustBeTheSameKind() {
    var rule = IcalRecurrence.parse("FREQ=DAILY");
    var e = assertThrows(IllegalArgumentException.class, () -> rule.nextOrNull(NOV_3, NOV_3_9AM));
    assertEquals("The time to find a start after must be a date, as the first start is, but is a date-time", e.getMessage());
  }

  /// Rules that keep going, or never make another start, still answer
  /// quickly: a rule from long ago is worked out from the time asked about,
  /// and one with no more starts stops looking once the calendar's 400 year
  /// cycle has passed without one.
  @Test
  void endlessRulesAnswerQuickly() {
    assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
      var never = IcalRecurrence.parse("FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=30");
      assertEquals(List.of(NOV_3), never.starts(NOV_3).toList());
      assertEquals(null, never.nextOrNull(NOV_3, NOV_3));
      var old = new IcalTime.Floating(LocalDateTime.of(1601, 1, 1, 8, 0));
      var hourly = IcalRecurrence.parse("FREQ=HOURLY;INTERVAL=7");
      var next = hourly.nextOrNull(old, new IcalTime.Floating(LocalDateTime.of(2026, 11, 3, 0, 0)));
      assertEquals(new IcalTime.Floating(LocalDateTime.of(2026, 11, 3, 5, 0)), next);
    });
  }
}
