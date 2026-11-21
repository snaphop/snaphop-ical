package com.snaphop.vcard.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.snaphop.vcard.VcardParseException;
import com.snaphop.vcard.VcardReader;
import com.snaphop.vcard.VcardWarning;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/// Text written to hurt a reader, and every sample broken at random: the
/// reader must finish soon, never throw anything but a
/// [VcardParseException], and never put the text in a warning's problem or
/// consequence.
class HostileTest {

  static final VcardReader LAX = VcardReader.builder().build();
  static final VcardReader STRICT = VcardReader.builder().mode(VcardReader.Mode.STRICT).build();

  /// Text a reader that is not linear would take minutes or hours on, or run
  /// out of memory for. Each is read in well under a second; the limit is
  /// three seconds, so that a slow machine passes and a quadratic reader fails.
  enum Big {
    /// A 64 MB photo, folded as phones fold it: passed over, not kept.
    PHOTO {
      @Override
      String text() {
        var b = new StringBuilder("BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Maya\r\nPHOTO;ENCODING=b;TYPE=JPEG:");
        var line = " " + "A".repeat(74) + "\r\n";
        b.append("\r\n").append(line.repeat(64 * 1024 * 1024 / 77));
        return b.append("END:VCARD\r\n").toString();
      }

      @Override
      String expected() {
        return "VCARD 3.0 individual\n  fn: Maya";
      }
    },
    /// One note folded a million times.
    FOLDED_NOTE {
      @Override
      String text() {
        return "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Maya\r\nNOTE:x\r\n" + " y\r\n".repeat(1_000_000) + "END:VCARD\r\n";
      }

      @Override
      String expected() {
        return "VCARD 3.0 individual\n  fn: Maya\n  note: x" + "y".repeat(1_000_000);
      }
    },
    /// A quoted-printable note with a million soft line breaks.
    SOFT_BREAKS {
      @Override
      String text() {
        return "BEGIN:VCARD\r\nVERSION:2.1\r\nFN:Maya\r\nNOTE;ENCODING=QUOTED-PRINTABLE:x=\r\n" + "=41=\r\n".repeat(1_000_000)
            + "z\r\nEND:VCARD\r\n";
      }

      @Override
      String expected() {
        return "VCARD 2.1 individual\n  fn: Maya\n  note: x" + "A".repeat(1_000_000) + "z";
      }
    },
    /// 100,000 cards.
    CARDS {
      @Override
      String text() {
        return "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Maya\r\nEND:VCARD\r\n".repeat(100_000);
      }

      @Override
      String expected() {
        return String.join("\n", java.util.Collections.nCopies(100_000, "VCARD 3.0 individual\n  fn: Maya"));
      }
    },
    /// 100,000 vCard 2.1 agents, each inside the one before.
    AGENTS {
      @Override
      String text() {
        return "BEGIN:VCARD\r\nVERSION:2.1\r\nFN:Maya\r\n" + "AGENT:\r\nBEGIN:VCARD\r\nVERSION:2.1\r\n".repeat(100_000)
            + "END:VCARD\r\n".repeat(100_001);
      }

      @Override
      String expected() {
        return "VCARD 2.1 individual\n  fn: Maya";
      }
    },
    /// One property with 100,000 types, in a 2.1 card's bare form.
    PARAMETERS {
      @Override
      String text() {
        return "BEGIN:VCARD\r\nVERSION:2.1\r\nFN:Maya\r\nTEL" + ";HOME".repeat(100_000) + ":555\r\nEND:VCARD\r\n";
      }

      @Override
      String expected() {
        return "VCARD 2.1 individual\n  fn: Maya\n  tel: 555 [home]";
      }
    },
    /// One property with 100,000 different types.
    TYPES {
      @Override
      String text() {
        var b = new StringBuilder("BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Maya\r\nTEL;TYPE=");
        for (int i = 0; i < 100_000; i++) b.append(i == 0 ? "" : ",").append('t').append(i);
        return b.append(":555\r\nEND:VCARD\r\n").toString();
      }

      @Override
      String expected() {
        var types = new ArrayList<String>();
        for (int i = 0; i < 100_000; i++) types.add("t" + i);
        return "VCARD 3.0 individual\n  fn: Maya\n  tel: 555 " + types;
      }
    },
    /// 100,000 emails, each with a label in its own group.
    LABELS {
      @Override
      String text() {
        var b = new StringBuilder("BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Maya\r\n");
        for (int i = 0; i < 100_000; i++) b.append("item").append(i).append(".EMAIL:m@x\r\nitem").append(i).append(".X-ABLabel:L\r\n");
        return b.append("END:VCARD\r\n").toString();
      }

      @Override
      String expected() {
        return "VCARD 3.0 individual\n  fn: Maya\n" + "  email: m@x label=L\n".repeat(100_000).stripTrailing();
      }
    },
    /// 10 MB of one line that is not a property, quoted in its warning cut short.
    LONG_JUNK {
      @Override
      String text() {
        return "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Maya\r\n" + "x".repeat(10 * 1024 * 1024) + "\r\nEND:VCARD\r\n";
      }

      @Override
      String expected() {
        return "VCARD 3.0 individual\n  fn: Maya";
      }
    },
    /// 100,000 backslashes and as many escaped commas.
    ESCAPES {
      @Override
      String text() {
        return "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Maya\r\nCATEGORIES:" + "a\\,".repeat(100_000) + "\\\\".repeat(100_000)
            + "\r\nEND:VCARD\r\n";
      }

      @Override
      String expected() {
        return "VCARD 3.0 individual\n  fn: Maya\n  categories: " + "a,".repeat(100_000) + "\\".repeat(100_000);
      }
    };

