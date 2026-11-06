package com.snaphop.ical;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/// A `DURATION` value: whole days, which are nominal (a day can be 23 or 25
/// hours long), and an exact time.
record Length(int days, Duration time) {

  static final Length ZERO = new Length(0, Duration.ZERO);

  static final Length ONE_DAY = new Length(1, Duration.ZERO);

  boolean negative() {
    return days < 0 || (days == 0 && time.isNegative());
  }

  LocalDateTime plus(LocalDateTime t) {
    return t.plusDays(days).plus(time);
  }

  ZonedDateTime plus(ZonedDateTime t) {
    return t.plusDays(days).plus(time);
  }
}

/// Reads the values of RFC 5545 section 3.3 that events use. Each method
/// throws [IllegalArgumentException] with a message saying what is wrong
/// with the value, for the caller to place.
final class Values {

  private Values() {}

  /// A `DATE`: `YYYYMMDD`.
  static LocalDate date(String s) {
    if (s.length() != 8 || !digits(s, 0, 8)) {
      throw new IllegalArgumentException(Problems.quote(s) + " is not a DATE (YYYYMMDD)");
    }
    try {
      return LocalDate.of(num(s, 0, 4), num(s, 4, 6), num(s, 6, 8));
    } catch (DateTimeException e) {
      throw new IllegalArgumentException(Problems.quote(s) + " is not a real date");
    }
  }

  /// A `DATE-TIME` without its `Z`, if it has one: `YYYYMMDDTHHMMSS`.
  static LocalDateTime dateTime(String s) {
    if (s.length() != 15 || !digits(s, 0, 8) || (s.charAt(8) != 'T' && s.charAt(8) != 't') || !digits(s, 9, 15)) {
      throw new IllegalArgumentException(Problems.quote(s) + " is not a DATE-TIME (YYYYMMDDTHHMMSS, with a Z for UTC)");
    }
    try {
      // A leap second, which java.time doesn't have, is the second before it.
      int second = Math.min(num(s, 13, 15), 59);
      return LocalDateTime.of(num(s, 0, 4), num(s, 4, 6), num(s, 6, 8), num(s, 9, 11), num(s, 11, 13), second);
    } catch (DateTimeException e) {
      throw new IllegalArgumentException(Problems.quote(s) + " is not a real date and time");
    }
  }

  /// Whether a `DATE-TIME` ends in `Z`, for UTC.
  static boolean utc(String s) {
    return s.endsWith("Z") || s.endsWith("z");
  }

  static String withoutZ(String s) {
    return utc(s) ? s.substring(0, s.length() - 1) : s;
  }

  /// A `DURATION`: `[+-]P` then weeks (`nW`), or days (`nD`) and a time
  /// (`T`, `nH`, `nM`, `nS`).
  static Length duration(String s) {
    var bad = new IllegalArgumentException(Problems.quote(s) + " is not a DURATION (such as PT1H30M, P1D or P2W)");
    int i = 0;
    int n = s.length();
    int sign = 1;
    if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
      if (s.charAt(i) == '-') sign = -1;
      i++;
    }
    if (i >= n || (s.charAt(i) != 'P' && s.charAt(i) != 'p')) throw bad;
    i++;
    long days = 0;
    long seconds = 0;
    boolean time = false;
    boolean any = false;
    while (i < n) {
      char c = s.charAt(i);
      if (c == 'T' || c == 't') {
        if (time) throw bad;
        time = true;
        i++;
        continue;
      }
      int start = i;
      while (i < n && Character.isDigit(s.charAt(i))) i++;
      if (i == start || i == n || i - start > 9) throw bad;
      long v = Long.parseLong(s.substring(start, i));
      char unit = Character.toUpperCase(s.charAt(i++));
      any = true;
      switch (unit) {
        case 'W' -> {
          if (time) throw bad;
          days += v * 7;
        }
        case 'D' -> {
          if (time) throw bad;
          days += v;
        }
        case 'H' -> {
          if (!time) throw bad;
          seconds += v * 3600;
        }
        case 'M' -> {
          if (!time) throw bad;
          seconds += v * 60;
        }
        case 'S' -> {
          if (!time) throw bad;
          seconds += v;
        }
        default -> throw bad;
      }
    }
    if (!any) throw bad;
    return new Length(Math.toIntExact(sign * days), Duration.ofSeconds(sign * seconds));
  }

  /// A `UTC-OFFSET`: `+hhmm` or `-hhmm`, with optional seconds.
  static ZoneOffset offset(String s) {
    var bad = new IllegalArgumentException(Problems.quote(s) + " is not a UTC-OFFSET (such as -0500 or +0530)");
    var t = s.strip();
    if ((t.length() != 5 && t.length() != 7) || (t.charAt(0) != '+' && t.charAt(0) != '-') || !digits(t, 1, t.length())) {
      throw bad;
    }
    int sign = t.charAt(0) == '-' ? -1 : 1;
    try {
      return ZoneOffset.ofHoursMinutesSeconds(
          sign * num(t, 1, 3), sign * num(t, 3, 5), t.length() == 7 ? sign * num(t, 5, 7) : 0);
    } catch (DateTimeException e) {
      throw bad;
    }
  }

  /// A `TEXT` value with its escapes undone: `\\`, `\;`, `\,` and `\n`.
  static String text(String raw) {
    if (raw.indexOf('\\') < 0) return raw;
    var b = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c == '\\' && i + 1 < raw.length()) {
        char next = raw.charAt(++i);
        b.append(next == 'n' || next == 'N' ? '\n' : next);
      } else {
        b.append(c);
      }
    }
    return b.toString();
  }

  private static boolean digits(String s, int from, int to) {
    for (int i = from; i < to; i++) {
      if (s.charAt(i) < '0' || s.charAt(i) > '9') return false;
    }
    return true;
  }

  private static int num(String s, int from, int to) {
    return Integer.parseInt(s, from, to, 10);
  }
}
