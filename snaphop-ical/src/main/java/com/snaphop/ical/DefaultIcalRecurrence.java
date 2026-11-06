package com.snaphop.ical;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/// An `RRULE` (RFC 5545 section 3.3.10), read, and the starts it makes.
///
/// Starts are worked out on the wall clock of the event's start: a weekly
/// rule at 09:00 stays at 09:00 across a change to daylight saving time. The
/// caller places them in a zone.
final class DefaultIcalRecurrence implements IcalRecurrence {

  /// Steps of the rule tried in one expansion, at most, so that no rule can
  /// keep a reader busy for long. A daily rule takes one step a day, and an
  /// hourly one a step an hour; a rule with a `COUNT` that has no `BYxxx`
  /// parts skips ahead to the window without them.
  static final long MAX_STEPS = 5_000_000;

  /// A `BYDAY` value: a day of the week, and which one of the month or year
  /// (0 for every one).
  record WeekdayNum(int n, DayOfWeek day) {}

  private final Frequency frequency;
  private final int interval;
  private final @Nullable Integer count;
  private final @Nullable IcalTime until;
  private final String text;
  private final int[] bySecond;
  private final int[] byMinute;
  private final int[] byHour;
  private final List<WeekdayNum> byDay;
  private final int[] byMonthDay;
  private final int[] byYearDay;
  private final int[] byWeekNo;
  private final int[] byMonth;
  private final int[] bySetPos;
  private final DayOfWeek weekStart;

  private DefaultIcalRecurrence(
      Frequency frequency,
      int interval,
      @Nullable Integer count,
      @Nullable IcalTime until,
      String text,
      int[] bySecond,
      int[] byMinute,
      int[] byHour,
      List<WeekdayNum> byDay,
      int[] byMonthDay,
      int[] byYearDay,
      int[] byWeekNo,
      int[] byMonth,
      int[] bySetPos,
      DayOfWeek weekStart) {
    this.frequency = frequency;
    this.interval = interval;
    this.count = count;
    this.until = until;
    this.text = text;
    this.bySecond = bySecond;
    this.byMinute = byMinute;
    this.byHour = byHour;
    this.byDay = List.copyOf(byDay);
    this.byMonthDay = byMonthDay;
    this.byYearDay = byYearDay;
    this.byWeekNo = byWeekNo;
    this.byMonth = byMonth;
    this.bySetPos = bySetPos;
    this.weekStart = weekStart;
  }

  @Override
  public Frequency frequency() {
    return frequency;
  }

  @Override
  public int interval() {
    return interval;
  }

  @Override
  public @Nullable Integer countOrNull() {
    return count;
  }

  @Override
  public @Nullable IcalTime untilOrNull() {
    return until;
  }

  @Override
  public String text() {
    return text;
  }

  @Override
  public String toString() {
    return "IcalRecurrence[" + text + "]";
  }

  /// How often the rule really makes starts: `FREQ=HOURLY;BYMINUTE=0,30` is
  /// every half hour, so [Frequency#MINUTELY].
  Frequency effectiveFrequency() {
    var f = frequency;
    if (byHour.length > 1 && f.compareTo(Frequency.HOURLY) > 0) f = Frequency.HOURLY;
    if (byMinute.length > 1 && f.compareTo(Frequency.MINUTELY) > 0) f = Frequency.MINUTELY;
    if (bySecond.length > 1 && f.compareTo(Frequency.SECONDLY) > 0) f = Frequency.SECONDLY;
    return f;
  }

