package com.snaphop.vcard;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/// A logical line: physical lines joined where they were folded, or where a
/// quoted-printable value went on to the next. `line` is the physical line
/// it starts on.
record Logical(int line, String text) {}

/// The logical lines between a `BEGIN:VCARD` and its `END:VCARD`.
record RawCard(int line, List<Logical> lines) {}

/// A property as written, after its lines are unfolded: its group, its name
/// upper-cased, its parameters by upper-cased name, and its value still
/// escaped (but decoded, when it was quoted-printable).
record Prop(String group, String name, Map<String, List<String>> params, String value, int line, String text) {

  List<String> params(String name) {
    var values = params.get(name);
    return values == null ? List.of() : values;
  }

  @Nullable String param(String name) {
    var values = params.get(name);
    return values == null || values.isEmpty() ? null : values.getFirst();
  }

  boolean has(String param, String value) {
    for (var v : params(param)) {
      if (v.equalsIgnoreCase(value)) return true;
    }
    return false;
  }
}

/// Bytes as text.
final class Bytes {

  private Bytes() {}

  private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

  /// The bytes as UTF-8 after a byte order mark, or as UTF-16 when the mark
  /// is UTF-16's. Bytes that are not UTF-8 are reported, then the whole text
  /// is read as Windows-1252.
  static String decode(byte[] bytes, Problems problems) {
    int n = bytes.length;
    if (n >= 2 && (bytes[0] & 0xff) == 0xfe && (bytes[1] & 0xff) == 0xff) {
      return new String(bytes, 2, n - 2, StandardCharsets.UTF_16BE);
    }
    if (n >= 2 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xfe) {
      return new String(bytes, 2, n - 2, StandardCharsets.UTF_16LE);
    }
    int start = n >= 3 && (bytes[0] & 0xff) == 0xef && (bytes[1] & 0xff) == 0xbb && (bytes[2] & 0xff) == 0xbf ? 3 : 0;
    CharsetDecoder utf8 = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
    var in = ByteBuffer.wrap(bytes, start, n - start);
    var out = CharBuffer.allocate((int) Math.min(Integer.MAX_VALUE - 16, (long) (n - start) + 16));
    var result = utf8.decode(in, out, true);
    if (!result.isError()) result = utf8.flush(out);
    if (!result.isError()) return out.flip().toString();

    int bad = in.position();
    int line = 1;
    int lineStart = start;
    for (int i = start; i < bad; i++) {
      byte b = bytes[i];
      if (b == '\n' || (b == '\r' && (i + 1 >= n || bytes[i + 1] != '\n'))) {
        line++;
        lineStart = i + 1;
      }
    }
    int lineEnd = bad;
    while (lineEnd < n && bytes[lineEnd] != '\r' && bytes[lineEnd] != '\n') lineEnd++;
    var text = new String(bytes, start, n - start, WINDOWS_1252);
    problems.report(line, "The text is not UTF-8", "it is all read as Windows-1252",
        Problems.quote(new String(bytes, lineStart, lineEnd - lineStart, WINDOWS_1252)));
    return text;
  }
}

/// Splits text into content lines (RFC 6350 section 3.2, as vCard 2.1 and
/// 3.0 share them) and groups them into cards.
final class ContentLines {

  private ContentLines() {}

  /// Reads the cards of `text`, as lines not yet parsed.
  static List<RawCard> cards(CharSequence text, Problems problems) {
    if (text.chars().allMatch(c -> Character.isWhitespace(c) || c == '\uFEFF')) {
      throw Problems.exception(0, "Not vCard: the text is empty", "");
    }
    var lines = unfold(text, problems);
    if (lines.isEmpty()) {
      throw Problems.exception(0, "Not vCard: the text is empty", "");
    }
    var first = lines.getFirst();
    if (!isBegin(first.text())) {
      throw new VcardParseException(
          Problems.at(first.line(), "Not vCard: the text should start with BEGIN:VCARD, but starts with " + Problems.quote(first.text())),
          first.line(), "Not vCard: the text should start with BEGIN:VCARD");
    }
    var cards = new ArrayList<RawCard>();
    List<Logical> open = null;
    int openLine = 0;
    int agents = 0;
    for (var l : lines) {
      var s = l.text();
      if (open == null) {
        if (isBegin(s)) {
          open = new ArrayList<>();
          openLine = l.line();
        } else if (isEnd(s)) {
          problems.report(l.line(), "END:VCARD has no BEGIN:VCARD", "it is left out", Problems.quote(s));
        } else {
          problems.report(l.line(), "A line is outside of a VCARD", "it is left out", Problems.quote(s));
        }
      } else if (isBegin(s)) {
        if (agents > 0 || (!open.isEmpty() && isEmptyAgent(open.getLast().text()))) {
          // vCard 2.1's AGENT: a card inside a card, as the AGENT's value.
          agents++;
        } else {
          problems.report(openLine, "A VCARD has no END:VCARD before line " + l.line() + "'s BEGIN:VCARD", "it ends there",
              Problems.quote(s));
          cards.add(new RawCard(openLine, open));
          open = new ArrayList<>();
          openLine = l.line();
        }
      } else if (isEnd(s)) {
        if (agents > 0) {
          agents--;
        } else {
          cards.add(new RawCard(openLine, open));
          open = null;
        }
      } else if (agents == 0) {
        open.add(l);
      }
    }
    if (open != null) {
      problems.report(openLine, "A VCARD has no END:VCARD before the text ends", "it ends there", Problems.quote("BEGIN:VCARD"));
      cards.add(new RawCard(openLine, open));
    }
    return cards;
  }

