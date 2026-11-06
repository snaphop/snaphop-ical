package com.snaphop.ical;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/// What is done with an [IcalTime] when working out occurrences.
final class Times {

  private Times() {}

  private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");

  /// Which time it is, as text that is the same however it was written: a
  /// date, an instant in UTC, or a wall clock time.
  static String key(IcalTime t) {
    return switch (t) {
      case IcalTime.Date d -> d.date().toString();
      case IcalTime.DateTime d -> d.dateTime().toInstant().toString();
      case IcalTime.Floating f -> LOCAL.format(f.dateTime());
    };
  }

  /// `t` plus `length`: its days on the wall clock, then its exact time.
  static IcalTime plus(IcalTime t, Length length, @Nullable Tz tz) {
    return switch (t) {
      case IcalTime.Date d -> new IcalTime.Date(d.date().plusDays(length.days()));
      case IcalTime.Floating f -> new IcalTime.Floating(length.plus(f.dateTime()));
      case IcalTime.DateTime d -> {
        var z = tz == null ? new JdkTz(d.dateTime().getZone()) : tz;
        var e = length.days() == 0 ? d.dateTime() : z.at(d.dateTime().toLocalDateTime().plusDays(length.days()));
        yield new IcalTime.DateTime(z.at(e.toInstant().plus(length.time())));
      }
    };
  }

  /// When `t` is, with a date taken as its start and a floating time as
  /// on the wall clock in `zone`.
  static Instant instant(IcalTime t, ZoneId zone) {
    return switch (t) {
      case IcalTime.Date d -> d.date().atStartOfDay(zone).toInstant();
      case IcalTime.Floating f -> f.dateTime().atZone(zone).toInstant();
      case IcalTime.DateTime d -> d.dateTime().toInstant();
    };
  }

  /// The wall clock time of `t` in `zone`.
  static LocalDateTime local(IcalTime t, ZoneId zone) {
    return switch (t) {
      case IcalTime.Date d -> d.date().atStartOfDay();
      case IcalTime.Floating f -> f.dateTime();
      case IcalTime.DateTime d -> d.dateTime().withZoneSameInstant(zone).toLocalDateTime();
    };
  }
}

/// Works out a calendar's occurrences in a window of days.
final class Occurrences {

  private final ZoneId zone;
  private final IcalReader.Cancelled cancelled;
  private final LocalDate from;
  private final LocalDate until;
  private final Instant fromInstant;
  private final Instant untilInstant;
  private final List<DefaultIcalOccurrence> out = new ArrayList<>();

  private Occurrences(DefaultIcalReader reader, LocalDate from, LocalDate until) {
    this.zone = reader.zone();
    this.cancelled = reader.cancelled();
    this.from = from;
    this.until = until;
    this.fromInstant = from.atStartOfDay(zone).toInstant();
    this.untilInstant = until.atStartOfDay(zone).toInstant();
  }

  static List<IcalOccurrence> of(DefaultIcalCalendar calendar, LocalDate from, LocalDate until) {
    var o = new Occurrences(calendar.reader(), from, until);
    o.add(calendar.events());
    o.out.sort(Comparator.comparing((DefaultIcalOccurrence x) -> Times.instant(x.start(), o.zone))
        .thenComparing(DefaultIcalOccurrence::id));
    return List.copyOf(o.out);
  }

