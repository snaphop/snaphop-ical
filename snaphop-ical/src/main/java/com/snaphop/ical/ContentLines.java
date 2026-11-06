package com.snaphop.ical;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/// A property as written, after its lines are unfolded: its name upper-cased,
/// its parameters, and its value still escaped.
///
/// `line` is the physical line it starts on; `text` is the whole unfolded line.
record Prop(String name, Map<String, List<String>> params, String value, int line, String text) {

  Prop {
    params = Map.copyOf(params);
  }

  @Nullable String param(String name) {
    var values = params.get(name);
    return values == null || values.isEmpty() ? null : values.getFirst();
  }
}

/// A component: what is between its `BEGIN` and `END` lines.
record Comp(String name, int line, List<Prop> props, List<Comp> comps) {

  Comp {
    props = List.copyOf(props);
    comps = List.copyOf(comps);
  }

  @Nullable Prop first(String name) {
    for (var p : props) {
      if (p.name().equals(name)) return p;
    }
    return null;
  }

  List<Prop> all(String name) {
    return props.stream().filter(p -> p.name().equals(name)).toList();
  }

  List<Comp> comps(String name) {
    return comps.stream().filter(c -> c.name().equals(name)).toList();
  }
}

/// What a read found wrong. In strict mode the first problem is thrown; in lax
/// mode each goes to `warnings` and reading goes on.
final class Problems {

  private final IcalReader.Mode mode;
  private final Consumer<? super IcalWarning> warnings;

  Problems(IcalReader.Mode mode, Consumer<? super IcalWarning> warnings) {
    this.mode = mode;
    this.warnings = warnings;
  }

  boolean strict() {
    return mode == IcalReader.Mode.STRICT;
  }

  /// Reports a problem only strict mode cares about: text that breaks RFC
  /// 5545 but reads as well without what it is missing.
  void strictly(int line, String message) {
    if (strict()) throw new IcalParseException(at(line, message), line);
  }

  /// Reports `problem`, then `where` it is (quoted text, or which event). A
  /// lax warning also says what was done about it: its `consequence`.
  void report(int line, String problem, String consequence, String where) {
    if (strict()) throw new IcalParseException(at(line, problem + ": " + where), line);
    warnings.accept(new IcalWarning(line, problem, consequence, where));
  }

  static String at(int line, String message) {
    return line > 0 ? "Line " + line + ": " + message : message;
  }

  /// Text to show in a message, quoted, and cut short when long.
  static String quote(String text) {
    var s = text.length() > 100 ? text.substring(0, 100) + "..." : text;
    return "\"" + s + "\"";
  }
}

/// Splits text into content lines (RFC 5545 section 3.1) and nests them into
/// components.
final class ContentLines {

  private ContentLines() {}

  /// A logical line: physical lines joined where they were folded.
  private record Logical(int line, String text) {}

  /// Reads the components at the top of `text`: normally one `VCALENDAR`.
  static List<Comp> read(CharSequence text, Problems problems) {
    if (text.chars().allMatch(c -> Character.isWhitespace(c) || c == '\uFEFF')) {
      throw new IcalParseException("Not iCalendar: the text is empty.", 0);
    }
    var lines = unfold(text, problems);
    var first = lines.isEmpty() ? null : lines.getFirst();
    if (first == null) {
      throw new IcalParseException("Not iCalendar: the text is empty.", 0);
    }
    if (!first.text().strip().equalsIgnoreCase("BEGIN:VCALENDAR")) {
      throw new IcalParseException(
          Problems.at(first.line(), "Not iCalendar: the text should start with BEGIN:VCALENDAR, but starts with "
              + Problems.quote(first.text())),
          first.line());
    }

    var top = new ArrayList<Comp>();
    var open = new ArrayList<Building>();
    for (var l : lines) {
      var prop = parse(l, problems);
      if (prop == null) continue;
      if (prop.name().equals("BEGIN")) {
        var name = prop.value().strip().toUpperCase(Locale.ROOT);
        if (open.isEmpty() && !name.equals("VCALENDAR")) {
          problems.report(l.line(), "A " + name + " is outside of a VCALENDAR", "it is left out", Problems.quote(l.text()));
        }
        open.add(new Building(name, l.line()));
      } else if (prop.name().equals("END")) {
        var name = prop.value().strip().toUpperCase(Locale.ROOT);
        int at = -1;
        for (int i = open.size() - 1; i >= 0; i--) {
          if (open.get(i).name.equals(name)) {
            at = i;
            break;
          }
        }
        if (at < 0) {
          problems.report(l.line(), "END:" + name + " has no BEGIN:" + name, "it is left out", Problems.quote(l.text()));
          continue;
        }
        while (open.size() > at + 1) {
          var unclosed = open.getLast();
          problems.report(unclosed.line, "BEGIN:" + unclosed.name + " has no END:" + unclosed.name + " before line " + l.line(),
              "it ends there", Problems.quote(l.text()));
          close(open, top);
        }
        close(open, top);
      } else if (open.isEmpty()) {
        problems.report(l.line(), "A property is outside of a VCALENDAR", "it is left out", Problems.quote(l.text()));
      } else {
        open.getLast().props.add(prop);
      }
    }
    while (!open.isEmpty()) {
      var unclosed = open.getLast();
      problems.report(unclosed.line, "BEGIN:" + unclosed.name + " has no END:" + unclosed.name + " before the text ends",
          "it ends there", Problems.quote("BEGIN:" + unclosed.name));
      close(open, top);
    }
    return top;
  }

