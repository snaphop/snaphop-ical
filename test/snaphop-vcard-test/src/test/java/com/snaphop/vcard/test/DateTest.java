package com.snaphop.vcard.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.snaphop.vcard.VcardReader;
import com.snaphop.vcard.VcardWarning;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/// Birthdays and anniversaries in every form apps write them, in each
/// version, as `BDAY` and as `ANNIVERSARY`: every [Form] × version ×
/// property, read by this reader and each oracle.
class DateTest {

  static final VcardReader READER = VcardReader.builder().build();

  /// A date as written, and the day it is: `--MM-DD` without a year, `""`
  /// for no day at all, or null for a value that is not a date, which is
  /// reported.
  enum Form {
    DATE("1996-04-15", "1996-04-15"),
    BASIC("19960415", "1996-04-15"),
    VALUE_DATE("1996-04-15", "1996-04-15", ";VALUE=date"),
    DATE_TIME_UTC("1996-04-15T10:00:00Z", "1996-04-15"),
    BASIC_DATE_TIME_UTC("19960415T100000Z", "1996-04-15"),
    DATE_TIME_OFFSET("1996-04-15T22:30:00-06:00", "1996-04-15"),
    DATE_TIME_FLOATING("1996-04-15T10:00:00", "1996-04-15"),
    LEAP_DAY("2000-02-29", "2000-02-29"),
    NO_YEAR("--0415", "--04-15"),
    NO_YEAR_EXTENDED("--04-15", "--04-15"),
    NO_YEAR_LEAP_DAY("--0229", "--02-29"),
    /// Apple's birthday without a year.
    APPLE_NO_YEAR("1604-04-15", "--04-15", ";X-APPLE-OMIT-YEAR=1604"),
    /// The year Apple leaves out must be the year written.
    APPLE_OTHER_YEAR("1996-04-15", "1996-04-15", ";X-APPLE-OMIT-YEAR=1604"),
    TEXT("circa 1800", "", ";VALUE=text"),
    YEAR("1996", ""),
    YEAR_MONTH("1996-04", ""),
    MONTH("--04", ""),
    DAY_OF_MONTH("---15", ""),
    TIME_ONLY("T102200", ""),
    NOT_A_DAY("2014-02-30", null),
    NOT_A_LEAP_YEAR("1999-02-29", null),
    MONTH_13("20141301", null),
    WORDS("April 15", null),
    /// Digits of another script, which `\d` in Java does not match.
    ARABIC_DIGITS("١٩٩٦-٠٤-١٥", null);

    final String written;
    final @Nullable String day;
    final String params;

    Form(String written, @Nullable String day) {
      this(written, day, "");
    }

    Form(String written, @Nullable String day, String params) {
      this.written = written;
      this.day = day;
      this.params = params;
    }
  }

  enum Version {
    V2_1,
    V3_0,
    V4_0
  }

  enum Property {
    BDAY(Field.BDAY),
    ANNIVERSARY(Field.ANNIVERSARY);

    final Field field;

    Property(Field field) {
      this.field = field;
    }
  }

  record Case(Form form, Version version, Property property) {

    String text() {
      return "BEGIN:VCARD\r\nVERSION:" + version.name().substring(1).replace('_', '.') + "\r\nFN:Maya\r\n" + property
          + form.params + ":" + form.written + "\r\nEND:VCARD\r\n";
    }

    String expected() {
      var day = form.day == null ? "" : form.day;
      return "1 cards" + (day.isEmpty() ? "" : "\n1: " + day);
    }

    @Override
    public String toString() {
      return form + " " + version + " " + property;
    }
  }

  static Stream<Case> all() {
    var cases = new ArrayList<Case>();
    for (var f : Form.values()) {
      for (var v : Version.values()) {
        for (var p : Property.values()) cases.add(new Case(f, v, p));
      }
    }
    return cases.stream();
  }

  static Stream<Arguments> ours() {
    return all().map(Arguments::of);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("ours")
  void ours(Case c) {
    var warnings = new ArrayList<VcardWarning>();
    var cards = READER.read(c.text(), warnings::add).cards().stream().map(Field::ours).toList();
    assertEquals(c.expected(), c.property.field.of(cards));
    var expected = c.form.day == null
        ? List.of("Line 4: " + c.property + " is not a date, so it is left out: \"" + c.property + c.form.params + ":" + c.form.written + "\"")
        : List.of();
    assertEquals(expected, warnings.stream().map(VcardWarning::message).toList());
  }

  static Stream<Arguments> oracles() {
    return all().flatMap(c -> Stream.of(OracleTest.Oracle.values()).map(o -> Arguments.of(c, o)));
  }

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
      assertEquals(c.expected(), theirs);
    } else {
      assertNotEquals(c.expected(), theirs, () -> "Listed as differing, but now agrees: " + reason);
    }
  }

  static @Nullable String reason(Case c, OracleTest.Oracle o) {
    if (c.form == Form.APPLE_NO_YEAR) return OracleTest.OMIT_YEAR;
    if (o == OracleTest.Oracle.EZ_VCARD && (c.form == Form.NOT_A_DAY || c.form == Form.NOT_A_LEAP_YEAR)) {
      return "ez-vcard keeps a day that does not exist (February 30, or February 29 of 1999) as a partial date,"
          + " without a warning; this reader reports it and leaves it out";
    }
    if (o == OracleTest.Oracle.ICALJS && !c.expected().contains(": ") && c.form != Form.TIME_ONLY) {
      return "ICAL.js gives a date as its text, unchecked: a year or a month alone, words, another script's digits,"
          + " or a day that does not exist";
    }
    return null;
  }
}
