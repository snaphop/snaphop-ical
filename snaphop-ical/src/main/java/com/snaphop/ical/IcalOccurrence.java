package com.snaphop.ical;

import java.time.LocalDate;
import java.time.LocalTime;
import org.jspecify.annotations.Nullable;

/// One instance of an event in a window of days
/// ([IcalCalendar#occurrences(java.time.LocalDate, java.time.LocalDate)]).
///
/// An event that does not repeat has one occurrence, if it falls in the window.
///
/// Besides the times as written ([#start()], [#end()]), an occurrence gives
/// what a calendar draws in the reader's zone: the days it is on, and its
/// times of day unless it is all day. A floating time is taken as already in
/// that zone.
///
/// Immutable and thread-safe.
public sealed interface IcalOccurrence {

  /// The event this is an instance of.
  ///
  /// If another event changes this instance, it is that event.
  ///
  /// @return the event, never null
  IcalEvent event();

  /// An id for this instance that stays the same each time the calendar is
  /// read, to match it with what was read before.
  ///
  /// - For an event that does not repeat, it is the `UID`.
  /// - For an instance of a repeating event, it is the `UID`, an `@`, and
  ///   [#recurrenceId()]: a date (`2026-11-26`), an instant in UTC
  ///   (`2026-11-02T22:00:00Z`) for a time in a zone, or a wall clock time
  ///   (`2026-11-03T17:30:00`) for a floating time. It is the same however
  ///   the time was written, and stays the same when the instance is moved.
  ///
  /// @return the id, never null
  String id();

  /// When this instance starts.
  ///
  /// @return the start, never null
  IcalTime start();

  /// When this instance ends, exclusive.
  ///
  /// @return the end, never null
  IcalTime end();

  /// The start the event's rule gave this instance, before any change moved it.
  ///
  /// This identifies the instance, as `RECURRENCE-ID` does, written as the
  /// repeating event's start is (a date, a time in a zone, or floating). For
  /// an event that does not repeat it is the event's start.
  ///
  /// @return the original start, never null
  IcalTime recurrenceId();

  /// Whether this instance takes whole days rather than a time of day.
  ///
  /// @return true if [#start()] is an [IcalTime.Date]
  default boolean allDay() {
    return start() instanceof IcalTime.Date;
  }

  /// The day this instance starts, in the reader's zone.
  ///
  /// @return the first day, never null
  LocalDate startDay();

  /// The last day this instance is on, in the reader's zone, inclusive.
  ///
  /// For an all-day instance it is the day before its end. For one at a time
  /// of day it is the day of its end, unless it ends at midnight: an event
  /// from 22:00 to 00:00 is on one day.
  ///
  /// @return the last day, the same as [#startDay()] for an instance on one day
  LocalDate lastDay();

  /// The time of day this instance starts, in the reader's zone.
  ///
  /// @return the time, or null if it is all day
  @Nullable LocalTime startTimeOrNull();

  /// The time of day this instance ends, in the reader's zone.
  ///
  /// @return the time, which is the start time for an instance with no
  ///     length, or null if it is all day
  @Nullable LocalTime endTimeOrNull();
}

record DefaultIcalOccurrence(
    IcalEvent event,
    String id,
    IcalTime start,
    IcalTime end,
    IcalTime recurrenceId,
    LocalDate startDay,
    LocalDate lastDay,
    @Nullable LocalTime startTimeOrNull,
    @Nullable LocalTime endTimeOrNull)
    implements IcalOccurrence {}