  /// Reads a rule's text, the value of `RRULE` or `EXRULE`.
  ///
  /// `soft` hears of parts that RFC 5545 doesn't allow but that can be read
  /// anyway: what is wrong, and what is done about it. Anything that can't be read throws
  /// [IllegalArgumentException], saying what.
  static DefaultIcalRecurrence parse(String text, BiConsumer<String, String> soft) {
    Frequency frequency = null;
    int interval = 1;
    Integer count = null;
    IcalTime until = null;
    int[] bySecond = {};
    int[] byMinute = {};
    int[] byHour = {};
    List<WeekdayNum> byDay = List.of();
    int[] byMonthDay = {};
    int[] byYearDay = {};
    int[] byWeekNo = {};
    int[] byMonth = {};
    int[] bySetPos = {};
    DayOfWeek weekStart = DayOfWeek.MONDAY;
    var seen = new TreeSet<String>();
    for (var part : text.strip().split(";", -1)) {
      if (part.isEmpty()) continue;
      int eq = part.indexOf('=');
      if (eq <= 0) throw new IllegalArgumentException("The rule part " + Problems.quote(part) + " has no NAME=value");
      var name = part.substring(0, eq).strip().toUpperCase(Locale.ROOT);
      var value = part.substring(eq + 1).strip();
      if (!seen.add(name)) throw new IllegalArgumentException("The rule has " + name + " twice");
      switch (name) {
        case "FREQ" -> frequency = frequency(value);
        case "INTERVAL" -> interval = number(name, value, 1, Integer.MAX_VALUE);
        case "COUNT" -> count = number(name, value, 1, Integer.MAX_VALUE);
        case "UNTIL" -> until = untilTime(value);
        case "BYSECOND" -> bySecond = numbers(name, value, 0, 60, false);
        case "BYMINUTE" -> byMinute = numbers(name, value, 0, 59, false);
        case "BYHOUR" -> byHour = numbers(name, value, 0, 23, false);
        case "BYDAY" -> byDay = weekdays(value);
        case "BYMONTHDAY" -> byMonthDay = numbers(name, value, 1, 31, true);
        case "BYYEARDAY" -> byYearDay = numbers(name, value, 1, 366, true);
        case "BYWEEKNO" -> byWeekNo = numbers(name, value, 1, 53, true);
        case "BYMONTH" -> byMonth = numbers(name, value, 1, 12, false);
        case "BYSETPOS" -> bySetPos = numbers(name, value, 1, 366, true);
        case "WKST" -> weekStart = weekday(value);
        case "RSCALE" -> {
          if (!value.equalsIgnoreCase("GREGORIAN")) {
            throw new IllegalArgumentException("RSCALE=" + value + " (RFC 7529) is not supported, only GREGORIAN");
          }
        }
        case "SKIP" -> {
          if (!value.equalsIgnoreCase("OMIT")) {
            throw new IllegalArgumentException("SKIP=" + value + " (RFC 7529) is not supported, only OMIT");
          }
        }
        default -> {
          if (!name.startsWith("X-")) {
            throw new IllegalArgumentException("The rule part " + Problems.quote(part) + " is not one RFC 5545 has");
          }
        }
      }
    }
    if (frequency == null) throw new IllegalArgumentException("The rule has no FREQ");
    if (count != null && until != null) {
      soft.accept("The rule has both COUNT and UNTIL, which RFC 5545 does not allow", "UNTIL is left out");
      until = null;
    }
    var f = frequency;
    if (byWeekNo.length > 0 && f != Frequency.YEARLY) {
      soft.accept("BYWEEKNO is only for FREQ=YEARLY", "it is left out");
      byWeekNo = new int[0];
    }
    if (byYearDay.length > 0 && (f == Frequency.DAILY || f == Frequency.WEEKLY || f == Frequency.MONTHLY)) {
      soft.accept("BYYEARDAY is not for FREQ=" + f, "it is left out");
      byYearDay = new int[0];
    }
    if (byMonthDay.length > 0 && f == Frequency.WEEKLY) {
      soft.accept("BYMONTHDAY is not for FREQ=WEEKLY", "it is left out");
      byMonthDay = new int[0];
    }
    boolean ordinals = byDay.stream().anyMatch(d -> d.n() != 0);
    if (ordinals && ((f != Frequency.MONTHLY && f != Frequency.YEARLY) || (f == Frequency.YEARLY && byWeekNo.length > 0))) {
      soft.accept("BYDAY with a number (such as 1MO) is only for FREQ=MONTHLY, or YEARLY without BYWEEKNO",
          "the numbers are left out");
      byDay = byDay.stream().map(d -> new WeekdayNum(0, d.day())).distinct().toList();
    }
    if (bySetPos.length > 0 && bySecond.length + byMinute.length + byHour.length + byDay.size() + byMonthDay.length
        + byYearDay.length + byWeekNo.length + byMonth.length == 0) {
      soft.accept("BYSETPOS needs another BYxxx part", "it is left out");
      bySetPos = new int[0];
    }
    return new DefaultIcalRecurrence(f, interval, count, until, text, bySecond, byMinute, byHour, byDay,
        byMonthDay, byYearDay, byWeekNo, byMonth, bySetPos, weekStart);
  }

  private static Frequency frequency(String value) {
    try {
      return Frequency.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("FREQ=" + value + " is not a frequency (such as DAILY or WEEKLY)");
    }
  }

