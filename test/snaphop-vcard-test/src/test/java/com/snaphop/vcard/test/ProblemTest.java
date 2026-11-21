package com.snaphop.vcard.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.snaphop.vcard.VcardParseException;
import com.snaphop.vcard.VcardReader;
import com.snaphop.vcard.VcardWarning;
import java.util.ArrayList;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/// vCard text with something wrong, and what each mode says about it, in
/// full: the message [VcardReader.Mode#STRICT] throws, its line and its
/// problem, and the warnings of [VcardReader.Mode#LAX], with what lax mode
/// still reads.
class ProblemTest {

  static final VcardReader STRICT = VcardReader.builder().mode(VcardReader.Mode.STRICT).build();
  static final VcardReader LAX = VcardReader.builder().build();

  enum Problem {
    /// A sign-in page where a file was: not vCard at all, so lax mode fails too.
    HTML("<html>Sign in to see your contacts</html>\n", 1, """
        Line 1: Not vCard: the text should start with BEGIN:VCARD, but starts with "<html>Sign in to see your contacts</html>\"""",
        "Not vCard: the text should start with BEGIN:VCARD", null, null),
    EMPTY("\r\n\r\n", 0, "Not vCard: the text is empty.", "Not vCard: the text is empty", null, null),
    ONLY_A_MARK("﻿", 0, "Not vCard: the text is empty.", "Not vCard: the text is empty", null, null),
    NO_VERSION(card("FN:Maya"), 1, """
        Line 1: A VCARD has no VERSION: "BEGIN:VCARD\"""", "A VCARD has no VERSION", """
        Line 1: A VCARD has no VERSION, so it is read as vCard 3.0: "BEGIN:VCARD\"""", "fn: Maya"),
    UNKNOWN_VERSION(card("VERSION:5.0", "FN:Maya"), 2, """
        Line 2: VERSION is not 2.1, 3.0 or 4.0: "VERSION:5.0\"""", "VERSION is not 2.1, 3.0 or 4.0", """
        Line 2: VERSION is not 2.1, 3.0 or 4.0, so the card is read as vCard 3.0: "VERSION:5.0\"""", "fn: Maya"),
    /// Strict mode alone cares: the card reads as well without it.
    NO_FN(card("VERSION:3.0", "N:Rivera;Maya;;;"), 1, """
        Line 1: A VCARD has no FN, which vCard 3.0 requires: "BEGIN:VCARD\"""", "A VCARD has no FN, which vCard 3.0 requires",
        "", "n: Rivera | Maya |  |  |"),
    BLANK_LINE(card("VERSION:3.0", "", "FN:Maya"), 3, "Line 3: A blank line, which vCard does not allow.",
        "A blank line, which vCard does not allow", "", "fn: Maya"),
    NOT_A_CONTENT_LINE(card("VERSION:3.0", "FN:Maya", "Call after 5"), 4, """
        Line 4: Not a content line (NAME:value): "Call after 5\"""", "Not a content line (NAME:value)", """
        Line 4: Not a content line (NAME:value), so it is left out: "Call after 5\"""", "fn: Maya"),
    NO_COLON(card("VERSION:3.0", "FN:Maya", "TEL;TYPE=cell"), 4, """
        Line 4: A property has no ':' before its value: "TEL;TYPE=cell\"""", "A property has no ':' before its value", """
        Line 4: A property has no ':' before its value, so it is left out: "TEL;TYPE=cell\"""", "fn: Maya"),
    OPEN_QUOTE(card("VERSION:4.0", "FN:Maya", "ADR;LABEL=\"12 Orchard Lane:;;12 Orchard Lane;;;;"), 4, """
        Line 4: A quoted parameter value has no closing quote: "ADR;LABEL="12 Orchard Lane:;;12 Orchard Lane;;;;\"""",
        "A quoted parameter value has no closing quote", """
        Line 4: A quoted parameter value has no closing quote, so the line is left out: "ADR;LABEL="12 Orchard Lane:;;12 Orchard Lane;;;;\"""",
        "fn: Maya"),
    PARAMETER_WITHOUT_NAME(card("VERSION:3.0", "FN:Maya", "TEL;=cell:555-010-0101"), 4, """
        Line 4: A parameter has no NAME=value: "TEL;=cell:555-010-0101\"""", "A parameter has no NAME=value", """
        Line 4: A parameter has no NAME=value, so the line is left out: "TEL;=cell:555-010-0101\"""", "fn: Maya"),
    /// vCard 2.1's bare parameters in a 3.0 card: strict mode alone cares.
    BARE_PARAMETER(card("VERSION:3.0", "FN:Maya", "TEL;CELL:555-010-0101"), 4, """
        Line 4: A parameter has no name, which only vCard 2.1 allows: "TEL;CELL:555-010-0101\"""",
        "A parameter has no name, which only vCard 2.1 allows", "", "fn: Maya\n  tel: 555-010-0101 [cell]"),
    EMPTY_PARAMETER(card("VERSION:2.1", "FN:Maya", "TEL;;CELL:555-010-0101"), 4, """
        Line 4: A parameter is empty: "TEL;;CELL:555-010-0101\"""", "A parameter is empty", "",
        "fn: Maya\n  tel: 555-010-0101 [cell]"),
    QUOTED_PRINTABLE_IN_3_0(card("VERSION:3.0", "FN;ENCODING=QUOTED-PRINTABLE:Jos=C3=A9"), 3, """
        Line 3: A quoted-printable value, which only vCard 2.1 allows: "FN;ENCODING=QUOTED-PRINTABLE:Jos=C3=A9\"""",
        "A quoted-printable value, which only vCard 2.1 allows", "", "fn: José"),
    BAD_QUOTED_PRINTABLE(card("VERSION:2.1", "FN;ENCODING=QUOTED-PRINTABLE:50=ZZ off"), 3, """
        Line 3: A quoted-printable value has an = that is not before two hex digits: "FN;ENCODING=QUOTED-PRINTABLE:50=ZZ off\"""",
        "A quoted-printable value has an = that is not before two hex digits", """
        Line 3: A quoted-printable value has an = that is not before two hex digits, so it is read as written: "FN;ENCODING=QUOTED-PRINTABLE:50=ZZ off\"""",
        "fn: 50=ZZ off"),
    UNKNOWN_CHARSET(card("VERSION:2.1", "FN;CHARSET=KLINGON;ENCODING=QUOTED-PRINTABLE:Jos=C3=A9"), 3, """
        Line 3: The CHARSET is not one this reader knows: "FN;CHARSET=KLINGON;ENCODING=QUOTED-PRINTABLE:Jos=C3=A9\"""",
        "The CHARSET is not one this reader knows", """
        Line 3: The CHARSET is not one this reader knows, so the value is read as UTF-8: "FN;CHARSET=KLINGON;ENCODING=QUOTED-PRINTABLE:Jos=C3=A9\"""",
        "fn: José"),
    NOT_A_DATE(card("VERSION:3.0", "FN:Maya", "BDAY:2014-02-30"), 4, """
        Line 4: BDAY is not a date: "BDAY:2014-02-30\"""", "BDAY is not a date", """
        Line 4: BDAY is not a date, so it is left out: "BDAY:2014-02-30\"""", "fn: Maya"),
    FOLD_FIRST(" continued\r\n" + card("VERSION:3.0", "FN:Maya"), 1, """
        Line 1: A folded line has no line before it to continue: " continued\"""",
        "A folded line has no line before it to continue", """
        Line 1: A folded line has no line before it to continue, so it is left out: " continued\"""", "fn: Maya"),
    LINE_OUTSIDE(card("VERSION:3.0", "FN:Maya") + "FN:Lost\r\n", 5, """
        Line 5: A line is outside of a VCARD: "FN:Lost\"""", "A line is outside of a VCARD", """
        Line 5: A line is outside of a VCARD, so it is left out: "FN:Lost\"""", "fn: Maya"),
    END_WITHOUT_BEGIN(card("VERSION:3.0", "FN:Maya") + "END:VCARD\r\n", 5, """
        Line 5: END:VCARD has no BEGIN:VCARD: "END:VCARD\"""", "END:VCARD has no BEGIN:VCARD", """
        Line 5: END:VCARD has no BEGIN:VCARD, so it is left out: "END:VCARD\"""", "fn: Maya"),
    NO_END("BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Maya\r\n", 1, """
        Line 1: A VCARD has no END:VCARD before the text ends: "BEGIN:VCARD\"""",
        "A VCARD has no END:VCARD before the text ends", """
        Line 1: A VCARD has no END:VCARD before the text ends, so it ends there: "BEGIN:VCARD\"""", "fn: Maya"),
    BEGIN_BEFORE_END("BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Maya\r\n" + card("VERSION:3.0", "FN:Jordan"), 1, """
        Line 1: A VCARD has no END:VCARD before line 4's BEGIN:VCARD: "BEGIN:VCARD\"""",
        "A VCARD has no END:VCARD before line 4's BEGIN:VCARD", """
        Line 1: A VCARD has no END:VCARD before line 4's BEGIN:VCARD, so it ends there: "BEGIN:VCARD\"""",
        "fn: Maya\nVCARD 3.0 individual\n  fn: Jordan"),
    /// A long line is quoted cut short, never whole.
    LONG_LINE(card("VERSION:3.0", "FN:Maya", "X".repeat(150)), 4, """
        Line 4: Not a content line (NAME:value): "%s...\"""".formatted("X".repeat(100)), "Not a content line (NAME:value)", """
        Line 4: Not a content line (NAME:value), so it is left out: "%s...\"""".formatted("X".repeat(100)), "fn: Maya");

