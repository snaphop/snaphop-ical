package com.snaphop.ical;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/// How something repeats: an `RRULE` value (RFC 5545 section 3.3.10) such as
/// `FREQ=WEEKLY;BYDAY=TU,FR`.
///
/// A rule is read with [#parse(String)], made with [#builder(Frequency)], or
/// comes with an [IcalEvent]. Given a first start, it makes the starts after
/// it:
///
/// ```java
/// var rule = IcalRecurrence.parse("FREQ=WEEKLY;BYDAY=TU,FR");
/// var start = new IcalTime.Date(LocalDate.of(2026, 11, 3));
/// rule.starts(start).limit(5).toList();              // November 3, 6, 10, 13 and 17
/// rule.nextOrNull(start, new IcalTime.Date(today));  // the first after today
/// rule.describeOrNull(start);                        // "Every week on Tuesday and Friday"
/// ```
///
/// Immutable and thread-safe.
public sealed interface IcalRecurrence permits DefaultIcalRecurrence {

  /// Reads a rule, as strictly as [IcalReader.Mode#STRICT] reads one in a
  /// calendar.
  ///
  /// @param rule the rule, such as `FREQ=WEEKLY;BYDAY=TU,FR`, with or without
  ///        `RRULE:` before it
  /// @return the rule
  /// @throws IcalParseException if the rule is not one RFC 5545 allows, saying
  ///         why, with [IcalParseException#line()] 0
  static IcalRecurrence parse(String rule) {
    return DefaultIcalRecurrence.parseStrictly(rule);
  }

  /// Starts making a rule.
  ///
  /// ```java
  /// IcalRecurrence.builder(IcalRecurrence.Frequency.MONTHLY)
  ///     .byDay(new IcalRecurrence.Weekday(2, DayOfWeek.TUESDAY))
  ///     .build(); // FREQ=MONTHLY;BYDAY=2TU
  /// ```
  ///
  /// @param frequency how often it repeats
  /// @return a builder of a rule with no other parts
  static Builder builder(Frequency frequency) {
    return new Builder(frequency);
  }

  /// How often the rule repeats: its `FREQ`.
  ///
  /// @return the frequency, never null
  Frequency frequency();

  /// How many [#frequency()] periods pass between instances: its `INTERVAL`.
  ///
  /// @return the interval, 1 if the rule has none
  int interval();

  /// How many instances the rule makes, at most: its `COUNT`.
  ///
  /// @return the count, or null if the rule has none
  @Nullable Integer countOrNull();

  /// The last time an instance may start: its `UNTIL`.
  ///
  /// @return the end of the rule, or null if the rule has none
  @Nullable IcalTime untilOrNull();

  /// The seconds of the minute it repeats at: its `BYSECOND`.
  ///
  /// @return the seconds in order, empty if the rule has none
  List<Integer> bySecond();

  /// The minutes of the hour it repeats at: its `BYMINUTE`.
  ///
  /// @return the minutes in order, empty if the rule has none
  List<Integer> byMinute();

  /// The hours of the day it repeats at: its `BYHOUR`.
  ///
  /// @return the hours in order, empty if the rule has none
  List<Integer> byHour();

  /// The days of the week it repeats on: its `BYDAY`.
  ///
  /// @return the days, in the order written, empty if the rule has none
  List<Weekday> byDay();

  /// The days of the month it repeats on: its `BYMONTHDAY`. A negative day
  /// counts from the end of the month: -1 is its last day.
  ///
  /// @return the days in order, empty if the rule has none
  List<Integer> byMonthDay();

  /// The days of the year it repeats on: its `BYYEARDAY`, negative from
  /// the end of the year.
  ///
  /// @return the days in order, empty if the rule has none
  List<Integer> byYearDay();

  /// The weeks of the year it repeats in: its `BYWEEKNO`, negative from the
  /// end of the year.
  ///
  /// @return the weeks in order, empty if the rule has none
  List<Integer> byWeekNo();

  /// The months it repeats in, 1 for January: its `BYMONTH`.
  ///
  /// @return the months in order, empty if the rule has none
  List<Integer> byMonth();

  /// Which of each period's instances it keeps: its `BYSETPOS`, negative
  /// from the last.
  ///
  /// @return the positions in order, empty if the rule has none
  List<Integer> bySetPos();

  /// The day weeks start on: its `WKST`.
  ///
  /// @return the day, [DayOfWeek#MONDAY] if the rule has none
  DayOfWeek weekStart();

  /// The rule as text, without the `RRULE:` name: as it was written, for a
  /// rule read; in RFC 5545's usual order, for one built.
  ///
  /// @return the rule text, never null
  String text();

