package com.snaphop.vcard.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.snaphop.vcard.VcardBook;
import com.snaphop.vcard.VcardReader;
import com.snaphop.vcard.VcardWarning;
import com.snaphop.vcard.test.Corpus.Sample;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/// Every sample written another way that means the same, read again: every
/// [Sample] × [Transform]. Each must give the same cards, value for value,
/// and the same warnings but for where they are.
class TransformTest {

  static final VcardReader READER = VcardReader.builder().build();

  /// A way to write the same cards. `read` reads the transformed text.
  enum Transform {
    LF {
      @Override
      String apply(String text) {
        return text.replace("\r\n", "\n");
      }
    },
    CR {
      @Override
      String apply(String text) {
        return text.replace("\r\n", "\r");
      }
    },
    /// Each line ending one of CRLF, LF or CR, at random.
    MIXED_LINE_ENDINGS {
      @Override
      String apply(String text) {
        var random = new Random(text.length());
        var endings = new String[] {"\r\n", "\n", "\r"};
        var b = new StringBuilder();
        for (var line : lines(text)) b.append(line).append(endings[random.nextInt(3)]);
        return b.toString();
      }
    },
    NO_LAST_LINE_BREAK {
      @Override
      String apply(String text) {
        return text.stripTrailing();
      }
    },
    /// Each line folded after every character.
    FOLD_1 {
      @Override
      String apply(String text) {
        return fold(text, 1, " ");
      }
    },
    FOLD_3 {
      @Override
      String apply(String text) {
        return fold(text, 3, " ");
      }
    },
    FOLD_TAB_10 {
      @Override
      String apply(String text) {
        return fold(text, 10, "\t");
      }
    },
    /// Folded at 75 octets of UTF-8, as RFC 6350 says to.
    FOLD_75 {
      @Override
      String apply(String text) {
        return fold(text, 75, " ");
      }
    },
    BYTE_ORDER_MARK {
      @Override
      String apply(String text) {
        return "\uFEFF" + text;
      }
    },
    LOWER_CASE_NAMES {
      @Override
      String apply(String text) {
        return heads(text, x -> x.toLowerCase(Locale.ROOT));
      }
    },
    UPPER_CASE_NAMES {
      @Override
      String apply(String text) {
        return heads(text, x -> x.toUpperCase(Locale.ROOT));
      }
    },
    /// A blank line after each card, which lax mode passes over.
    BLANK_LINES {
      @Override
      String apply(String text) {
        return Pattern.compile("(?im)^(END:VCARD)\\r?\\n?").matcher(text).replaceAll("$1\r\n\r\n");
      }
    },
    UTF_8_BYTES {
      @Override
      Got read(String text) {
        return Got.of(READER.read(text.getBytes(StandardCharsets.UTF_8)));
      }
    },
    UTF_8_BYTES_WITH_MARK {
      @Override
      Got read(String text) {
        return Got.of(READER.read(("\uFEFF" + text).getBytes(StandardCharsets.UTF_8)));
      }
    },
    UTF_16LE_BYTES_WITH_MARK {
      @Override
      Got read(String text) {
        return Got.of(READER.read(("\uFEFF" + text).getBytes(StandardCharsets.UTF_16LE)));
      }
    },
    UTF_16BE_BYTES_WITH_MARK {
      @Override
      Got read(String text) {
        return Got.of(READER.read(("\uFEFF" + text).getBytes(StandardCharsets.UTF_16BE)));
      }
    },
    /// A reader that gives one character at a time.
    READER_ONE_CHAR_AT_A_TIME {
      @Override
      Got read(String text) {
        try {
          return Got.of(READER.read(new Reader() {
            final StringReader in = new StringReader(text);

            @Override
            public int read(char[] buffer, int offset, int length) throws IOException {
              return in.read(buffer, offset, Math.min(1, length));
            }

            @Override
            public void close() {}
          }));
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    },
    /// The warnings handed to a consumer, not kept.
    CONSUMER {
      @Override
      Got read(String text) {
        var warnings = new ArrayList<VcardWarning>();
        var book = READER.read(text, warnings::add);
        assertEquals(List.of(), book.warnings());
        return new Got(Render.book(book), problems(warnings));
      }
    };

    String apply(String text) {
      return text;
    }

    Got read(String text) {
      return Got.of(READER.read(apply(text)));
    }
  }

  static Stream<Arguments> cases() {
    var cases = new ArrayList<Arguments>();
    for (var s : Sample.values()) {
      for (var t : Transform.values()) cases.add(Arguments.of(s, t));
    }
    return cases.stream();
  }

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("cases")
  void same(Sample sample, Transform t) {
    var text = sample.text();
    var expected = Got.of(READER.read(text));
    var actual = t.read(text);
    assertEquals(expected.cards(), actual.cards(), () -> t + " changed what " + sample + " reads as");
    assertEquals(expected.problems(), actual.problems(), () -> t + " changed the warnings of " + sample);
  }

  /// A sample read twice in one text: twice its cards.
  @ParameterizedTest
  @EnumSource(Sample.class)
  void twice(Sample sample) {
    var text = sample.text();
    if (!text.endsWith("\n") && !text.endsWith("\r")) text += "\r\n";
    var once = Render.book(READER.read(text));
    assertEquals(once + "\n" + once, Render.book(READER.read(text + text)));
  }

  /// What a read gave: its cards rendered, and its warnings without where they are.
  record Got(String cards, List<String> problems) {
    static Got of(VcardBook book) {
      return new Got(Render.book(book), TransformTest.problems(book.warnings()));
    }
  }

  static List<String> problems(List<VcardWarning> warnings) {
    return warnings.stream().map(w -> w.problem() + ", so " + w.consequence()).toList();
  }

  /// The text's physical lines, without their line breaks.
  static List<String> lines(String text) {
    var lines = new ArrayList<>(List.of(text.split("\r\n|\r|\n", -1)));
    if (lines.getLast().isEmpty()) lines.removeLast();
    return lines;
  }

  /// Each property's lines unfolded, then folded every `width` octets of
  /// UTF-8 (never inside a character), with `indent` before each part
  /// after the first. A vCard 2.1 value that goes on with quoted-printable's
  /// soft line breaks is left as it is: the reader can't tell a fold from a
  /// soft break's next line that starts with a space, and no app writes both.
  static String fold(String text, int width, String indent) {
    var b = new StringBuilder();
    boolean soft = false;
    for (var line : unfolded(text)) {
      boolean continues = soft;
      soft = (soft || isQuotedPrintable(line)) && line.endsWith("=");
      if (line.isEmpty() || continues || soft || isQuotedPrintable(line)) {
        b.append(line).append("\r\n");
        continue;
      }
      int octets = 0;
      for (int i = 0; i < line.length(); ) {
        int cp = line.codePointAt(i);
        int size = Character.toString(cp).getBytes(StandardCharsets.UTF_8).length;
        if (octets > 0 && octets + size > width) {
          b.append("\r\n").append(indent);
          octets = 0;
        }
        b.appendCodePoint(cp);
        octets += size;
        i += Character.charCount(cp);
      }
      b.append("\r\n");
    }
    return b.toString();
  }

  private static boolean isQuotedPrintable(String line) {
    int colon = line.indexOf(':');
    return colon > 0 && line.substring(0, colon).toUpperCase(Locale.ROOT).contains("QUOTED-PRINTABLE");
  }

  /// The text's lines with folded ones joined (across the blank lines an
  /// iPhone's CR CR LF makes), and other blank lines kept.
  static List<String> unfolded(String text) {
    var out = new ArrayList<String>();
    for (var line : lines(text)) {
      if (!line.isEmpty() && (line.charAt(0) == ' ' || line.charAt(0) == '\t')) {
        int last = out.size() - 1;
        while (last >= 0 && out.get(last).isEmpty()) last--;
        if (last >= 0) {
          while (out.size() > last + 1) out.removeLast();
          out.set(last, out.get(last) + line.substring(1));
          continue;
        }
      }
      out.add(line);
    }
    return out;
  }

  private static final Pattern HEAD = Pattern.compile("^([A-Za-z0-9_.-]+)((?:;[^:\"]*(?:\"[^\"]*\")?)*):");

  /// Each property's name, group and parameter names changed by `change`,
  /// and `BEGIN:VCARD` and `END:VCARD` whole. The lines a quoted-printable
  /// value goes on to are left as they are.
  static String heads(String text, UnaryOperator<String> change) {
    var b = new StringBuilder();
    boolean soft = false;
    for (var line : lines(text)) {
      boolean continues = soft;
      soft = (soft || isQuotedPrintable(line)) && line.endsWith("=");
      var m = HEAD.matcher(line);
      if (continues) {
        b.append(line);
      } else if (line.regionMatches(true, 0, "BEGIN:", 0, 6) || line.regionMatches(true, 0, "END:", 0, 4)) {
        b.append(change.apply(line));
      } else if (m.find()) {
        var params = new StringBuilder();
        for (var p : m.group(2).split(";", -1)) {
          if (p.isEmpty()) continue;
          int eq = p.indexOf('=');
          params.append(';').append(eq < 0 ? p : change.apply(p.substring(0, eq)) + p.substring(eq));
        }
        b.append(change.apply(m.group(1))).append(params).append(':').append(line.substring(m.end()));
      } else {
        b.append(line);
      }
      b.append("\r\n");
    }
    return b.toString();
  }
}
