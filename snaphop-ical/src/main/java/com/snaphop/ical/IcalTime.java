package com.snaphop.ical;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;

/// A time in a calendar: a whole date, or a date-time in a zone.
///
/// iCalendar writes a date-time three ways: in UTC, with a `TZID`, or floating
/// (no zone, meaning the same wall clock time everywhere). All three are a
/// [DateTime]. A floating time is placed in the zone set by
/// [IcalReader.Builder#zone(java.time.ZoneId)].
///
/// Switch over the two kinds:
///
/// ```java
/// String text = switch (time) {
///   case IcalTime.Date d -> d.date().toString();
///   case IcalTime.DateTime t -> t.dateTime().toString();
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

  /// A date and time of day in a zone.
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
}