  /// The starts the rule makes from `start`, in order, worked out as they
  /// are asked for.
  ///
  /// The first is `start` itself, which RFC 5545 counts as the first
  /// instance even when the rule wouldn't make it. Starts are worked out on
  /// the wall clock of `start`: a weekly rule at 09:00 in New York stays at
  /// 09:00 when daylight saving time begins. A time that the change skips is
  /// moved later by the length of the gap.
  ///
  /// The stream ends at the rule's `COUNT` or `UNTIL`. Without either it ends
  /// only when the rule can make no more starts, as
  /// `FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=30` makes none after the first, so
  /// limit it, as with [Stream#limit(long)] or
  /// [Stream#takeWhile(java.util.function.Predicate)].
  ///
  /// @param start the first start, which places the rule's starts: a date
  ///        gives dates, and a date-time gives date-times in its zone
  /// @return the starts, in the form of `start`
  Stream<IcalTime> starts(IcalTime start);

  /// The first start after a time: for a task due on a repeating date, when
  /// it is due next.
  ///
  /// It is worked out without the starts before `after`, unless the rule
  /// has a `COUNT`, which they count toward.
  ///
  /// @param start the first start, as for [#starts(IcalTime)]
  /// @param after the time the start must be after: a date if `start` is
  ///        one, a date-time in any zone if `start` is one, or a floating
  ///        date-time if `start` is one
  /// @return the first start after `after`, or null if the rule has none
  /// @throws IllegalArgumentException if `after` is not the same kind of
  ///         time as `start`
  @Nullable IcalTime nextOrNull(IcalTime start, IcalTime after);

  /// The rule in plain English words, as a person would say it: "Every 2
  /// weeks on Monday and Thursday", "Every month on the last Friday, 10
  /// times".
  ///
  /// `start` gives what the rule leaves to it: `FREQ=YEARLY` from November 3
  /// is "Every year on November 3".
  ///
  /// @param start the first start
  /// @return the words, or null for a rule they would not be clear for:
  ///         one with `BYSETPOS`, `BYWEEKNO`, `BYYEARDAY`, `BYSECOND`, more
  ///         than one time of day, or a frequency more often than daily
  @Nullable String describeOrNull(IcalTime start);

  /// The `FREQ` values of RFC 5545.
  enum Frequency {
    /// Every second.
    SECONDLY,
    /// Every minute.
    MINUTELY,
    /// Every hour.
    HOURLY,
    /// Every day.
    DAILY,
    /// Every week.
    WEEKLY,
    /// Every month.
    MONTHLY,
    /// Every year.
    YEARLY
  }

  /// A `BYDAY` value: a day of the week, and which one of the month or year
  /// it is. `2TU` is the second Tuesday, `-1FR` the last Friday, and `MO`
  /// every Monday.
  ///
  /// @param n which one, from 1 to 53 or -53 to -1, or 0 for every one
  /// @param day the day of the week
  record Weekday(int n, DayOfWeek day) {

    /// Makes a `BYDAY` value.
    ///
    /// @param n which one, from 1 to 53 or -53 to -1, or 0 for every one
    /// @param day the day of the week
    /// @throws IllegalArgumentException if `n` is not from -53 to 53
    public Weekday {
      Objects.requireNonNull(day, "day");
      if (Math.abs(n) > 53) {
        throw new IllegalArgumentException("A BYDAY number is from 1 to 53, or -53 to -1, or 0 for every one: " + n);
      }
    }

    /// The value as `BYDAY` writes it, such as `2TU`.
    ///
    /// @return the text
    @Override
    public String toString() {
      return (n == 0 ? "" : Integer.toString(n)) + day.name().substring(0, 2);
    }
  }

  /// Makes an [IcalRecurrence] from its parts, checked as [#parse(String)]
  /// checks a rule's text.
  final class Builder {

    private final Frequency frequency;
    private int interval = 1;
    private @Nullable Integer count;
    private @Nullable IcalTime until;
    private int[] bySecond = {};
    private int[] byMinute = {};
    private int[] byHour = {};
    private List<Weekday> byDay = List.of();
    private int[] byMonthDay = {};
    private int[] byYearDay = {};
    private int[] byWeekNo = {};
    private int[] byMonth = {};
    private int[] bySetPos = {};
    private @Nullable DayOfWeek weekStart;

    private Builder(Frequency frequency) {
      this.frequency = Objects.requireNonNull(frequency, "frequency");
    }

    /// Sets `INTERVAL`.
    ///
    /// @param interval how many periods pass between instances, 1 or more
    /// @return this
    public Builder interval(int interval) {
      this.interval = interval;
      return this;
    }

    /// Sets `COUNT`, in place of any `UNTIL`.
    ///
    /// @param count how many instances, 1 or more
    /// @return this
    public Builder count(int count) {
      this.count = count;
      this.until = null;
      return this;
    }

