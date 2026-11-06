package com.snaphop.ical;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/// Where the times of a `TZID` are: a zone of the JDK, or the rules of a
/// `VTIMEZONE` that matches none.
sealed interface Tz {

  /// The time at a wall clock time. A time that a change to daylight saving
  /// time skips is moved later by the length of the gap, and of a time that
  /// happens twice the first is taken, as RFC 5545 says.
  ZonedDateTime at(LocalDateTime local);

  /// The time at an instant.
  ZonedDateTime at(Instant instant);
}

record JdkTz(ZoneId zone) implements Tz {

  @Override
  public ZonedDateTime at(LocalDateTime local) {
    return ZonedDateTime.of(local, zone);
  }

  @Override
  public ZonedDateTime at(Instant instant) {
    return ZonedDateTime.ofInstant(instant, zone);
  }
}

/// A `VTIMEZONE` that is no JDK zone: its times get fixed offsets, each the
/// one its rules give at that time.
record RulesTz(ZoneRules rules) implements Tz {

  @Override
  public ZonedDateTime at(LocalDateTime local) {
    var offsets = rules.getValidOffsets(local);
    if (offsets.isEmpty()) {
      var gap = rules.getTransition(local);
      if (gap == null) throw new DateTimeException("no offset at " + local);
      var moved = local.plus(gap.getDuration());
      return ZonedDateTime.ofStrict(moved, gap.getOffsetAfter(), gap.getOffsetAfter());
    }
    return ZonedDateTime.ofStrict(local, offsets.getFirst(), offsets.getFirst());
  }

  @Override
  public ZonedDateTime at(Instant instant) {
    return ZonedDateTime.ofInstant(instant, rules.getOffset(instant));
  }
}

/// Finds the zone of each `TZID` in a calendar, without the network:
///
/// 1. A zone name the JDK knows, such as `America/New_York`, even after a
///    prefix (`/mozilla.org/20050126_1/America/New_York`).
/// 2. The calendar's `VTIMEZONE` with that `TZID`: the JDK zone whose offsets
///    agree with it from 2020 to 2037, a Windows zone name's first, else its
///    own rules.
/// 3. A Windows zone name (`Eastern Standard Time`), as Outlook writes them.
final class Zones {

  /// Years over which a `VTIMEZONE` must agree with a JDK zone to be it.
  private static final int MATCH_FROM = 2020;

  private static final int MATCH_UNTIL = 2037;

  /// How far a `VTIMEZONE`'s rules are worked out.
  private static final LocalDateTime RULES_UNTIL = LocalDateTime.of(2200, 1, 1, 0, 0);

  private final Map<String, Comp> vtimezones = new HashMap<>();
  private final Map<String, Optional<Tz>> found = new HashMap<>();

  Zones(List<Comp> vtimezones) {
    for (var c : vtimezones) {
      var tzid = c.first("TZID");
      if (tzid != null) this.vtimezones.putIfAbsent(tzid.value().strip(), c);
    }
  }

  /// The zone of `tzid`, or null if it is none of the above.
  @Nullable Tz find(String tzid) {
    return found.computeIfAbsent(tzid.strip(), t -> Optional.ofNullable(resolve(t))).orElse(null);
  }

  private @Nullable Tz resolve(String tzid) {
    var jdk = jdk(tzid);
    if (jdk != null) return new JdkTz(jdk);
    var vtz = vtimezones.get(tzid);
    if (vtz != null) {
      var rules = rules(vtz);
      if (rules != null) {
        var match = match(rules.rules(), Math.max(MATCH_FROM, rules.firstYear()), WINDOWS.get(tzid));
        return match != null ? new JdkTz(match) : new RulesTz(rules.rules());
      }
    }
    var windows = WINDOWS.get(tzid);
    return windows == null ? null : new JdkTz(ZoneId.of(windows));
  }

  /// A JDK zone named `tzid`, or the end of it after a `/`.
  private static @Nullable ZoneId jdk(String tzid) {
    var name = tzid;
    while (true) {
      if (!name.isEmpty() && !name.startsWith("/")) {
        try {
          return ZoneId.of(name);
        } catch (DateTimeException e) {
          // Try what follows the next slash.
        }
      }
      int slash = name.indexOf('/', 1);
      if (slash < 0) return null;
      name = name.substring(slash + 1);
      if (name.indexOf('/') < 0 && !name.equals("UTC")) return null;
    }
  }

