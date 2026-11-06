package com.snaphop.ical;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneOffsetTransitionRule;
import java.time.zone.ZoneRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/// Writes calendar text for [IcalWriter], and the values of times that
/// rules and messages use.
final class Writing {

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuuMMdd");
  private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss");

  /// The year after the last a `VTIMEZONE` covers, as [Zones] reads one.
  private static final int LAST_YEAR = 2200;

  /// A zone that times are written in: its `TZID`, rules, and the first
  /// instant written in it.
  private record Zone(String tzid, ZoneRules rules, Instant first) {}

  private final String stamp;
  private final Map<String, Zone> zones = new TreeMap<>();
  private final Lines events = new Lines();

  private Writing(DefaultIcalWriter writer) {
    var s = writer.stamp() != null ? writer.stamp() : Instant.now();
    this.stamp = value(new IcalTime.DateTime(s.truncatedTo(ChronoUnit.SECONDS).atZone(ZoneOffset.UTC)), true);
  }

  static void write(DefaultIcalWriter writer, List<? extends IcalEvent> events, StringBuilder out) {
    var w = new Writing(writer);
    for (var e : events) w.event((DefaultIcalEvent) e);
    var lines = new Lines();
    lines.line("BEGIN:VCALENDAR");
    lines.line("VERSION:2.0");
    lines.line("PRODID:" + text(writer.prodId()));
    lines.line("CALSCALE:GREGORIAN");
    var name = writer.name();
    if (name != null) {
      lines.line("NAME:" + text(name));
      lines.line("X-WR-CALNAME:" + text(name));
    }
    var refresh = writer.refreshInterval();
    if (refresh != null) {
      var d = duration(new Length(0, refresh.truncatedTo(ChronoUnit.SECONDS)));
      lines.line("REFRESH-INTERVAL;VALUE=DURATION:" + d);
      lines.line("X-PUBLISHED-TTL:" + d);
    }
    for (var z : w.zones.values()) vtimezone(z, lines);
    out.append(lines.out).append(w.events.out).append("END:VCALENDAR\r\n");
  }

  private void event(DefaultIcalEvent e) {
    events.line("BEGIN:VEVENT");
    events.line("UID:" + text(e.uid()));
    events.line("DTSTAMP:" + stamp);
    time("DTSTART", e.start(), e);
    if (!(e.start() instanceof IcalTime.Date) && e.length().days() != 0) {
      // Whole days, which last a day on the wall clock, only DURATION says.
      events.line("DURATION:" + duration(e.length()));
    } else {
      time("DTEND", e.end(), e);
    }
    var rid = e.recurrenceIdOrNull();
    if (rid != null) time("RECURRENCE-ID", rid, e);
    for (var rule : e.rules()) events.line("RRULE:" + rule(rule, e));
    for (var r : e.rdates()) {
      var end = r.end();
      if (end == null) {
        time("RDATE", r.start(), e);
      } else {
        period(r.start(), end, e);
      }
    }
    for (var x : e.exceptionDates()) time("EXDATE", x, e);
    if (!e.summary().isEmpty()) events.line("SUMMARY:" + text(e.summary()));
    if (!e.location().isEmpty()) events.line("LOCATION:" + text(e.location()));
    if (!e.description().isEmpty()) events.line("DESCRIPTION:" + text(e.description()));
    if (e.status() != IcalEvent.Status.NONE) events.line("STATUS:" + e.status());
    events.line("END:VEVENT");
  }