    abstract String text();

    abstract String expected();
  }

  @ParameterizedTest
  @EnumSource(Big.class)
  void big(Big big) {
    var text = big.text();
    var book = assertTimeoutPreemptively(Duration.ofSeconds(3), () -> LAX.read(text));
    assertEquals(big.expected(), Render.book(book));
  }

  /// The problems this reader reports, and what it does about them: its own
  /// words, and property names it knows. Nothing from the text.
  static final Pattern PROBLEM = Pattern.compile(String.join("|",
      "Not vCard: the text is empty",
      "Not vCard: the text should start with BEGIN:VCARD",
      "The text is not UTF-8",
      "A blank line, which vCard does not allow",
      "A folded line has no line before it to continue",
      "A line is outside of a VCARD",
      "END:VCARD has no BEGIN:VCARD",
      "A VCARD has no END:VCARD before the text ends",
      "A VCARD has no END:VCARD before line \\d+'s BEGIN:VCARD",
      "A VCARD has no VERSION",
      "VERSION is not 2\\.1, 3\\.0 or 4\\.0",
      "A VCARD has no FN, which vCard (3\\.0|4\\.0) requires",
      "Not a content line \\(NAME:value\\)",
      "A property has no ':' before its value",
      "A quoted parameter value has no closing quote",
      "A parameter has no NAME=value",
      "A parameter has no name, which only vCard 2\\.1 allows",
      "A parameter is empty",
      "A quoted-printable value, which only vCard 2\\.1 allows",
      "A quoted-printable value has an = that is not before two hex digits",
      "The CHARSET is not one this reader knows",
      "(BDAY|ANNIVERSARY|X-ANNIVERSARY|X-MS-ANNIVERSARY|X-EVOLUTION-ANNIVERSARY|X-ABDATE) is not a date"));

  static final Pattern CONSEQUENCE = Pattern.compile(String.join("|",
      "it is left out",
      "the line is left out",
      "it is read as vCard 3\\.0",
      "the card is read as vCard 3\\.0",
      "it ends there",
      "it is read as written",
      "the value is read as UTF-8",
      "it is all read as Windows-1252"));

  /// Ways to break text.
  enum Mutation {
    DELETE_CHARACTERS,
    INSERT_SPECIAL_CHARACTERS,
    DUPLICATE_LINES,
    DELETE_LINES,
    SWAP_LINES,
    TRUNCATE,
    CHANGE_CASE,
    RANDOM_BYTES;

    static final String SPECIAL = ":;,=\"\\^.\r\n \t\uFEFF\u0000é🎂";