  private void add(List<IcalEvent> events) {
    // A repeating event's changed times are events of their own, with its
    // UID and a RECURRENCE-ID: the time they change.
    var masters = new LinkedHashMap<String, DefaultIcalEvent>();
    var changes = new LinkedHashMap<String, List<DefaultIcalEvent>>();
    for (var e : events) {
      var d = (DefaultIcalEvent) e;
      if (d.recurrenceIdOrNull() == null) masters.put(d.uid(), d);
      else changes.computeIfAbsent(d.uid(), k -> new ArrayList<>()).add(d);
    }
    for (var m : masters.values()) {
      var changed = new HashSet<String>();
      for (var c : changes.getOrDefault(m.uid(), List.of())) {
        changed.add(Times.key(sameForm(c.recurrenceIdOrNull(), m)));
      }
      for (var i : instances(m)) {
        var key = Times.key(i.recurrenceId());
        if (changed.contains(key)) continue;
        var end = i.end() == null ? Times.plus(i.start(), m.length(), m.tz()) : i.end();
        add(m, m.repeats() ? m.uid() + "@" + key : m.uid(), i.start(), end, i.recurrenceId());
      }
    }
    for (var list : changes.values()) {
      for (var c : list) {
        var m = masters.get(c.uid());
        var rid = m == null ? c.recurrenceIdOrNull() : sameForm(c.recurrenceIdOrNull(), m);
        if (rid == null) continue;
        add(c, m != null && !m.repeats() ? c.uid() : c.uid() + "@" + Times.key(rid), c.start(), c.end(), rid);
      }
    }
  }

  private void add(DefaultIcalEvent event, String id, IcalTime start, IcalTime end, IcalTime recurrenceId) {
    if (cancelled == IcalReader.Cancelled.OMIT && event.cancelled()) return;
    var s = Times.instant(start, zone);
    var e = Times.instant(end, zone);
    boolean in = e.isAfter(s) ? s.isBefore(untilInstant) && e.isAfter(fromInstant) : !s.isBefore(fromInstant) && s.isBefore(untilInstant);
    if (!in) return;
    if (start instanceof IcalTime.Date d) {
      var last = end instanceof IcalTime.Date ed && ed.date().isAfter(d.date()) ? ed.date().minusDays(1) : d.date();
      out.add(new DefaultIcalOccurrence(event, id, start, end, recurrenceId, d.date(), last, null, null));
      return;
    }
    var ls = Times.local(start, zone);
    var le = Times.local(end, zone);
    var last = le.isAfter(ls) ? le.minusNanos(1).toLocalDate() : ls.toLocalDate();
    out.add(new DefaultIcalOccurrence(event, id, start, end, recurrenceId, ls.toLocalDate(), last, ls.toLocalTime(), le.toLocalTime()));
  }

  /// An instance of a repeating event: its start, its end if an `RDATE`
  /// period gave it one, and which instance it is, in the form of the
  /// event's start. A period keeps its own times.
  private record Instance(IcalTime start, @Nullable IcalTime end, IcalTime recurrenceId) {}

  /// The instances of an event that may be in the window: its rules' and
  /// `RDATE`s', less its exceptions.
  private List<Instance> instances(DefaultIcalEvent m) {
    if (!m.repeats()) return List.of(new Instance(m.start(), m.end(), m.start()));
    var starts = new LinkedHashMap<String, Instance>();
    if (m.rules().isEmpty()) starts.put(Times.key(m.start()), new Instance(m.start(), null, m.start()));
    for (var rule : m.rules()) {
      expand(m, rule, t -> starts.putIfAbsent(Times.key(t), new Instance(t, null, t)));
    }
    for (var r : m.rdates()) {
      var s = sameForm(r.start(), m);
      starts.putIfAbsent(Times.key(s), r.end() == null ? new Instance(s, null, s) : new Instance(r.start(), r.end(), s));
    }
    var excluded = new HashSet<String>();
    var excludedDays = new HashSet<LocalDate>();
    for (var x : m.exceptionDates()) {
      var s = sameForm(x, m);
      if (s instanceof IcalTime.Date d && !(m.start() instanceof IcalTime.Date)) excludedDays.add(d.date());
      else excluded.add(Times.key(s));
    }
    for (var rule : m.exceptionRules()) expand(m, rule, t -> excluded.add(Times.key(t)));
    var list = new ArrayList<Instance>();
    for (var i : starts.values()) {
      if (excluded.contains(Times.key(i.recurrenceId()))) continue;
      if (!excludedDays.isEmpty() && excludedDays.contains(Times.local(i.recurrenceId(), localZone(m)).toLocalDate())) continue;
      list.add(i);
    }
    return list;
  }