  private record Rules(ZoneRules rules, int firstYear) {}

  private record Onset(Instant at, ZoneOffset from, ZoneOffset to) {}

  /// The rules of a `VTIMEZONE`'s `STANDARD` and `DAYLIGHT` parts, or null
  /// if it has none that can be read.
  private static @Nullable Rules rules(Comp vtz) {
    var onsets = new ArrayList<Onset>();
    for (var part : vtz.comps()) {
      if (!part.name().equals("STANDARD") && !part.name().equals("DAYLIGHT")) continue;
      try {
        var start = part.first("DTSTART");
        var fromProp = part.first("TZOFFSETFROM");
        var toProp = part.first("TZOFFSETTO");
        if (start == null || fromProp == null || toProp == null) continue;
        var from = Values.offset(fromProp.value());
        var to = Values.offset(toProp.value());
        var local = Values.dateTime(Values.withoutZ(start.value().strip()));
        onsets.add(new Onset(local.toInstant(from), from, to));
        for (var rrule : part.all("RRULE")) {
          var rule = DefaultIcalRecurrence.parse(rrule.value(), soft -> {});
          var until = rule.untilOrNull();
          rule.expand(local, false, local, RULES_UNTIL,
              t -> switch (until) {
                case IcalTime.DateTime u -> !t.toInstant(from).isAfter(u.dateTime().toInstant());
                case IcalTime.Floating u -> !t.isAfter(u.dateTime());
                case IcalTime.Date u -> !t.toLocalDate().isAfter(u.date());
                case null -> true;
              },
              t -> {
                onsets.add(new Onset(t.toInstant(from), from, to));
                return true;
              });
        }
        for (var rdate : part.all("RDATE")) {
          for (var v : rdate.value().split(",", -1)) {
            var t = v.strip();
            if (t.isEmpty()) continue;
            var l = t.length() == 8 ? Values.date(t).atStartOfDay() : Values.dateTime(Values.withoutZ(t));
            onsets.add(new Onset(Values.utc(t) ? l.toInstant(ZoneOffset.UTC) : l.toInstant(from), from, to));
          }
        }
      } catch (IllegalArgumentException | DateTimeException e) {
        // A part that can't be read is left out; the zone may still work.
      }
    }
    if (onsets.isEmpty()) return null;
    onsets.sort(Comparator.comparing(Onset::at));
    var first = onsets.getFirst();
    var base = first.from();
    var current = base;
    var transitions = new ArrayList<ZoneOffsetTransition>();
    for (var o : onsets) {
      if (o.to().equals(current)) continue;
      transitions.add(ZoneOffsetTransition.of(LocalDateTime.ofEpochSecond(o.at().getEpochSecond(), 0, current), current, o.to()));
      current = o.to();
    }
    if (transitions.isEmpty()) return new Rules(ZoneRules.of(first.to()), first.at().atOffset(base).getYear());
    var rules = ZoneRules.of(base, base, List.of(), transitions, List.of());
    return new Rules(rules, first.at().atOffset(base).getYear());
  }

