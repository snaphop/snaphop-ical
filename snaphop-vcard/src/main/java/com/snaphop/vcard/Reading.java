package com.snaphop.vcard;

import java.time.DateTimeException;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/// Reads text into [Vcard]s: each card's lines parsed in its version, then
/// its values read into plain types.
final class Reading {

  private Reading() {}

  static List<Vcard> read(CharSequence text, Problems problems) {
    var cards = new ArrayList<Vcard>();
    for (var raw : ContentLines.cards(text, problems)) cards.add(card(raw, problems));
    return cards;
  }

  private static Vcard card(RawCard raw, Problems problems) {
    var version = version(raw, problems);
    var props = new ArrayList<Prop>();
    for (var l : raw.lines()) {
      var p = ContentLines.parse(l, version, problems);
      if (p != null) props.add(p);
    }
    return new Card(raw.line(), version, props, problems).build();
  }

  /// The card's `VERSION`, found before its lines are parsed, since how they
  /// are parsed depends on it.
  private static Vcard.Version version(RawCard raw, Problems problems) {
    for (var l : raw.lines()) {
      var s = l.text();
      if (s.length() > 8 && s.regionMatches(true, 0, "VERSION", 0, 7) && (s.charAt(7) == ':' || s.charAt(7) == ';')) {
        var value = s.substring(s.indexOf(':') + 1).strip();
        for (var v : Vcard.Version.values()) {
          if (v.text().equals(value)) return v;
        }
        problems.report(l.line(), "VERSION is not 2.1, 3.0 or 4.0", "the card is read as vCard 3.0", Problems.quote(s));
        return Vcard.Version.V3_0;
      }
    }
    problems.report(raw.line(), "A VCARD has no VERSION", "it is read as vCard 3.0", Problems.quote("BEGIN:VCARD"));
    return Vcard.Version.V3_0;
  }

  /// One card's properties, read into a [DefaultVcard].
  private static final class Card {

    private final int line;
    private final Vcard.Version version;
    private final List<Prop> props;
    private final Problems problems;
    /// Apple's labels (`item1.X-ABLabel`), by group in upper case.
    private final Map<String, String> labels = new HashMap<>();

    Card(int line, Vcard.Version version, List<Prop> props, Problems problems) {
      this.line = line;
      this.version = version;
      this.props = props;
      this.problems = problems;
      for (var p : props) {
        if (p.name().equals("X-ABLABEL") && !p.group().isEmpty()) {
          labels.putIfAbsent(p.group().toUpperCase(Locale.ROOT), appleLabel(Text.unescape(p.value()).strip()));
        }
      }
    }

