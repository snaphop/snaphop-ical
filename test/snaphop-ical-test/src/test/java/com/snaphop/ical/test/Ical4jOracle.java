package com.snaphop.ical.test;

import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.CalendarParserFactory;
import net.fortuna.ical4j.data.ContentHandlerContext;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.DateProperty;

/// ical4j as an oracle: occurrences worked out as agentic-todo's `todo-ical`
/// did with it. ical4j expands one event at a time, so this matches changed
/// times (`RECURRENCE-ID`) to their events and leaves out cancelled ones.
final class Ical4jOracle {

  private Ical4jOracle() {}

  static List<Row> occurrences(String text, ZoneId zone, LocalDate from, LocalDate until) throws IOException, ParserException {
    var calendar = read(text);
    var masters = new LinkedHashMap<String, VEvent>();
    var changes = new HashMap<String, List<VEvent>>();
    for (VEvent e : calendar.<VEvent>getComponents(Component.VEVENT)) {
      var uid = text(e, Property.UID);
      if (uid.isEmpty()) continue;
      if (e.getProperty(Property.RECURRENCE_ID).isPresent()) changes.computeIfAbsent(uid, k -> new ArrayList<>()).add(e);
      else masters.putIfAbsent(uid, e);
    }
    var out = new ArrayList<Row>();
    for (var entry : masters.entrySet()) {
      var uid = entry.getKey();
      var master = entry.getValue();
      var repeats = master.getProperty(Property.RRULE).isPresent() || master.getProperty(Property.RDATE).isPresent();
      var changed = new HashSet<String>();
      for (var c : changes.getOrDefault(uid, List.of())) changed.add(key(recurrenceId(c)));
      if (cancelled(master)) continue;
      for (var p : periods(master, zone, from, until)) {
        var k = key(p.getStart());
        if (changed.contains(k)) continue;
        add(out, repeats ? uid + "@" + k : uid, master, p.getStart(), p.getEnd(), zone, from, until);
      }
    }
    for (var list : changes.values()) {
      for (var c : list) {
        if (cancelled(c)) continue;
        var uid = text(c, Property.UID);
        var master = masters.get(uid);
        var repeats = master == null || master.getProperty(Property.RRULE).isPresent() || master.getProperty(Property.RDATE).isPresent();
        var start = start(c);
        var end = c.<Temporal>getEndDate(true).map(d -> d.getDate()).orElse(start);
        add(out, repeats ? uid + "@" + key(recurrenceId(c)) : uid, c, start, end, zone, from, until);
      }
    }
    return out;
  }

  /// The events ical4j reads, one a line as [Row#event] writes them.
  static List<String> events(String text) throws IOException, ParserException {
    var out = new ArrayList<String>();
    for (VEvent e : read(text).<VEvent>getComponents(Component.VEVENT)) {
      var start = e.<DateProperty<Temporal>>getProperty(Property.DTSTART).map(DateProperty::getDate).orElse(null);
      var end = start == null ? null : e.<Temporal>getEndDate(true).map(d -> d.getDate()).orElse(start);
      var rid = e.<DateProperty<Temporal>>getProperty(Property.RECURRENCE_ID).map(DateProperty::getDate).orElse(null);
      out.add(Row.event(text(e, Property.UID), text(e, Property.SUMMARY), text(e, Property.DESCRIPTION), text(e, Property.LOCATION),
          text(e, Property.STATUS), start == null ? "" : key(start), end == null ? "" : key(end), rid == null ? "" : key(rid)));
    }
    return out;
  }

  private static net.fortuna.ical4j.model.Calendar read(String text) throws IOException, ParserException {
    var context = new ContentHandlerContext().withSuppressInvalidProperties(true);
    return new CalendarBuilder(CalendarParserFactory.getInstance().get(), context,
        TimeZoneRegistryFactory.getInstance().createRegistry()).build(new StringReader(text));
  }

  /// A time as [Row#event] writes it, and as [com.snaphop.ical.IcalOccurrence#id()] does.
  private static String key(Temporal t) {
    return switch (t) {
      case LocalDate d -> d.toString();
      case LocalDateTime d -> Row.local(d);
      default -> instant(t, ZoneId.of("UTC")).toString();
    };
  }

  private static void add(List<Row> out, String id, VEvent e, Temporal start, Temporal end, ZoneId zone, LocalDate from, LocalDate until) {
    if (!Row.inWindow(instant(start, zone), instant(end, zone), zone, from, until)) return;
    out.add(new Row(text(start, zone), text(end, zone), text(e, Property.SUMMARY), id));
  }

  /// The event's times in the window, as ical4j works them out.
  @SuppressWarnings("unchecked")
  private static List<Period<Temporal>> periods(VEvent e, ZoneId zone, LocalDate from, LocalDate until) {
    var start = start(e);
    // The window, wide enough for long events, in the form of the event's start.
    var f = from.minusDays(40);
    var u = until.plusDays(2);
    Period<? extends Temporal> window = switch (start) {
      case LocalDate d -> new Period<>(f, u);
      case LocalDateTime d -> new Period<>(f.atStartOfDay(), u.atStartOfDay());
      case ZonedDateTime d -> new Period<>(f.atStartOfDay(zone).withZoneSameInstant(d.getZone()), u.atStartOfDay(zone).withZoneSameInstant(d.getZone()));
      case OffsetDateTime d -> new Period<>(f.atStartOfDay(zone).toOffsetDateTime(), u.atStartOfDay(zone).toOffsetDateTime());
      case Instant d -> new Period<>(f.atStartOfDay(zone).toInstant(), u.atStartOfDay(zone).toInstant());
      default -> throw new IllegalArgumentException("a start ical4j gives as " + start.getClass().getSimpleName());
    };
    var list = new ArrayList<Period<Temporal>>();
    for (var p : e.<Temporal>calculateRecurrenceSet(window)) list.add((Period<Temporal>) (Period<?>) p);
    return list;
  }

  private static Temporal start(VEvent e) {
    return e.<DateProperty<Temporal>>getProperty(Property.DTSTART).orElseThrow().getDate();
  }

  private static Temporal recurrenceId(VEvent changed) {
    return ((DateProperty<?>) changed.getRequiredProperty(Property.RECURRENCE_ID)).getDate();
  }


  private static String text(Temporal t, ZoneId zone) {
    return switch (t) {
      case LocalDate d -> d.toString();
      case LocalDateTime d -> Row.local(d);
      default -> Row.local(LocalDateTime.ofInstant(instant(t, zone), zone));
    };
  }

  private static Instant instant(Temporal t, ZoneId zone) {
    return switch (t) {
      case Instant i -> i;
      case ZonedDateTime z -> z.toInstant();
      case OffsetDateTime o -> o.toInstant();
      case LocalDateTime d -> d.atZone(zone).toInstant();
      case LocalDate d -> d.atStartOfDay(zone).toInstant();
      default -> throw new IllegalArgumentException("a time ical4j gives as " + t.getClass().getSimpleName());
    };
  }

  private static boolean cancelled(VEvent e) {
    return text(e, Property.STATUS).equalsIgnoreCase("CANCELLED");
  }

  private static String text(VEvent e, String name) {
    return e.getProperty(name).map(p -> Objects.requireNonNullElse(p.getValue(), "")).orElse("");
  }
}
