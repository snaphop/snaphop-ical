package com.snaphop.ical.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.snaphop.ical.IcalEvent;
import com.snaphop.ical.IcalReader;
import com.snaphop.ical.IcalRecurrence;
import com.snaphop.ical.IcalTime;
import com.snaphop.ical.IcalWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/// [IcalWriter]: a family's calendar written in full, read back by this
/// reader and both oracles, and every calendar of the other tests written
/// and read again.
class WriterTest {

  static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

  static IcalTime.DateTime at(int month, int day, int hour, int minute, ZoneId zone) {
    return new IcalTime.DateTime(ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, zone));
  }

  static IcalTime.Date day(int month, int day) {
    return new IcalTime.Date(LocalDate.of(2026, month, day));
  }

  /// A family's November, as a todo app would publish it.
  static List<IcalEvent> family() {
    return List.of(
        IcalEvent.builder("trash@todo.example.com", day(11, 3))
            .summary("Take out the trash")
            .recurrence(IcalRecurrence.parse("FREQ=WEEKLY;BYDAY=TU,FR"))
            .exceptionDates(List.of(day(11, 27)))
            .build(),
        IcalEvent.builder("piano@todo.example.com", at(11, 2, 17, 0, NEW_YORK))
            .end(at(11, 2, 17, 30, NEW_YORK))
            .summary("Piano lesson")
            .location("Music school, Room 2; upstairs")
            .description("Bring the blue book.\nPractice scales: C, G and D major 🎹 before the lesson, and the"
                + " new piece's first page, slowly, with the metronome at 60.")
            .recurrence(IcalRecurrence.builder(IcalRecurrence.Frequency.WEEKLY).count(8).build())
            .build(),
        IcalEvent.builder("piano@todo.example.com", at(11, 17, 18, 0, NEW_YORK))
            .end(at(11, 17, 18, 30, NEW_YORK))
            .recurrenceId(at(11, 16, 17, 0, NEW_YORK))
            .summary("Piano lesson (moved)")
            .build(),
        IcalEvent.builder("grandma-call@todo.example.com", at(11, 5, 15, 0, ZoneId.of("Europe/London")))
            .end(at(11, 5, 16, 0, ZoneId.of("Europe/London")))
            .summary("Call with Grandma")
            .build(),
        IcalEvent.builder("flight@todo.example.com", at(11, 20, 13, 0, ZoneOffset.UTC))
            .end(at(11, 20, 19, 0, ZoneOffset.UTC))
            .summary("Flight to Denver")
            .build(),
        IcalEvent.builder("dentist@todo.example.com", new IcalTime.Floating(LocalDateTime.of(2026, 11, 10, 10, 0)))
            .end(new IcalTime.Floating(LocalDateTime.of(2026, 11, 10, 11, 0)))
            .summary("Dentist")
            .status(IcalEvent.Status.CANCELLED)
            .build(),
        IcalEvent.builder("grandma-visit@todo.example.com", day(11, 26))
            .end(day(11, 29))
            .summary("Grandma visits")
            .build());
  }

  static IcalWriter writer() {
    return IcalWriter.builder()
        .prodId("-//SnapHop//Agentic Todo//EN")
        .name("Gent family")
        .refreshInterval(Duration.ofHours(1))
        .stamp(Instant.parse("2026-11-06T12:00:00Z"))
        .build();
  }

  @Test
  void aFamilyCalendar() {
    var text = writer().write(family());
    assertEquals("""
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//SnapHop//Agentic Todo//EN
        CALSCALE:GREGORIAN
        NAME:Gent family
        X-WR-CALNAME:Gent family
        REFRESH-INTERVAL;VALUE=DURATION:PT1H
        X-PUBLISHED-TTL:PT1H
        BEGIN:VTIMEZONE
        TZID:America/New_York
        BEGIN:STANDARD
        DTSTART:20250101T000000
        TZOFFSETFROM:-0500
        TZOFFSETTO:-0500
        END:STANDARD
        BEGIN:DAYLIGHT
        DTSTART:20250309T020000
        TZOFFSETFROM:-0500
        TZOFFSETTO:-0400
        RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=2SU
        END:DAYLIGHT
        BEGIN:STANDARD
        DTSTART:20251102T020000
        TZOFFSETFROM:-0400
        TZOFFSETTO:-0500
        RRULE:FREQ=YEARLY;BYMONTH=11;BYDAY=1SU
        END:STANDARD
        END:VTIMEZONE
        BEGIN:VTIMEZONE
        TZID:Europe/London
        BEGIN:STANDARD
        DTSTART:20250101T000000
        TZOFFSETFROM:+0000
        TZOFFSETTO:+0000
        END:STANDARD
        BEGIN:DAYLIGHT
        DTSTART:20250330T010000
        TZOFFSETFROM:+0000
        TZOFFSETTO:+0100
        RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU
        END:DAYLIGHT
        BEGIN:STANDARD
        DTSTART:20251026T020000
        TZOFFSETFROM:+0100
        TZOFFSETTO:+0000
        RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU
        END:STANDARD
        END:VTIMEZONE
        BEGIN:VEVENT
        UID:trash@todo.example.com
        DTSTAMP:20261106T120000Z
        DTSTART;VALUE=DATE:20261103
        DTEND;VALUE=DATE:20261104
        RRULE:FREQ=WEEKLY;BYDAY=TU,FR
        EXDATE;VALUE=DATE:20261127
        SUMMARY:Take out the trash
        END:VEVENT
        BEGIN:VEVENT
        UID:piano@todo.example.com
        DTSTAMP:20261106T120000Z
        DTSTART;TZID=America/New_York:20261102T170000
        DTEND;TZID=America/New_York:20261102T173000
        RRULE:FREQ=WEEKLY;COUNT=8
        SUMMARY:Piano lesson
        LOCATION:Music school\\, Room 2\\; upstairs
        DESCRIPTION:Bring the blue book.\\nPractice scales: C\\, G and D major 🎹 b
         efore the lesson\\, and the new piece's first page\\, slowly\\, with the metr
         onome at 60.
        END:VEVENT
        BEGIN:VEVENT
        UID:piano@todo.example.com
        DTSTAMP:20261106T120000Z
        DTSTART;TZID=America/New_York:20261117T180000
        DTEND;TZID=America/New_York:20261117T183000
        RECURRENCE-ID;TZID=America/New_York:20261116T170000
        SUMMARY:Piano lesson (moved)
        END:VEVENT
        BEGIN:VEVENT
        UID:grandma-call@todo.example.com
        DTSTAMP:20261106T120000Z
        DTSTART;TZID=Europe/London:20261105T150000
        DTEND;TZID=Europe/London:20261105T160000
        SUMMARY:Call with Grandma
        END:VEVENT
        BEGIN:VEVENT
        UID:flight@todo.example.com
        DTSTAMP:20261106T120000Z
        DTSTART:20261120T130000Z
        DTEND:20261120T190000Z
        SUMMARY:Flight to Denver
        END:VEVENT
        BEGIN:VEVENT
        UID:dentist@todo.example.com
        DTSTAMP:20261106T120000Z
        DTSTART:20261110T100000
        DTEND:20261110T110000
        SUMMARY:Dentist
        STATUS:CANCELLED
        END:VEVENT
        BEGIN:VEVENT
        UID:grandma-visit@todo.example.com
        DTSTAMP:20261106T120000Z
        DTSTART;VALUE=DATE:20261126
        DTEND;VALUE=DATE:20261129
        SUMMARY:Grandma visits
        END:VEVENT
        END:VCALENDAR
        """, text.replace("\r\n", "\n"));
    for (var line : text.split("\r\n", -1)) {
      assertTrue(line.getBytes(StandardCharsets.UTF_8).length <= 75, line);
    }
    assertEquals(text.length(), text.replace("\r\n", "").length() + 2 * text.split("\r\n", -1).length - 2, "every line ends with CRLF");
  }

  /// What a calendar app shows of it in November, read back in strict
  /// mode: the moved lesson, the trash skipped on Thanksgiving's Friday,
  /// the cancelled dentist left out.
  @Test
  void readBack() {
    var calendar = IcalReader.builder().zone(NEW_YORK).mode(IcalReader.Mode.STRICT).build().read(writer().write(family()));
    assertEquals("""
        name: Gent family
        2026-11-02 17:00-17:30 Piano lesson @ Music school, Room 2; upstairs [piano@todo.example.com@2026-11-02T22:00:00Z] Bring the blue book.
        Practice scales: C, G and D major 🎹 before the lesson, and the new piece's first page, slowly, with the metronome at 60.
        2026-11-03 all day Take out the trash [trash@todo.example.com@2026-11-03]
        2026-11-05 10:00-11:00 Call with Grandma [grandma-call@todo.example.com]
        2026-11-06 all day Take out the trash [trash@todo.example.com@2026-11-06]
        2026-11-09 17:00-17:30 Piano lesson @ Music school, Room 2; upstairs [piano@todo.example.com@2026-11-09T22:00:00Z] Bring the blue book.
        Practice scales: C, G and D major 🎹 before the lesson, and the new piece's first page, slowly, with the metronome at 60.
        2026-11-10 all day Take out the trash [trash@todo.example.com@2026-11-10]
        2026-11-13 all day Take out the trash [trash@todo.example.com@2026-11-13]
        2026-11-17 all day Take out the trash [trash@todo.example.com@2026-11-17]
        2026-11-17 18:00-18:30 Piano lesson (moved) [piano@todo.example.com@2026-11-16T22:00:00Z]
        2026-11-20 all day Take out the trash [trash@todo.example.com@2026-11-20]
        2026-11-20 08:00-14:00 Flight to Denver [flight@todo.example.com]
        2026-11-23 17:00-17:30 Piano lesson @ Music school, Room 2; upstairs [piano@todo.example.com@2026-11-23T22:00:00Z] Bring the blue book.
        Practice scales: C, G and D major 🎹 before the lesson, and the new piece's first page, slowly, with the metronome at 60.
        2026-11-24 all day Take out the trash [trash@todo.example.com@2026-11-24]
        2026-11-26..2026-11-28 all day Grandma visits [grandma-visit@todo.example.com]
        2026-11-30 17:00-17:30 Piano lesson @ Music school, Room 2; upstairs [piano@todo.example.com@2026-11-30T22:00:00Z] Bring the blue book.
        Practice scales: C, G and D major 🎹 before the lesson, and the new piece's first page, slowly, with the metronome at 60.
        """, SampleCalendarTest.render(calendar, SampleCalendarTest.NOVEMBER, SampleCalendarTest.DECEMBER));
  }

  /// ical4j and ICAL.js read what is written as this reader does.
  @Test
  void oraclesReadItTheSame() throws Exception {
    var text = writer().write(family());
    var calendar = IcalReader.builder().zone(NEW_YORK).build().read(text);
    var from = LocalDate.of(2026, 10, 1);
    var until = LocalDate.of(2027, 2, 1);
    var ours = Row.render(Row.ours(calendar, NEW_YORK, from, until));
    assertEquals(ours, Row.render(Ical4jOracle.occurrences(text, NEW_YORK, from, until)));
    assertEquals(ours, Row.render(IcaljsOracle.occurrences(text, NEW_YORK, from, until)));
    assertEquals(CorpusTest.lines(Row.events(calendar)), CorpusTest.lines(Ical4jOracle.events(text)));
    assertEquals(CorpusTest.lines(Row.events(calendar)), CorpusTest.lines(IcaljsOracle.events(text)));
  }

  /// Events that can't be written, and why, in full.
  enum Bad {
    BLANK_UID(() -> IcalEvent.builder(" ", day(11, 3)).build(), "An event's UID must not be blank"),
    UID_ON_TWO_LINES(() -> IcalEvent.builder("a\nb", day(11, 3)).build(), """
        A UID can't have a line break, other control character or '"': "a
        b\""""),
    END_A_DATE_TIME(() -> IcalEvent.builder("a", day(11, 3)).end(at(11, 4, 9, 0, NEW_YORK)).build(),
        "An event's end is a date-time but its start is a date: 20261104T090000"),
    END_BEFORE_START(() -> IcalEvent.builder("a", at(11, 3, 9, 0, NEW_YORK)).end(at(11, 3, 8, 0, NEW_YORK)).build(),
        "An event's end must be at or after its start 20261103T090000, but is 20261103T080000"),
    ALL_DAY_ENDS_WHEN_IT_STARTS(() -> IcalEvent.builder("a", day(11, 3)).end(day(11, 3)).build(),
        "An event's end must be after its start 20261103, but is 20261103"),
    NAME_ON_TWO_LINES(() -> IcalWriter.builder().name("Gent\r\nfamily").build(), """
        A calendar name can't have a line break, other control character or '"': "Gent\r
        family\""""),
    REFRESH_TOO_OFTEN(() -> IcalWriter.builder().refreshInterval(Duration.ofSeconds(10)).build(),
        "A refresh interval must be a minute or more: PT10S");

    @SuppressWarnings("ImmutableEnumChecker") // Each makes a new, immutable value.
    final java.util.function.Supplier<?> make;
    final String message;

    Bad(java.util.function.Supplier<?> make, String message) {
      this.make = make;
      this.message = message;
    }
  }

  @ParameterizedTest
  @EnumSource(Bad.class)
  void cantBeWritten(Bad b) {
    var e = assertThrows(IllegalArgumentException.class, b.make::get);
    assertEquals(b.message, e.getMessage());
  }

  static Stream<String> zones() {
    return ZoneId.getAvailableZoneIds().stream().sorted();
  }

  /// Each JDK zone's `VTIMEZONE`, read under another name so that the
  /// reader can't use the JDK's: noon each day from 2025 to 2030 is when
  /// the JDK says.
  @ParameterizedTest
  @MethodSource("zones")
  void everyZone(String id) {
    var zone = ZoneId.of(id);
    var start = new IcalTime.DateTime(ZonedDateTime.of(2025, 1, 1, 12, 0, 0, 0, zone));
    var event = IcalEvent.builder("noon", start).recurrence(IcalRecurrence.parse("FREQ=DAILY")).build();
    var text = IcalWriter.builder().stamp(Instant.EPOCH).build().write(List.of(event));
    if (zone.normalized().equals(ZoneOffset.UTC)) {
      assertTrue(text.contains("DTSTART:20250101T120000Z"), text);
      return;
    }
    var renamed = text.replace("TZID:" + id + "\r\n", "TZID:" + id + " copy\r\n").replace("TZID=" + id + ":", "TZID=" + id + " copy:");
    var calendar = IcalReader.builder().zone(ZoneOffset.UTC).mode(IcalReader.Mode.STRICT).build().read(renamed);
    var expected = new ArrayList<String>();
    var actual = new ArrayList<String>();
    var from = LocalDate.of(2025, 1, 1);
    var until = LocalDate.of(2031, 1, 1);
    for (var d = from; d.isBefore(until); d = d.plusDays(1)) expected.add(ZonedDateTime.of(d.atTime(12, 0), zone).toInstant().toString());
    for (var o : calendar.occurrences(from.minusDays(1), until.plusDays(1))) {
      var s = ((IcalTime.DateTime) o.start()).dateTime().toInstant();
      if (!s.isBefore(from.atStartOfDay(ZoneOffset.UTC).minusDays(1).toInstant())) actual.add(s.toString());
    }
    while (!actual.isEmpty() && actual.getLast().compareTo(expected.getLast()) > 0) actual.removeLast();
    assertEquals(String.join("\n", expected), String.join("\n", actual.subList(Math.max(0, actual.size() - expected.size()), actual.size())));
  }

  static Stream<Path> corpus() throws Exception {
    return CorpusTest.files();
  }

  /// Every calendar of ICAL.js's samples and parser tests, read, written,
  /// and read again: the same events, with the same occurrences.
  @ParameterizedTest
  @MethodSource("corpus")
  void corpusAgain(Path file) {
    again(CorpusTest.name(file), CorpusTest.text(file), CorpusTest.NEW_YORK, null, null);
  }

  /// Each sample of [SampleCalendarTest], written and read again.
  @ParameterizedTest
  @EnumSource(SampleCalendarTest.Sample.class)
  void sampleAgain(SampleCalendarTest.Sample sample) {
    again(sample.name(), sample.text(), sample.zone, sample.from, sample.until);
  }

  /// What is lost writing a calendar read, and why.
  static final Map<String, String> CHANGED = Map.of();

  static void again(String name, String text, ZoneId zone, @Nullable LocalDate from, @Nullable LocalDate until) {
    var reader = IcalReader.builder().zone(zone).build();
    var first = reader.read(text);
    var written = writer().write(first.events());
    var second = IcalReader.builder().zone(zone).mode(IcalReader.Mode.STRICT).build().read(written);
    var start = from != null ? from : CorpusTest.from(first);
    var end = until != null ? until : start.plusYears(1);
    var before = CorpusTest.lines(Row.events(first)) + "\n" + Row.render(Row.ours(first, zone, start, end));
    var after = CorpusTest.lines(Row.events(second)) + "\n" + Row.render(Row.ours(second, zone, start, end));
    var why = CHANGED.get(name);
    if (why == null) assertEquals(before, after, name + "\n" + written);
    else assertTrue(!before.equals(after), "Listed as changed (" + why + "), but now the same");
  }
}
