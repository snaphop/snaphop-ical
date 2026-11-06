package com.snaphop.ical.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.snaphop.ical.IcalCalendar;
import com.snaphop.ical.IcalRecurrence;
import com.snaphop.ical.IcalReader;
import com.snaphop.ical.IcalTime;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/// The recurrence rules of libical's and ICAL.js's own tests (`test/corpus`),
/// each read as the event it describes, through the public reader, in UTC.
///
/// Both projects test a rule iterator, not events, so two rules of RFC 5545
/// about events are applied to what they expect:
///
/// - `DTSTART` is always the first instance, and counts toward `COUNT`, even
///   when the rule wouldn't make it. libical's iterator leaves it out then.
/// - A rule with no end, which the projects test for its first instances,
///   is compared for those.
///
/// What the reader works out differently is listed in [#DIFFERENT], with
/// why, and checked to still differ.
class RecurrenceSpecTest {

  static final Path CORPUS = Path.of("..", "corpus");

  /// Records worked out differently from what their project expects, by
  /// where they are, and why.
  static final Map<String, String> DIFFERENT = Map.of(
      "libical:435", "libical has no answer: it has not implemented BYWEEKNO with BYMONTH and BYMONTHDAY",
      "icaljs:685", "BYSETPOS=0 is no position: ICAL.js makes no instance, and this reader leaves the event out with a warning");

  /// One record: a rule, its start, and the instances expected.
  record Spec(String where, String comment, String rule, String start, @Nullable String startAt, @Nullable Integer max,
      boolean noInstance, List<String> instances) {

    @Override
    public String toString() {
      return where + " " + rule + " from " + start;
    }
  }

  static Stream<Spec> libical() {
    return specs(CORPUS.resolve("libical/icalrecur_test.txt"), "libical");
  }

  static Stream<Spec> icaljs() {
    return specs(CORPUS.resolve("icaljs/recur_iterator.txt"), "icaljs");
  }

  @ParameterizedTest
  @MethodSource("libical")
  void libicalRecord(Spec spec) {
    check(spec);
  }

  @ParameterizedTest
  @MethodSource("icaljs")
  void icaljsRecord(Spec spec) {
    check(spec);
  }

  /// The same instances, asked for in two windows split at each instance's
  /// day: a rule worked out from part way, with its `COUNT` counted from
  /// its start, gives what it gives from the start.
  @ParameterizedTest
  @MethodSource({"libical", "icaljs"})
  void windowsSplitAnywhere(Spec spec) {
    var calendar = calendar(spec);
    if (!calendar.warnings().isEmpty()) return;
    var from = date(spec.start()).minusDays(1);
    // Just past the last instance expected, so that no rule makes too many.
    var known = spec.instances().stream().filter(i -> !i.startsWith("*")).toList();
    var until = known.isEmpty() ? from.plusYears(1) : date(known.getLast()).plusDays(2);
    var whole = calendar.occurrences(from, until).stream().map(o -> basic(o.start())).toList();
    for (var at : whole.stream().map(RecurrenceSpecTest::date).distinct().toList()) {
      if (!at.isAfter(from)) continue;
      var split = new ArrayList<String>();
      for (var o : calendar.occurrences(from, at)) split.add(basic(o.start()));
      for (var o : calendar.occurrences(at, until)) split.add(basic(o.start()));
      assertEquals(String.join("\n", whole), String.join("\n", split), spec.where() + " split at " + at);
    }
  }

  static void check(Spec spec) {
    var expected = String.join("\n", expected(spec));
    var actual = String.join("\n", actual(spec));
    var why = DIFFERENT.get(spec.where());
    if (why == null) assertEquals(expected, actual, spec.where() + " " + spec.comment());
    else assertNotEquals(expected, actual, "Listed as different (" + why + "), but now the same");
  }

  /// What the project expects, with RFC 5545's rule for `DTSTART` applied.
  /// A rule ICAL.js makes no instance of has its start, the one RFC 5545
  /// always counts.
  static List<String> expected(Spec spec) {
    if (spec.noInstance()) return List.of(spec.start());
    var instances = new ArrayList<String>();
    // libical's runner skips the instances before START-AT.
    for (var i : spec.instances()) if (spec.startAt() == null || i.compareTo(spec.startAt()) >= 0) instances.add(i);
    if (prepends(spec)) {
      instances.addFirst(spec.start());
      var count = count(spec.rule());
      if (count != null) while (instances.size() > count) instances.removeLast();
    }
    return instances;
  }

  /// Whether RFC 5545 adds the start to what the project expects.
  static boolean prepends(Spec spec) {
    return spec.startAt() == null && !spec.instances().isEmpty() && !spec.instances().contains(spec.start());
  }

