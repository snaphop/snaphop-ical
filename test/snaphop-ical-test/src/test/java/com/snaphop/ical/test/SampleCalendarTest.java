package com.snaphop.ical.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.snaphop.ical.IcalCalendar;
import com.snaphop.ical.IcalReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/// Sample calendars, each read for a window of days in a zone and compared
/// in full with what a calendar should show; and with what ical4j and
/// ICAL.js, the oracles, work out from the same text.
class SampleCalendarTest {

  static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
  static final LocalDate NOVEMBER = LocalDate.of(2026, 11, 1);
  static final LocalDate DECEMBER = LocalDate.of(2026, 12, 1);

  enum Sample {
    /// A weekly stand-up with a skipped date and a moved time, an all-day
    /// event with escaped text, and a cancelled event.
    FAMILY("family.ics", """
        name: null
        2026-11-02 09:30-09:45 Stand-up @ Kitchen [standup@example.com@2026-11-02T14:30:00Z]
        2026-11-05 all day Trash day [trash@example.com] Bins out the night before, recycling too.
        2026-11-09 11:00-11:15 Stand-up (moved) [standup@example.com@2026-11-09T14:30:00Z]
        2026-11-11 09:30-09:45 Stand-up @ Kitchen [standup@example.com@2026-11-11T14:30:00Z]
        2026-11-16 09:30-09:45 Stand-up @ Kitchen [standup@example.com@2026-11-16T14:30:00Z]
        2026-11-18 09:30-09:45 Stand-up @ Kitchen [standup@example.com@2026-11-18T14:30:00Z]
        """),
    /// Apple Calendar, from agentic-todo: a weekly lesson with a skipped
    /// date, one time moved and one cancelled, and an all-day visit over
    /// three days.
    APPLE("apple.ics", """
        name: Gent Family
        2026-11-02 17:00-17:30 Piano lesson @ Music school [piano-1@2026-11-02T22:00:00Z]
        2026-11-17 18:00-18:30 Piano lesson (moved) [piano-1@2026-11-16T22:00:00Z]
        2026-11-26..2026-11-28 all day Grandma visits [grandma] Bring the pie, and the good chairs
        2026-11-30 17:00-17:30 Piano lesson @ Music school [piano-1@2026-11-30T22:00:00Z]
        """),
    /// Google Calendar, from agentic-todo: UTC times, another zone's time
    /// in New York's, a whole day, a show ending at midnight, and one before
    /// the window.
    GOOGLE("google.ics", """
        name: Work
        2026-11-10 10:00-11:00 Dentist [abc@google.com]
        2026-11-12 19:00-21:00 Game in Chicago [chi@google.com]
        2026-11-20 all day No school [day@google.com]
        2026-11-20 22:00-00:00 Late show [late@google.com]
        """),
    /// A sports app, from agentic-todo: times with no zone, a length
    /// instead of an end, a long folded title, a cancelled game, and two
    /// events left out. The oracles expand `FREQ=MINUTELY`, which this
    /// reader refuses by default.
    SPORTS("sports.ics", """
        name: null
        2026-11-03 17:30-19:00 Practice [practice@2026-11-03T17:30:00]
        2026-11-07 09:00-10:30 Baseball game vs the Tigers at the long field behind the elementary school @ Field 3 [game-1]
        2026-11-10 17:30-19:00 Practice [practice@2026-11-10T17:30:00]
        2026-11-17 17:30-19:00 Practice [practice@2026-11-17T17:30:00]
        warning: Line 19: An event has no UID, so it is left out: "Practice with no UID" at 20261121T090000
        warning: Line 26: The event repeats more often than hourly, so it is left out: "Every minute" at 20261101T000000
        """, Differs.BOTH);

    final String file;
    final String expected;
    final Differs differs;
    final ZoneId zone;
    final LocalDate from;
    final LocalDate until;

    Sample(String file, String expected) {
      this(file, expected, Differs.NEITHER);
    }

    Sample(String file, String expected, Differs differs) {
      this(file, expected, differs, NEW_YORK, NOVEMBER, DECEMBER);
    }

    Sample(String file, String expected, Differs differs, ZoneId zone, LocalDate from, LocalDate until) {
      this.file = file;
      this.expected = expected;
      this.differs = differs;
      this.zone = zone;
      this.from = from;
      this.until = until;
    }

    String text() {
      try (var in = Objects.requireNonNull(SampleCalendarTest.class.getResourceAsStream(file), file)) {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    IcalCalendar read() {
      return IcalReader.builder().zone(zone).build().read(text());
    }
  }

  /// Which oracles work out something else, as a sample's comment says why.
  enum Differs {
    NEITHER,
    ICAL4J,
    ICALJS,
    BOTH
  }

  @ParameterizedTest
  @EnumSource(Sample.class)
  void readsWhatACalendarShows(Sample sample) {
    assertEquals(sample.expected, render(sample.read(), sample.from, sample.until));
  }

  @ParameterizedTest
  @EnumSource(Sample.class)
  void ical4jAgrees(Sample sample) throws Exception {
    var ours = Row.render(Row.ours(sample.read(), sample.zone, sample.from, sample.until));
    var ical4j = Row.render(Ical4jOracle.occurrences(sample.text(), sample.zone, sample.from, sample.until));
    check(ical4j, ours, sample.differs == Differs.ICAL4J || sample.differs == Differs.BOTH);
  }

  @ParameterizedTest
  @EnumSource(Sample.class)
  void icaljsAgrees(Sample sample) {
    var ours = Row.render(Row.ours(sample.read(), sample.zone, sample.from, sample.until));
    var icaljs = Row.render(IcaljsOracle.occurrences(sample.text(), sample.zone, sample.from, sample.until));
    check(icaljs, ours, sample.differs == Differs.ICALJS || sample.differs == Differs.BOTH);
  }

  /// The oracle agrees, or, where the sample says it differs, still does.
  static void check(String oracle, String ours, boolean differs) {
    if (differs) assertNotEquals(oracle, ours, "The sample says this oracle differs, but it now agrees");
    else assertEquals(oracle, ours);
  }

  /// What a calendar shows, one line an occurrence: its days, its times or
  /// all day, title, place, id and notes; then the warnings.
  static String render(IcalCalendar calendar, LocalDate from, LocalDate until) {
    var out = new StringBuilder("name: ").append(calendar.nameOrNull()).append('\n');
    for (var o : calendar.occurrences(from, until)) {
      var e = o.event();
      out.append(o.startDay()).append(o.lastDay().equals(o.startDay()) ? "" : ".." + o.lastDay()).append(' ')
          .append(o.allDay() ? "all day" : o.startTimeOrNull() + "-" + o.endTimeOrNull()).append(' ').append(e.summary())
          .append(e.location().isEmpty() ? "" : " @ " + e.location()).append(" [").append(o.id()).append(']')
          .append(e.description().isEmpty() ? "" : " " + e.description()).append('\n');
    }
    for (var w : calendar.warnings()) out.append("warning: ").append(w).append('\n');
    return out.toString();
  }
}
