package com.snaphop.ical.test;

import com.snaphop.ical.IcalCalendar;
import com.snaphop.ical.IcalEvent;
import com.snaphop.ical.IcalOccurrence;
import com.snaphop.ical.IcalTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/// One occurrence as our reader and the oracles can all describe it: its
/// id, title, and start and end on the wall clock of the reader's zone (a
/// date for all day, else a date-time).
///
/// Each oracle works out ids as [IcalOccurrence#id()] does, from the `UID`
/// and the original start.
record Row(String start, String end, String summary, String id) {

  static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");

  /// Rows, one a line, in order.
  static String render(List<Row> rows) {
    return rows.stream()
        .sorted(Comparator.comparing(Row::start).thenComparing(Row::id).thenComparing(Row::end))
        .map(r -> r.start + "/" + r.end + " " + r.summary + " [" + r.id + "]\n")
        .collect(Collectors.joining());
  }

  /// Whether an occurrence from `start` to `end` is in the window, as
  /// [IcalCalendar#occurrences(LocalDate, LocalDate)] says.
  static boolean inWindow(Instant start, Instant end, ZoneId zone, LocalDate from, LocalDate until) {
    var f = from.atStartOfDay(zone).toInstant();
    var u = until.atStartOfDay(zone).toInstant();
    return end.isAfter(start) ? start.isBefore(u) && end.isAfter(f) : !start.isBefore(f) && start.isBefore(u);
  }

  static String local(LocalDateTime t) {
    return LOCAL.format(t);
  }

  /// Our reader's occurrences as rows.
  static List<Row> ours(IcalCalendar calendar, ZoneId zone, LocalDate from, LocalDate until) {
    return calendar.occurrences(from, until).stream()
        .map(o -> new Row(text(o.start(), zone), text(o.end(), zone), o.event().summary(), o.id()))
        .toList();
  }

  /// An event as read, on one line: its `UID`, title, start and end (a date,
  /// a wall clock time if floating, else an instant), `RECURRENCE-ID`,
  /// status, place and notes.
  static String event(String uid, String summary, String description, String location, String status, String start,
      String end, String recurrenceId) {
    return uid + " | " + summary + " | " + start + " / " + end + (recurrenceId.isEmpty() ? "" : " | changes " + recurrenceId)
        + (status.isEmpty() ? "" : " | " + status.toUpperCase(Locale.ROOT)) + (location.isEmpty() ? "" : " | @ " + location)
        + (description.isEmpty() ? "" : " | " + description.replace("\r", "").replace("\n", "\\n"));
  }

  /// Our reader's events, as [#event] writes them.
  static List<String> events(IcalCalendar calendar) {
    return calendar.events().stream().map(e -> {
      var rid = e.recurrenceIdOrNull();
      return event(e.uid(), e.summary(), e.description(), e.location(),
          e.status() == IcalEvent.Status.NONE ? "" : e.status().name(), key(e.start()), key(e.end()), rid == null ? "" : key(rid));
    }).toList();
  }

  /// A time as [#event] writes it.
  static String key(IcalTime t) {
    return switch (t) {
      case IcalTime.Date d -> d.date().toString();
      case IcalTime.Floating f -> local(f.dateTime());
      case IcalTime.DateTime d -> d.dateTime().toInstant().toString();
    };
  }

  static String text(IcalTime t, ZoneId zone) {
    return switch (t) {
      case IcalTime.Date d -> d.date().toString();
      case IcalTime.Floating f -> local(f.dateTime());
      case IcalTime.DateTime d -> local(d.dateTime().withZoneSameInstant(zone).toLocalDateTime());
    };
  }
}