    /// Sets `UNTIL`, in place of any `COUNT`.
    ///
    /// RFC 5545 requires it in the form of the start: a date for a date, a
    /// date-time in UTC for a date-time in a zone (a date-time in any zone is
    /// written in UTC), and a floating date-time for a floating one.
    ///
    /// @param until the last time an instance may start
    /// @return this
    public Builder until(IcalTime until) {
      this.until = Objects.requireNonNull(until, "until");
      this.count = null;
      return this;
    }

    /// Sets `BYSECOND`.
    ///
    /// @param seconds from 0 to 60
    /// @return this
    public Builder bySecond(int... seconds) {
      this.bySecond = seconds.clone();
      return this;
    }

    /// Sets `BYMINUTE`.
    ///
    /// @param minutes from 0 to 59
    /// @return this
    public Builder byMinute(int... minutes) {
      this.byMinute = minutes.clone();
      return this;
    }

    /// Sets `BYHOUR`.
    ///
    /// @param hours from 0 to 23
    /// @return this
    public Builder byHour(int... hours) {
      this.byHour = hours.clone();
      return this;
    }

    /// Sets `BYDAY` to every one of some days of the week.
    ///
    /// @param days the days
    /// @return this
    public Builder byDay(DayOfWeek... days) {
      this.byDay = Arrays.stream(days).map(d -> new Weekday(0, d)).toList();
      return this;
    }

    /// Sets `BYDAY`.
    ///
    /// @param days the days, each perhaps which one of the month or year
    /// @return this
    public Builder byDay(Weekday... days) {
      this.byDay = List.of(days);
      return this;
    }

    /// Sets `BYMONTHDAY`.
    ///
    /// @param days from 1 to 31, or -31 to -1 from the end of the month
    /// @return this
    public Builder byMonthDay(int... days) {
      this.byMonthDay = days.clone();
      return this;
    }

    /// Sets `BYYEARDAY`.
    ///
    /// @param days from 1 to 366, or -366 to -1 from the end of the year
    /// @return this
    public Builder byYearDay(int... days) {
      this.byYearDay = days.clone();
      return this;
    }

    /// Sets `BYWEEKNO`.
    ///
    /// @param weeks from 1 to 53, or -53 to -1 from the end of the year
    /// @return this
    public Builder byWeekNo(int... weeks) {
      this.byWeekNo = weeks.clone();
      return this;
    }

    /// Sets `BYMONTH`.
    ///
    /// @param months from 1 (January) to 12
    /// @return this
    public Builder byMonth(int... months) {
      this.byMonth = months.clone();
      return this;
    }

    /// Sets `BYSETPOS`.
    ///
    /// @param positions from 1 to 366, or -366 to -1 from the last
    /// @return this
    public Builder bySetPos(int... positions) {
      this.bySetPos = positions.clone();
      return this;
    }

    /// Sets `WKST`.
    ///
    /// @param day the day weeks start on
    /// @return this
    public Builder weekStart(DayOfWeek day) {
      this.weekStart = Objects.requireNonNull(day, "day");
      return this;
    }

    /// Makes the rule.
    ///
    /// @return the rule
    /// @throws IllegalArgumentException if the parts are not a rule RFC 5545
    ///         allows, saying why, as [#parse(String)] says it
    public IcalRecurrence build() {
      var parts = new ArrayList<String>();
      parts.add("FREQ=" + frequency);
      if (until != null) parts.add("UNTIL=" + Writing.value(until, true));
      if (count != null) parts.add("COUNT=" + count);
      if (interval != 1) parts.add("INTERVAL=" + interval);
      numbers(parts, "BYSECOND", bySecond);
      numbers(parts, "BYMINUTE", byMinute);
      numbers(parts, "BYHOUR", byHour);
      if (!byDay.isEmpty()) parts.add("BYDAY=" + String.join(",", byDay.stream().map(Weekday::toString).toList()));
      numbers(parts, "BYMONTHDAY", byMonthDay);
      numbers(parts, "BYYEARDAY", byYearDay);
      numbers(parts, "BYWEEKNO", byWeekNo);
      numbers(parts, "BYMONTH", byMonth);
      numbers(parts, "BYSETPOS", bySetPos);
      if (weekStart != null) parts.add("WKST=" + weekStart.name().substring(0, 2));
      var text = String.join(";", parts);
      try {
        return DefaultIcalRecurrence.parseStrictly(text);
      } catch (IcalParseException e) {
        throw new IllegalArgumentException(e.getMessage(), e);
      }
    }

    private static void numbers(List<String> parts, String name, int[] values) {
      if (values.length == 0) return;
      parts.add(name + "=" + String.join(",", Arrays.stream(values).mapToObj(Integer::toString).toList()));
    }
  }
}