  /// The instances the reader works out, as the records write them.
  static List<String> actual(Spec spec) {
    var calendar = calendar(spec);
    if (!calendar.warnings().isEmpty()) return calendar.warnings();
    var first = date(spec.startAt() == null ? spec.start() : spec.startAt());
    var finite = count(spec.rule()) != null || spec.rule().toUpperCase(Locale.ROOT).contains("UNTIL=");
    // A record whose INSTANCES is libical's error has none to go by.
    var instances = spec.instances().stream().filter(i -> !i.startsWith("*")).toList();
    // Far enough to see a finite rule end, else just past the last expected.
    LocalDate until = finite || instances.isEmpty() || (spec.max() != null && spec.max() > instances.size())
        ? first.plusYears(instances.isEmpty() ? 10 : Math.max(10, date(instances.getLast()).getYear() - first.getYear() + 2))
        : date(instances.getLast()).plusDays(1);
    var out = new ArrayList<String>();
    for (var o : calendar.occurrences(first, until)) {
      var s = basic(o.start());
      if (spec.startAt() != null && s.compareTo(spec.startAt()) < 0) continue;
      out.add(s);
    }
    int extra = prepends(spec) ? 1 : 0;
    int max = spec.noInstance() ? 1 : spec.max() != null ? spec.max() + extra : finite ? Integer.MAX_VALUE : instances.size() + extra;
    return out.size() > max ? out.subList(0, max) : out;
  }

  /// The event a record describes, read in UTC.
  static IcalCalendar calendar(Spec spec) {
    var text = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//snaphop-ical//recurrence spec//EN
        BEGIN:VEVENT
        UID:spec
        DTSTAMP:20260101T000000Z
        DTSTART:%s
        RRULE:%s
        END:VEVENT
        END:VCALENDAR
        """.formatted(spec.start(), spec.rule());
    return IcalReader.builder()
        .zone(ZoneOffset.UTC)
        .mostFrequent(IcalRecurrence.Frequency.SECONDLY)
        .build()
        .read(text);
  }

  static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuuMMdd");
  static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss");

  static String basic(IcalTime t) {
    return switch (t) {
      case IcalTime.Date d -> DATE.format(d.date());
      case IcalTime.Floating f -> LOCAL.format(f.dateTime());
      case IcalTime.DateTime d -> LOCAL.format(d.dateTime().withZoneSameInstant(ZoneOffset.UTC)) + "Z";
    };
  }

  static LocalDate date(String basic) {
    return LocalDate.parse(basic.substring(0, 8), DATE);
  }

  static @Nullable Integer count(String rule) {
    for (var part : rule.split(";", -1)) {
      if (part.toUpperCase(Locale.ROOT).startsWith("COUNT=")) return Integer.parseInt(part.substring(6));
    }
    return null;
  }

  /// Reads records separated by blank lines: `# comment`, `RRULE:`,
  /// `DTSTART:`, and optionally `START-AT:` and `MAX:`, then `INSTANCES:`.
  static Stream<Spec> specs(Path file, String source) {
    List<String> lines;
    try {
      lines = Files.readAllLines(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    var out = new ArrayList<Spec>();
    var comment = new ArrayList<String>();
    String rule = null;
    String start = null;
    String startAt = null;
    Integer max = null;
    boolean noInstance = false;
    String instances = null;
    int at = 0;
    for (int i = 0; i <= lines.size(); i++) {
      var line = i < lines.size() ? lines.get(i) : "";
      if (line.isBlank()) {
        if (rule != null && start != null) {
          var list = instances == null || instances.isBlank() ? List.<String>of()
              : Stream.of(instances.split(",", -1)).map(String::strip).collect(Collectors.toList());
          out.add(new Spec(source + ":" + at, String.join(" ", comment), rule, start, startAt, max, noInstance, list));
        }
        comment.clear();
        rule = start = startAt = instances = null;
        max = null;
        noInstance = false;
        continue;
      }
      if (line.startsWith("#")) {
        comment.add(line.substring(1).strip());
      } else if (line.startsWith("RRULE:")) {
        rule = line.substring(6);
        at = i + 1;
      } else if (line.startsWith("DTSTART:")) {
        start = line.substring(8);
      } else if (line.startsWith("START-AT:")) {
        startAt = line.substring(9);
      } else if (line.startsWith("NO-INSTANCE:")) {
        noInstance = true;
      } else if (line.startsWith("MAX:")) {
        max = Integer.parseInt(line.substring(4));
      } else if (line.startsWith("INSTANCES:")) {
        instances = line.substring(10);
      }
    }
    return out.stream();
  }
}
