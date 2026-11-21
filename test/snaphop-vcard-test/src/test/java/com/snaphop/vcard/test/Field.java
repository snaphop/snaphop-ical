package com.snaphop.vcard.test;

import com.snaphop.vcard.Vcard;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/// What is compared between this reader and an oracle, one field of each
/// card at a time, each as lines of text that every reader is rendered to
/// the same way ([Field#ours(Vcard)], and each oracle's own).
///
/// The rendering is plain on purpose: values stripped and empty ones left
/// out (as this reader documents), types in lower case and sorted, parts
/// joined by `|`, newlines shown as `\n`.
enum Field {
  VERSION,
  UID,
  FN,
  N,
  NICKNAME,
  ORG,
  TITLE,
  BDAY,
  ANNIVERSARY,
  EMAIL,
  TEL,
  ADR,
  URL,
  RELATED,
  NOTE,
  CATEGORIES,
  KIND,
  MEMBER;

  /// One card, field by field.
  record Card(Map<Field, List<String>> fields) {

    String get(Field f) {
      var values = fields.get(f);
      return values == null ? "" : String.join("\n", values);
    }
  }

  /// Builds a [Card], leaving out empty values.
  static final class Builder {
    private final Map<Field, List<String>> fields = new EnumMap<>(Field.class);

    Builder add(Field f, @Nullable String value) {
      var v = clean(value);
      if (!v.isEmpty()) fields.computeIfAbsent(f, k -> new ArrayList<>()).add(v);
      return this;
    }

    /// A value and its types, as `value [type, type]`.
    Builder value(Field f, @Nullable String value, List<String> types) {
      var v = clean(value);
      if (v.isEmpty()) return this;
      return add(f, v + types(types));
    }

    /// Parts, each a list joined by `joiner`, joined by `|`.
    Builder parts(Field f, List<List<String>> parts, String joiner, List<String> types) {
      var rendered = new ArrayList<String>();
      boolean any = false;
      for (var part : parts) {
        var values = part.stream().map(Field::clean).filter(s -> !s.isEmpty()).toList();
        var joined = clean(String.join(joiner, values));
        any |= !joined.isEmpty();
        rendered.add(joined);
      }
      if (!any) return this;
      while (rendered.size() > 1 && rendered.getLast().isEmpty()) rendered.removeLast();
      return add(f, String.join("|", rendered) + types(types));
    }

    Card build() {
      return new Card(fields);
    }
  }

  static String clean(@Nullable String value) {
    if (value == null) return "";
    return value.replace("\r\n", "\n").replace('\r', '\n').strip().replace("\n", "\\n");
  }

  static String types(List<String> types) {
    var t = types.stream().map(s -> s.strip().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).distinct().sorted()
        .collect(Collectors.joining(", "));
    return t.isEmpty() ? "" : " [" + t + "]";
  }

  /// This reader's card, rendered.
  static Card ours(Vcard c) {
    var b = new Builder();
    b.add(VERSION, c.version().text());
    b.add(UID, c.uid());
    b.add(FN, c.formattedName());
    var n = c.name();
    b.parts(N, List.of(List.of(n.family()), List.of(n.given()), List.of(n.additional()), List.of(n.prefixes()),
        List.of(n.suffixes())), " ", List.of());
    c.nicknames().forEach(v -> b.add(NICKNAME, v));
    if (!c.organization().isEmpty()) b.add(ORG, String.join("|", c.organization()));
    c.titles().forEach(v -> b.add(TITLE, v));
    var bday = c.birthdayOrNull();
    if (bday != null) b.add(BDAY, bday.toString());
    var anniversary = c.anniversaryOrNull();
    if (anniversary != null) b.add(ANNIVERSARY, anniversary.toString());
    c.emails().forEach(v -> b.value(EMAIL, v.value(), v.types()));
    c.phones().forEach(v -> b.value(TEL, v.value(), v.types()));
    for (var a : c.addresses()) {
      b.parts(ADR, List.of(List.of(a.poBox()), List.of(a.extended()), List.of(a.street()), List.of(a.locality()),
          List.of(a.region()), List.of(a.postalCode()), List.of(a.country())), "\n", a.types());
    }
    c.urls().forEach(v -> b.value(URL, v.value(), v.types()));
    c.related().forEach(v -> b.value(RELATED, v.value(), v.types()));
    c.notes().forEach(v -> b.add(NOTE, v));
    c.categories().forEach(v -> b.add(CATEGORIES, v));
    b.add(KIND, c.kind().name().toLowerCase(Locale.ROOT));
    c.members().forEach(v -> b.add(MEMBER, v));
    return b.build();
  }

  /// The field of every card, one line each, numbered: what is compared.
  String of(List<Card> cards) {
    var lines = new ArrayList<String>();
    lines.add(cards.size() + " cards");
    for (int i = 0; i < cards.size(); i++) {
      var v = cards.get(i).get(this);
      if (!v.isEmpty()) lines.add(i + 1 + ": " + v.replace("\n", "\n" + (i + 1) + ": "));
    }
    return String.join("\n", lines);
  }

  /// A date as this reader gives one: `2012-06-06`, or `--06-06` with no
  /// year. From a date-time, its date as written.
  static String day(String written) {
    var s = written.strip();
    int t = s.indexOf('T');
    if (t >= 0) s = s.substring(0, t);
    if (s.matches("\\d{8}")) return s.substring(0, 4) + "-" + s.substring(4, 6) + "-" + s.substring(6);
    if (s.matches("--\\d{4}")) return "--" + s.substring(2, 4) + "-" + s.substring(4);
    return s;
  }
}
