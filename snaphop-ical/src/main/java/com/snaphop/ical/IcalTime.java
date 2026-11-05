package com.snaphop.ical;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Objects;

/// A time in a calendar, as it was written:
///
/// - [Date]: a whole day.
/// - [DateTime]: a date-time in a zone, written in UTC or with a `TZID`.
/// - [Floating]: a date-time with no zone, meaning the same wall clock time
///   wherever it is read. It stays floating here; the reader's zone places
///   it only when it must be ordered among other times or put in a window.
///
/// Switch over the kinds:
///
/// ```java
/// String text = switch (time) {
///   case IcalTime.Date d -> d.date().toString();
///   case IcalTime.DateTime t -> t.dateTime().toString();
///   case IcalTime.Floating f -> f.dateTime() + " (floating)";
/// };
/// ```
public sealed interface IcalTime {

  /// A whole day, as all-day events use.
  ///
  /// @param date the day
  record Date(LocalDate date) implements IcalTime {

    /// Makes a date.
    ///
    /// @param date the day
    public Date {
      Objects.requireNonNull(date, "date");
    }
  }

  /// A date and time of day in a zone, written in UTC or with a `TZID`.
  ///
  /// @param dateTime the date-time and its zone
  record DateTime(ZonedDateTime dateTime) implements IcalTime {

    /// Makes a date-time.
    ///
    /// @param dateTime the date-time and its zone
    public DateTime {
      Objects.requireNonNull(dateTime, "dateTime");
    }
  }

  /// A date and time of day written without a zone: the same wall clock time
  /// wherever it is read.
  ///
  /// @param dateTime the wall clock date-time
  record Floating(LocalDateTime dateTime) implements IcalTime {

    /// Makes a floating date-time.
    ///
    /// @param dateTime the wall clock date-time
    public Floating {
      Objects.requireNonNull(dateTime, "dateTime");
    }
  }
}
