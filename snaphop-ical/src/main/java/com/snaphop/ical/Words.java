package com.snaphop.ical;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/// A rule in plain English, for [IcalRecurrence#describeOrNull(IcalTime)].
final class Words {

  private Words() {}

  private static final Set<DayOfWeek> WEEKDAYS =
      EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.ENGLISH);

  static @Nullable String describeOrNull(IcalRecurrence r, IcalTime start) {
    if (!r.bySetPos().isEmpty() || !r.byWeekNo().isEmpty() || !r.byYearDay().isEmpty() || !r.bySecond().isEmpty()
        || r.byHour().size() > 1 || r.byMinute().size() > 1 || r.frequency().compareTo(IcalRecurrence.Frequency.DAILY) < 0) {
      return null;
    }
    var day = switch (start) {
      case IcalTime.Date d -> d.date();
      case IcalTime.DateTime d -> d.dateTime().toLocalDate();
      case IcalTime.Floating f -> f.dateTime().toLocalDate();
    };
    var when = switch (r.frequency()) {
      case DAILY -> daily(r);
      case WEEKLY -> weekly(r, day);
      case MONTHLY -> monthly(r, day);
      case YEARLY -> yearly(r, day);
      default -> null;
    };
    if (when == null) return null;
    var out = new StringBuilder(when);
    if (!(start instanceof IcalTime.Date)) {
      var time = switch (start) {
        case IcalTime.DateTime d -> d.dateTime().toLocalTime();
        case IcalTime.Floating f -> f.dateTime().toLocalTime();
        case IcalTime.Date d -> LocalTime.MIDNIGHT;
      };
      if (!r.byHour().isEmpty()) time = time.withHour(r.byHour().getFirst());
      if (!r.byMinute().isEmpty()) time = time.withMinute(r.byMinute().getFirst());
      out.append(" at ").append(TIME.format(time));
    }
    var count = r.countOrNull();
    if (count != null) out.append(count == 1 ? ", once" : ", " + count + " times");
    var until = r.untilOrNull();
    if (until != null) {
      var last = switch (until) {
        case IcalTime.Date d -> d.date();
        case IcalTime.Floating f -> f.dateTime().toLocalDate();
        case IcalTime.DateTime d -> start instanceof IcalTime.DateTime s
            ? d.dateTime().withZoneSameInstant(s.dateTime().getZone()).toLocalDate()
            : d.dateTime().toLocalDate();
      };
      out.append(", until ").append(DATE.format(last));
    }
    return out.toString();
  }

  private static @Nullable String daily(IcalRecurrence r) {
    if (!r.byMonthDay().isEmpty()) return null;
    if (!r.byDay().isEmpty()) {
      if (r.interval() != 1) return null;
      var w = weekly(r, LocalDate.MIN);
      return w == null ? null : w + inMonths(r);
    }
    return every(r.interval(), "day") + inMonths(r);
  }

  private static @Nullable String weekly(IcalRecurrence r, LocalDate day) {
    var days = new ArrayList<DayOfWeek>();
    for (var w : r.byDay()) {
      if (w.n() != 0) return null;
      days.add(w.day());
    }
    if (days.isEmpty()) days.add(day.getDayOfWeek());
    if (EnumSet.copyOf(days).equals(WEEKDAYS)) {
      return (r.interval() == 1 ? "Every weekday" : every(r.interval(), "week") + " on weekdays") + inMonths(r);
    }
    days.sort(null);
    return every(r.interval(), "week") + " on " + and(days.stream().map(Words::name).toList()) + inMonths(r);
  }

  private static @Nullable String monthly(IcalRecurrence r, LocalDate day) {
    var on = monthDays(r, day);
    return on == null ? null : every(r.interval(), "month") + " on " + on + inMonths(r);
  }

  private static @Nullable String yearly(IcalRecurrence r, LocalDate day) {
    var months = r.byMonth().isEmpty() ? List.of(day.getMonthValue()) : r.byMonth();
    var names = and(months.stream().map(m -> name(Month.of(m))).toList());
    var every = every(r.interval(), "year");
    if (!r.byDay().isEmpty()) {
      if (r.byMonth().isEmpty() || !r.byMonthDay().isEmpty()) return null;
      var on = monthDays(r, day);
      return on == null ? null : every + " on " + on + " of " + names;
    }
    var days = r.byMonthDay().isEmpty() ? List.of(day.getDayOfMonth()) : r.byMonthDay();
    if (months.size() == 1 && days.size() == 1 && days.getFirst() > 0) {
      return every + " on " + names + " " + days.getFirst();
    }
    return every + " on the " + and(days.stream().map(Words::monthDay).toList()) + " of " + names;
  }

  /// "the 1st and 15th", "the last day", "the second Tuesday": the days of
  /// a month a rule is on.
  private static @Nullable String monthDays(IcalRecurrence r, LocalDate day) {
    if (!r.byDay().isEmpty()) {
      if (!r.byMonthDay().isEmpty()) return null;
      var days = new ArrayList<String>();
      for (var w : r.byDay()) {
        var nth = ordinal(w.n());
        if (nth == null) return null;
        days.add(nth + " " + name(w.day()));
      }
      return "the " + and(days);
    }
    var days = r.byMonthDay().isEmpty() ? List.of(day.getDayOfMonth()) : r.byMonthDay();
    return "the " + and(days.stream().map(Words::monthDay).toList());
  }

  private static String monthDay(int n) {
    if (n == -1) return "last day";
    if (n < 0) return (n == -2 ? "second" : suffixed(-n)) + " to last day";
    return suffixed(n);
  }

  private static String suffixed(int n) {
    var suffix = n % 100 >= 11 && n % 100 <= 13 ? "th" : switch (n % 10) {
      case 1 -> "st";
      case 2 -> "nd";
      case 3 -> "rd";
      default -> "th";
    };
    return n + suffix;
  }

  private static @Nullable String ordinal(int n) {
    return switch (n) {
      case 1 -> "first";
      case 2 -> "second";
      case 3 -> "third";
      case 4 -> "fourth";
      case 5 -> "fifth";
      case -1 -> "last";
      case -2 -> "second to last";
      case -3 -> "third to last";
      default -> null;
    };
  }

  private static String every(int interval, String unit) {
    return interval == 1 ? "Every " + unit : "Every " + interval + " " + unit + "s";
  }

  private static String inMonths(IcalRecurrence r) {
    if (r.byMonth().isEmpty()) return "";
    return " in " + and(r.byMonth().stream().map(m -> name(Month.of(m))).toList());
  }

  private static String name(DayOfWeek d) {
    return d.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
  }

  private static String name(Month m) {
    return m.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
  }

  /// "A", "A and B", "A, B and C".
  private static String and(List<String> items) {
    if (items.size() == 1) return items.getFirst();
    return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.getLast();
  }
}
