package com.snaphop.ical;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// One `VEVENT` of a calendar.
///
/// A repeating event is one event with a rule. Its instances are in
/// [IcalCalendar#occurrences()].
///
/// Immutable and thread-safe.
public sealed interface IcalEvent {

  /// The event's `UID`, which identifies it across versions of the calendar.
  ///
  /// @return the uid, never null
  String uid();

  /// The event's `SUMMARY`, its title.
  ///
  /// @return the summary, or an empty string if the event has none
  String summary();

  /// The event's `DESCRIPTION`.
  ///
  /// @return the description, or an empty string if the event has none
  String description();

  /// The event's `LOCATION`.
  ///
  /// @return the location, or an empty string if the event has none
  String location();

  /// When the event starts: its `DTSTART`.
  ///
  /// For a repeating event this is the start of its first instance.
  ///
  /// @return the start, never null
  IcalTime start();

  /// When the event ends, exclusive.
  ///
  /// This is `DTEND` if the event has one. Otherwise it is the start plus the
  /// event's `DURATION`. Without either, RFC 5545 makes an event that starts on
  /// a date last that whole day, and one that starts at a date-time end when it
  /// starts.
  ///
  /// @return the end, never null
  IcalTime end();

  /// Whether the event takes whole days rather than a time of day.
  ///
  /// @return true if [#start()] is an [IcalTime.Date]
  default boolean allDay() {
    return start() instanceof IcalTime.Date;
  }

  /// The event's `STATUS`.
  ///
  /// @return the status, [Status#NONE] if the event has none
  Status status();

  /// Whether the event is cancelled.
  ///
  /// @return true if [#status()] is [Status#CANCELLED]
  default boolean cancelled() {
    return status() == Status.CANCELLED;
  }

  /// How the event repeats: its `RRULE`.
  ///
  /// @return the rule, or null if the event does not repeat by rule
  @Nullable IcalRecurrence recurrenceOrNull();

  /// Instances of a repeating event that do not happen: its `EXDATE` values.
  ///
  /// @return the exception dates, in the order written, never null
  List<IcalTime> exceptionDates();

  /// Which instance of a repeating event this event changes: its `RECURRENCE-ID`.
  ///
  /// The value is that instance's original start. The event with the same
  /// [#uid()] and a rule is the one being changed.
  ///
  /// @return the original start of the changed instance, or null if this event
  ///     does not change an instance
  @Nullable IcalTime recurrenceIdOrNull();

  /// The `STATUS` values RFC 5545 allows for an event.
  enum Status {
    /// The event has no `STATUS`.
    NONE,
    /// `TENTATIVE`: the event may not happen.
    TENTATIVE,
    /// `CONFIRMED`: the event will happen.
    CONFIRMED,
    /// `CANCELLED`: the event will not happen.
    CANCELLED
  }
}

record DefaultIcalEvent(
    String uid,
    String summary,
    String description,
    String location,
    IcalTime start,
    IcalTime end,
    IcalEvent.Status status,
    @Nullable IcalRecurrence recurrenceOrNull,
    List<IcalTime> exceptionDates,
    @Nullable IcalTime recurrenceIdOrNull)
    implements IcalEvent {

  DefaultIcalEvent {
    exceptionDates = List.copyOf(exceptionDates);
  }
}
