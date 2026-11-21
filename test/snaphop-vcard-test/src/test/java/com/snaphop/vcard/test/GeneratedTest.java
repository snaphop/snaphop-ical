package com.snaphop.vcard.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.snaphop.vcard.VcardReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/// Cards written for the test, one property each, in every combination of
/// [Version] × [Property] × [Value] × [Params] × [Fold], each read by
/// this reader and by each oracle and compared with what was written.
///
/// This reader must read every one as written. Where an oracle does not,
/// [#DIFFERENCES] says why, and the test checks it still does not.
class GeneratedTest {

  static final VcardReader READER = VcardReader.builder().mode(VcardReader.Mode.STRICT).build();

  enum Version {
    V2_1("2.1"),
    V3_0("3.0"),
    V4_0("4.0");

    final String text;

    Version(String text) {
      this.text = text;
    }
  }

  /// Text that needs care: escapes, quoted-printable, folding between characters.
  enum Value {
    PLAIN("Maya"),
    COMMA("Rivera, Maya"),
    SEMICOLON("Soccer; then piano"),
    BACKSLASH("C:\\Users\\maya"),
    NEWLINE("Line one\nLine two"),
    COLON("Pick up at 3:30"),
    ACCENTS("José Muñoz Ñandú"),
    EMOJI("Party 🎂🎉 time"),
    CJK("王小明"),
    QUOTE("Say \"hi\""),
    EQUALS("a=b=c"),
    CARET("x^n^^y"),
    LONG("Allergic to peanuts, not tree nuts; picks up Tuesdays at 3:30. Café José 🎂 "
        + "and a very long note that goes on past seventy five octets more than once, Ñ.");

    final String text;

    Value(String text) {
      this.text = text;
    }
  }

  /// How a property's types are written.
  enum Params {
    /// No types.
    NONE,
    /// `TYPE=home,work`, or vCard 2.1's `HOME;WORK`.
    LIST,
    /// `TYPE=home;TYPE=work`.
    REPEATED,
    /// `TYPE="home,work"` (vCard 4.0).
    QUOTED;

    @Nullable String write(Version v) {
      return switch (this) {
        case NONE -> "";
        case LIST -> v == Version.V2_1 ? ";HOME;WORK" : ";TYPE=home,work";
        case REPEATED -> v == Version.V2_1 ? null : ";TYPE=home;TYPE=work";
        case QUOTED -> v == Version.V4_0 ? ";TYPE=\"home,work\"" : null;
      };
    }
  }

  enum Fold {
    NONE,
    AT_75,
    EVERY_CHARACTER
  }

  /// A property written with a value, and what it reads as, as [Field]s render it.
  enum Property {
    FN(Field.FN) {
      @Override
      String line(Version v, String value, String params) {
        return "FN" + text(v, value, Kind.TEXT);
      }
    },
    NOTE(Field.NOTE) {
      @Override
      String line(Version v, String value, String params) {
        return "NOTE" + text(v, value, Kind.TEXT);
      }
    },
    TITLE(Field.TITLE) {
      @Override
      String line(Version v, String value, String params) {
        return "TITLE" + text(v, value, Kind.TEXT);
      }
    },
    N_FAMILY(Field.N) {
      @Override
      String line(Version v, String value, String params) {
        return "N" + text(v, value, Kind.PART) + ";Given;;;";
      }

      @Override
      String expected(String value) {
        return Field.clean(value) + "|Given";
      }
    },
    ADR_STREET(Field.ADR) {
      @Override
      String line(Version v, String value, String params) {
        return "ADR" + params + text(v, ";;" + MARK + ";Town;;;", value, Kind.PART);
      }

      @Override
      String expected(String value) {
        return "||" + Field.clean(value) + "|Town";
      }

      @Override
      boolean typed() {
        return true;
      }
    },
    ORG_UNIT(Field.ORG) {
      @Override
      String line(Version v, String value, String params) {
        return "ORG" + text(v, "Rivera Bakery;" + MARK, value, Kind.ORG);
      }

      @Override
      String expected(String value) {
        return "Rivera Bakery|" + Field.clean(value);
      }
    },
    NICKNAME_LIST(Field.NICKNAME) {
      @Override
      String line(Version v, String value, String params) {
        return "NICKNAME" + text(v, MARK + ",Mimi", value, Kind.PART);
      }

      @Override
      String expected(String value) {
        return Field.clean(value) + "\nMimi";
      }
    },
    CATEGORIES_LIST(Field.CATEGORIES) {
      @Override
      String line(Version v, String value, String params) {
        return "CATEGORIES" + text(v, "Family," + MARK, value, Kind.PART);
      }

      @Override
      String expected(String value) {
        return "Family\n" + Field.clean(value);
      }
    },
    EMAIL(Field.EMAIL) {
      @Override
      String line(Version v, String value, String params) {
        return "EMAIL" + params + text(v, value, Kind.TEXT);
      }

      @Override
      boolean typed() {
        return true;
      }
    },
    TEL(Field.TEL) {
      @Override
      String line(Version v, String value, String params) {
        return "TEL" + params + text(v, value, Kind.TEXT);
      }

      @Override
      boolean typed() {
        return true;
      }
    };

