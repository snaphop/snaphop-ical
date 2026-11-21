package com.snaphop.vcard;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/// What a read found wrong. In strict mode the first problem is thrown; in lax
/// mode each goes to `warnings` and reading goes on.
///
/// A problem and its consequence are this library's words and property
/// names only, never the text: only `where` quotes it.
final class Problems {

  private final VcardReader.Mode mode;
  private final Consumer<? super VcardWarning> warnings;

  Problems(VcardReader.Mode mode, Consumer<? super VcardWarning> warnings) {
    this.mode = mode;
    this.warnings = warnings;
  }

  boolean strict() {
    return mode == VcardReader.Mode.STRICT;
  }

  /// Reports a problem only strict mode cares about: text that breaks the
  /// specification but reads as well without what it is missing.
  void strictly(int line, String problem, String where) {
    if (strict()) throw exception(line, problem, where);
  }

  /// Reports `problem`, then `where` it is. A lax warning also says what was
  /// done about it: its `consequence`.
  void report(int line, String problem, String consequence, String where) {
    if (strict()) throw exception(line, problem, where);
    warnings.accept(new VcardWarning(line, problem, consequence, where));
  }

  static VcardParseException exception(int line, String problem, String where) {
    return new VcardParseException(at(line, problem + (where.isEmpty() ? "." : ": " + where)), line, problem);
  }

  static String at(int line, String message) {
    return line > 0 ? "Line " + line + ": " + message : message;
  }

  /// Text to show in a message, quoted, and cut short when long.
  static String quote(String text) {
    var s = text.length() > 100 ? text.substring(0, Character.isHighSurrogate(text.charAt(99)) ? 99 : 100) + "..." : text;
    return "\"" + s + "\"";
  }
}

/// Values: escapes, and the lists and parts they are split into.
final class Text {

  private Text() {}

  /// A value with its escapes undone: `\n` or `\N` is a newline, and a
  /// backslash before anything else is that character (`\,` `\;` `\\`, and
  /// those the specifications don't name but apps write, such as Apple's
  /// `http\://`). Line breaks (CRLF or CR, as quoted-printable writes them)
  /// become `\n`.
  static String unescape(String raw) {
    if (raw.indexOf('\\') < 0 && raw.indexOf('\r') < 0) return raw;
    var b = new StringBuilder(raw.length());
    int n = raw.length();
    for (int i = 0; i < n; i++) {
      char c = raw.charAt(i);
      if (c == '\\' && i + 1 < n) {
        char next = raw.charAt(i + 1);
        b.append(next == 'n' || next == 'N' ? '\n' : next);
        i++;
        continue;
      } else if (c == '\r') {
        b.append('\n');
        if (i + 1 < n && raw.charAt(i + 1) == '\n') i++;
        continue;
      }
      b.append(c);
    }
    return b.toString();
  }

  /// Splits a raw value at each `separator` that is not escaped. The parts
  /// keep their escapes.
  static List<String> split(String raw, char separator) {
    if (raw.indexOf(separator) < 0) return List.of(raw);
    var parts = new ArrayList<String>();
    int start = 0;
    int n = raw.length();
    for (int i = 0; i < n; i++) {
      char c = raw.charAt(i);
      if (c == '\\') {
        i++;
      } else if (c == separator) {
        parts.add(raw.substring(start, i));
        start = i + 1;
      }
    }
    parts.add(raw.substring(start));
    return parts;
  }

  /// The values of a list (`CATEGORIES:a,b`): unescaped, stripped, without
  /// empty ones.
  static List<String> list(String raw) {
    var out = new ArrayList<String>();
    for (var part : split(raw, ',')) {
      var v = unescape(part).strip();
      if (!v.isEmpty()) out.add(v);
    }
    return out;
  }

  /// One part of a structured value (`N`, `ADR`), its own list of values
  /// joined by `joiner`.
  static String part(List<String> parts, int index, String joiner) {
    if (index >= parts.size()) return "";
    return String.join(joiner, list(parts.get(index)));
  }
}
