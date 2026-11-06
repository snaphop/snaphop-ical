package com.snaphop.ical;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/// Reads calendar text into a [DefaultIcalCalendar]: the events of its
/// components, each checked, with what can't be read reported.
final class Reading {

  private final DefaultIcalReader reader;
  private final Problems problems;
  private Zones zones = new Zones(List.of());
  private final Set<String> unknownZones = new HashSet<>();

  private Reading(DefaultIcalReader reader, Consumer<? super IcalWarning> warnings) {
    this.reader = reader;
    this.problems = new Problems(reader.mode(), warnings);
  }

  /// Reads `text`, keeping its warnings in the calendar.
  static DefaultIcalCalendar read(CharSequence text, DefaultIcalReader reader) {
    var warnings = new ArrayList<IcalWarning>();
    return new Reading(reader, warnings::add).calendar(text, warnings);
  }

  /// Reads `text`, handing its warnings to `warnings` as they are found.
  static DefaultIcalCalendar read(CharSequence text, DefaultIcalReader reader, Consumer<? super IcalWarning> warnings) {
    return new Reading(reader, warnings).calendar(text, List.of());
  }

  private DefaultIcalCalendar calendar(CharSequence text, List<IcalWarning> warnings) {
    var calendars = ContentLines.read(text, problems);
    if (calendars.isEmpty()) {
      // Not reached: the text starts a VCALENDAR, which ContentLines ends
      // when the text does, if nothing else does.
      throw new IcalParseException("Not iCalendar: the text has no VCALENDAR.", 0);
    }
    var calendar = calendars.getFirst();
    for (var more : calendars.subList(1, calendars.size())) {
      problems.report(more.line(), "The text has more than one VCALENDAR", "their events are read as one calendar's",
          Problems.quote("BEGIN:VCALENDAR"));
    }
    if (problems.strict()) {
      var version = calendar.first("VERSION");
      if (version == null) {
        problems.strictly(calendar.line(), "The VCALENDAR has no VERSION, which RFC 5545 requires.");
      } else if (!version.value().strip().equals("2.0")) {
        problems.strictly(version.line(), "VERSION is not 2.0, so this is not RFC 5545 iCalendar: " + Problems.quote(version.text()));
      }
      if (calendar.first("PRODID") == null) {
        problems.strictly(calendar.line(), "The VCALENDAR has no PRODID, which RFC 5545 requires.");
      }
    }
    var name = text(calendar, "X-WR-CALNAME");
    if (name.isBlank()) name = text(calendar, "NAME");

    var vtimezones = new ArrayList<Comp>();
    for (var c : calendars) vtimezones.addAll(c.comps("VTIMEZONE"));
    zones = new Zones(vtimezones);

    var events = new ArrayList<IcalEvent>();
    var masters = new HashSet<String>();
    var changes = new HashSet<String>();
    for (var c : calendars) {
      for (var comp : c.comps("VEVENT")) {
        var e = event(comp);
        if (e == null) continue;
        var rid = e.recurrenceIdOrNull();
        boolean added = rid == null ? masters.add(e.uid()) : changes.add(e.uid() + "\n" + Times.key(rid));
        if (!added) {
          problems.report(comp.line(), "Another event before it has the UID " + Problems.quote(e.uid())
              + (rid == null ? " and no RECURRENCE-ID" : " and the same RECURRENCE-ID"), "it is left out", describe(comp));
          continue;
        }
        events.add(e);
      }
    }
    return new DefaultIcalCalendar(name.isBlank() ? null : name.strip(), events, warnings, reader);
  }

