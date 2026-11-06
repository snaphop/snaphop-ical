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
        """, Differs.BOTH),
    /// New York's change from daylight saving time, on November 1: a time
    /// that happens twice is the first; an end from DTEND is the same exact
    /// length every time (a night shift is 7 hours one night, as clocks go
    /// back), and a DURATION of a day is a day on the wall clock. RFC 5545
    /// says each (3.3.5, 3.8.5.3), as ical4j agrees; ICAL.js takes the
    /// second 01:30 and keeps the night shift's wall clock end.
    DST_ENDS("dst.ics", """
        name: null
        2026-10-25 01:30-02:30 Early swim [early-swim@example.com@2026-10-25T05:30:00Z]
        2026-10-30..2026-10-31 23:00-07:00 Night shift [night-shift@example.com@2026-10-31T03:00:00Z]
        2026-10-31..2026-11-01 18:00-18:00 Sleepover [sleepover@example.com]
        2026-10-31..2026-11-01 23:00-06:00 Night shift [night-shift@example.com@2026-11-01T03:00:00Z]
        2026-11-01 01:30-01:30 Early swim [early-swim@example.com@2026-11-01T05:30:00Z]
        2026-11-01..2026-11-02 23:00-07:00 Night shift [night-shift@example.com@2026-11-02T04:00:00Z]
        2026-11-08 01:30-02:30 Early swim [early-swim@example.com@2026-11-08T06:30:00Z]
        """, Differs.ICALJS, NEW_YORK, LocalDate.of(2026, 10, 24), LocalDate.of(2026, 11, 10)),
    /// New York's change to daylight saving time, on March 8: 02:30 that day
    /// doesn't happen, so it is 03:30, an hour later, as RFC 5545 says and
    /// ical4j agrees. ICAL.js makes it 01:30.
    DST_STARTS("dst.ics", """
        name: null
        2026-03-06 02:30-03:00 Paper route [paper-route@example.com@2026-03-06T07:30:00Z]
        2026-03-07 02:30-03:00 Paper route [paper-route@example.com@2026-03-07T07:30:00Z]
        2026-03-08 03:30-04:00 Paper route [paper-route@example.com@2026-03-08T07:30:00Z]
        2026-03-09 02:30-03:00 Paper route [paper-route@example.com@2026-03-09T06:30:00Z]
        """, Differs.ICALJS, NEW_YORK, LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 12)),
    /// Exchange: Windows zone names, with Outlook's VTIMEZONE (which matches
    /// New York's) or none; a custom zone that matches no real one; and a
    /// zone that is nothing, read as floating. ICAL.js reads the Windows
    /// name with no VTIMEZONE as floating; ical4j fails on the zone that is
    /// nothing.
    OUTLOOK("outlook.ics", """
        name: Calendar
        2026-11-04 10:00-11:00 Team meeting @ Room 4 [040000008200E00074C5B7101A82E00800000000@2026-11-04T15:00:00Z]
        2026-11-05 03:43-04:13 Call with the ship [customized@example.com]
        2026-11-06 09:00-10:00 Call with Paris [paris@example.com]
        2026-11-07 09:00-10:00 Rover check [mars@example.com]
        2026-11-18 10:00-11:00 Team meeting @ Room 4 [040000008200E00074C5B7101A82E00800000000@2026-11-18T15:00:00Z]
        warning: Line 56: The time zone "Mars/Olympus_Mons" is not a zone name this reader knows, nor a VTIMEZONE of the calendar, so its times are read as floating: "DTSTART;TZID=Mars/Olympus_Mons:20261107T090000"
        """, Differs.BOTH),
    /// Changed times: one moved into the window from before it, one moved out
    /// of it, and one an invitation to a single time of an event the
    /// calendar doesn't have. Exception dates in UTC for times in a zone;
    /// RDATEs of whole days and of a period; and a cancelled repeating event.
    /// ICAL.js fails on the period. ical4j leaves the UTC exception
    /// dates in, and names the period's instance by its time, not its day.
    MOVED("moved.ics", """
        name: null
        2026-11-01 14:00-15:30 Soccer (rain date) [soccer@example.com@2026-10-31T14:00:00Z]
        2026-11-03 all day School fair [fair@example.com@2026-11-03]
        2026-11-12 19:00-20:00 Book club (one evening I was invited to) [invited-once@example.com@2026-11-13T00:00:00Z]
        2026-11-17 all day School fair [fair@example.com@2026-11-17]
        2026-11-20 17:00-21:00 School fair [fair@example.com@2026-11-20]
        2026-11-21 10:00-11:30 Soccer [soccer@example.com@2026-11-21T15:00:00Z]
        2026-11-24 all day School fair [fair@example.com@2026-11-24]
        """, Differs.BOTH),
    /// Google Calendar's all-day rules: weekly with UNTIL in UTC (the end
    /// of the last day in New York) and a skipped date, the last Friday of
    /// each month, and a birthday; and a weekend given in UTC.
    GOOGLE_ALL_DAY("google-all-day.ics", """
        name: Kids
        2026-11-02 all day Library books due [library@google.com@2026-11-02]
        2026-11-13..2026-11-16 18:00-08:00 Camping weekend [camp@google.com]
        2026-11-16 all day Library books due [library@google.com@2026-11-16]
        2026-11-19 all day Grandpa's birthday [birthday@google.com@2026-11-19]
        2026-11-23 all day Library books due [library@google.com@2026-11-23]
        2026-11-27 all day Pizza Friday [pizza@google.com@2026-11-27]
        """),
    /// The window's edges: what started before it and ends in it is in;
    /// what ends as it starts, or starts as it ends, is not; a time with no
    /// length at its first moment is in. And a second VCALENDAR, which is
    /// all ical4j reads of the text.
    EDGES("edges.ics", """
        name: null
        2026-10-28..2026-11-02 08:00-20:00 Trip that started in October [trip@example.com]
        2026-11-01 00:00-00:00 Reminder at the window's first moment [midnight@example.com]
        2026-11-30 all day From a second VCALENDAR in the same text [second-calendar@example.com]
        warning: Line 38: The text has more than one VCALENDAR, so their events are read as one calendar's: "BEGIN:VCALENDAR"
        """, Differs.ICAL4J);

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
  void ical4jAgrees(Sample sample) {
    var ours = Row.render(Row.ours(sample.read(), sample.zone, sample.from, sample.until));
    var ical4j = CorpusTest.oracle(() -> Row.render(Ical4jOracle.occurrences(sample.text(), sample.zone, sample.from, sample.until)));
    check(ical4j, ours, sample.differs == Differs.ICAL4J || sample.differs == Differs.BOTH);
  }

  @ParameterizedTest
  @EnumSource(Sample.class)
  void icaljsAgrees(Sample sample) {
    var ours = Row.render(Row.ours(sample.read(), sample.zone, sample.from, sample.until));
    var icaljs = CorpusTest.oracle(() -> Row.render(IcaljsOracle.occurrences(sample.text(), sample.zone, sample.from, sample.until)));
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
