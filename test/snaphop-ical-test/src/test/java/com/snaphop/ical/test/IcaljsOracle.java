package com.snaphop.ical.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.IOAccess;

/// ICAL.js as an oracle, run on GraalJS: occurrences as Mozilla's calendar
/// code works them out (`icaljs-oracle.js`).
///
/// ICAL.js knows only the zones a calendar defines, so a zone the calendar
/// names but doesn't define is handed to it as a `VTIMEZONE` made from the
/// JDK's rules for it.
final class IcaljsOracle {

  private IcaljsOracle() {}

  private static final Pattern TZID_PARAM = Pattern.compile("TZID=(\"?)([^;:\"]+)\\1", Pattern.CASE_INSENSITIVE);
  private static final Pattern TZID_PROP = Pattern.compile("^TZID:(.*)$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

  private static Context context;
  private static Value oracle;

  static synchronized List<Row> occurrences(String text, ZoneId zone, LocalDate from, LocalDate until) {
    var zones = zones(text);
    var untilUnix = until.plusDays(2).atStartOfDay().toEpochSecond(ZoneOffset.UTC);
    var rows = oracle.getMember("occurrences").execute(text, zones, untilUnix);
    var out = new ArrayList<Row>();
    for (long i = 0; i < rows.getArraySize(); i++) {
      var r = rows.getArrayElement(i);
      if (r.getMember("status").asString().equalsIgnoreCase("CANCELLED")) continue;
      var start = r.getMember("start");
      var end = r.getMember("end");
      if (end.isNull()) end = start;
      if (!Row.inWindow(instant(start, zone), instant(end, zone), zone, from, until)) continue;
      var uid = r.getMember("uid").asString();
      var id = r.getMember("repeats").asBoolean() ? uid + "@" + key(r.getMember("rid")) : uid;
      out.add(new Row(text(start, zone), text(end, zone), r.getMember("summary").asString(), id));
    }
    return out;
  }

  /// The events ICAL.js reads, one a line as [Row#event] writes them.
  static synchronized List<String> events(String text) {
    var zones = zones(text);
    var events = oracle.getMember("events").execute(text, zones);
    var out = new ArrayList<String>();
    for (long i = 0; i < events.getArraySize(); i++) {
      var e = events.getArrayElement(i);
      var rid = e.getMember("rid");
      out.add(Row.event(e.getMember("uid").asString(), e.getMember("summary").asString(), e.getMember("description").asString(),
          e.getMember("location").asString(), e.getMember("status").asString(), key(e.getMember("start")),
          e.getMember("end").isNull() ? key(e.getMember("start")) : key(e.getMember("end")), rid.isNull() ? "" : key(rid)));
    }
    return out;
  }

  /// `VTIMEZONE`s, as a JS array, for the zones `text` names but doesn't
  /// define.
  private static Value zones(String text) {
    if (oracle == null) start();
    context.eval("js", "ICAL.TimezoneService.reset()");
    var unfolded = text.replace("\r\n ", "").replace("\n ", "").replace("\r\n\t", "").replace("\n\t", "");
    var defined = new LinkedHashSet<String>();
    var m = TZID_PROP.matcher(unfolded);
    while (m.find()) defined.add(m.group(1).strip());
    var zones = new ArrayList<String>();
    var named = new LinkedHashSet<String>();
    var p = TZID_PARAM.matcher(unfolded);
    while (p.find()) named.add(p.group(2).strip());
    for (var tzid : named) {
      if (defined.contains(tzid)) continue;
      try {
        zones.add(vtimezone(tzid, ZoneId.of(tzid)));
      } catch (java.time.DateTimeException e) {
        // ICAL.js reads the time as floating, as it would anyway.
      }
    }
    var zoneArray = context.eval("js", "[]");
    for (int i = 0; i < zones.size(); i++) zoneArray.setArrayElement(i, zones.get(i));
    return zoneArray;
  }

  /// ICAL.js's own modules, at the commit the corpus's tests come from.
  static final Path LIB = RecurrenceSpecTest.CORPUS.resolve("icaljs/lib").toAbsolutePath().normalize();

  private static void start() {
    var files = IOAccess.newBuilder().fileSystem(FileSystem.newReadOnlyFileSystem(FileSystem.newDefaultFileSystem())).build();
    context = Context.newBuilder("js").option("engine.WarnInterpreterOnly", "false").allowIO(files).build();
    var start = "import ICAL from \"" + LIB.resolve("module.js").toUri() + "\"; globalThis.ICAL = ICAL;";
    context.eval(Source.newBuilder("js", start, "start.mjs").mimeType("application/javascript+module").buildLiteral());
    oracle = context.eval(Source.create("js", resource("icaljs-oracle.js")));
  }

  private static String resource(String name) {
    try (var in = Objects.requireNonNull(IcaljsOracle.class.getResourceAsStream(name), name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Instant instant(Value t, ZoneId zone) {
    if (t.getMember("date").asBoolean()) return LocalDate.parse(t.getMember("local").asString()).atStartOfDay(zone).toInstant();
    if (t.getMember("floating").asBoolean()) return LocalDateTime.parse(t.getMember("local").asString()).atZone(zone).toInstant();
    return Instant.ofEpochSecond(t.getMember("unix").asLong());
  }

  private static String text(Value t, ZoneId zone) {
    if (t.getMember("date").asBoolean() || t.getMember("floating").asBoolean()) return t.getMember("local").asString();
    return Row.local(LocalDateTime.ofInstant(Instant.ofEpochSecond(t.getMember("unix").asLong()), zone));
  }

  private static String key(Value t) {
    if (t.getMember("date").asBoolean() || t.getMember("floating").asBoolean()) return t.getMember("local").asString();
    return Instant.ofEpochSecond(t.getMember("unix").asLong()).toString();
  }

  private static final DateTimeFormatter BASIC = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss");

  /// A `VTIMEZONE` for `zone` from the JDK's rules: each change of offset
  /// from 1900 to 2100 as its own part.
  static String vtimezone(String tzid, ZoneId zone) {
    var rules = zone.getRules();
    var b = new StringBuilder("BEGIN:VCALENDAR\r\nBEGIN:VTIMEZONE\r\nTZID:").append(tzid).append("\r\n");
    var t = LocalDate.of(1900, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
    var end = LocalDate.of(2100, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
    var first = rules.getOffset(t);
    b.append("BEGIN:STANDARD\r\nDTSTART:19000101T000000\r\nTZOFFSETFROM:").append(offset(first))
        .append("\r\nTZOFFSETTO:").append(offset(first)).append("\r\nEND:STANDARD\r\n");
    for (var tr = rules.nextTransition(t); tr != null && tr.getInstant().isBefore(end); tr = rules.nextTransition(tr.getInstant())) {
      var part = rules.isDaylightSavings(tr.getInstant()) ? "DAYLIGHT" : "STANDARD";
      b.append("BEGIN:").append(part).append("\r\nDTSTART:").append(BASIC.format(tr.getDateTimeBefore()))
          .append("\r\nTZOFFSETFROM:").append(offset(tr.getOffsetBefore()))
          .append("\r\nTZOFFSETTO:").append(offset(tr.getOffsetAfter()))
          .append("\r\nEND:").append(part).append("\r\n");
    }
    return b.append("END:VTIMEZONE\r\nEND:VCALENDAR\r\n").toString();
  }

  private static String offset(ZoneOffset o) {
    int s = o.getTotalSeconds();
    var sign = s < 0 ? "-" : "+";
    s = Math.abs(s);
    var hm = String.format("%s%02d%02d", sign, s / 3600, (s / 60) % 60);
    return s % 60 == 0 ? hm : hm + String.format("%02d", s % 60);
  }
}