    final Field field;

    Property(Field field) {
      this.field = field;
    }

    abstract String line(Version v, String value, String params);

    String expected(String value) {
      return Field.clean(value);
    }

    boolean typed() {
      return false;
    }

    /// Whether the value can be written in this property in this version.
    /// vCard 2.1 has no escape for a comma in a list, nor for a backslash.
    boolean writable(Version v, Value value) {
      if (v == Version.V2_1 && value.text.contains("\\")) return false;
      if (v == Version.V2_1 && value.text.contains(",") && (this == NICKNAME_LIST || this == CATEGORIES_LIST)) return false;
      if (v == Version.V2_1 && value.text.contains(";") && this == ORG_UNIT) return false;
      return true;
    }
  }

  static final String MARK = "\u0000";

  enum Kind {
    /// Text: `\,` `\;` `\\` and `\n` escaped.
    TEXT,
    /// A part of a structured value or list: as text.
    PART,
    /// A part of `ORG`, which has no lists.
    ORG
  }

  static String text(Version v, String value, Kind kind) {
    return text(v, MARK, value, kind);
  }

  /// `:` and the value, escaped as the version writes it, put in place of
  /// [#MARK] in `template`. vCard 2.1 writes a value with a newline,
  /// non-ASCII text or an `=` as quoted-printable UTF-8.
  static String text(Version v, String template, String value, Kind kind) {
    if (v == Version.V2_1) {
      var escaped = value.replace(";", "\\;");
      boolean qp = !escaped.chars().allMatch(c -> c >= 0x20 && c < 0x7f) || escaped.contains("=");
      var whole = template.replace(MARK, qp ? quotedPrintable(escaped) : escaped);
      return qp ? ";CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:" + softBreaks(whole) : ":" + whole;
    }
    var escaped = value.replace("\\", "\\\\").replace(";", "\\;").replace("\n", "\\n").replace(",", "\\,");
    return ":" + template.replace(MARK, escaped);
  }