  /// A rule's text, with its `UNTIL` in the form RFC 5545 requires for the
  /// event's start: a date for a date, UTC for a date-time, and a floating
  /// date-time for a floating one. It ends the rule at the same instance.
  private static String rule(DefaultIcalRecurrence rule, DefaultIcalEvent e) {
    var until = rule.untilOrNull();
    if (until == null) return rule.text();
    IcalTime u = switch (e.start()) {
      case IcalTime.Date d -> switch (until) {
        case IcalTime.Date x -> x;
        case IcalTime.DateTime x -> new IcalTime.Date(x.dateTime().toLocalDate());
        case IcalTime.Floating x -> new IcalTime.Date(x.dateTime().toLocalDate());
      };
      case IcalTime.Floating f -> switch (until) {
        case IcalTime.Floating x -> x;
        case IcalTime.DateTime x -> new IcalTime.Floating(x.dateTime().toLocalDateTime());
        case IcalTime.Date x -> new IcalTime.Floating(x.date().atTime(23, 59, 59));
      };
      case IcalTime.DateTime d -> {
        var tz = e.tz() != null ? e.tz() : new JdkTz(d.dateTime().getZone());
        yield switch (until) {
          case IcalTime.DateTime x -> x;
          case IcalTime.Floating x -> new IcalTime.DateTime(tz.at(x.dateTime()));
          case IcalTime.Date x -> new IcalTime.DateTime(tz.at(x.date().plusDays(1).atStartOfDay()).minusSeconds(1));
        };
      }
    };
    var parts = new ArrayList<String>();
    for (var part : rule.text().split(";", -1)) {
      int eq = part.indexOf('=');
      parts.add(eq > 0 && part.substring(0, eq).strip().equalsIgnoreCase("UNTIL") ? "UNTIL=" + value(u, true) : part);
    }
    return String.join(";", parts);
  }

  /// A property whose value is a time, in its form: a date, a floating
  /// date-time, a date-time in UTC, or one with its zone's `TZID`.
  private void time(String name, IcalTime t, DefaultIcalEvent e) {
    events.line(switch (t) {
      case IcalTime.Date d -> name + ";VALUE=DATE:" + DATE.format(d.date());
      case IcalTime.Floating f -> name + ":" + LOCAL.format(f.dateTime());
      case IcalTime.DateTime d -> {
        var zone = zone(d.dateTime(), e);
        yield zone == null ? name + ":" + value(t, true) : name + ";TZID=" + param(zone) + ":" + LOCAL.format(d.dateTime());
      }
    });
  }

  /// An `RDATE` period: the end in the start's form.
  private void period(IcalTime start, IcalTime end, DefaultIcalEvent e) {
    events.line(switch (start) {
      case IcalTime.Date d -> "RDATE;VALUE=DATE:" + DATE.format(d.date());
      case IcalTime.Floating f -> "RDATE;VALUE=PERIOD:" + LOCAL.format(f.dateTime()) + "/" + LOCAL.format(Times.local(end, ZoneOffset.UTC));
      case IcalTime.DateTime d -> {
        var zone = zone(d.dateTime(), e);
        var to = Times.instant(end, d.dateTime().getZone()).atZone(d.dateTime().getZone());
        yield zone == null
            ? "RDATE;VALUE=PERIOD:" + value(start, true) + "/" + value(new IcalTime.DateTime(to), true)
            : "RDATE;VALUE=PERIOD;TZID=" + param(zone) + ":" + LOCAL.format(d.dateTime()) + "/" + LOCAL.format(to);
      }
    });
  }

  /// The `TZID` of a date-time, noting its zone to write, or null for UTC.
  /// A time read in a `VTIMEZONE` that is no JDK zone has a fixed offset,
  /// and is written in that `VTIMEZONE`'s `TZID` again.
  private @Nullable String zone(ZonedDateTime t, DefaultIcalEvent e) {
    var z = t.getZone();
    if (z.normalized().equals(ZoneOffset.UTC)) return null;
    var tzid = z.getId();
    var rules = z.getRules();
    if (z instanceof ZoneOffset && e.tz() instanceof RulesTz r) {
      tzid = r.tzid();
      rules = r.rules();
    }
    var instant = t.toInstant();
    var known = zones.get(tzid);
    if (known == null || instant.isBefore(known.first())) zones.put(tzid, new Zone(tzid, rules, instant));
    return tzid;
  }