  /// A `VEVENT`, or null if it is left out.
  private @Nullable DefaultIcalEvent event(Comp c) {
    var where = describe(c);
    var uid = one(c, "UID");
    if (uid == null || uid.value().isBlank()) {
      problems.report(c.line(), "An event has no UID", "it is left out", where);
      return null;
    }
    if (c.first("DTSTAMP") == null) {
      problems.strictly(c.line(), "An event has no DTSTAMP, which RFC 5545 requires: " + where);
    }
    var startProp = one(c, "DTSTART");
    if (startProp == null) {
      problems.report(c.line(), "An event has no DTSTART", "it is left out", where);
      return null;
    }
    Timed start;
    try {
      start = time(startProp, startProp.value());
    } catch (IllegalArgumentException e) {
      problems.report(startProp.line(), "DTSTART " + e.getMessage(), "the event is left out", Problems.quote(startProp.text()));
      return null;
    }

    var length = start.time() instanceof IcalTime.Date ? Length.ONE_DAY : Length.ZERO;
    IcalTime end = null;
    var endProp = one(c, "DTEND");
    var durationProp = one(c, "DURATION");
    if (endProp != null && durationProp != null) {
      problems.report(durationProp.line(), "An event has both DTEND and DURATION, which RFC 5545 does not allow",
          "DURATION is left out", Problems.quote(durationProp.text()));
      durationProp = null;
    }
    if (endProp != null) {
      try {
        var e = time(endProp, endProp.value()).time();
        var exact = exact(start.time(), e, start.tz());
        if (exact == null) {
          problems.report(endProp.line(), "DTEND is " + kind(e) + " but DTSTART is " + kind(start.time()),
              "DTEND is left out", Problems.quote(endProp.text()));
        } else if (exact.negative()) {
          problems.report(endProp.line(), "DTEND is before DTSTART", "DTEND is left out", Problems.quote(endProp.text()));
        } else {
          length = exact;
          end = e;
        }
      } catch (IllegalArgumentException e) {
        problems.report(endProp.line(), "DTEND " + e.getMessage(), "it is left out", Problems.quote(endProp.text()));
      }
    } else if (durationProp != null) {
      try {
        var d = Values.duration(durationProp.value().strip());
        if (d.negative()) {
          problems.report(durationProp.line(), "DURATION is negative", "it is left out", Problems.quote(durationProp.text()));
        } else if (start.time() instanceof IcalTime.Date && !d.time().isZero()) {
          problems.report(durationProp.line(), "DURATION has a time of day but DTSTART is a date",
              "only its days are used", Problems.quote(durationProp.text()));
          length = new Length(d.days() + (int) d.time().toDays(), Duration.ZERO);
        } else {
          length = d;
        }
      } catch (IllegalArgumentException e) {
        problems.report(durationProp.line(), "DURATION " + e.getMessage(), "it is left out", Problems.quote(durationProp.text()));
      }
    }
    if (end == null) end = Times.plus(start.time(), length, start.tz());

    var status = IcalEvent.Status.NONE;
    var statusProp = one(c, "STATUS");
    if (statusProp != null) {
      switch (statusProp.value().strip().toUpperCase(Locale.ROOT)) {
        case "TENTATIVE" -> status = IcalEvent.Status.TENTATIVE;
        case "CONFIRMED" -> status = IcalEvent.Status.CONFIRMED;
        case "CANCELLED" -> status = IcalEvent.Status.CANCELLED;
        default -> problems.report(statusProp.line(), "STATUS is not TENTATIVE, CONFIRMED or CANCELLED",
            "the event has none", Problems.quote(statusProp.text()));
      }
    }

    var rules = new ArrayList<DefaultIcalRecurrence>();
    for (var p : c.all("RRULE")) {
      var rule = rule(p, start.time(), where);
      if (rule == null) return null;
      rules.add(rule);
    }
    var exceptionRules = new ArrayList<DefaultIcalRecurrence>();
    for (var p : c.all("EXRULE")) {
      problems.strictly(p.line(), "EXRULE was taken out of iCalendar by RFC 5545: " + Problems.quote(p.text()));
      var rule = rule(p, start.time(), where);
      if (rule == null) return null;
      exceptionRules.add(rule);
    }

    var exceptionDates = new ArrayList<IcalTime>();
    for (var p : c.all("EXDATE")) {
      for (var v : p.value().split(",", -1)) {
        if (v.isBlank()) continue;
        try {
          exceptionDates.add(time(p, v.strip()).time());
        } catch (IllegalArgumentException e) {
          problems.report(p.line(), "EXDATE " + e.getMessage(), "that date is left out", Problems.quote(p.text()));
        }
      }
    }
    var rdates = new ArrayList<RDate>();
    for (var p : c.all("RDATE")) {
      for (var v : p.value().split(",", -1)) {
        if (v.isBlank()) continue;
        try {
          rdates.add(rdate(p, v.strip()));
        } catch (IllegalArgumentException e) {
          problems.report(p.line(), "RDATE " + e.getMessage(), "that date is left out", Problems.quote(p.text()));
        }
      }
    }

    IcalTime recurrenceId = null;
    var ridProp = one(c, "RECURRENCE-ID");
    if (ridProp != null) {
      try {
        recurrenceId = time(ridProp, ridProp.value()).time();
      } catch (IllegalArgumentException e) {
        problems.report(ridProp.line(), "RECURRENCE-ID " + e.getMessage(), "the event is left out", Problems.quote(ridProp.text()));
        return null;
      }
      var range = ridProp.param("RANGE");
      if (range != null && range.equalsIgnoreCase("THISANDFUTURE")) {
        problems.report(ridProp.line(), "RANGE=THISANDFUTURE is not supported", "the event changes only that one time",
            Problems.quote(ridProp.text()));
      }
    }

    return new DefaultIcalEvent(
        Values.text(uid.value()).strip(),
        text(c, "SUMMARY"),
        text(c, "DESCRIPTION"),
        text(c, "LOCATION"),
        start.time(),
        end,
        status,
        exceptionDates,
        recurrenceId,
        start.tz(),
        length,
        rules,
        exceptionRules,
        rdates,
        c.line());
  }