  private static final class Building {
    final String name;
    final int line;
    final List<Prop> props = new ArrayList<>();
    final List<Comp> comps = new ArrayList<>();

    Building(String name, int line) {
      this.name = name;
      this.line = line;
    }
  }

  private static void close(List<Building> open, List<Comp> top) {
    var b = open.removeLast();
    var comp = new Comp(b.name, b.line, b.props, b.comps);
    if (!open.isEmpty()) {
      open.getLast().comps.add(comp);
    } else if (comp.name().equals("VCALENDAR")) {
      top.add(comp);
    }
  }

  /// Joins folded lines: a line starting with a space or tab continues the
  /// one before it. Lines may end with CRLF, LF or CR. Blank lines are
  /// dropped, though strict mode reports them.
  private static List<Logical> unfold(CharSequence text, Problems problems) {
    var out = new ArrayList<Logical>();
    int n = text.length();
    int i = 0;
    if (n > 0 && text.charAt(0) == '﻿') i = 1;
    int physical = 0;
    StringBuilder current = null;
    int currentLine = 0;
    while (i < n) {
      int start = i;
      while (i < n && text.charAt(i) != '\r' && text.charAt(i) != '\n') i++;
      int end = i;
      if (i < n) i += text.charAt(i) == '\r' && i + 1 < n && text.charAt(i + 1) == '\n' ? 2 : 1;
      physical++;
      if (end > start && (text.charAt(start) == ' ' || text.charAt(start) == '\t')) {
        if (current != null) {
          current.append(text, start + 1, end);
          continue;
        }
        problems.report(physical, "A folded line has no line before it to continue", "it is left out",
            Problems.quote(text.subSequence(start, end).toString()));
        continue;
      }
      if (current != null) {
        out.add(new Logical(currentLine, current.toString()));
        current = null;
      }
      if (end == start) {
        if (i < n) problems.strictly(physical, "A blank line, which RFC 5545 does not allow.");
        continue;
      }
      current = new StringBuilder().append(text, start, end);
      currentLine = physical;
    }
    if (current != null) out.add(new Logical(currentLine, current.toString()));
    return out;
  }

  /// Parses `name *(";" param) ":" value`, or reports why it can't.
  private static @Nullable Prop parse(Logical l, Problems problems) {
    var s = l.text();
    int n = s.length();
    int i = 0;
    while (i < n && isNameChar(s.charAt(i))) i++;
    if (i == 0 || i == n || (s.charAt(i) != ':' && s.charAt(i) != ';')) {
      problems.report(l.line(), "Not a content line (NAME:value)", "it is left out", Problems.quote(s));
      return null;
    }
    var name = s.substring(0, i).toUpperCase(Locale.ROOT);
    var params = new LinkedHashMap<String, List<String>>();
    while (i < n && s.charAt(i) == ';') {
      i++;
      int ps = i;
      while (i < n && isNameChar(s.charAt(i))) i++;
      if (i == ps || i == n || s.charAt(i) != '=') {
        problems.report(l.line(), "A parameter has no NAME=value", "the line is left out", Problems.quote(s));
        return null;
      }
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
          values.add(caret(s.substring(i + 1, close)));
          i = close + 1;
        } else {
          int vs = i;
          while (i < n && s.charAt(i) != ',' && s.charAt(i) != ';' && s.charAt(i) != ':' && s.charAt(i) != '"') i++;
          values.add(caret(s.substring(vs, i)));
        }
        if (i < n && s.charAt(i) == ',') {
          i++;
          continue;
        }
        break;
      }
      params.merge(pname, values, (a, b) -> {
        var all = new ArrayList<>(a);
        all.addAll(b);
        return all;
      });
    }
    if (i >= n || s.charAt(i) != ':') {
      problems.report(l.line(), "A property has no ':' before its value", "it is left out", Problems.quote(s));
      return null;
    }
    return new Prop(name, params, s.substring(i + 1), l.line(), s);
  }

  private static boolean isNameChar(char c) {
    return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_';
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