    Vcard build() {
      var uid = "";
      var appleUid = "";
      var fn = "";
      boolean hasFn = false;
      var name = Vcard.Name.EMPTY;
      boolean hasName = false;
      var nicknames = new ArrayList<String>();
      List<String> organization = List.of();
      boolean hasOrg = false;
      var titles = new ArrayList<String>();
      Vcard.Day birthday = null;
      Vcard.Day anniversary = null;
      Vcard.Day otherAnniversary = null;
      var emails = new ArrayList<Vcard.Value>();
      var phones = new ArrayList<Vcard.Value>();
      var addresses = new ArrayList<Vcard.Address>();
      var urls = new ArrayList<Vcard.Value>();
      var related = new ArrayList<Vcard.Value>();
      var notes = new ArrayList<String>();
      var categories = new LinkedHashSet<String>();
      Vcard.Kind kind = null;
      Vcard.Kind appleKind = null;
      var members = new ArrayList<String>();
      var appleMembers = new ArrayList<String>();
      var properties = new ArrayList<Vcard.Property>();

      for (var p : props) {
        properties.add(new Vcard.Property(p.group(), p.name(), p.params(), p.value(), p.line()));
        switch (p.name()) {
          case "UID" -> {
            if (uid.isEmpty()) uid = text(p);
          }
          case "X-ABUID" -> {
            if (appleUid.isEmpty()) appleUid = text(p);
          }
          case "FN" -> {
            hasFn = true;
            if (fn.isEmpty()) fn = text(p);
          }
          case "N" -> {
            if (!hasName) {
              hasName = true;
              name = name(p);
            }
          }
          case "NICKNAME" -> nicknames.addAll(Text.list(p.value()));
          case "ORG" -> {
            if (!hasOrg) {
              hasOrg = true;
              var parts = new ArrayList<String>();
              for (var part : Text.split(p.value(), ';')) {
                var v = Text.unescape(part).strip();
                if (!v.isEmpty()) parts.add(v);
              }
              organization = parts;
            }
          }
          case "TITLE" -> add(titles, text(p));
          case "BDAY" -> {
            if (birthday == null) birthday = day(p);
          }
          case "ANNIVERSARY" -> {
            if (anniversary == null) anniversary = day(p);
          }
          case "X-ANNIVERSARY", "X-MS-ANNIVERSARY", "X-EVOLUTION-ANNIVERSARY" -> {
            if (otherAnniversary == null) otherAnniversary = day(p);
          }
          case "X-ABDATE" -> {
            if (otherAnniversary == null && label(p).equals("anniversary")) otherAnniversary = day(p);
          }
          case "EMAIL" -> value(emails, p, text(p));
          case "TEL" -> value(phones, p, phone(p));
          case "ADR" -> address(addresses, p);
          case "URL" -> value(urls, p, text(p));
          case "RELATED", "X-ABRELATEDNAMES" -> value(related, p, text(p));
          case "X-SPOUSE", "X-MS-SPOUSE", "X-EVOLUTION-SPOUSE" -> relation(related, p, "spouse");
          case "X-MANAGER", "X-MS-MANAGER", "X-EVOLUTION-MANAGER" -> relation(related, p, "manager");
          case "X-ASSISTANT", "X-MS-ASSISTANT", "X-EVOLUTION-ASSISTANT" -> relation(related, p, "assistant");
          case "NOTE" -> add(notes, text(p));
          case "CATEGORIES" -> categories.addAll(Text.list(p.value()));
          case "KIND" -> {
            if (kind == null) kind = kind(text(p));
          }
          case "X-ADDRESSBOOKSERVER-KIND" -> {
            if (appleKind == null) appleKind = kind(text(p));
          }
          case "X-ABSHOWAS" -> {
            if (appleKind == null && text(p).equalsIgnoreCase("COMPANY")) appleKind = Vcard.Kind.ORGANIZATION;
          }
          case "MEMBER" -> add(members, text(p));
          case "X-ADDRESSBOOKSERVER-MEMBER" -> add(appleMembers, text(p));
          default -> {}
        }
      }
      if (!hasFn && version != Vcard.Version.V2_1) {
        problems.strictly(line, "A VCARD has no FN, which vCard " + version.text() + " requires", Problems.quote("BEGIN:VCARD"));
      }
      return new DefaultVcard(
          version,
          line,
          uid.isEmpty() ? appleUid : uid,
          fn,
          name,
          nicknames,
          organization,
          titles,
          birthday,
          anniversary != null ? anniversary : otherAnniversary,
          emails,
          phones,
          addresses,
          urls,
          related,
          notes,
          List.copyOf(categories),
          kind != null ? kind : appleKind != null ? appleKind : Vcard.Kind.INDIVIDUAL,
          members.isEmpty() ? appleMembers : members,
          properties);
    }

    private static String text(Prop p) {
      return Text.unescape(p.value()).strip();
    }

    private static void add(List<String> to, String value) {
      if (!value.isEmpty()) to.add(value);
    }

    private Vcard.Name name(Prop p) {
      var parts = Text.split(p.value(), ';');
      var joiner = " ";
      return new Vcard.Name(part(parts, 0, joiner), part(parts, 1, joiner), part(parts, 2, joiner), part(parts, 3, joiner),
          part(parts, 4, joiner));
    }

    /// A part of a structured value. vCard 2.1 has no lists in parts, so a
    /// comma there is text.
    private String part(List<String> parts, int index, String joiner) {
      if (version == Vcard.Version.V2_1) {
        return index < parts.size() ? Text.unescape(parts.get(index)).strip() : "";
      }
      return Text.part(parts, index, joiner);
    }

    private void address(List<Vcard.Address> to, Prop p) {
      var parts = Text.split(p.value(), ';');
      var a = new Vcard.Address(part(parts, 0, "\n"), part(parts, 1, "\n"), part(parts, 2, "\n"), part(parts, 3, "\n"),
          part(parts, 4, "\n"), part(parts, 5, "\n"), part(parts, 6, "\n"), types(p), label(p), preferred(p));
      if (!(a.poBox() + a.extended() + a.street() + a.locality() + a.region() + a.postalCode() + a.country()).isEmpty()) {
        to.add(a);
      }
    }