  private static boolean isBegin(String s) {
    return is(s, "BEGIN");
  }

  private static boolean isEnd(String s) {
    return is(s, "END");
  }

  /// `BEGIN:VCARD` or `END:VCARD`, in any case, with white space around the value.
  private static boolean is(String s, String keyword) {
    int k = keyword.length();
    if (s.length() < k + 1 || !s.regionMatches(true, 0, keyword, 0, k) || s.charAt(k) != ':') return false;
    return s.substring(k + 1).strip().equalsIgnoreCase("VCARD");
  }

  private static boolean isEmptyAgent(String s) {
    int start = 0;
    int i = nameEnd(s, 0);
    if (i < s.length() && s.charAt(i) == '.') {
      start = i + 1;
      i = nameEnd(s, start);
    }
    if (!s.substring(start, i).equalsIgnoreCase("AGENT")) return false;
    int colon = s.indexOf(':', i);
    return colon >= 0 && s.substring(colon + 1).isBlank();
  }

  private static int nameEnd(String s, int from) {
    int i = from;
    while (i < s.length() && isNameChar(s.charAt(i))) i++;
    return i;
  }

  /// Joins folded lines: a line starting with a space or tab continues the
  /// one before it, and a quoted-printable value that ends with `=` goes on
  /// to the next line. Lines may end with CRLF, LF or CR.
  ///
  /// Blank lines are passed over, though strict mode reports them, but for
  /// the one vCard 2.1 writes after a base64 value. They do not end a line:
  /// an iPhone's CR CR LF makes one after every line, folded or not. A blank
  /// line does end a quoted-printable value, as Android writes one: `=`,
  /// then a blank line.
  ///
  /// Photos, logos, sounds and keys are passed over: their lines are not kept.
  static List<Logical> unfold(CharSequence text, Problems problems) {
    var out = new ArrayList<Logical>();
    int n = text.length();
    int i = 0;
    if (n > 0 && text.charAt(0) == '\uFEFF') i = 1;
    int physical = 0;
    StringBuilder current = null;
    int currentLine = 0;
    boolean skipping = false;
    boolean skippingBase64 = false;
    boolean softBreak = false;
    while (i < n) {
      int start = i;
      while (i < n && text.charAt(i) != '\r' && text.charAt(i) != '\n') i++;
      int end = i;
      if (i < n) i += text.charAt(i) == '\r' && i + 1 < n && text.charAt(i + 1) == '\n' ? 2 : 1;
      physical++;
      if (end == start) {
        if (softBreak) {
          softBreak = false;
        } else if (i < n && !(skipping && skippingBase64) && !(current != null && isBase64(current))) {
          problems.strictly(physical, "A blank line, which vCard does not allow", "");
        }
        continue;
      }
      if (softBreak && current != null) {
        // Quoted-printable's soft line break: `=` at the end of a line.
        current.setLength(current.length() - 1);
        current.append(text, start, end);
        softBreak = softBreak(current);
        continue;
      }
      if (text.charAt(start) == ' ' || text.charAt(start) == '\t') {
        if (skipping) continue;
        if (current != null) {
          current.append(text, start + 1, end);
          softBreak = softBreak(current);
          continue;
        }
        problems.report(physical, "A folded line has no line before it to continue", "it is left out",
            Problems.quote(text.subSequence(start, end).toString()));
        continue;
      }
      if (current != null) out.add(new Logical(currentLine, current.toString()));
      current = null;
      skipping = isBinary(text, start, end);
      if (skipping) {
        skippingBase64 = isBase64(text.subSequence(start, end));
        continue;
      }
      current = new StringBuilder(end - start).append(text, start, end);
      currentLine = physical;
      softBreak = softBreak(current);
    }
    if (current != null) out.add(new Logical(currentLine, current.toString()));
    return out;
  }

