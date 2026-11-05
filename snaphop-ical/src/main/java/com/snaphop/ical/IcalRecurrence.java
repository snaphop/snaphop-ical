package com.snaphop.ical;

import org.jspecify.annotations.Nullable;

/// How an event repeats: an `RRULE` value such as `FREQ=WEEKLY;BYDAY=MO,WE`.
///
/// Immutable and thread-safe.
public sealed interface IcalRecurrence {

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

  /// The rule as it was written, without the `RRULE:` name.
  ///
  /// @return the rule text, never null
  String text();

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
}

record DefaultIcalRecurrence(
    IcalRecurrence.Frequency frequency,
    int interval,
    @Nullable Integer countOrNull,
    @Nullable IcalTime untilOrNull,
    String text)
    implements IcalRecurrence {}
