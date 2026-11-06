package com.snaphop.ical.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.snaphop.ical.IcalCalendar;
import com.snaphop.ical.IcalReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/// Every calendar of ICAL.js's samples and parser tests (`test/corpus/icaljs`)
/// read by our reader and by both oracles: the events each reads, and the
/// occurrences each works out over a year from the first event, in New York.
///
/// Where an oracle reads a file differently, the maps below say why, and the
/// test checks it still does.
class CorpusTest {

  static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

  static final String NO_END = "a DTSTART date-time and no DTEND or DURATION: RFC 5545 ends it when it starts,"
      + " as ICAL.js does; ical4j ends it a day later";
  static final String ICAL4J_PARSER = "ical4j fails to read the file; this reader reads it, with warnings for what it skips";
  static final String NO_UID = "an event with no UID, which this reader leaves out with a warning";
  static final String EMPTY_VTIMEZONE = "a VTIMEZONE with no TZID, which this reader skips and the oracle fails on";
  static final String DATE_EXDATE = "EXDATE;VALUE=DATE:20240611 for an event at 03:00 UTC: this reader leaves out the"
      + " instance on June 11 in UTC, as ICAL.js's own test (recur_expansion_test.js) at the corpus's commit expects;"
      + " ical4j and ICAL.js 2.1.0, the oracle, leave out none";

  static final Map<String, String> ICALJS_EVENTS = Map.of(
      "samples/timezone_from_file.ics", NO_UID,
      "parser/component.ics", "events with no UID or DTSTART: ICAL.js fails, and this reader leaves them out with a warning");

  static final Map<String, String> ICAL4J_EVENTS = Map.ofEntries(
      Map.entry("samples/timezone_from_file.ics", NO_UID),
      Map.entry("samples/blank_line_mid.ics", "ical4j fails on a blank line, which this reader skips"),
      Map.entry("samples/only_dtstart_time.ics", NO_END),
      Map.entry("samples/rdate_exdate.ics", NO_END),
      Map.entry("samples/recur_duplicate_dates.ics", NO_END),
      Map.entry("samples/recur_instances.ics", EMPTY_VTIMEZONE),
      Map.entry("parser/base64.ics", ICAL4J_PARSER),
      Map.entry("parser/component.ics", ICAL4J_PARSER),
      Map.entry("parser/period.ics", ICAL4J_PARSER),
      Map.entry("parser/property_params.ics", ICAL4J_PARSER),
      Map.entry("parser/quoted_params.ics", ICAL4J_PARSER));

  static final Map<String, String> ICALJS_OCCURRENCES = Map.of(
      "samples/multiple_rrules.ics", "ICAL.js ends the second RRULE a week early: 2012-07-29 is a Sunday before its UNTIL,"
          + " and ical4j agrees it is an instance",
      "samples/recur_duplicate_dates.ics", "ICAL.js gives an instance twice when its RRULE and RDATE both make it;"
          + " a recurrence set has each once, as ical4j agrees",
      "samples/recur_instances.ics", EMPTY_VTIMEZONE,
      "samples/rdate_exdate.ics", DATE_EXDATE);

  static final Map<String, String> ICAL4J_OCCURRENCES = Map.ofEntries(
      Map.entry("samples/blank_line_mid.ics", "ical4j fails on a blank line, which this reader skips"),
      Map.entry("samples/recur_instances.ics", EMPTY_VTIMEZONE),
      Map.entry("samples/rdate_exdate.ics", DATE_EXDATE),
      Map.entry("parser/base64.ics", ICAL4J_PARSER),
      Map.entry("parser/component.ics", ICAL4J_PARSER),
      Map.entry("parser/period.ics", ICAL4J_PARSER),
      Map.entry("parser/property_params.ics", ICAL4J_PARSER),
      Map.entry("parser/quoted_params.ics", ICAL4J_PARSER));

  static Stream<Path> files() throws IOException {
    var corpus = RecurrenceSpecTest.CORPUS.resolve("icaljs");
    return Stream.concat(list(corpus.resolve("samples")), list(corpus.resolve("parser")));
  }

  private static Stream<Path> list(Path dir) throws IOException {
    try (var files = Files.list(dir)) {
      return files.filter(f -> f.toString().endsWith(".ics")).sorted(Comparator.comparing(Path::toString)).toList().stream();
    }
  }

  @ParameterizedTest
  @MethodSource("files")
  void icaljsReadsTheSameEvents(Path file) {
    var text = text(file);
    check(name(file), ICALJS_EVENTS, oracle(() -> lines(IcaljsOracle.events(text))), lines(Row.events(read(text))));
  }

  @ParameterizedTest
  @MethodSource("files")
  void ical4jReadsTheSameEvents(Path file) {
    var text = text(file);
    check(name(file), ICAL4J_EVENTS, oracle(() -> lines(Ical4jOracle.events(text))), lines(Row.events(read(text))));
  }

  @ParameterizedTest
  @MethodSource("files")
  void icaljsWorksOutTheSameOccurrences(Path file) {
    var text = text(file);
    var calendar = read(text);
    var from = from(calendar);
    var until = from.plusYears(1);
    check(name(file), ICALJS_OCCURRENCES, oracle(() -> Row.render(IcaljsOracle.occurrences(text, NEW_YORK, from, until))),
        Row.render(Row.ours(calendar, NEW_YORK, from, until)));
  }

  @ParameterizedTest
  @MethodSource("files")
  void ical4jWorksOutTheSameOccurrences(Path file) {
    var text = text(file);
    var calendar = read(text);
    var from = from(calendar);
    var until = from.plusYears(1);
    check(name(file), ICAL4J_OCCURRENCES, oracle(() -> Row.render(Ical4jOracle.occurrences(text, NEW_YORK, from, until))),
        Row.render(Row.ours(calendar, NEW_YORK, from, until)));
  }

  static void check(String file, Map<String, String> differs, String oracle, String ours) {
    var why = differs.get(file);
    if (why == null) assertEquals(oracle, ours, file);
    else assertNotEquals(oracle, ours, "Listed as different (" + why + "), but now the same");
  }

  /// What an oracle works out, or what it fails with.
  static String oracle(Callable<String> oracle) {
    try {
      return oracle.call();
    } catch (Exception e) {
      return "Failed: " + e;
    }
  }

  static IcalCalendar read(String text) {
    return IcalReader.builder().zone(NEW_YORK).build().read(text);
  }

  /// The day the first event starts, or 2026-01-01 with none.
  static LocalDate from(IcalCalendar calendar) {
    return calendar.events().stream()
        .map(e -> LocalDate.parse(Row.text(e.start(), NEW_YORK).substring(0, 10)))
        .min(Comparator.naturalOrder())
        .orElse(LocalDate.of(2026, 1, 1));
  }

  static String lines(java.util.List<String> lines) {
    return String.join("\n", lines);
  }

  static String name(Path file) {
    return file.getParent().getFileName() + "/" + file.getFileName();
  }

  static String text(Path file) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