  /// Whether the line is a quoted-printable value ending in a soft line break.
  private static boolean softBreak(CharSequence line) {
    int n = line.length();
    return n > 0 && line.charAt(n - 1) == '=' && headHas(line, "QUOTED-PRINTABLE");
  }

  private static boolean isBase64(CharSequence line) {
    return headHas(line, "BASE64");
  }

  /// Whether the name and parameters, before the first `:`, have `word`, in any case.
  private static boolean headHas(CharSequence line, String word) {
    int n = line.length();
    int k = word.length();
    for (int i = 0; i + k <= n; i++) {
      char c = line.charAt(i);
      if (c == ':') return false;
      if ((c == word.charAt(0) || c == Character.toLowerCase(word.charAt(0)))
          && line.subSequence(i, i + k).toString().equalsIgnoreCase(word)) {
        return true;
      }
    }
    return false;
  }

  /// `PHOTO`, `LOGO`, `SOUND` and `KEY`, with or without a group.
  private static boolean isBinary(CharSequence text, int start, int end) {
    int i = start;
    while (i < end && isNameChar(text.charAt(i))) i++;
    int nameStart = start;
    if (i < end && text.charAt(i) == '.') {
      nameStart = i + 1;
      i = nameStart;
      while (i < end && isNameChar(text.charAt(i))) i++;
    }
    if (i >= end || (text.charAt(i) != ';' && text.charAt(i) != ':')) return false;
    var name = text.subSequence(nameStart, i).toString().toUpperCase(Locale.ROOT);
    return switch (name) {
      case "PHOTO", "LOGO", "SOUND", "KEY" -> true;
      default -> false;
    };
  }

  static boolean isNameChar(char c) {
    return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_';
  }

  /// Parses `[group "."] name *(";" param) ":" value`, or reports why it
  /// can't. A quoted-printable value is decoded.
  static @Nullable Prop parse(Logical l, Vcard.Version version, Problems problems) {
    var s = l.text();
    int n = s.length();
    int i = nameEnd(s, 0);
    var group = "";
    int nameStart = 0;
    if (i > 0 && i < n && s.charAt(i) == '.') {
      group = s.substring(0, i);
      nameStart = i + 1;
      i = nameEnd(s, nameStart);
    }
    if (i == nameStart || i == n || (s.charAt(i) != ':' && s.charAt(i) != ';')) {
      problems.report(l.line(), "Not a content line (NAME:value)", "it is left out", Problems.quote(s));
      return null;
    }
    var name = s.substring(nameStart, i).toUpperCase(Locale.ROOT);
    var params = new LinkedHashMap<String, List<String>>();
    while (i < n && s.charAt(i) == ';') {
      i++;
      int ps = i;
      i = nameEnd(s, i);
      if (i < n && s.charAt(i) == '=' && i > ps) {
        var pname = s.substring(ps, i).toUpperCase(Locale.ROOT);
        i++;
        var values = new ArrayList<String>();
        while (true) {
          if (i < n && s.charAt(i) == '"') {
            int close = s.indexOf('"', i + 1);
            if (close < 0) {
              problems.report(l.line(), "A quoted parameter value has no closing quote", "the line is left out", Problems.quote(s));
              return null;
            }
            values.add(version == Vcard.Version.V4_0 ? caret(s.substring(i + 1, close)) : s.substring(i + 1, close));
            i = close + 1;
          } else {
            int vs = i;
            while (i < n && s.charAt(i) != ',' && s.charAt(i) != ';' && s.charAt(i) != ':' && s.charAt(i) != '"') i++;
            var v = s.substring(vs, i);
            values.add(version == Vcard.Version.V4_0 ? caret(v) : v);
          }
          if (i < n && s.charAt(i) == ',') {
            i++;
            continue;
          }
          break;
        }
        add(params, pname, values);
      } else if (i > ps && i < n && (s.charAt(i) == ';' || s.charAt(i) == ':')) {
        // vCard 2.1's bare parameter: `TEL;HOME;VOICE:`.
        var value = s.substring(ps, i);
        if (version != Vcard.Version.V2_1) {
          problems.strictly(l.line(), "A parameter has no name, which only vCard 2.1 allows", Problems.quote(s));
        }
        add(params, bareName(value), List.of(value));
      } else if (i == ps && i < n && (s.charAt(i) == ';' || s.charAt(i) == ':')) {
        // An empty parameter: `TEL;;HOME:`.
        problems.strictly(l.line(), "A parameter is empty", Problems.quote(s));
      } else {
        problems.report(l.line(), "A parameter has no NAME=value", "the line is left out", Problems.quote(s));
        return null;
      }
    }
    if (i >= n || s.charAt(i) != ':') {
      problems.report(l.line(), "A property has no ':' before its value", "it is left out", Problems.quote(s));
      return null;
    }
    var value = s.substring(i + 1);
    var prop = new Prop(group, name, params, value, l.line(), s);
    if (prop.has("ENCODING", "QUOTED-PRINTABLE")) {
      if (version != Vcard.Version.V2_1) {
        problems.strictly(l.line(), "A quoted-printable value, which only vCard 2.1 allows", Problems.quote(s));
      }
      prop = new Prop(group, name, params, QuotedPrintable.decode(prop, problems), l.line(), s);
    }
    return prop;
  }