  /// A rule, or null if the event is left out for it.
  private @Nullable DefaultIcalRecurrence rule(Prop p, IcalTime start, String where) {
    DefaultIcalRecurrence rule;
    try {
      rule = DefaultIcalRecurrence.parse(p.value(), (problem, consequence) ->
          problems.report(p.line(), p.name() + ": " + problem, consequence, Problems.quote(p.text())));
    } catch (IllegalArgumentException e) {
      problems.report(p.line(), p.name() + ": " + e.getMessage(), "the event is left out", Problems.quote(p.text()));
      return null;
    }
    if (start instanceof IcalTime.Date && rule.frequency().compareTo(IcalRecurrence.Frequency.DAILY) < 0) {
      problems.report(p.line(), p.name() + " repeats more often than daily but DTSTART is a date", "the event is left out",
          Problems.quote(p.text()));
      return null;
    }
    if (rule.effectiveFrequency().compareTo(reader.mostFrequent()) < 0) {
      problems.report(p.line(), "The event repeats more often than " + word(reader.mostFrequent()), "it is left out", where);
      return null;
    }
    var until = rule.untilOrNull();
    if (problems.strict() && until != null) {
      boolean ok = switch (start) {
        case IcalTime.Date d -> until instanceof IcalTime.Date;
        case IcalTime.Floating f -> until instanceof IcalTime.Floating;
        case IcalTime.DateTime t -> until instanceof IcalTime.DateTime;
      };
      if (!ok) {
        problems.strictly(p.line(), "UNTIL must be " + (start instanceof IcalTime.DateTime ? "in UTC" : kind(start))
            + " as DTSTART is " + kind(start) + ": " + Problems.quote(p.text()));
      }
    }
    return rule;
  }

  private static String word(IcalRecurrence.Frequency f) {
    return switch (f) {
      case SECONDLY -> "every second";
      case MINUTELY -> "every minute";
      case HOURLY -> "hourly";
      case DAILY -> "daily";
      case WEEKLY -> "weekly";
      case MONTHLY -> "monthly";
      case YEARLY -> "yearly";
    };
  }

  /// A time, and for a date-time in a zone, the zone.
  record Timed(IcalTime time, @Nullable Tz tz) {}

  /// The time `value` of `p`, in the form its `VALUE` and `TZID` say.
  private Timed time(Prop p, String value) {
    var v = value.strip();
    var type = p.param("VALUE");
    if ((type != null && type.equalsIgnoreCase("DATE")) || (type == null && v.length() == 8)) {
      return new Timed(new IcalTime.Date(Values.date(v)), null);
    }
    if (type != null && !type.equalsIgnoreCase("DATE-TIME")) {
      throw new IllegalArgumentException("has VALUE=" + type + ", where a DATE or DATE-TIME should be");
    }
    var local = Values.dateTime(Values.withoutZ(v));
    if (Values.utc(v)) {
      return new Timed(new IcalTime.DateTime(local.atZone(ZoneOffset.UTC)), new JdkTz(ZoneOffset.UTC));
    }
    var tzid = p.param("TZID");
    if (tzid == null) return new Timed(new IcalTime.Floating(local), null);
    var tz = zones.find(tzid);
    if (tz == null) {
      if (unknownZones.add(tzid.strip())) {
        problems.report(p.line(), "The time zone " + Problems.quote(tzid)
            + " is not a zone name this reader knows, nor a VTIMEZONE of the calendar", "its times are read as floating",
            Problems.quote(p.text()));
      }
      return new Timed(new IcalTime.Floating(local), null);
    }
    try {
      return new Timed(new IcalTime.DateTime(tz.at(local)), tz);
    } catch (DateTimeException e) {
      throw new IllegalArgumentException(Problems.quote(v) + " is not a time in " + Problems.quote(tzid));
    }
  }