  /// A JDK zone whose offsets agree with `rules` over the years, trying
  /// `preferred` first, or null if none does.
  private static @Nullable ZoneId match(ZoneRules rules, int fromYear, @Nullable String preferred) {
    var samples = new TreeSet<Instant>();
    var from = LocalDate.of(fromYear, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
    var until = LocalDate.of(MATCH_UNTIL + 1, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
    for (int y = fromYear; y <= MATCH_UNTIL; y++) {
      samples.add(LocalDate.of(y, 1, 15).atStartOfDay().toInstant(ZoneOffset.UTC));
      samples.add(LocalDate.of(y, 7, 15).atStartOfDay().toInstant(ZoneOffset.UTC));
    }
    for (var t : rules.getTransitions()) {
      var at = t.getInstant();
      if (at.isBefore(from) || !at.isBefore(until)) continue;
      samples.add(at.minusSeconds(60));
      samples.add(at.plusSeconds(60));
    }
    var candidates = new ArrayList<String>();
    if (preferred != null) candidates.add(preferred);
    candidates.addAll(new TreeSet<>(ZoneId.getAvailableZoneIds()));
    for (var id : candidates) {
      if (!id.contains("/") || id.startsWith("Etc/") || id.startsWith("SystemV/")) continue;
      var zone = ZoneId.of(id).getRules();
      boolean same = true;
      for (var s : samples) {
        if (!zone.getOffset(s).equals(rules.getOffset(s))) {
          same = false;
          break;
        }
      }
      if (same) return ZoneId.of(id);
    }
    return null;
  }

  /// Windows zone names and the zone each is, for the zones Outlook and
  /// Exchange name.
  private static final Map<String, String> WINDOWS = windows("""
      Dateline Standard Time=Etc/GMT+12
      UTC-11=Etc/GMT+11
      Aleutian Standard Time=America/Adak
      Hawaiian Standard Time=Pacific/Honolulu
      Marquesas Standard Time=Pacific/Marquesas
      Alaskan Standard Time=America/Anchorage
      UTC-09=Etc/GMT+9
      Pacific Standard Time (Mexico)=America/Tijuana
      UTC-08=Etc/GMT+8
      Pacific Standard Time=America/Los_Angeles
      US Mountain Standard Time=America/Phoenix
      Mountain Standard Time (Mexico)=America/Mazatlan
      Mountain Standard Time=America/Denver
      Yukon Standard Time=America/Whitehorse
      Central America Standard Time=America/Guatemala
      Central Standard Time=America/Chicago
      Easter Island Standard Time=Pacific/Easter
      Central Standard Time (Mexico)=America/Mexico_City
      Canada Central Standard Time=America/Regina
      SA Pacific Standard Time=America/Bogota
      Eastern Standard Time (Mexico)=America/Cancun
      Eastern Standard Time=America/New_York
      Haiti Standard Time=America/Port-au-Prince
      Cuba Standard Time=America/Havana
      US Eastern Standard Time=America/Indiana/Indianapolis
      Turks And Caicos Standard Time=America/Grand_Turk
      Paraguay Standard Time=America/Asuncion
      Atlantic Standard Time=America/Halifax
      Venezuela Standard Time=America/Caracas
      Central Brazilian Standard Time=America/Cuiaba
      SA Western Standard Time=America/La_Paz
      Pacific SA Standard Time=America/Santiago
      Newfoundland Standard Time=America/St_Johns
      Tocantins Standard Time=America/Araguaina
      E. South America Standard Time=America/Sao_Paulo
      SA Eastern Standard Time=America/Cayenne
      Argentina Standard Time=America/Argentina/Buenos_Aires
      Greenland Standard Time=America/Nuuk
      Montevideo Standard Time=America/Montevideo
      Magallanes Standard Time=America/Punta_Arenas
      Saint Pierre Standard Time=America/Miquelon
      Bahia Standard Time=America/Bahia
      UTC-02=Etc/GMT+2
      Azores Standard Time=Atlantic/Azores
      Cape Verde Standard Time=Atlantic/Cape_Verde
      UTC=Etc/UTC
      Coordinated Universal Time=Etc/UTC
      GMT Standard Time=Europe/London
      Greenwich Standard Time=Atlantic/Reykjavik
      Sao Tome Standard Time=Africa/Sao_Tome
      Morocco Standard Time=Africa/Casablanca
      W. Europe Standard Time=Europe/Berlin
      Central Europe Standard Time=Europe/Budapest
      Romance Standard Time=Europe/Paris
      Central European Standard Time=Europe/Warsaw
      W. Central Africa Standard Time=Africa/Lagos
      Jordan Standard Time=Asia/Amman
      GTB Standard Time=Europe/Bucharest
      Middle East Standard Time=Asia/Beirut
      Egypt Standard Time=Africa/Cairo
      E. Europe Standard Time=Europe/Chisinau
      Syria Standard Time=Asia/Damascus
      West Bank Standard Time=Asia/Hebron
      South Africa Standard Time=Africa/Johannesburg
      FLE Standard Time=Europe/Kyiv
      Israel Standard Time=Asia/Jerusalem
      South Sudan Standard Time=Africa/Juba
      Kaliningrad Standard Time=Europe/Kaliningrad
      Sudan Standard Time=Africa/Khartoum
      Libya Standard Time=Africa/Tripoli
      Namibia Standard Time=Africa/Windhoek
      Arabic Standard Time=Asia/Baghdad
      Turkey Standard Time=Europe/Istanbul
      Arab Standard Time=Asia/Riyadh
      Belarus Standard Time=Europe/Minsk
      Russian Standard Time=Europe/Moscow
      E. Africa Standard Time=Africa/Nairobi
      Volgograd Standard Time=Europe/Volgograd
      Iran Standard Time=Asia/Tehran
      Arabian Standard Time=Asia/Dubai
      Astrakhan Standard Time=Europe/Astrakhan
      Azerbaijan Standard Time=Asia/Baku
      Russia Time Zone 3=Europe/Samara
      Mauritius Standard Time=Indian/Mauritius
      Saratov Standard Time=Europe/Saratov
      Georgian Standard Time=Asia/Tbilisi
      Caucasus Standard Time=Asia/Yerevan
      Afghanistan Standard Time=Asia/Kabul
      West Asia Standard Time=Asia/Tashkent
      Ekaterinburg Standard Time=Asia/Yekaterinburg
      Pakistan Standard Time=Asia/Karachi
      Qyzylorda Standard Time=Asia/Qyzylorda
      India Standard Time=Asia/Kolkata
      Sri Lanka Standard Time=Asia/Colombo
      Nepal Standard Time=Asia/Kathmandu
      Central Asia Standard Time=Asia/Bishkek
      Bangladesh Standard Time=Asia/Dhaka
      Omsk Standard Time=Asia/Omsk
      Myanmar Standard Time=Asia/Yangon
      SE Asia Standard Time=Asia/Bangkok
      Altai Standard Time=Asia/Barnaul
      W. Mongolia Standard Time=Asia/Hovd
      North Asia Standard Time=Asia/Krasnoyarsk
      N. Central Asia Standard Time=Asia/Novosibirsk
      Tomsk Standard Time=Asia/Tomsk
      China Standard Time=Asia/Shanghai
      North Asia East Standard Time=Asia/Irkutsk
      Singapore Standard Time=Asia/Singapore
      W. Australia Standard Time=Australia/Perth
      Taipei Standard Time=Asia/Taipei
      Ulaanbaatar Standard Time=Asia/Ulaanbaatar
      Aus Central W. Standard Time=Australia/Eucla
      Transbaikal Standard Time=Asia/Chita
      Tokyo Standard Time=Asia/Tokyo
      North Korea Standard Time=Asia/Pyongyang
      Korea Standard Time=Asia/Seoul
      Yakutsk Standard Time=Asia/Yakutsk
      Cen. Australia Standard Time=Australia/Adelaide
      AUS Central Standard Time=Australia/Darwin
      E. Australia Standard Time=Australia/Brisbane
      AUS Eastern Standard Time=Australia/Sydney
      West Pacific Standard Time=Pacific/Port_Moresby
      Tasmania Standard Time=Australia/Hobart
      Vladivostok Standard Time=Asia/Vladivostok
      Lord Howe Standard Time=Australia/Lord_Howe
      Bougainville Standard Time=Pacific/Bougainville
      Russia Time Zone 10=Asia/Srednekolymsk
      Magadan Standard Time=Asia/Magadan
      Norfolk Standard Time=Pacific/Norfolk
      Sakhalin Standard Time=Asia/Sakhalin
      Central Pacific Standard Time=Pacific/Guadalcanal
      Russia Time Zone 11=Asia/Kamchatka
      New Zealand Standard Time=Pacific/Auckland
      UTC+12=Etc/GMT-12
      Fiji Standard Time=Pacific/Fiji
      Chatham Islands Standard Time=Pacific/Chatham
      UTC+13=Etc/GMT-13
      Tonga Standard Time=Pacific/Tongatapu
      Samoa Standard Time=Pacific/Apia
      Line Islands Standard Time=Pacific/Kiritimati
      """);

  private static Map<String, String> windows(String table) {
    var known = ZoneId.getAvailableZoneIds();
    var map = new HashMap<String, String>();
    for (var line : table.lines().toList()) {
      int eq = line.lastIndexOf('=');
      var zone = line.substring(eq + 1);
      // Skip a zone this JDK's data doesn't have.
      if (known.contains(zone)) map.put(line.substring(0, eq), zone);
    }
    return Map.copyOf(map);
  }
}