  /// Gives `out` each start of `rule` for `m` that may be in the window.
  private void expand(DefaultIcalEvent m, DefaultIcalRecurrence rule, Consumer<IcalTime> out) {
    var start = m.start();
    var tz = m.tz();
    LocalDateTime first = switch (start) {
      case IcalTime.Date d -> d.date().atStartOfDay();
      case IcalTime.Floating f -> f.dateTime();
      case IcalTime.DateTime d -> d.dateTime().toLocalDateTime();
    };
    // Wide enough for any zone's offset from the reader's, and the length.
    var lo = from.minusDays(2L + Math.max(0, m.length().days())).atStartOfDay().minus(m.length().time().abs());
    var hi = until.plusDays(2).atStartOfDay();
    rule.expand(first, start instanceof IcalTime.Date, lo, hi, withinUntil(rule, start, tz), t -> {
      if (t.isAfter(hi)) return false;
      out.accept(switch (start) {
        case IcalTime.Date d -> new IcalTime.Date(t.toLocalDate());
        case IcalTime.Floating f -> new IcalTime.Floating(t);
        case IcalTime.DateTime d -> new IcalTime.DateTime(tzOf(d, tz).at(t));
      });
      return true;
    });
  }

  /// Whether a start, on the wall clock of the event's start, is at or
  /// before the rule's `UNTIL`, in whatever form `UNTIL` is.
  private Predicate<LocalDateTime> withinUntil(DefaultIcalRecurrence rule, IcalTime start, @Nullable Tz tz) {
    var until = rule.untilOrNull();
    if (until == null) return t -> true;
    return switch (until) {
      case IcalTime.Date u -> t -> !t.toLocalDate().isAfter(u.date());
      case IcalTime.Floating u -> t -> !t.isAfter(u.dateTime());
      case IcalTime.DateTime u -> {
        var limit = u.dateTime().toInstant();
        yield switch (start) {
          case IcalTime.Date d -> t -> !t.toLocalDate().atStartOfDay(zone).toInstant().isAfter(limit);
          case IcalTime.Floating f -> t -> !t.atZone(zone).toInstant().isAfter(limit);
          case IcalTime.DateTime d -> {
            var z = tzOf(d, tz);
            yield t -> !z.at(t).toInstant().isAfter(limit);
          }
        };
      }
    };
  }

  /// `t` in the form of `m`'s start, so that it can be matched with the
  /// starts of `m`'s instances. A date stays a date.
  private IcalTime sameForm(@Nullable IcalTime t, DefaultIcalEvent m) {
    if (t == null) throw new IllegalStateException("no time");
    return switch (m.start()) {
      case IcalTime.Date d -> switch (t) {
        case IcalTime.Date x -> x;
        case IcalTime.DateTime x -> new IcalTime.Date(x.dateTime().toLocalDate());
        case IcalTime.Floating x -> new IcalTime.Date(x.dateTime().toLocalDate());
      };
      case IcalTime.DateTime d -> switch (t) {
        case IcalTime.Date x -> x;
        case IcalTime.DateTime x -> new IcalTime.DateTime(tzOf(d, m.tz()).at(x.dateTime().toInstant()));
        case IcalTime.Floating x -> new IcalTime.DateTime(tzOf(d, m.tz()).at(x.dateTime()));
      };
      case IcalTime.Floating d -> switch (t) {
        case IcalTime.Date x -> x;
        case IcalTime.DateTime x -> new IcalTime.Floating(x.dateTime().withZoneSameInstant(zone).toLocalDateTime());
        case IcalTime.Floating x -> x;
      };
    };
  }

  private static Tz tzOf(IcalTime.DateTime start, @Nullable Tz tz) {
    return tz != null ? tz : new JdkTz(start.dateTime().getZone());
  }

  /// The zone of the wall clock `m`'s starts are on, where a date is
  /// matched with them: an `EXDATE` of June 11 for an event at 03:00 UTC
  /// leaves out the one on June 11 in UTC, as ICAL.js does.
  private ZoneId localZone(DefaultIcalEvent m) {
    return m.start() instanceof IcalTime.DateTime d ? d.dateTime().getZone() : zone;
  }
}
