package com.snaphop.ical;

import java.time.LocalDate;
import java.util.List;

/// A calendar read by an [IcalReader]: one `VCALENDAR`.
///
/// Immutable and thread-safe.
public sealed interface IcalCalendar {

  /// The events of the calendar, in the order they were written.
  ///
  /// An event that changes one instance of a repeating event has a
  /// [IcalEvent#recurrenceIdOrNull()] and is listed here too.
  ///
  /// @return the events, never null
  List<IcalEvent> events();

  /// The occurrences of every event in a window of days, ordered by start.
  ///
  /// The days are in the reader's zone. An occurrence is in the window when
  /// any part of it falls on or after `from` and before `until`.
  ///
  /// Repeating events are expanded by their rule, skipping their exception
  /// dates. An instance changed by another event is replaced by that event.
  /// Cancelled instances are kept; check [IcalEvent#cancelled()].
  ///
  /// A calendar can be asked for any number of windows.
  ///
  /// @param from the first day
  /// @param until the day after the last day
  /// @return the occurrences, never null
  /// @throws IllegalArgumentException if `until` is not after `from`
  List<IcalOccurrence> occurrences(LocalDate from, LocalDate until);

  /// What [IcalReader.Mode#LAX] skipped, one message each, with line numbers.
  ///
  /// @return the warnings, always empty in [IcalReader.Mode#STRICT]
  List<String> warnings();
}

record DefaultIcalCalendar(List<IcalEvent> events, List<String> warnings, java.time.ZoneId zone)
    implements IcalCalendar {

  DefaultIcalCalendar {
    events = List.copyOf(events);
    warnings = List.copyOf(warnings);
  }

  @Override
  public List<IcalOccurrence> occurrences(LocalDate from, LocalDate until) {
    if (!until.isAfter(from)) {
      throw new IllegalArgumentException(
          "A window must end after it starts. from: " + from + ", until: " + until);
    }
    throw new UnsupportedOperationException("Expanding occurrences is not implemented yet.");
  }
}