  private static IcalTime untilTime(String value) {
    if (value.length() == 8) return new IcalTime.Date(Values.date(value));
    var local = Values.dateTime(Values.withoutZ(value));
    if (Values.utc(value)) return new IcalTime.DateTime(local.atZone(ZoneOffset.UTC));
    return new IcalTime.Floating(local);
  }

  private static int number(String name, String value, int min, int max) {
    try {
      int n = Integer.parseInt(value);
      if (n >= min && n <= max) return n;
    } catch (NumberFormatException e) {
      // Said below.
    }
    throw new IllegalArgumentException(name + "=" + value + " is not a number from " + min + (max == Integer.MAX_VALUE ? " up" : " to " + max));
  }

  private static int[] numbers(String name, String value, int min, int max, boolean negative) {
    var parts = Arrays.stream(value.split(",", -1)).map(String::strip).filter(p -> !p.isEmpty()).toList();
    if (parts.isEmpty()) throw new IllegalArgumentException(name + "= has no values");
    var out = new int[parts.size()];
    for (int i = 0; i < out.length; i++) {
      var p = parts.get(i);
      int n;
      try {
        n = Integer.parseInt(p.startsWith("+") ? p.substring(1) : p);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(name + "=" + value + " has " + Problems.quote(p) + ", which is not a number");
      }
      int abs = Math.abs(n);
      if (abs < min || abs > max || (n < 0 && !negative)) {
        throw new IllegalArgumentException(name + "=" + value + " has " + n + ", which is not "
            + (negative ? "from " + min + " to " + max + ", or -" + max + " to -" + min : "from " + min + " to " + max));
      }
      out[i] = n;
    }
    return Arrays.stream(out).distinct().sorted().toArray();
  }

  private static List<WeekdayNum> weekdays(String value) {
    var out = new ArrayList<WeekdayNum>();
    for (var raw : value.split(",", -1)) {
      var p = raw.strip().toUpperCase(Locale.ROOT);
      if (p.isEmpty()) continue;
      if (p.length() < 2) throw new IllegalArgumentException("BYDAY=" + value + " has " + Problems.quote(raw) + ", which is not a day");
      var day = weekday(p.substring(p.length() - 2));
      var num = p.substring(0, p.length() - 2);
      int n = 0;
      if (!num.isEmpty()) {
        try {
          n = Integer.parseInt(num.startsWith("+") ? num.substring(1) : num);
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException("BYDAY=" + value + " has " + Problems.quote(raw) + ", which is not a day");
        }
        if (n == 0 || Math.abs(n) > 53) {
          throw new IllegalArgumentException("BYDAY=" + value + " has " + Problems.quote(raw) + ", whose number is not from 1 to 53, or -53 to -1");
        }
      }
      var d = new WeekdayNum(n, day);
      if (!out.contains(d)) out.add(d);
    }
    if (out.isEmpty()) throw new IllegalArgumentException("BYDAY= has no days");
    return out;
  }

  private static DayOfWeek weekday(String s) {
    return switch (s.toUpperCase(Locale.ROOT)) {
      case "MO" -> DayOfWeek.MONDAY;
      case "TU" -> DayOfWeek.TUESDAY;
      case "WE" -> DayOfWeek.WEDNESDAY;
      case "TH" -> DayOfWeek.THURSDAY;
      case "FR" -> DayOfWeek.FRIDAY;
      case "SA" -> DayOfWeek.SATURDAY;
      case "SU" -> DayOfWeek.SUNDAY;
      default -> throw new IllegalArgumentException(Problems.quote(s) + " is not a day (MO, TU, WE, TH, FR, SA or SU)");
    };
  }

  /// Calls `out` with each start of the rule, in order: `start` first, as
  /// RFC 5545 counts it the first even when the rule wouldn't make it.
  ///
  /// Stops when `out` returns false, at the rule's `COUNT`, when `withinUntil`
  /// says a start is past `UNTIL`, or once starts would be after `to`. Starts
  /// before `from` may be left out, so that a rule with no `COUNT` need not be
  /// worked out from its first start.
  ///
  /// @return false if it stopped because it took [#MAX_STEPS]
  boolean expand(
      LocalDateTime start,
      boolean dates,
      LocalDateTime from,
      LocalDateTime to,
      Predicate<LocalDateTime> withinUntil,
      Predicate<LocalDateTime> out) {
    var run = new Run(start, dates, to, withinUntil, out);
    // Without a COUNT, or when each period has one start, the periods
    // before the window need not be worked out.
    boolean skip = from.isAfter(start) && (count == null || run.onePerPeriod());
    if (skip && count != null) {
      run.emitted = run.startsBefore(from);
      if (run.emitted >= count) return true;
    } else if (!run.emit(start)) {
      return true;
    }
    var begin = skip ? from : start;
    return frequency.compareTo(Frequency.DAILY) >= 0 ? run.periods(begin) : run.subDaily(begin);
  }