    private void value(List<Vcard.Value> to, Prop p, String value) {
      if (!value.isEmpty()) to.add(new Vcard.Value(value, types(p), label(p), preferred(p)));
    }

    /// A relation written as its own property: the relation is its type.
    private void relation(List<Vcard.Value> to, Prop p, String type) {
      var value = text(p);
      if (value.isEmpty()) return;
      var types = new LinkedHashSet<String>();
      types.add(type);
      types.addAll(types(p));
      to.add(new Vcard.Value(value, List.copyOf(types), label(p), preferred(p)));
    }

    /// A phone number: a `tel:` URI as its number, with any extension.
    private static String phone(Prop p) {
      var v = text(p);
      if (!v.regionMatches(true, 0, "tel:", 0, 4)) return v;
      var parts = v.substring(4).split(";", -1);
      var number = parts[0].strip();
      for (int i = 1; i < parts.length; i++) {
        if (parts[i].regionMatches(true, 0, "ext=", 0, 4) && parts[i].length() > 4) number += " ext. " + parts[i].substring(4).strip();
      }
      return number;
    }

    /// Each `TYPE` in lower case, split at commas, each once.
    private static List<String> types(Prop p) {
      var types = new LinkedHashSet<String>();
      for (var t : p.params("TYPE")) {
        for (var one : Text.split(t, ',')) {
          var word = one.strip().toLowerCase(Locale.ROOT);
          if (!word.isEmpty()) types.add(word);
        }
      }
      return List.copyOf(types);
    }

    private static boolean preferred(Prop p) {
      var pref = p.param("PREF");
      return (pref != null && pref.strip().equals("1")) || types(p).contains("pref");
    }

    private String label(Prop p) {
      if (p.group().isEmpty()) return "";
      var label = labels.get(p.group().toUpperCase(Locale.ROOT));
      return label == null ? "" : label;
    }

    /// Apple's own words come as `_$!<Mother>!$_`, typed ones as typed.
    private static String appleLabel(String label) {
      if (label.startsWith("_$!<") && label.endsWith(">!$_") && label.length() >= 8) {
        return label.substring(4, label.length() - 4).toLowerCase(Locale.ROOT);
      }
      return label;
    }

    private static Vcard.Kind kind(String value) {
      return switch (value.toLowerCase(Locale.ROOT)) {
        case "individual" -> Vcard.Kind.INDIVIDUAL;
        case "group" -> Vcard.Kind.GROUP;
        case "org" -> Vcard.Kind.ORGANIZATION;
        case "location" -> Vcard.Kind.LOCATION;
        default -> Vcard.Kind.OTHER;
      };
    }

    /// A day of the year, or null: from a date, a date-time, or vCard 4's
    /// date without a year. Less than a day (a year, a month, a time) and
    /// text are no day; what is not a date at all is reported.
    private Vcard.@Nullable Day day(Prop p) {
      if (p.has("VALUE", "text")) return null;
      var v = text(p);
      int t = v.indexOf('T');
      var date = t < 0 ? v : v.substring(0, t);
      if (date.isEmpty() && t >= 0) return null;
      try {
        var full = FULL.matcher(date);
        if (full.matches()) {
          int year = Integer.parseInt(full.group(1));
          var monthDay = MonthDay.of(Integer.parseInt(full.group(2)), Integer.parseInt(full.group(3)));
          var omit = p.param("X-APPLE-OMIT-YEAR");
          if (omit != null && omit.strip().equals(full.group(1))) return new Vcard.Day(monthDay, null);
          return new Vcard.Day(monthDay, year);
        }
        var noYear = NO_YEAR.matcher(date);
        if (noYear.matches()) return new Vcard.Day(MonthDay.of(Integer.parseInt(noYear.group(1)), Integer.parseInt(noYear.group(2))), null);
        if (PARTIAL.matcher(date).matches()) return null;
      } catch (DateTimeException e) {
        // Reported below.
      }
      problems.report(p.line(), p.name() + " is not a date", "it is left out", Problems.quote(p.text()));
      return null;
    }

    private static final Pattern FULL = Pattern.compile("(\\d{4})-?(\\d{2})-?(\\d{2})");
    private static final Pattern NO_YEAR = Pattern.compile("--(\\d{2})-?(\\d{2})");
    /// A year, a year and month, a month, or a day of the month (vCard 4).
    private static final Pattern PARTIAL = Pattern.compile("\\d{4}|\\d{4}-\\d{2}|--\\d{2}|---\\d{2}");
  }
}