    final String text;
    final int line;
    final String strict;
    final String problem;
    /// The lax warnings, a line each; "" for none; null when lax fails too.
    final @Nullable String lax;
    /// What lax mode reads, after the card's first line; null when it fails.
    final @Nullable String reads;

    Problem(String text, int line, String strict, String problem, @Nullable String lax, @Nullable String reads) {
      this.text = text;
      this.line = line;
      this.strict = strict;
      this.problem = problem;
      this.lax = lax;
      this.reads = reads;
    }
  }

  static String card(String... lines) {
    return "BEGIN:VCARD\r\n" + String.join("\r\n", lines) + "\r\nEND:VCARD\r\n";
  }

  @ParameterizedTest
  @EnumSource(Problem.class)
  void strict(Problem p) {
    var e = assertThrows(VcardParseException.class, () -> STRICT.read(p.text));
    assertEquals(p.strict, e.getMessage());
    assertEquals(p.line, e.line());
    assertEquals(p.problem, e.problem());
  }

  @ParameterizedTest
  @EnumSource(Problem.class)
  void lax(Problem p) {
    if (p.lax == null) {
      var e = assertThrows(VcardParseException.class, () -> LAX.read(p.text));
      assertEquals(p.strict, e.getMessage());
      return;
    }
    var book = LAX.read(p.text);
    assertEquals(p.lax, String.join("\n", book.warnings().stream().map(VcardWarning::message).toList()));
    var rendered = Render.book(book);
    assertEquals(p.reads, rendered.substring(rendered.indexOf('\n') + 3));
    // Each warning without its text says only the reader's own words.
    for (var w : book.warnings()) {
      assertEquals("Line " + w.line() + ": " + w.problem() + ", so " + w.consequence(), w.messageWithoutText());
    }
  }