    byte[] apply(byte[] bytes, Random random) {
      if (this == RANDOM_BYTES) {
        var b = bytes.clone();
        for (int i = 0; i < 1 + random.nextInt(8); i++) b[random.nextInt(b.length)] = (byte) random.nextInt(256);
        return b;
      }
      var text = new String(bytes, StandardCharsets.UTF_8);
      var lines = new ArrayList<>(List.of(text.split("\r\n|\n|\r", -1)));
      var b = new StringBuilder(text);
      switch (this) {
        case DELETE_CHARACTERS -> {
          for (int i = 0; i < 1 + random.nextInt(8) && !b.isEmpty(); i++) b.deleteCharAt(random.nextInt(b.length()));
          return bytes(b);
        }
        case INSERT_SPECIAL_CHARACTERS -> {
          for (int i = 0; i < 1 + random.nextInt(8); i++) {
            int at = random.nextInt(b.length() + 1);
            int c = SPECIAL.codePointAt(SPECIAL.offsetByCodePoints(0, random.nextInt(SPECIAL.codePointCount(0, SPECIAL.length()))));
            b.insert(at, Character.toString(c));
          }
          return bytes(b);
        }
        case DUPLICATE_LINES -> {
          for (int i = 0; i < 1 + random.nextInt(4); i++) lines.add(random.nextInt(lines.size()), lines.get(random.nextInt(lines.size())));
        }
        case DELETE_LINES -> {
          for (int i = 0; i < 1 + random.nextInt(4) && lines.size() > 1; i++) lines.remove(random.nextInt(lines.size()));
        }
        case SWAP_LINES -> {
          for (int i = 0; i < 1 + random.nextInt(4); i++) java.util.Collections.swap(lines, random.nextInt(lines.size()), random.nextInt(lines.size()));
        }
        case TRUNCATE -> {
          return java.util.Arrays.copyOf(bytes, random.nextInt(bytes.length + 1));
        }
        case CHANGE_CASE -> {
          for (int i = 0; i < 1 + random.nextInt(8); i++) {
            int at = random.nextInt(lines.size());
            lines.set(at, random.nextBoolean() ? lines.get(at).toLowerCase(java.util.Locale.ROOT) : lines.get(at).toUpperCase(java.util.Locale.ROOT));
          }
        }
        default -> throw new AssertionError(this);
      }
      return String.join("\r\n", lines).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] bytes(CharSequence s) {
      return s.toString().getBytes(StandardCharsets.UTF_8);
    }
  }

  static final int TRIES = 200;

  static Stream<Arguments> fuzz() {
    var cases = new ArrayList<Arguments>();
    for (var s : Corpus.Sample.values()) {
      for (var m : Mutation.values()) cases.add(Arguments.of(s, m));
    }
    return cases.stream();
  }

  /// Each sample broken [#TRIES] times each way, the same way each run, and
  /// read in both modes, as text and as bytes.
  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("fuzz")
  void fuzz(Corpus.Sample sample, Mutation mutation) {
    var random = new Random((sample.name() + mutation.name()).hashCode());
    var original = sample.text().getBytes(StandardCharsets.UTF_8);
    for (int i = 0; i < TRIES; i++) {
      var broken = mutation.apply(original, random);
      for (var mode : VcardReader.Mode.values()) {
        var reader = mode == VcardReader.Mode.LAX ? LAX : STRICT;
        check(mode, () -> reader.read(broken), broken);
        check(mode, () -> reader.read(new String(broken, StandardCharsets.UTF_8)), broken);
      }
    }
  }

  private static void check(VcardReader.Mode mode, java.util.function.Supplier<com.snaphop.vcard.VcardBook> read, byte[] text) {
    try {
      var book = read.get();
      for (VcardWarning w : book.warnings()) {
        assertTrue(PROBLEM.matcher(w.problem()).matches(), () -> "A problem not in the reader's words: " + w.problem());
        assertTrue(CONSEQUENCE.matcher(w.consequence()).matches(), () -> "A consequence not in the reader's words: " + w.consequence());
      }
    } catch (VcardParseException e) {
      assertTrue(PROBLEM.matcher(e.problem()).matches(), () -> "A problem not in the reader's words: " + e.problem());
      if (mode == VcardReader.Mode.LAX) assertTrue(e.problem().startsWith("Not vCard"), e::getMessage);
    } catch (RuntimeException | StackOverflowError e) {
      fail("Threw " + e + " on:\n" + new String(text, StandardCharsets.UTF_8), e);
    }
  }
}