  private static void add(Map<String, List<String>> params, String name, List<String> values) {
    params.computeIfAbsent(name, k -> new ArrayList<>()).addAll(values);
  }

  /// What a bare vCard 2.1 parameter stands for: an `ENCODING`, a `VALUE`,
  /// a `CHARSET`, else a `TYPE`.
  private static String bareName(String value) {
    return switch (value.toUpperCase(Locale.ROOT)) {
      case "7BIT", "8BIT", "QUOTED-PRINTABLE", "BASE64", "B" -> "ENCODING";
      case "INLINE", "URL", "CONTENT-ID", "CID" -> "VALUE";
      default -> "TYPE";
    };
  }

  /// RFC 6868: `^n` is a newline, `^'` a double quote and `^^` a caret.
  private static String caret(String value) {
    if (value.indexOf('^') < 0) return value;
    var b = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '^' && i + 1 < value.length()) {
        char next = value.charAt(i + 1);
        if (next == 'n' || next == 'N') {
          b.append('\n');
          i++;
          continue;
        } else if (next == '\'') {
          b.append('"');
          i++;
          continue;
        } else if (next == '^') {
          b.append('^');
          i++;
          continue;
        }
      }
      b.append(c);
    }
    return b.toString();
  }
}

/// Quoted-printable values (vCard 2.1), in their `CHARSET`.
final class QuotedPrintable {

  private QuotedPrintable() {}

  static String decode(Prop p, Problems problems) {
    Charset charset = StandardCharsets.UTF_8;
    var name = p.param("CHARSET");
    if (name != null) {
      try {
        charset = Charset.forName(name.strip());
      } catch (IllegalArgumentException e) {
        problems.report(p.line(), "The CHARSET is not one this reader knows", "the value is read as UTF-8", Problems.quote(p.text()));
      }
    }
    var raw = p.value();
    var bytes = new java.io.ByteArrayOutputStream(raw.length());
    boolean reported = false;
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c == '=') {
        int hi = i + 2 < raw.length() ? hex(raw.charAt(i + 1)) : -1;
        int lo = hi >= 0 ? hex(raw.charAt(i + 2)) : -1;
        if (hi >= 0 && lo >= 0) {
          bytes.write(hi * 16 + lo);
          i += 2;
          continue;
        }
        if (i == raw.length() - 1) continue;
        if (!reported) {
          problems.report(p.line(), "A quoted-printable value has an = that is not before two hex digits", "it is read as written",
              Problems.quote(p.text()));
          reported = true;
        }
      }
      if (c < 0x80) {
        bytes.write(c);
      } else {
        // Text written as itself, not as =XX: its own bytes in the charset.
        int end = Character.isHighSurrogate(c) && i + 1 < raw.length() ? i + 2 : i + 1;
        bytes.writeBytes(raw.substring(i, end).getBytes(charset));
        i = end - 1;
      }
    }
    return new String(bytes.toByteArray(), charset);
  }

  /// An ASCII hex digit's value, or -1. Not [Character#digit(char, int)],
  /// which takes other scripts' digits too.
  private static int hex(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    return -1;
  }
}