  /// Bytes that are not UTF-8: an error in strict mode, and in lax mode
  /// read as Windows-1252, as older Outlook wrote them.
  @ParameterizedTest
  @EnumSource(VcardReader.Mode.class)
  void notUtf8(VcardReader.Mode mode) {
    var bytes = card("VERSION:2.1", "FN:Hélène Dubois").getBytes(java.nio.charset.Charset.forName("windows-1252"));
    var reader = VcardReader.builder().mode(mode).build();
    var message = """
        Line 3: The text is not UTF-8, so it is all read as Windows-1252: "FN:Hélène Dubois\"""";
    if (mode == VcardReader.Mode.STRICT) {
      var e = assertThrows(VcardParseException.class, () -> reader.read(bytes));
      assertEquals("""
          Line 3: The text is not UTF-8: "FN:Hélène Dubois\"""", e.getMessage());
      return;
    }
    var book = reader.read(bytes);
    assertEquals(message, book.warnings().getFirst().message());
    assertEquals("Hélène Dubois", book.cards().getFirst().formattedName());
  }

  /// Every warning of every sample, in full: the samples are as apps write
  /// them, so there should be none.
  @ParameterizedTest
  @EnumSource(Corpus.Sample.class)
  void samples(Corpus.Sample s) {
    var warnings = new ArrayList<String>();
    LAX.read(s.bytes()).warnings().forEach(w -> warnings.add(w.message()));
    assertEquals(s == Corpus.Sample.OUTLOOK ? 1 : 0, warnings.size(), () -> String.join("\n", warnings));
  }
}