  private RDate rdate(Prop p, String value) {
    int slash = value.indexOf('/');
    var type = p.param("VALUE");
    if (slash < 0 && (type == null || !type.equalsIgnoreCase("PERIOD"))) return new RDate(time(p, value).time(), null);
    if (slash < 0) throw new IllegalArgumentException(Problems.quote(value) + " is not a PERIOD (start/end or start/duration)");
    var s = time(withValue(p), value.substring(0, slash));
    var e = value.substring(slash + 1);
    if (e.startsWith("P") || e.startsWith("+P") || e.startsWith("-P")) {
      return new RDate(s.time(), Times.plus(s.time(), Values.duration(e), s.tz()));
    }
    var end = time(withValue(p), e).time();
    var exact = exact(s.time(), end, s.tz());
    if (exact == null || exact.negative()) {
      throw new IllegalArgumentException(Problems.quote(value) + " ends before it starts, or in another form than it starts");
    }
    return new RDate(s.time(), Times.plus(s.time(), exact, s.tz()));
  }

  /// `p` read as a `DATE-TIME`, for the parts of a `PERIOD`.
  private static Prop withValue(Prop p) {
    var params = new java.util.HashMap<>(p.params());
    params.remove("VALUE");
    return new Prop(p.name(), params, p.value(), p.line(), p.text());
  }

  /// The exact length from `start` to `end`, or null if they are not both
  /// dates or both date-times.
  private static @Nullable Length exact(IcalTime start, IcalTime end, @Nullable Tz tz) {
    return switch (start) {
      case IcalTime.Date s -> end instanceof IcalTime.Date e ? new Length((int) ChronoUnit.DAYS.between(s.date(), e.date()), Duration.ZERO) : null;
      case IcalTime.Floating s -> switch (end) {
        case IcalTime.Floating e -> new Length(0, Duration.between(s.dateTime(), e.dateTime()));
        case IcalTime.DateTime e -> new Length(0, Duration.between(s.dateTime(), e.dateTime().toLocalDateTime()));
        case IcalTime.Date e -> null;
      };
      case IcalTime.DateTime s -> switch (end) {
        case IcalTime.DateTime e -> new Length(0, Duration.between(s.dateTime(), e.dateTime()));
        case IcalTime.Floating e -> new Length(0, Duration.between(s.dateTime(),
            tz == null ? e.dateTime().atZone(s.dateTime().getZone()) : tz.at(e.dateTime())));
        case IcalTime.Date e -> null;
      };
    };
  }

  private static String kind(IcalTime t) {
    return switch (t) {
      case IcalTime.Date d -> "a date";
      case IcalTime.DateTime d -> "a date-time";
      case IcalTime.Floating f -> "a floating date-time";
    };
  }

  /// The one property `name` of `c`, reporting more than one.
  private @Nullable Prop one(Comp c, String name) {
    var all = c.all(name);
    if (all.size() > 1) {
      problems.report(all.get(1).line(), "An event has more than one " + name, "the first is used", Problems.quote(all.get(1).text()));
    }
    return all.isEmpty() ? null : all.getFirst();
  }

  private static String text(Comp c, String name) {
    var p = c.first(name);
    return p == null ? "" : Values.text(p.value());
  }

  /// Which event: its title and start as written.
  static String describe(Comp c) {
    var title = text(c, "SUMMARY").strip();
    var start = c.first("DTSTART");
    return (title.isEmpty() ? "(no title)" : Problems.quote(title)) + " at " + (start == null ? "no start" : start.value().strip());
  }
}