  /// A `VTIMEZONE` from a zone's rules, from the start of the year before its
  /// first use: each change of offset until the zone's yearly rules take
  /// over, then those rules, as yearly `RRULE`s.
  private static void vtimezone(Zone zone, Lines out) {
    var rules = zone.rules();
    out.line("BEGIN:VTIMEZONE");
    out.line("TZID:" + zone.tzid());
    var local = LocalDate.of(zone.first().atOffset(ZoneOffset.UTC).getYear() - 1, 1, 1).atStartOfDay();
    var offset = rules.getOffset(local);
    var begin = local.toInstant(offset);
    var end = LocalDate.of(LAST_YEAR, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
    observance(out, rules.isDaylightSavings(begin), local, offset, offset, null);
    var yearly = yearly(rules, begin, end);
    var ruled = yearly.stream().map(y -> y.first().getInstant()).min(Instant::compareTo).orElse(end);
    for (var t = rules.nextTransition(begin); t != null && t.getInstant().isBefore(ruled); t = rules.nextTransition(t.getInstant())) {
      observance(out, rules.isDaylightSavings(t.getInstant()), t.getDateTimeBefore(), t.getOffsetBefore(), t.getOffsetAfter(), null);
    }
    for (var y : yearly) {
      var t = y.first();
      observance(out, rules.isDaylightSavings(t.getInstant()), t.getDateTimeBefore(), t.getOffsetBefore(), t.getOffsetAfter(), y.rule());
    }
    out.line("END:VTIMEZONE");
  }

  private static void observance(Lines out, boolean daylight, LocalDateTime start, ZoneOffset from, ZoneOffset to, @Nullable String rule) {
    var kind = daylight ? "DAYLIGHT" : "STANDARD";
    out.line("BEGIN:" + kind);
    out.line("DTSTART:" + LOCAL.format(start));
    out.line("TZOFFSETFROM:" + offset(from));
    out.line("TZOFFSETTO:" + offset(to));
    if (rule != null) out.line("RRULE:" + rule);
    out.line("END:" + kind);
  }

  /// A zone's yearly rule as an `RRULE`, with the first change it makes.
  private record Yearly(ZoneOffsetTransition first, String rule) {}

  /// The zone's yearly rules as `RRULE`s, each checked to make every
  /// change the rule makes until [#LAST_YEAR]; none if the zone has no
  /// yearly rules, or one can't be written so.
  private static List<Yearly> yearly(ZoneRules rules, Instant begin, Instant end) {
    var historical = rules.getTransitions();
    var after = historical.isEmpty() || historical.getLast().getInstant().isBefore(begin) ? begin : historical.getLast().getInstant();
    var out = new ArrayList<Yearly>();
    for (var r : rules.getTransitionRules()) {
      int year = after.atOffset(ZoneOffset.UTC).getYear() - 1;
      while (!r.createTransition(year).getInstant().isAfter(after)) year++;
      var first = r.createTransition(year);
      var expected = new ArrayList<LocalDateTime>();
      for (int y = year; r.createTransition(y).getInstant().isBefore(end); y++) expected.add(r.createTransition(y).getDateTimeBefore());
      var rule = rule(r, first.getDateTimeBefore(), expected, end);
      if (rule == null) return List.of();
      out.add(new Yearly(first, rule));
    }
    return out;
  }

  /// The first of some `RRULE`s for a yearly rule that makes `expected`.
  private static @Nullable String rule(ZoneOffsetTransitionRule r, LocalDateTime start, List<LocalDateTime> expected, Instant end) {
    var d = start.toLocalDate();
    var month = "FREQ=YEARLY;BYMONTH=" + d.getMonthValue();
    var day = d.getDayOfWeek().name().substring(0, 2);
    var candidates = new ArrayList<String>();
    candidates.add(month + ";BYDAY=-1" + day);
    candidates.add(month + ";BYDAY=" + ((d.getDayOfMonth() - 1) / 7 + 1) + day);
    int indicator = r.getDayOfMonthIndicator();
    if (indicator > 0) {
      for (int from = indicator; from <= indicator + 1 && from + 6 <= 31; from++) {
        var days = new ArrayList<String>();
        for (int i = from; i < from + 7; i++) days.add(Integer.toString(i));
        candidates.add(month + ";BYMONTHDAY=" + String.join(",", days) + ";BYDAY=" + day);
      }
    }
    candidates.add(month + ";BYMONTHDAY=" + d.getDayOfMonth());
    var to = LocalDateTime.ofInstant(end, ZoneOffset.UTC);
    for (var c : candidates) {
      var rule = DefaultIcalRecurrence.parse(c, (problem, consequence) -> {});
      var made = new ArrayList<LocalDateTime>();
      rule.expand(start, false, start, to, t -> true, t -> made.size() <= expected.size() && made.add(t));
      if (made.equals(expected)) return c;
    }
    return null;
  }

  private static String offset(ZoneOffset o) {
    int s = o.getTotalSeconds();
    var sign = s < 0 ? "-" : "+";
    s = Math.abs(s);
    var hm = sign + two(s / 3600) + two(s / 60 % 60);
    return s % 60 == 0 ? hm : hm + two(s % 60);
  }

  private static String two(int n) {
    return n < 10 ? "0" + n : Integer.toString(n);
  }

  /// A time's value: a date, a floating date-time, or a date-time, in UTC
  /// with a `Z` when `utc` is true, else on its own wall clock.
  static String value(IcalTime t, boolean utc) {
    return switch (t) {
      case IcalTime.Date d -> DATE.format(d.date());
      case IcalTime.Floating f -> LOCAL.format(f.dateTime());
      case IcalTime.DateTime d -> utc ? LOCAL.format(d.dateTime().withZoneSameInstant(ZoneOffset.UTC)) + "Z" : LOCAL.format(d.dateTime());
    };
  }

  /// The kind of time `t` is, for messages: "a date".
  static String kind(IcalTime t) {
    return switch (t) {
      case IcalTime.Date d -> "a date";
      case IcalTime.DateTime d -> "a date-time";
      case IcalTime.Floating f -> "a floating date-time";
    };
  }

  /// A `DURATION` value, such as `P1DT2H30M`.
  static String duration(Length length) {
    var out = new StringBuilder("P");
    if (length.days() != 0) out.append(length.days()).append('D');
    var time = length.time();
    if (!time.isZero() || length.days() == 0) {
      out.append('T');
      long s = time.toSeconds();
      if (s / 3600 > 0) out.append(s / 3600).append('H');
      if (s / 60 % 60 > 0) out.append(s / 60 % 60).append('M');
      if (s % 60 > 0 || s == 0) out.append(s % 60).append('S');
    }
    return out.toString();
  }

  /// Text as a `TEXT` value: escaped, with line breaks as `\n`, and without
  /// the control characters RFC 5545 doesn't allow.
  static String text(String s) {
    var out = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '\\' -> out.append("\\\\");
        case ';' -> out.append("\\;");
        case ',' -> out.append("\\,");
        case '\n' -> out.append("\\n");
        case '\r' -> {
          out.append("\\n");
          if (i + 1 < s.length() && s.charAt(i + 1) == '\n') i++;
        }
        default -> {
          if (c == '\t' || (c >= ' ' && c != 0x7f)) out.append(c);
        }
      }
    }
    return out.toString();
  }

  /// A parameter value, quoted when it has a character that ends one.
  private static String param(String value) {
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == ';' || c == ':' || c == ',') return "\"" + value + "\"";
    }
    return value;
  }

  /// Checks that `s` has no line break or other control character, nor a
  /// `"`: text that can't be escaped where it goes.
  static void oneLine(String what, String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if ((c < ' ' && c != '\t') || c == 0x7f || c == '"') {
        throw new IllegalArgumentException(what + " can't have a line break, other control character or '\"': " + Problems.quote(s));
      }
    }
  }

  /// Content lines, each folded at 75 octets of UTF-8, between characters,
  /// and ended with CRLF.
  private static final class Lines {
    final StringBuilder out = new StringBuilder();

    void line(String line) {
      int octets = 0;
      for (int i = 0; i < line.length(); ) {
        int cp = line.codePointAt(i);
        int n = cp < 0x80 ? 1 : cp < 0x800 ? 2 : cp < 0x10000 ? 3 : 4;
        if (octets + n > 75) {
          out.append("\r\n ");
          octets = 1;
        }
        out.appendCodePoint(cp);
        octets += n;
        i += Character.charCount(cp);
      }
      out.append("\r\n");
    }
  }
}