  /// One expansion: what depends on the start, and how far it has got.
  private final class Run {
    final LocalDateTime start;
    final LocalDate startDay;
    final boolean dates;
    final LocalDateTime to;
    final Predicate<LocalDateTime> withinUntil;
    final Predicate<LocalDateTime> out;
    final int[] hours;
    final int[] minutes;
    final int[] seconds;
    final int[] months;
    final int[] monthDays;
    final List<WeekdayNum> days;
    int emitted;
    long steps;
    boolean done;

    Run(LocalDateTime start, boolean dates, LocalDateTime to, Predicate<LocalDateTime> withinUntil, Predicate<LocalDateTime> out) {
      this.start = start;
      this.startDay = start.toLocalDate();
      this.dates = dates;
      this.to = to;
      this.withinUntil = withinUntil;
      this.out = out;
      hours = byHour.length > 0 ? byHour : new int[] {start.getHour()};
      minutes = byMinute.length > 0 ? byMinute : new int[] {start.getMinute()};
      seconds = bySecond.length > 0 ? bySecond : new int[] {start.getSecond()};
      // RFC 5545's defaults, from the start, for a rule with no day parts.
      boolean dayParts = byWeekNo.length + byYearDay.length + byMonthDay.length + byDay.size() > 0;
      int[] months = byMonth;
      int[] monthDays = byMonthDay;
      List<WeekdayNum> days = byDay;
      if (frequency == Frequency.YEARLY && byWeekNo.length == 0 && byYearDay.length == 0) {
        // A yearly day of the month is in the start's month, as libical and
        // ICAL.js read it.
        if (months.length == 0 && monthDays.length > 0) months = new int[] {startDay.getMonthValue()};
      }
      if (frequency == Frequency.YEARLY && byWeekNo.length > 0 && byDay.isEmpty() && byMonthDay.length == 0
          && byYearDay.length == 0) {
        // A week number alone is the start's day of the week in it.
        days = List.of(new WeekdayNum(0, startDay.getDayOfWeek()));
      }
      if (!dayParts) {
        switch (frequency) {
          case YEARLY -> {
            if (months.length == 0) months = new int[] {startDay.getMonthValue()};
            monthDays = new int[] {startDay.getDayOfMonth()};
          }
          case MONTHLY -> monthDays = new int[] {startDay.getDayOfMonth()};
          case WEEKLY -> days = List.of(new WeekdayNum(0, startDay.getDayOfWeek()));
          default -> {}
        }
      }
      this.months = months;
      this.monthDays = monthDays;
      this.days = days;
    }

    /// Whether each period has exactly one start: the rule has no `BYxxx`
    /// parts, and its start's day is in every month or year.
    boolean onePerPeriod() {
      if (bySecond.length + byMinute.length + byHour.length + byDay.size() + byMonthDay.length + byYearDay.length
          + byWeekNo.length + byMonth.length + bySetPos.length > 0) {
        return false;
      }
      return switch (frequency) {
        case MONTHLY -> startDay.getDayOfMonth() <= 28;
        case YEARLY -> !(startDay.getMonthValue() == 2 && startDay.getDayOfMonth() == 29);
        default -> true;
      };
    }

    /// How many starts there are before the period [#periods] or [#subDaily]
    /// would begin at for `from`, when each period has one.
    int startsBefore(LocalDateTime from) {
      long n;
      if (frequency.compareTo(Frequency.DAILY) >= 0) {
        n = Math.max(0, Math.floorDiv(periodIndex(from.toLocalDate()), interval));
      } else {
        long step = unitSeconds() * interval;
        long base = periodBase().toEpochSecond(ZoneOffset.UTC);
        long dayStart = from.toLocalDate().atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        n = Math.max(0, Math.ceilDiv(dayStart - base, step));
      }
      return (int) Math.min(n, Integer.MAX_VALUE);
    }

    long unitSeconds() {
      return switch (frequency) {
        case HOURLY -> 3600;
        case MINUTELY -> 60;
        default -> 1;
      };
    }