  static String quotedPrintable(String s) {
    var b = new StringBuilder();
    for (byte x : s.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8)) {
      int c = x & 0xff;
      if (c >= 0x20 && c < 0x7f && c != '=') b.append((char) c);
      else b.append(String.format("=%02X", c));
    }
    return b.toString();
  }

  /// Quoted-printable's soft line breaks, at most 70 characters a line,
  /// never inside an `=XX`.
  static String softBreaks(String s) {
    var b = new StringBuilder();
    int line = 0;
    for (int i = 0; i < s.length(); ) {
      int step = s.charAt(i) == '=' && i + 2 < s.length() ? 3 : 1;
      if (line + step > 70) {
        b.append("=\r\n");
        line = 0;
      }
      b.append(s, i, i + step);
      line += step;
      i += step;
    }
    return b.toString();
  }

  record Case(Version version, Property property, Value value, Params params, Fold fold) {

    String text() {
      var lines = new ArrayList<String>();
      lines.add("BEGIN:VCARD");
      lines.add("VERSION:" + version.text);
      if (property != Property.FN) lines.add("FN:Test");
      if (property != Property.N_FAMILY && version == Version.V3_0) lines.add("N:Test;;;;");
      lines.add(property.line(version, value.text, java.util.Objects.requireNonNull(params.write(version))));
      lines.add("END:VCARD");
      var text = String.join("\r\n", lines) + "\r\n";
      return switch (fold) {
        case NONE -> text;
        case AT_75 -> TransformTest.fold(text, 75, " ");
        case EVERY_CHARACTER -> TransformTest.fold(text, 1, " ");
      };
    }

    String expected() {
      var types = property.typed() ? (params == Params.NONE ? "" : " [home, work]") : "";
      var e = property.expected(value.text);
      return "1 cards\n1: " + (e + types).replace("\n", "\n1: ");
    }

    @Override
    public String toString() {
      return version + " " + property + " " + value + " " + params + " " + fold;
    }
  }

  static Stream<Case> all() {
    var cases = new ArrayList<Case>();
    for (var v : Version.values()) {
      for (var p : Property.values()) {
        for (var value : Value.values()) {
          if (!p.writable(v, value)) continue;
          for (var params : Params.values()) {
            if (params != Params.NONE && !p.typed()) continue;
            if (params.write(v) == null) continue;
            for (var f : Fold.values()) cases.add(new Case(v, p, value, params, f));
          }
        }
      }
    }
    return cases.stream();
  }

  static Stream<Arguments> ours() {
    return all().map(Arguments::of);
  }

  /// This reader, in strict mode, reads each as written.
  @ParameterizedTest(name = "{0}")
  @MethodSource("ours")
  void ours(Case c) {
    var cards = READER.read(c.text()).cards().stream().map(Field::ours).toList();
    assertEquals(c.expected(), c.property.field.of(cards), c.text());
  }

  static Stream<Arguments> oracles() {
    return all().flatMap(c -> Stream.of(OracleTest.Oracle.values()).map(o -> Arguments.of(c, o)));
  }

  /// Each oracle reads each as written, but where listed.
  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("oracles")
  void oracle(Case c, OracleTest.Oracle o) {
    String theirs;
    try {
      theirs = c.property.field.of(o.read(c.text()));
    } catch (RuntimeException e) {
      theirs = "fails: " + e.getMessage();
    }
    var reason = reason(c, o);
    if (reason == null) {
      assertEquals(c.expected(), theirs, c.text());
    } else {
      assertNotEquals(c.expected(), theirs, () -> "Listed as differing, but now agrees: " + reason);
    }
  }

  static @Nullable String reason(Case c, OracleTest.Oracle o) {
    for (var d : DIFFERENCES) {
      if (d.applies().test(c, o)) return d.reason();
    }
    return null;
  }

  interface Rule {
    boolean test(Case c, OracleTest.Oracle o);
  }

  record Difference(String reason, Rule applies) {}

  static boolean quotedPrintable(Case c) {
    return c.version == Version.V2_1 && (!c.value.text.chars().allMatch(x -> x >= 0x20 && x < 0x7f) || c.value.text.contains("="));
  }

  static boolean text(Property p) {
    return switch (p) {
      case FN, NOTE, TITLE, EMAIL, TEL, NICKNAME_LIST, CATEGORIES_LIST -> true;
      default -> false;
    };
  }

  static final List<Difference> DIFFERENCES = List.of(
      new Difference("vCard 2.1 folded at every character: ez-vcard drops all the white space that starts a folded 2.1"
          + " line, so a space folded onto its own line is lost; this reader drops the one space a fold adds, as"
          + " RFC 6350 says and as writers fold", (c, o) -> o == OracleTest.Oracle.EZ_VCARD && c.version == Version.V2_1
              && c.fold == Fold.EVERY_CHARACTER && !quotedPrintable(c)
              && c.property.line(c.version, c.value.text, "").substring(1).contains(" ")),
      new Difference(OracleTest.VCARD_21, (c, o) -> o == OracleTest.Oracle.ICALJS && c.version == Version.V2_1
          && (c.params == Params.LIST || quotedPrintable(c))),
      new Difference("ICAL.js reads vCard 2.1 as 3.0, where a comma in N or ADR separates values; 2.1 has no lists",
          (c, o) -> o == OracleTest.Oracle.ICALJS && c.version == Version.V2_1 && c.value.text.contains(",")
              && (c.property == Property.N_FAMILY || c.property == Property.ADR_STREET)),
      new Difference("ICAL.js reads a vCard 3.0 TEL as a phone number, not text, and undoes none of its escapes",
          (c, o) -> o == OracleTest.Oracle.ICALJS && c.property == Property.TEL && c.version == Version.V3_0
              && (c.value.text.contains("\\") || c.value.text.contains("\n"))),
      new Difference("ICAL.js keeps \\; in a text value (RFC 6350's grammar has no such escape outside structured values,"
          + " though apps write it and RFC 2426 has it); this reader and ez-vcard undo it. Not in a"
          + " vCard 2.1 or 3.0 TEL, which it reads as a phone number",
          (c, o) -> o == OracleTest.Oracle.ICALJS && text(c.property) && c.value.text.contains(";")
              && (c.property != Property.TEL || c.version == Version.V4_0)));

  static List<Case> list() {
    return all().toList();
  }
}