    /// The start of the first period of a rule more frequent than daily.
    LocalDateTime periodBase() {
      return start.truncatedTo(frequency == Frequency.HOURLY ? ChronoUnit.HOURS
          : frequency == Frequency.MINUTELY ? ChronoUnit.MINUTES : ChronoUnit.SECONDS);
    }

    /// Gives `t` to `out` unless the rule is over; false once it is.
    boolean emit(LocalDateTime t) {
      if (done) return false;
      if (emitted > 0 && !withinUntil.test(t)) {
        done = true;
        return false;
      }
      emitted++;
      if (!out.test(t) || (count != null && emitted >= count)) {
        done = true;
        return false;
      }
      return true;
    }

    /// The starts of one period, in order, with `BYSETPOS` applied.
    boolean emitAll(List<LocalDateTime> candidates) {
      var chosen = bySetPos.length == 0 ? candidates : setPos(candidates);
      for (var c : chosen) {
        if (!c.isAfter(start)) continue;
        if (!emit(c)) return false;
      }
      return true;
    }

    /// Yearly, monthly, weekly and daily rules: one period at a time.
    boolean periods(LocalDateTime from) {
      long p = Math.max(0, Math.floorDiv(periodIndex(from.toLocalDate()), interval) * (long) interval);
      var times = dates ? List.of(LocalTime.MIDNIGHT) : times(hours, minutes, seconds);
      for (; ; p += interval) {
        if (++steps > MAX_STEPS) return false;
        var period = periodDays(p);
        if (period == null || period.getFirst().atStartOfDay().isAfter(to)) return true;
        var candidates = new ArrayList<LocalDateTime>();
        for (var d : period) {
          if (!matches(d)) continue;
          for (var t : times) candidates.add(d.atTime(t));
        }
        if (!emitAll(candidates)) return true;
      }
    }

    /// Hourly, minutely and secondly rules: day by day, skipping days that
    /// can't match, then each period of the day on the rule's grid.
    boolean subDaily(LocalDateTime from) {
      long baseSecond = periodBase().toEpochSecond(ZoneOffset.UTC);
      long step = unitSeconds() * interval;
      for (var day = from.toLocalDate(); !day.atStartOfDay().isAfter(to); day = day.plusDays(1)) {
        if (++steps > MAX_STEPS) return false;
        if (!matches(day)) continue;
        long dayStart = day.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        long first = Math.max(0, Math.ceilDiv(dayStart - baseSecond, step)) * step + baseSecond;
        for (long s = first; s < dayStart + 86400; s += step) {
          if (++steps > MAX_STEPS) return false;
          var period = LocalDateTime.ofEpochSecond(s, 0, ZoneOffset.UTC);
          if (period.isAfter(to)) return true;
          if (!limited(byHour, period.getHour())) continue;
          if (frequency != Frequency.HOURLY && !limited(byMinute, period.getMinute())) continue;
          if (frequency == Frequency.SECONDLY && !limited(bySecond, period.getSecond())) continue;
          int[] ms = frequency == Frequency.HOURLY ? minutes : new int[] {period.getMinute()};
          int[] ss = frequency == Frequency.SECONDLY ? new int[] {period.getSecond()} : seconds;
          var candidates = new ArrayList<LocalDateTime>();
          for (var t : times(new int[] {period.getHour()}, ms, ss)) candidates.add(day.atTime(t));
          if (!emitAll(candidates)) return true;
        }
      }
      return true;
    }

    /// The period number of a day: 0 for the period the start is in. A
    /// yearly rule by week number goes by the years its weeks are numbered
    /// in, as libical does: a week 1 can start in December.
    long periodIndex(LocalDate day) {
      return switch (frequency) {
        case YEARLY -> byWeekNo.length > 0 ? weekYear(day) - (long) weekYear(startDay) : day.getYear() - (long) startDay.getYear();
        case MONTHLY -> ChronoUnit.MONTHS.between(startDay.withDayOfMonth(1), day.withDayOfMonth(1));
        case WEEKLY -> Math.floorDiv(ChronoUnit.DAYS.between(weekOf(startDay), day), 7);
        default -> ChronoUnit.DAYS.between(startDay, day);
      };
    }

    /// The days of period `p`, or null past the end of time.
    @Nullable List<LocalDate> periodDays(long p) {
      try {
        return switch (frequency) {
          case YEARLY -> {
            if (byWeekNo.length > 0) {
              int y = Math.toIntExact(weekYear(startDay) + p);
              yield range(week1(y), week1(y + 1));
            }
            var y = LocalDate.of(Math.toIntExact(startDay.getYear() + p), 1, 1);
            yield range(y, y.plusYears(1));
          }
          case MONTHLY -> {
            var m = startDay.withDayOfMonth(1).plusMonths(p);
            yield range(m, m.plusMonths(1));
          }
          case WEEKLY -> {
            var w = weekOf(startDay).plusWeeks(p);
            yield range(w, w.plusWeeks(1));
          }
          default -> List.of(startDay.plusDays(p));
        };
      } catch (DateTimeException | ArithmeticException e) {
        return null;
      }
    }

    /// Whether a day is one the rule's day parts allow.
    boolean matches(LocalDate d) {
      if (months.length > 0 && !contains(months, d.getMonthValue())) return false;
      if (byWeekNo.length > 0 && !weekNoMatches(d)) return false;
      if (byYearDay.length > 0 && !nthMatches(byYearDay, d.getDayOfYear(), d.lengthOfYear())) return false;
      if (monthDays.length > 0 && !nthMatches(monthDays, d.getDayOfMonth(), d.lengthOfMonth())) return false;
      if (!days.isEmpty()) {
        boolean inMonth = frequency == Frequency.MONTHLY || (frequency == Frequency.YEARLY && byMonth.length > 0);
        int pos = inMonth ? d.getDayOfMonth() : d.getDayOfYear();
        int len = inMonth ? d.lengthOfMonth() : d.lengthOfYear();
        for (var w : days) {
          if (w.day() != d.getDayOfWeek()) continue;
          if (w.n() == 0) return true;
          int nth = w.n() > 0 ? (pos - 1) / 7 + 1 : -((len - pos) / 7 + 1);
          if (nth == w.n()) return true;
        }
        return false;
      }
      return true;
    }
  }

  private LocalDate weekOf(LocalDate d) {
    return d.with(TemporalAdjusters.previousOrSame(weekStart));
  }

  /// Whether a day is in one of `BYWEEKNO`'s weeks: weeks start on `WKST`,
  /// and week 1 is the first with at least four days of its year.
  private boolean weekNoMatches(LocalDate d) {
    int year = weekYear(d);
    var w1 = week1(year);
    int week = (int) (ChronoUnit.DAYS.between(w1, d) / 7) + 1;
    int weeks = (int) (ChronoUnit.DAYS.between(w1, week1(year + 1)) / 7);
    for (int n : byWeekNo) {
      if (n > 0 ? week == n : week == weeks + 1 + n) return true;
    }
    return false;
  }

  /// The year whose weeks a day's week is numbered in.
  private int weekYear(LocalDate d) {
    int year = d.getYear();
    if (d.isBefore(week1(year))) return year - 1;
    return d.isBefore(week1(year + 1)) ? year : year + 1;
  }

  /// The first day of week 1: the week that has January 4.
  private LocalDate week1(int year) {
    return LocalDate.of(year, 1, 4).with(TemporalAdjusters.previousOrSame(weekStart));
  }

  /// Whether `pos` (counting from 1) is one of `values`, where a negative
  /// value counts back from `len`.
  private static boolean nthMatches(int[] values, int pos, int len) {
    for (int n : values) {
      if (n > 0 ? pos == n : pos == len + 1 + n) return true;
    }
    return false;
  }

  private static boolean limited(int[] values, int v) {
    return values.length == 0 || contains(values, v);
  }

  private static List<LocalDate> range(LocalDate from, LocalDate until) {
    var out = new ArrayList<LocalDate>();
    for (var d = from; d.isBefore(until); d = d.plusDays(1)) out.add(d);
    return out;
  }

  private static List<LocalTime> times(int[] hours, int[] minutes, int[] seconds) {
    var out = new ArrayList<LocalTime>();
    for (int h : hours) {
      for (int m : minutes) {
        for (int s : seconds) out.add(LocalTime.of(h, m, Math.min(s, 59)));
      }
    }
    out.sort(null);
    return out;
  }

  private List<LocalDateTime> setPos(List<LocalDateTime> candidates) {
    var chosen = new TreeSet<LocalDateTime>();
    int size = candidates.size();
    for (int n : bySetPos) {
      int i = n > 0 ? n - 1 : size + n;
      if (i >= 0 && i < size) chosen.add(candidates.get(i));
    }
    return List.copyOf(chosen);
  }

  private static boolean contains(int[] values, int v) {
    for (int x : values) {
      if (x == v) return true;
    }
    return false;
  }
}
